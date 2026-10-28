package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.util.ocr.FrameRouteDiagnostic
import com.pokerarity.scanner.util.ocr.FrameOcrRequest
import com.pokerarity.scanner.util.ocr.OCRProcessor
import com.pokerarity.scanner.util.ocr.ScanConfidenceGate
import com.pokerarity.scanner.util.ocr.ScanConfidenceInput
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import com.pokerarity.scanner.util.ocr.ScreenRouteAction
import com.pokerarity.scanner.util.ocr.SpeciesRefiner
import com.pokerarity.scanner.util.ocr.VariantVisualSummary
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import com.pokerarity.scanner.util.vision.Phase2VariantFeatureMerger
import com.pokerarity.scanner.util.vision.VariantDecisionEngine
import com.pokerarity.scanner.util.vision.VisualFeatureDetector
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import com.pokerarity.scanner.util.diagnostics.ScanReplayReportRedactor
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEBUG-ONLY Phase 2A diagnostic: the exact-frame replay driven through the PRODUCTION
 * routing frame step. Each staged frame is first routed via the real [ScanManager]
 * router (cheap bitmap/anchor preflight); only PROCEED_DETAIL frames enter species OCR
 * (via the same production frame step, [ScanManager.processRoutedFrame]) and the
 * existing post-OCR stage chain. It writes only aggregate route counts and latency;
 * per-frame scan details remain in process memory. Not a product test.
 *
 * Input: cacheDir/replay_in (staged PNGs). Args: -e scale baseline900|native.
 * Output: aggregate-only cacheDir/router_replay_out/report.json.
 */
@RunWith(AndroidJUnit4::class)
class Phase2aRouterReplayTest {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @Test
    fun routerReplayExactFrames() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()

        val scalePolicy = if (args.getString("scale") == "native") "native" else "baseline900"
        val inDir = File(appContext.cacheDir, "replay_in")
        val outDir = File(appContext.cacheDir, "router_replay_out")
        outDir.deleteRecursively()
        outDir.mkdirs()
        appContext.getExternalFilesDir(null)?.let { File(it, "router_replay_out").deleteRecursively() }

        val frames = inDir.listFiles { f -> f.extension.equals("png", true) }
            ?.sortedBy { it.name }
            .orEmpty()
        org.junit.Assume.assumeTrue("No replay input frames available", frames.isNotEmpty())

