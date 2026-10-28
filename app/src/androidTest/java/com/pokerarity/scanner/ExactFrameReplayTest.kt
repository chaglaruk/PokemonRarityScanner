package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.diagnostics.ScanReplayReportRedactor
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.service.reconcileSpeciesProfileEvidence
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * DEBUG-ONLY diagnostic replay: pushes arbitrary bitmap files through the REAL production
 * recognition components and keeps detailed diagnostics in process memory only.
 * Not a product test; lives only in the temporary diagnostic worktree.
 *
 * Input: target context cacheDir/replay_in/ (PNG files staged via adb push + run-as).
 * Args:  -e scale baseline900|native   (default baseline900 = production policy)
 *        -e only F01,F02               (optional frame filter by filename prefix)
 * Output: aggregate-only cacheDir/replay_out/report.json.
 */
@RunWith(AndroidJUnit4::class)
class ExactFrameReplayTest {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @Test
    fun replayExactFrames() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()

        val scalePolicy = if (args.getString("scale") == "native") "native" else "baseline900"
        val only = args.getString("only")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        val inDir = File(appContext.cacheDir, "replay_in")
        val outDir = File(appContext.cacheDir, "replay_out")
        outDir.deleteRecursively()
        outDir.mkdirs()
        appContext.getExternalFilesDir(null)?.let { File(it, "replay_out").deleteRecursively() }

        val frames = inDir.listFiles { f -> f.extension.equals("png", true) }
            ?.sortedBy { it.name }
            ?.filter { frame -> only == null || only.any { p -> frame.name.startsWith(p) } }
            .orEmpty()
        org.junit.Assume.assumeTrue("No replay input frames available", frames.isNotEmpty())

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
                        file = file,
                        scalePolicy = scalePolicy,
                        ocrProcessor = ocrProcessor,
                        speciesRefiner = speciesRefiner,
                        variantDecisionEngine = variantDecisionEngine,
                        phase2VariantClassifier = phase2VariantClassifier,
                        visualDetector = visualDetector,
                        rarityCalculator = rarityCalculator,
                        scanConfidenceGate = scanConfidenceGate,
                        consistencyGate = consistencyGate
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    mapOf("outcome" to "ERROR")
                }
            }
        } finally {
            ocrProcessor.release()
        }

        val report = linkedMapOf<String, Any?>(
            "generatedAtMs" to System.currentTimeMillis(),
            "scalePolicy" to scalePolicy
        ).apply { putAll(ScanReplayReportRedactor.aggregate(reports)) }
        val reportFile = File(outDir, "report.json")
        reportFile.writeText(gson.toJson(report))
        // Keep only aggregate metrics in app-private cache; do not export frame artifacts.
    }

    private suspend fun replayOne(
        file: File,
        scalePolicy: String,
        ocrProcessor: OCRProcessor,
        speciesRefiner: SpeciesRefiner,
        variantDecisionEngine: VariantDecisionEngine,
        phase2VariantClassifier: Phase2VariantClassifier,
        visualDetector: VisualFeatureDetector,
        rarityCalculator: RarityCalculator,
        scanConfidenceGate: ScanConfidenceGate,
        consistencyGate: ScanConsistencyGate
    ): Map<String, Any?> {
        val decoded = BitmapFactory.decodeFile(file.absolutePath).let {
            requireNotNull(it) { "decode failed" }
        }
        // Production caller-side policy: full frame downscaled to width 900 before OCR.
        val bitmap = if (scalePolicy == "baseline900" && decoded.width > 900) {
            val scaled = Bitmap.createScaledBitmap(decoded, 900, decoded.height * 900 / decoded.width, true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }
        return runCatching {
        val cpQuality = estimateCpQualityReplica(bitmap)

        val t0 = System.currentTimeMillis()
        val fast = ocrProcessor.processImageWithDiagnostics(
            FrameOcrRequest(
                bitmap = bitmap, includeSecondaryFields = false, frameIndex = 0,
                frameRole = "fast", estimatedCpCropQuality = cpQuality
            )
        )
        val detailed = ocrProcessor.processImageWithDiagnostics(
            FrameOcrRequest(
                bitmap = bitmap, includeSecondaryFields = true, frameIndex = 1,
                frameRole = "detailed_best", estimatedCpCropQuality = cpQuality
            )
        )
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

        val refined = speciesRefiner.refine(
            fused,
            fast.diagnostic.fieldCandidates + detailed.diagnostic.fieldCandidates
        )
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

        val classified = variantDecisionEngine.classify(bitmap, authorityPokemon)
        val classifiedPokemon = classified.pokemon
        val sizeTag = extractRawField(classifiedPokemon.rawOcrText, "SizeTag").ifBlank { null }

        val visualBase = visualDetector.detect(bitmap, classifiedPokemon.name, sizeTag)
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

        rarityCalculator.calculate(finalPokemon, scoringVisual)

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

        val totalMs = System.currentTimeMillis() - t0
        mapOf(
            "outcome" to (scanDecision?.decision?.name ?: "STOPPED_BEFORE_CONFIDENCE"),
            "totalLatencyMs" to totalMs
        )
        }.also { if (!bitmap.isRecycled) bitmap.recycle() }.getOrThrow()
    }

    private fun applyOcrOverrides(pokemon: com.pokerarity.scanner.data.model.PokemonData, visual: VisualFeatures): VisualFeatures {
        val ocrLucky = extractRawField(pokemon.rawOcrText, "LuckyDetected").equals("true", ignoreCase = true)
        return if (ocrLucky && !visual.isLucky) {
            visual.copy(isLucky = true, hasLocationCard = false, confidence = maxOf(visual.confidence, 0.75f))
        } else {
            visual
        }
    }

    // Faithful replica of ScanManager.estimateCpQuality (private in production).
    private fun estimateCpQualityReplica(bitmap: Bitmap): Double = estimateReplayCpQuality(bitmap)

    private fun extractRawField(rawOcrText: String, key: String): String {
        return rawOcrText.split("|")
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(":")
            ?.trim()
            .orEmpty()
    }

}
