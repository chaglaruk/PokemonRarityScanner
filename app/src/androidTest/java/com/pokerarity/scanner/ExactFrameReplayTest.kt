package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.service.reconcileSpeciesProfileEvidence
import com.pokerarity.scanner.util.ocr.ImagePreprocessor
import com.pokerarity.scanner.util.ocr.FrameOcrRequest
import com.pokerarity.scanner.util.ocr.OCRProcessor
import com.pokerarity.scanner.util.ocr.ScanConfidenceGate
import com.pokerarity.scanner.util.ocr.ScanConfidenceInput
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import com.pokerarity.scanner.util.ocr.ScreenRegions
import com.pokerarity.scanner.util.ocr.SpeciesRefiner
import com.pokerarity.scanner.util.ocr.VariantVisualSummary
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import com.pokerarity.scanner.util.vision.Phase2VariantFeatureMerger
import com.pokerarity.scanner.util.vision.VariantDecisionEngine
import com.pokerarity.scanner.util.vision.VisualFeatureDetector
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * DEBUG-ONLY diagnostic replay: pushes arbitrary bitmap files through the REAL production
 * recognition components and dumps complete stage-by-stage diagnostics.
 * Not a product test; lives only in the temporary diagnostic worktree.
 *
 * Input: target context cacheDir/replay_in/ (PNG files staged via adb push + run-as).
 * Args:  -e scale baseline900|native   (default baseline900 = production policy)
 *        -e only F01,F02               (optional frame filter by filename prefix)
 * Output: cacheDir/replay_out/report.json + per-frame crop PNGs (pulled via run-as).
 */
@RunWith(AndroidJUnit4::class)
class ExactFrameReplayTest {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @Test
    fun replayExactFrames() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()

        val scalePolicy = args.getString("scale") ?: "baseline900"
        val only = args.getString("only")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        val inDir = File(appContext.cacheDir, "replay_in")
        val outDir = File(appContext.cacheDir, "replay_out")
        outDir.deleteRecursively()
        outDir.mkdirs()

        val frames = inDir.listFiles { f -> f.extension.equals("png", true) }
            ?.sortedBy { it.name }
            ?.filter { frame -> only == null || only.any { p -> frame.name.startsWith(p) } }
            .orEmpty()
        org.junit.Assume.assumeTrue(
            "No replay input PNGs in ${inDir.absolutePath}",
            frames.isNotEmpty()
        )

        val ocrProcessor = OCRProcessor(appContext)
        val rarityCalculator = RarityCalculator(appContext)
        val speciesRefiner = SpeciesRefiner(appContext, rarityCalculator)
        val visualDetector = VisualFeatureDetector(appContext)
        val variantDecisionEngine = VariantDecisionEngine(appContext)
        val phase2VariantClassifier = Phase2VariantClassifier(appContext)
        val scanConfidenceGate = ScanConfidenceGate()
        val consistencyGate = ScanConsistencyGate(appContext, rarityCalculator)
        ocrProcessor.ensureInitialized()

        val reports = mutableListOf<Map<String, Any?>>()
        try {
            for (file in frames) {
                reports += try {
                    replayOne(
                        frameId = file.nameWithoutExtension,
                        file = file,
                        scalePolicy = scalePolicy,
                        ocrProcessor = ocrProcessor,
                        speciesRefiner = speciesRefiner,
                        variantDecisionEngine = variantDecisionEngine,
                        phase2VariantClassifier = phase2VariantClassifier,
                        visualDetector = visualDetector,
                        rarityCalculator = rarityCalculator,
                        scanConfidenceGate = scanConfidenceGate,
                        consistencyGate = consistencyGate,
                        outDir = outDir
                    )
                } catch (t: Throwable) {
                    Log.e(TAG, "Frame ${file.name} failed: ${t.message}", t)
                    mapOf(
                        "frame" to file.nameWithoutExtension,
                        "error" to (t.message ?: t.toString()),
                        "errorType" to t.javaClass.simpleName
                    )
                }
            }
        } finally {
            ocrProcessor.release()
        }