        val manager = ScanManager(appContext)
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
            for ((index, file) in frames.withIndex()) {
                reports += try {
                    val startedAt = android.os.SystemClock.elapsedRealtime()
                    val detail = replayOne(
                        frameId = file.nameWithoutExtension,
                        file = file,
                        index = index,
                        scalePolicy = scalePolicy,
                        manager = manager,
                        ocrProcessor = ocrProcessor,
                        speciesRefiner = speciesRefiner,
                        variantDecisionEngine = variantDecisionEngine,
                        phase2VariantClassifier = phase2VariantClassifier,
                        visualDetector = visualDetector,
                        rarityCalculator = rarityCalculator,
                        scanConfidenceGate = scanConfidenceGate,
                        consistencyGate = consistencyGate
                    )
                    val route = detail["route"] as? Map<*, *>
                    mapOf(
                        "outcome" to (route?.get("action") as? String ?: "ERROR"),
                        "totalLatencyMs" to (android.os.SystemClock.elapsedRealtime() - startedAt)
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
        // Keep only aggregate metrics in app-private cache; do not export per-frame scan details.
    }

    private suspend fun replayOne(
        frameId: String,
        file: File,
        index: Int,
        scalePolicy: String,
        manager: ScanManager,
        ocrProcessor: OCRProcessor,
        speciesRefiner: SpeciesRefiner,
        variantDecisionEngine: VariantDecisionEngine,
        phase2VariantClassifier: Phase2VariantClassifier,
        visualDetector: VisualFeatureDetector,
        rarityCalculator: RarityCalculator,
        scanConfidenceGate: ScanConfidenceGate,
        consistencyGate: ScanConsistencyGate
    ): Map<String, Any?> {
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
            ?: return mapOf("frame" to frameId, "error" to "decode_failed")

        // Production caller-side policy: full frame downscaled to width 900 before OCR.
        val bitmap = if (scalePolicy == "baseline900" && decoded.width > 900) {
            val scaled = Bitmap.createScaledBitmap(decoded, 900, decoded.height * 900 / decoded.width, true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }

        // Per-frame CP crop quality from the actual staged bitmap, using the same
        // benchmark logic as the Phase 1 exact-frame replay.
        val cpQuality = estimateReplayCpQuality(bitmap)

        // 1. Production routing preflight (same router instance the pipeline uses).
        val routeStart = android.os.SystemClock.elapsedRealtime()
        val route = manager.screenRouter.route(bitmap)
        val routeMs = android.os.SystemClock.elapsedRealtime() - routeStart

        val routes = mutableListOf<FrameRouteDiagnostic>()
        val results = mutableListOf<ScanFrameCandidate>()
        val frameDiagnostics = mutableListOf<com.pokerarity.scanner.util.ocr.FrameDiagnostic>()
        var fastMs = 0L
        var detailedMs = 0L
        var detailedDiagnostic: com.pokerarity.scanner.util.ocr.FrameDiagnostic? = null
        var refined: com.pokerarity.scanner.data.model.PokemonData? = null
        var speciesEvidenceAfterReconcile: com.pokerarity.scanner.util.ocr.SpeciesEvidence? = null
        var consistencyDecision: com.pokerarity.scanner.util.ocr.ScanConsistencyGate.Decision? = null
        var scanDecision: com.pokerarity.scanner.util.ocr.ScanDecision? = null
        var finalPokemon: com.pokerarity.scanner.data.model.PokemonData? = null

        if (route.action == ScreenRouteAction.PROCEED_DETAIL) {
            // 2. Production frame step: OCR + evidence via the real seam. The frame step
            // owns and recycles this bitmap, exactly as production does. Source dims are
            // preserved so the Phase 2B calibration signature matches the pipeline.
            val ocrStart = android.os.SystemClock.elapsedRealtime()
            val sourceBitmap = BitmapFactory.decodeFile(file.absolutePath)
            val sourceWidth = sourceBitmap?.width ?: 0
            val sourceHeight = sourceBitmap?.height ?: 0
            sourceBitmap?.recycle()
            manager.processRoutedFrame(
                ScanManager.DecodedFrame(
                    index, file.absolutePath, bitmap, cpQuality, pooled = false,
                    sourceWidth = sourceWidth, sourceHeight = sourceHeight
                ),
                results, frameDiagnostics, routes
            )
            fastMs = android.os.SystemClock.elapsedRealtime() - ocrStart
            val fastDiagnostic = frameDiagnostics.lastOrNull()
            val fastCandidate = results.lastOrNull()
            // Production reuses the fast frame's recognition context for the detailed
            // pass; the harness mirrors that so evidence matches the fixed pipeline.
            val fastRecognitionContext = fastCandidate?.recognitionContext
            if (fastDiagnostic == null || fastCandidate == null) {
                return mapOf(
                    "frame" to frameId,
                    "route" to route.routeFields(routeMs),
                    "ocrInvoked" to false,
                    "error" to "detail route produced no OCR result"
                )
            }

            // 3. Existing post-OCR stage chain. Production re-decodes the best frame's
            // file for the detailed pass, so mirror that here.
            val stageBitmap = BitmapFactory.decodeFile(file.absolutePath)!!.let { source ->
                if (scalePolicy == "baseline900" && source.width > 900) {
                    val scaled = Bitmap.createScaledBitmap(source, 900, source.height * 900 / source.width, true)
                    if (scaled !== source) source.recycle()
                    scaled
                } else {
                    source
                }
            }
            try {
                val t1 = android.os.SystemClock.elapsedRealtime()
                val detailed = ocrProcessor.processImageWithDiagnostics(
                    FrameOcrRequest(
                        bitmap = stageBitmap,
                        includeSecondaryFields = true,
                        frameIndex = 1,
                        frameRole = "detailed_best",
                        estimatedCpCropQuality = cpQuality,
                        calibration = fastRecognitionContext?.calibrationHint,
                        geometry = fastRecognitionContext?.geometry
                    )
                )
                detailedMs = android.os.SystemClock.elapsedRealtime() - t1
                detailedDiagnostic = detailed.diagnostic

                val detailedEvidence = ScanManager.deriveSpeciesEvidence(
                    detailed.diagnostic.fieldCandidates, detailed.pokemon, rarityCalculator
                )
                val detailedCandidate = ScanFrameCandidate(file.absolutePath, detailed.pokemon, cpQuality, detailedEvidence)
                val anchoredSelection = ScanFrameFusion.resolveAnchoredFrames(
                    frames = listOf(fastCandidate),
                    authoritative = fastCandidate,
                    detailed = detailedCandidate
                )
                val fused = anchoredSelection?.frame?.data ?: ScanFrameFusion.fuse(
                    frames = listOf(fastCandidate),
                    authoritative = fastCandidate.data,
                    detailed = detailed.pokemon,
                    validCpList = ScanFrameFusion.validCpCandidates(listOf(fastCandidate)),
                    bestCpQuality = cpQuality
                )
                var speciesEvidence = anchoredSelection?.speciesEvidence ?: fastCandidate.speciesEvidence

                val refinedData = speciesRefiner.refine(
                    fused,
                    fastDiagnostic.fieldCandidates + detailed.diagnostic.fieldCandidates
                )
                refined = refinedData
                speciesEvidence = com.pokerarity.scanner.service.reconcileSpeciesProfileEvidence(
                    speciesEvidence,
                    ScanManager.profileStatus(refinedData, speciesEvidence.selectedCanonicalSpecies, rarityCalculator)
                )
                speciesEvidenceAfterReconcile = speciesEvidence
                val consistency = consistencyGate.evaluate(fused, refinedData, speciesEvidence)
                consistencyDecision = consistency
                val productionStoppedBeforeConfidence = consistency.shouldRetry
                val authorityPokemon = consistency.pokemon

                val classified = variantDecisionEngine.classify(stageBitmap, authorityPokemon)
                val classifiedPokemon = classified.pokemon
                val sizeTag = extractRawField(classifiedPokemon.rawOcrText, "SizeTag").ifBlank { null }
                val visualBase = visualDetector.detect(stageBitmap, classifiedPokemon.name, sizeTag)
                val visual = variantDecisionEngine.mergeVisualFeatures(
                    applyOcrOverrides(classifiedPokemon, visualBase),
                    classified.fullMatch,
                    classified.resolvedMatch ?: classified.globalMatch
                )

                val cpCandidates = listOfNotNull(classifiedPokemon.cp)
                val fixedCp = rarityCalculator.validateAndFixCP(classifiedPokemon, cpCandidates, visual)
                val final = if (fixedCp != null && fixedCp > 0 && fixedCp != classifiedPokemon.cp) {
                    classifiedPokemon.copy(cp = fixedCp)
                } else {
                    classifiedPokemon
                }
                finalPokemon = final
                val phase2Result = runCatching {
                    phase2VariantClassifier.classify(stageBitmap, final.realName ?: final.name)
                }.getOrNull()
                val scoringVisual = Phase2VariantFeatureMerger.merge(visual, phase2Result)

                scanDecision = if (productionStoppedBeforeConfidence) {
                    null
                } else {
                    scanConfidenceGate.evaluate(
                        ScanConfidenceInput(
                            pokemon = final,
                            frames = listOf(fastDiagnostic, detailed.diagnostic),
                            consistencyReason = consistency.reason,
                            visualSummary = VariantVisualSummary.from(scoringVisual, final.variantDecisionTrace),
                            speciesEvidence = speciesEvidence
                        )
                    )
                }
            } finally {
                if (!stageBitmap.isRecycled) stageBitmap.recycle()
            }
        } else {
            bitmap.recycle()
        }

        return mapOf(
            "frame" to frameId,
            "route" to route.routeFields(routeMs),
            "ocrInvoked" to frameDiagnostics.isNotEmpty(),
            "fastMs" to fastMs,
            "detailedMs" to detailedMs,
            "routeMs" to routeMs,
            "cpQuality" to cpQuality,
            "finalSpecies" to (finalPokemon?.realName ?: finalPokemon?.name ?: refined?.realName ?: refined?.name),
            "speciesEvidence" to speciesEvidenceAfterReconcile?.let {
                mapOf(
                    "selectedCanonicalSpecies" to it.selectedCanonicalSpecies,
                    "authority" to it.authority.name,
                    "profileStatus" to it.profileStatus.name,
                    "reasonCodes" to it.reasonCodes,
                    "observationsAgree" to it.observationsAgree,
                    "authorityConflict" to it.authorityConflict
                )
            },
            "consistency" to consistencyDecision?.let {
                mapOf("reason" to it.reason, "shouldRetry" to it.shouldRetry)
            },
            "scanDecision" to scanDecision?.let {
                mapOf(
                    "decision" to it.decision.name,
                    "confidence" to it.confidence,
                    "mayShowOverlay" to it.mayShowOverlay,
                    "maySaveScan" to it.maySaveScan,
                    "collectionSafe" to it.collectionSafe
                )
            },
            "detailedScreenState" to detailedDiagnostic?.screenState
        )
    }

    private fun com.pokerarity.scanner.util.ocr.ScreenRouteDecision.routeFields(routeMs: Long) = mapOf(
        "action" to action.name,
        "screenType" to screenType.name,
        "confidence" to confidence,
        "safeFallback" to safeFallback,
        "requiresContentCorroboration" to requiresContentCorroboration,
        "reason" to reason,
        "routeMs" to routeMs
    )

    private fun applyOcrOverrides(pokemon: com.pokerarity.scanner.data.model.PokemonData, visual: VisualFeatures): VisualFeatures {
        val ocrLucky = extractRawField(pokemon.rawOcrText, "LuckyDetected").equals("true", ignoreCase = true)
        return if (ocrLucky && !visual.isLucky) {
            visual.copy(isLucky = true, hasLocationCard = false, confidence = maxOf(visual.confidence, 0.75f))
        } else {
            visual
        }
    }

    private fun extractRawField(rawOcrText: String, key: String): String {
        return rawOcrText.split("|")
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(":")
            ?.trim()
            .orEmpty()
    }

}