        val report = mapOf(
            "generatedAtMs" to System.currentTimeMillis(),
            "scalePolicy" to scalePolicy,
            "frameCount" to frames.size,
            "frames" to reports
        )
        val reportFile = File(outDir, "report.json")
        reportFile.writeText(gson.toJson(report))
        appContext.getExternalFilesDir(null)?.let { ext ->
            val extDir = File(ext, "replay_out")
            extDir.mkdirs()
            reportFile.copyTo(File(extDir, "report.json"), overwrite = true)
            outDir.listFiles { f -> f.isDirectory }?.forEach { frameDir ->
                frameDir.copyRecursively(File(extDir, frameDir.name), overwrite = true)
            }
        }
        Log.i(TAG, "Replay complete: ${frames.size} frames -> ${reportFile.absolutePath}")
    }

    private suspend fun replayOne(
        frameId: String,
        file: File,
        scalePolicy: String,
        ocrProcessor: OCRProcessor,
        speciesRefiner: SpeciesRefiner,
        variantDecisionEngine: VariantDecisionEngine,
        phase2VariantClassifier: Phase2VariantClassifier,
        visualDetector: VisualFeatureDetector,
        rarityCalculator: RarityCalculator,
        scanConfidenceGate: ScanConfidenceGate,
        consistencyGate: ScanConsistencyGate,
        outDir: File
    ): Map<String, Any?> {
        val frameDir = File(outDir, frameId)
        frameDir.mkdirs()

        val decoded = BitmapFactory.decodeFile(file.absolutePath).let {
            requireNotNull(it) { "decode failed" }
        }
        val originalDims = decoded.width to decoded.height

        // Production caller-side policy: full frame downscaled to width 900 before OCR.
        val bitmap = if (scalePolicy == "baseline900" && decoded.width > 900) {
            val scaled = Bitmap.createScaledBitmap(decoded, 900, decoded.height * 900 / decoded.width, true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }
        val scaledDims = bitmap.width to bitmap.height

        val cpQuality = estimateCpQualityReplica(bitmap)

        val t0 = System.currentTimeMillis()
        val fast = ocrProcessor.processImageWithDiagnostics(
            FrameOcrRequest(
                bitmap = bitmap, includeSecondaryFields = false, frameIndex = 0,
                frameRole = "fast", estimatedCpCropQuality = cpQuality
            )
        )
        val fastMs = System.currentTimeMillis() - t0

        val t1 = System.currentTimeMillis()
        val detailed = ocrProcessor.processImageWithDiagnostics(
            FrameOcrRequest(
                bitmap = bitmap, includeSecondaryFields = true, frameIndex = 1,
                frameRole = "detailed_best", estimatedCpCropQuality = cpQuality
            )
        )
        val detailedMs = System.currentTimeMillis() - t1

        saveCrops(bitmap, fast.diagnostic.crops, frameDir, "fast")
        saveCrops(bitmap, detailed.diagnostic.crops, frameDir, "detailed")

        val fastEvidence = ScanManager.deriveSpeciesEvidence(
            fast.diagnostic.fieldCandidates,
            fast.pokemon,
            rarityCalculator
        )
        val detailedEvidence = ScanManager.deriveSpeciesEvidence(
            detailed.diagnostic.fieldCandidates,
            detailed.pokemon,
            rarityCalculator
        )
        val fastCandidate = ScanFrameCandidate(file.absolutePath, fast.pokemon, cpQuality, fastEvidence)
        val detailedCandidate = ScanFrameCandidate(
            file.absolutePath,
            detailed.pokemon,
            cpQuality,
            detailedEvidence
        )
        val anchoredSelection = ScanFrameFusion.resolveAnchoredFrames(
            frames = listOf(fastCandidate),
            authoritative = fastCandidate,
            detailed = detailedCandidate
        )
        val fused = anchoredSelection?.frame?.data ?: ScanFrameFusion.fuse(
            frames = listOf(fastCandidate),
            authoritative = fast.pokemon,
            detailed = detailed.pokemon,
            validCpList = ScanFrameFusion.validCpCandidates(listOf(fastCandidate)),
            bestCpQuality = cpQuality
        )
        var speciesEvidence = anchoredSelection?.speciesEvidence ?: fastEvidence

        val refineStart = System.currentTimeMillis()
        val refined = speciesRefiner.refine(
            fused,
            fast.diagnostic.fieldCandidates + detailed.diagnostic.fieldCandidates
        )
        val refineMs = System.currentTimeMillis() - refineStart
        speciesEvidence = reconcileSpeciesProfileEvidence(
            speciesEvidence,
            ScanManager.profileStatus(
                refined,
                speciesEvidence.selectedCanonicalSpecies,
                rarityCalculator
            )
        )
        val consistencyDecision = consistencyGate.evaluate(fused, refined, speciesEvidence)
        val productionStoppedBeforeConfidence = consistencyDecision.shouldRetry
        val authorityPokemon = consistencyDecision.pokemon

        val classifyStart = System.currentTimeMillis()
        val classified = variantDecisionEngine.classify(bitmap, authorityPokemon)
        val classifyMs = System.currentTimeMillis() - classifyStart
        val classifiedPokemon = classified.pokemon
        val sizeTag = extractRawField(classifiedPokemon.rawOcrText, "SizeTag").ifBlank { null }

        val visualStart = System.currentTimeMillis()
        val visualBase = visualDetector.detect(bitmap, classifiedPokemon.name, sizeTag)
        val visualMs = System.currentTimeMillis() - visualStart
        val visual = variantDecisionEngine.mergeVisualFeatures(
            applyOcrOverrides(classifiedPokemon, visualBase),
            classified.fullMatch,
            classified.resolvedMatch ?: classified.globalMatch
        )

        val cpCandidates = listOfNotNull(classifiedPokemon.cp)
        val fixedCp = rarityCalculator.validateAndFixCP(classifiedPokemon, cpCandidates, visual)
        val finalPokemon = if (fixedCp != null && fixedCp > 0 && fixedCp != classifiedPokemon.cp) {
            classifiedPokemon.copy(cp = fixedCp)
        } else {
            classifiedPokemon
        }
        val phase2Result = runCatching {
            phase2VariantClassifier.classify(bitmap, finalPokemon.realName ?: finalPokemon.name)
        }.getOrNull()
        val scoringVisual = Phase2VariantFeatureMerger.merge(visual, phase2Result)

        val rarityStart = System.currentTimeMillis()
        val rarity = rarityCalculator.calculate(finalPokemon, scoringVisual)
        val rarityMs = System.currentTimeMillis() - rarityStart

        val scanDecision = if (productionStoppedBeforeConfidence) {
            null
        } else {
            scanConfidenceGate.evaluate(
                ScanConfidenceInput(
                    pokemon = finalPokemon,
                    frames = listOf(fast.diagnostic, detailed.diagnostic),
                    consistencyReason = consistencyDecision.reason,
                    visualSummary = VariantVisualSummary.from(
                        scoringVisual,
                        finalPokemon.variantDecisionTrace
                    ),
                    speciesEvidence = speciesEvidence
                )
            )
        }

        // Phase 2E: mirror the production identity inputs (gate-accepted species proxy =
        // hard evidence surviving the consistency gate; acceptance proxy = scan decision).
        val replayLockedSpecies = speciesEvidence.selectedCanonicalSpecies
            ?.takeIf { speciesEvidence.hasHardAuthority && !productionStoppedBeforeConfidence }
        val phase2ShinyDemoted = Phase2VariantFeatureMerger.shinyDemotionApplied(visual, phase2Result)
        val recognitionIdentity = com.pokerarity.scanner.util.ocr.RecognitionIdentityFactory.build(
            com.pokerarity.scanner.util.ocr.RecognitionIdentityFactory.Input(
                speciesEvidence = speciesEvidence,
                scanAccepted = scanDecision?.maySaveScan ?: false,
                lockedSpecies = replayLockedSpecies,
                classifierSpecies = classifiedPokemon.variantDecisionTrace?.classifierSpecies,
                fullMatchWinnerSpecies = classified.fullMatch?.winnerSpecies,
                formCandidates = finalPokemon.speciesResolverTrace?.formCandidates.orEmpty(),
                supportedFormIds = replaySnapshotFormIds(replayLockedSpecies),
                mergedFeatures = scoringVisual,
                phase2ShinyDemoted = phase2ShinyDemoted,
                sizeTag = sizeTag
            )
        )

        val totalMs = System.currentTimeMillis() - t0
        Log.i(
            TAG,
            "REPLAY $frameId scale=$scalePolicy screen=${detailed.diagnostic.screenState}/${detailed.diagnostic.screenConfidence} " +
                "species=${finalPokemon.realName ?: finalPokemon.name} " +
                "decision=${scanDecision?.decision?.name ?: "STOPPED_BEFORE_CONFIDENCE"} " +
                "conf=${scanDecision?.confidence ?: 0f} fastMs=$fastMs detailedMs=$detailedMs totalMs=$totalMs"
        )

        return mapOf(
            "frame" to frameId,
            "scalePolicy" to scalePolicy,
            "inputDims" to mapOf("w" to originalDims.first, "h" to originalDims.second),
            "ocrInputDims" to mapOf("w" to scaledDims.first, "h" to scaledDims.second),
            "estimatedCpCropQuality" to cpQuality,
            "fastDiagnostic" to gson.toJsonTree(fast.diagnostic),
            "detailedDiagnostic" to gson.toJsonTree(detailed.diagnostic),
            "refined" to mapOf(
                "name" to refined.name, "realName" to refined.realName, "candyName" to refined.candyName,
                "cp" to refined.cp, "hp" to refined.hp, "maxHp" to refined.maxHp,
                "stardust" to refined.stardust, "arcLevel" to refined.arcLevel,
                "resolverTrace" to gson.toJsonTree(refined.speciesResolverTrace)
            ),
            "classifiedSpecies" to (classifiedPokemon.realName ?: classifiedPokemon.name),
            "variantTrace" to gson.toJsonTree(classifiedPokemon.variantDecisionTrace),
            "visual" to gson.toJsonTree(scoringVisual),
            "cpFix" to mapOf("ocrCp" to classifiedPokemon.cp, "fixedCp" to fixedCp),
            "finalSpecies" to (finalPokemon.realName ?: finalPokemon.name),
            "finalCp" to finalPokemon.cp,
            "finalHp" to finalPokemon.hp,
            "finalMaxHp" to finalPokemon.maxHp,
            "fastSpeciesEvidence" to gson.toJsonTree(fastEvidence),
            "detailedSpeciesEvidence" to gson.toJsonTree(detailedEvidence),
            "anchoredSpeciesEvidence" to gson.toJsonTree(anchoredSelection?.speciesEvidence),
            "speciesEvidence" to gson.toJsonTree(speciesEvidence),
            "consistencyDecision" to mapOf(
                "shouldRetry" to consistencyDecision.shouldRetry,
                "reason" to consistencyDecision.reason,
                "species" to (consistencyDecision.pokemon.realName ?: consistencyDecision.pokemon.name)
            ),
            "productionStoppedBeforeConfidenceGate" to productionStoppedBeforeConfidence,
            "scanDecision" to scanDecision?.let { gson.toJsonTree(it) },
            "recognitionIdentity" to gson.toJsonTree(recognitionIdentity),
            "rarity" to mapOf(
                "ivEstimate" to rarity.ivEstimate,
                "totalScore" to rarity.totalScore,
                "tier" to rarity.tier.name,
                "breakdown" to rarity.breakdown
            ),
            "latencyMs" to mapOf(
                "fastPass" to fastMs, "detailedPass" to detailedMs, "refine" to refineMs,
                "variantClassify" to classifyMs, "visual" to visualMs, "rarity" to rarityMs,
                "totalRecognized" to totalMs
            )
        )
    }

    private fun saveCrops(source: Bitmap, crops: List<com.pokerarity.scanner.util.ocr.CropDiagnostic>, dir: File, role: String) {
        crops.forEachIndexed { i, c ->
            val l = c.left ?: return@forEachIndexed
            val t = c.top ?: return@forEachIndexed
            val r = c.right ?: return@forEachIndexed
            val b = c.bottom ?: return@forEachIndexed
            val cl = l.coerceIn(0, source.width - 1)
            val ct = t.coerceIn(0, source.height - 1)
            val cw = (r - l).coerceIn(1, source.width - cl)
            val ch = (b - t).coerceIn(1, source.height - ct)
            runCatching {
                val crop = Bitmap.createBitmap(source, cl, ct, cw, ch)
                File(dir, "crop_${role}_${i}_${c.field}_${c.source}.png").outputStream().use {
                    crop.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                if (crop !== source) crop.recycle()
            }
        }
    }

    private fun applyOcrOverrides(pokemon: com.pokerarity.scanner.data.model.PokemonData, visual: VisualFeatures): VisualFeatures {
        val ocrLucky = extractRawField(pokemon.rawOcrText, "LuckyDetected").equals("true", ignoreCase = true)
        return if (ocrLucky && !visual.isLucky) {
            visual.copy(isLucky = true, hasLocationCard = false, confidence = maxOf(visual.confidence, 0.75f))
        } else {
            visual
        }
    }

    /** Phase 2E supported snapshot form rows of the locked species; null when unavailable. */
    private fun replaySnapshotFormIds(species: String?): Set<String>? =
        species?.trim()?.takeUnless(String::isEmpty)
            ?.let { locked ->
                com.pokerarity.scanner.util.ocr.RecognitionSnapshotHolder
                    .getOrNull(InstrumentationRegistry.getInstrumentation().targetContext)
                    ?.forSpecies(locked)
            }
            ?.flatMap { it.forms }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }

    // Faithful replica of ScanManager.estimateCpQuality (private in production).
    private fun estimateCpQualityReplica(bitmap: Bitmap): Double = estimateReplayCpQuality(bitmap)

    private fun extractRawField(rawOcrText: String, key: String): String {
        return rawOcrText.split("|")
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(":")
            ?.trim()
            .orEmpty()
    }

    private companion object {
        const val TAG = "ExactFrameReplay"
    }
}
