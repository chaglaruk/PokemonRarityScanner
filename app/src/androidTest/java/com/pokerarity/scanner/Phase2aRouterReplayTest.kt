package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.RequestOrigin
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.service.ScanRequestToken
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.service.detailedScanFrameCandidate
import com.pokerarity.scanner.service.resolvePhase2AuthorityGate
import com.pokerarity.scanner.util.ocr.FrameRouteDiagnostic
import com.pokerarity.scanner.util.ocr.FrameOcrRequest
import com.pokerarity.scanner.util.ocr.OCRProcessor
import com.pokerarity.scanner.util.ocr.RecognitionIdentityFactory
import com.pokerarity.scanner.util.ocr.ScanConfidenceGate
import com.pokerarity.scanner.util.ocr.ScanConfidenceInput
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import com.pokerarity.scanner.util.ocr.SpeciesRefiner
import com.pokerarity.scanner.util.ocr.VariantVisualSummary
import com.pokerarity.scanner.util.perf.DebugScanTiming
import com.pokerarity.scanner.util.perf.ScanTimingStage
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import com.pokerarity.scanner.util.vision.Phase2VariantFeatureMerger
import com.pokerarity.scanner.util.vision.ShinyFormAmbiguityGuard
import com.pokerarity.scanner.util.vision.ShinySignatureStore
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
 * existing post-OCR stage chain. It writes aggregate metrics and hash-keyed, redacted
 * per-frame decisions to app-private cache for controlled local A/B analysis. Not a
 * product test; the exported report contains no image bytes, OCR text, or source paths.
 *
 * Input: cacheDir/replay_in (staged PNGs). Args: -e scale baseline900|native|productionNativeRetry,
 * -e targetRevision <full-git-sha>, and optionally -e expectedFrameCount <count>.
 * Output: hash-keyed redacted cacheDir/router_replay_out/report.json.
 */
@RunWith(AndroidJUnit4::class)
class Phase2aRouterReplayTest {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    private fun replayInputDirectory(cacheDir: File, seeded: Boolean): File =
        File(cacheDir, if (seeded) "replay_seeded_variant_batch" else "replay_in")

    /** Typed numeric scan evidence for app-private QA reports, never raw OCR text. */
    private fun numericReplayRead(value: Int?): Map<String, Any?> = mapOf(
        "status" to if (value == null) "MISSING" else "PRESENT",
        "value" to value
    )

    @Test
    fun numericReplayReadDistinguishesMissingFromPresent() {
        val present = numericReplayRead(192)
        check(present["status"] == "PRESENT" && present["value"] == 192)
        val missing = numericReplayRead(null)
        check(missing["status"] == "MISSING" && missing["value"] == null)
    }

    /** Lightweight contract test: does not initialize OCR or any variant model. */
    @Test
    fun bundledVariantSeedLeavesManuallyStagedFramesUntouched() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cacheDir = instrumentation.targetContext.cacheDir
        val stagedDir = replayInputDirectory(cacheDir, false)
        val seededDir = replayInputDirectory(cacheDir, true)
        check(stagedDir != seededDir)
        check(stagedDir.mkdirs() || stagedDir.isDirectory)

        val sentinel = File(stagedDir, "qa_seed_sentinel_${System.nanoTime()}.txt")
        val original = "staged-input-preservation"
        try {
            sentinel.writeText(original)
            seedVariantBatch(instrumentation.context, seededDir)
            check(sentinel.readText() == original) {
                "Bundled fixture seeding changed independently staged replay input"
            }
            val seededFrames = seededDir.listFiles { file -> file.extension.equals("png", true) }
            check(seededFrames?.size == 8) { "Expected eight isolated bundled frames" }
        } finally {
            // Remove only the sentinel created by this test, never staged user images.
            sentinel.delete()
        }
    }

    @Test
    fun routerReplayExactFrames() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        check(DebugScanTiming.isEnabled()) { "QA debug timing instrumentation is disabled" }

        val scalePolicy = when (args.getString("scale")) {
            "native" -> "native"
            "productionNativeRetry" -> "productionNativeRetry"
            else -> "baseline900"
        }
        val excludedFrame = args.getString("exclude")?.trim()?.takeIf { it.isNotEmpty() }
        val targetRevision = args.getString("targetRevision")?.trim()?.takeIf { it.isNotEmpty() }
        val expectedFrameCount = args.getString("expectedFrameCount")?.toIntOrNull()
        // Bundled diagnostic seeds must never overwrite independently staged replay images.
        val seedVariantBatchRequested = args.getString("seedVariantBatch") == "true"
        val inDir = replayInputDirectory(appContext.cacheDir, seedVariantBatchRequested)
        val outDir = File(appContext.cacheDir, "router_replay_out")
        if (seedVariantBatchRequested) {
            seedVariantBatch(instrumentation.context, inDir)
        }

        val frames = inDir.listFiles { f -> f.extension.equals("png", true) }
            ?.sortedBy { it.name }
            ?.filter { excludedFrame == null || it.nameWithoutExtension != excludedFrame }
            .orEmpty()
        org.junit.Assume.assumeTrue("No replay input frames available", frames.isNotEmpty())
        expectedFrameCount?.let {
            check(frames.size == it) { "Expected $it replay frames but found ${frames.size}" }
        }
        targetRevision?.let {
            check(Regex("[0-9a-f]{40}").matches(it)) { "targetRevision must be a full lowercase Git SHA" }
        }
        // Validate inputs before replacing a previous successful replay report.
        outDir.deleteRecursively()
        outDir.mkdirs()
        appContext.getExternalFilesDir(null)?.let { File(it, "router_replay_out").deleteRecursively() }

        val manager = ScanManager(appContext)
        val ocrProcessor = OCRProcessor(appContext)
        val rarityCalculator = RarityCalculator(appContext)
        val speciesRefiner = SpeciesRefiner(appContext, rarityCalculator)
        val visualDetector = VisualFeatureDetector(appContext)
        val variantDecisionEngine = VariantDecisionEngine(appContext)
        val phase2VariantClassifier = Phase2VariantClassifier(appContext)
        val scanConfidenceGate = ScanConfidenceGate()
        val consistencyGate = ScanConsistencyGate(appContext, rarityCalculator)
        val initializationStart = android.os.SystemClock.elapsedRealtime()
        ocrProcessor.ensureInitialized()
        val initializationMs = android.os.SystemClock.elapsedRealtime() - initializationStart

        val reports = mutableListOf<Map<String, Any?>>()
        try {
            for ((index, file) in frames.withIndex()) {
                reports += try {
                    val startedAt = android.os.SystemClock.elapsedRealtime()
                    val detail = replayOne(
                        frameId = file.nameWithoutExtension,
                        context = appContext,
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
                    val memoryInfo = Debug.MemoryInfo()
                    Debug.getMemoryInfo(memoryInfo)
                    detail + mapOf(
                        "outcome" to (route?.get("action") as? String ?: "ERROR"),
                        "totalLatencyMs" to (android.os.SystemClock.elapsedRealtime() - startedAt),
                        "instrumentationProcessPssKb" to memoryInfo.totalPss
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    mapOf(
                        "frame" to file.nameWithoutExtension,
                        "frameIndex" to index,
                        "outcome" to "ERROR",
                        "errorCode" to "frame_processing_error"
                    )
                }
            }
        } finally {
            ocrProcessor.release()
        }

        val report = linkedMapOf<String, Any?>(
            "schemaVersion" to 1,
            "sourceRevision" to (targetRevision ?: BuildConfig.QA_SOURCE_SHA),
            "targetApplicationRevision" to targetRevision,
            "instrumentationRevision" to BuildConfig.QA_SOURCE_SHA,
            "targetApplicationId" to appContext.packageName,
            "generatedAtMs" to System.currentTimeMillis(),
            "scalePolicy" to scalePolicy,
            "initializationMs" to initializationMs,
            "excludedFrameId" to excludedFrame
        ).apply {
            putAll(ScanReplayReportRedactor.aggregate(reports))
            put("frameSummaries", reports)
        }
        val reportFile = File(outDir, "report.json")
        reportFile.writeText(gson.toJson(report))
        // Keep only aggregate metrics in app-private cache; do not export per-frame scan details.
    }

    private suspend fun replayOne(
        frameId: String,
        context: Context,
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
            ?: return mapOf("frame" to frameId, "frameIndex" to index, "error" to "decode_failed")

        // Production caller-side policy: full frame downscaled to width 900 before OCR.
        val bitmap = if (scalePolicy != "native" && decoded.width > 900) {
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
        val routed = manager.screenRouter.routeWithClassification(bitmap)
        val route = routed.decision
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
        var finalVisual: VisualFeatures? = null
        var visualDetectorShinyPositive: Boolean? = null
        var variantMergedShinyPositive: Boolean? = null
        var phase2Predictions: List<Map<String, Any?>> = emptyList()
        var shinyDecisionReason: String? = null
        var unresolvedShinyForms = false
        var ocrFieldStatuses: List<Map<String, Any?>> = emptyList()
        var variantStageEvents: List<Map<String, Any?>> = emptyList()

        // 2. Run every route through the production frame step. The previous harness
        // skipped non-PROCEED routes before ScanManager could apply its bounded
        // provisional StorageList probe, so it did not replay the production policy.
        // The step owns/recycles the bitmap and consumes this exact preflight result.
        val ocrStart = android.os.SystemClock.elapsedRealtime()
        val sourceBitmap = BitmapFactory.decodeFile(file.absolutePath)
        val sourceWidth = sourceBitmap?.width ?: 0
        val sourceHeight = sourceBitmap?.height ?: 0
        sourceBitmap?.recycle()
        manager.processRoutedFrame(
            ScanManager.DecodedFrame(
                index, file.absolutePath, bitmap, cpQuality, pooled = false,
                sourceWidth = sourceWidth, sourceHeight = sourceHeight, routedScreen = routed
            ),
            results, frameDiagnostics, routes
        )
        fastMs = android.os.SystemClock.elapsedRealtime() - ocrStart
        val fastDiagnostic = frameDiagnostics.lastOrNull()
        val fastCandidate = results.lastOrNull()
        val actualRoute = routes.singleOrNull()?.let { frameRoute ->
            mapOf(
                "action" to frameRoute.action.name,
                "screenType" to frameRoute.screenType,
                "confidence" to frameRoute.confidence,
                "safeFallback" to frameRoute.safeFallback,
                "requiresContentCorroboration" to route.requiresContentCorroboration,
                "reason" to frameRoute.reason,
                "routeMs" to routeMs
            )
        } ?: route.routeFields(routeMs)
        // A terminal/provisional rejection is a measured routing outcome, not a
        // replay error. It never enters the detailed identity or variant stages.
        if (fastDiagnostic == null || fastCandidate == null) {
            return mapOf(
                "frame" to frameId,
                "route" to actualRoute,
                "ocrInvoked" to (fastMs > 0L && routes.singleOrNull()?.reason?.let {
                    it.startsWith("storage_detail_ui_") || it.startsWith("route_content_")
                } == true),
                "fastMs" to fastMs,
                "detailedMs" to 0L,
                "routeMs" to routeMs,
                "speciesStatus" to "NO_RESULT",
                "shinyStatus" to "NO_RESULT",
                "numericDecision" to mapOf("status" to "NO_RESULT")
            )
        }
        // Production reuses the fast frame's recognition context for the detailed
        // pass; the harness mirrors that so evidence matches the fixed pipeline.
        val fastRecognitionContext = fastCandidate.recognitionContext
        val productionDetailedPassNeeded = scalePolicy == "productionNativeRetry" &&
            ScanManager.shouldRunDetailedPassForAuthoritative(
                fastCandidate.data,
                fastCandidate.cpQuality,
                fastCandidate.speciesEvidence
            )
        val nativeResolutionRequested = productionDetailedPassNeeded &&
            ScanFrameFusion.shouldAttemptNativeResolutionForUnresolvedIdentity(fastCandidate.speciesEvidence)

            // 3. Existing post-OCR stage chain. Production re-decodes the best frame's
            // file for the detailed pass, so mirror that here.
            val stageBitmap = BitmapFactory.decodeFile(file.absolutePath)!!.let { source ->
                if (scalePolicy != "native" && source.width > 900) {
                    val scaled = Bitmap.createScaledBitmap(source, 900, source.height * 900 / source.width, true)
                    if (scaled !== source) source.recycle()
                    scaled
                } else {
                    source
                }
            }
            try {
                val detailedStart = android.os.SystemClock.elapsedRealtime()
                val detailed = if (scalePolicy == "productionNativeRetry") {
                    if (productionDetailedPassNeeded) {
                        manager.runDetailedPassIfNeeded(
                            file.absolutePath,
                            fastRecognitionContext,
                            preferNativeResolution = nativeResolutionRequested,
                            retryContradictedCp = nativeResolutionRequested &&
                                fastCandidate.speciesEvidence.profileStatus ==
                                com.pokerarity.scanner.util.ocr.SpeciesProfileStatus.CONTRADICTORY
                        )
                    } else {
                        null
                    }
                } else {
                    ocrProcessor.processImageWithDiagnostics(
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
                }
                detailedMs = android.os.SystemClock.elapsedRealtime() - detailedStart
                detailedDiagnostic = detailed?.diagnostic

                val detailedCandidate = detailed?.let {
                    detailedScanFrameCandidate(fastCandidate, it, rarityCalculator)
                }
                val anchoredSelection = ScanFrameFusion.resolveAnchoredFrames(
                    frames = listOf(fastCandidate),
                    authoritative = fastCandidate,
                    detailed = detailedCandidate,
                    evidenceWithoutActionCosts = { frame, excludeEvolutionCost, excludePowerUpCost ->
                        ScanManager.deriveSpeciesEvidenceWithoutActionCosts(
                            frame.fieldCandidates, frame.data, rarityCalculator,
                            excludeEvolutionCost = excludeEvolutionCost,
                            excludePowerUpCost = excludePowerUpCost
                        )
                    }
                )
                val fused = anchoredSelection?.frame?.data ?: ScanFrameFusion.fuse(
                    frames = listOf(fastCandidate),
                    authoritative = fastCandidate.data,
                    detailed = detailed?.pokemon ?: fastCandidate.data,
                    validCpList = ScanFrameFusion.validCpCandidates(listOf(fastCandidate)),
                    bestCpQuality = cpQuality
                )
                var speciesEvidence = anchoredSelection?.let {
                    ScanManager.finalAnchoredSpeciesEvidence(it, fused, rarityCalculator)
                } ?: fastCandidate.speciesEvidence

                val refinedData = speciesRefiner.refine(
                    fused,
                    fastDiagnostic.fieldCandidates + detailed?.diagnostic?.fieldCandidates.orEmpty()
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
                val phase2AuthorityGate = resolvePhase2AuthorityGate(
                    speciesEvidence = speciesEvidence,
                    candidateSpecies = authorityPokemon.realName ?: authorityPokemon.name,
                    retryRequested = productionStoppedBeforeConfidence
                )

                val timingToken = ScanRequestToken(
                    requestId = index.toLong() + 1L,
                    attemptId = 1,
                    projectionEpoch = 1L,
                    origin = RequestOrigin.USER
                )
                val classified = DebugScanTiming.withOwner(
                    token = timingToken,
                    sequence = index.toLong(),
                    frame = index
                ) {
                    DebugScanTiming.measure(ScanTimingStage.VARIANT_CLASSIFIER) {
                        variantDecisionEngine.classify(stageBitmap, authorityPokemon)
                    }
                }
                variantStageEvents = DebugScanTiming.snapshot(timingToken)?.events
                    .orEmpty()
                    .filter {
                        it.stage == ScanTimingStage.VARIANT_CLASSIFIER ||
                            it.stage.name.startsWith("VARIANT_")
                    }
                    .map { event ->
                        mapOf<String, Any?>(
                            "stage" to event.stage.name,
                            "durationMs" to (event.endNanos - event.startNanos) / 1_000_000.0,
                            "outcome" to event.outcome
                        )
                    }
                val classifiedPokemon = classified.pokemon
                val sizeTag = extractRawField(classifiedPokemon.rawOcrText, "SizeTag").ifBlank { null }
                val visualBase = visualDetector.detect(stageBitmap, classifiedPokemon.name, sizeTag)
                visualDetectorShinyPositive = visualBase.isShiny
                val visual = variantDecisionEngine.mergeVisualFeaturesForLockedSpecies(
                    visualFeatures = applyOcrOverrides(classifiedPokemon, visualBase),
                    lockedSpecies = phase2AuthorityGate.acceptedSpecies,
                    fullMatch = classified.fullMatch,
                    fallbackMatch = classified.resolvedMatch ?: classified.globalMatch
                )
                variantMergedShinyPositive = visual.isShiny

                val cpCandidates = listOfNotNull(classifiedPokemon.cp)
                val fixedCp = rarityCalculator.validateAndFixCP(classifiedPokemon, cpCandidates, visual)
                val final = if (fixedCp != null && fixedCp > 0 && fixedCp != classifiedPokemon.cp) {
                    classifiedPokemon.copy(cp = fixedCp)
                } else {
                    classifiedPokemon
                }
                finalPokemon = final
                val acceptedSpecies = phase2AuthorityGate.acceptedSpecies
                val phase2Result = if (
                    phase2AuthorityGate.mayRunSpeciesScopedPhase2 && !acceptedSpecies.isNullOrBlank()
                ) {
                    runCatching { phase2VariantClassifier.classify(stageBitmap, acceptedSpecies) }.getOrNull()
                } else {
                    null
                }
                val phase2ShinyDemoted = phase2AuthorityGate.mayApplyPhase2 &&
                    Phase2VariantFeatureMerger.shinyDemotionApplied(visual, phase2Result)
                val mergedVisual = if (phase2AuthorityGate.mayApplyPhase2 && phase2Result != null) {
                    Phase2VariantFeatureMerger.merge(visual, phase2Result)
                } else {
                    visual
                }
                unresolvedShinyForms = ShinySignatureStore.hasUnresolvedForms(
                    context,
                    acceptedSpecies ?: speciesEvidence.selectedCanonicalSpecies
                )
                val shinyGuard = ShinyFormAmbiguityGuard.apply(
                    features = mergedVisual,
                    phase2ShinyDemoted = phase2ShinyDemoted,
                    unresolvedForms = unresolvedShinyForms
                )
                val scoringVisual = shinyGuard.features
                phase2Predictions = phase2Result?.predictions?.map { prediction ->
                    mapOf(
                        "target" to prediction.target,
                        "predictedValue" to prediction.predictedValue,
                        "confidence" to prediction.confidence,
                        "margin" to prediction.margin,
                        "positiveScore" to prediction.positiveScore,
                        "negativeScore" to prediction.negativeScore,
                        "passedThreshold" to prediction.passedThreshold,
                        "source" to prediction.source
                    )
                }.orEmpty()

                scanDecision = if (productionStoppedBeforeConfidence) {
                    null
                } else {
                    scanConfidenceGate.evaluate(
                        ScanConfidenceInput(
                            pokemon = final,
                            frames = listOfNotNull(fastDiagnostic, detailedDiagnostic),
                            consistencyReason = consistency.reason,
                            visualSummary = VariantVisualSummary.from(scoringVisual, final.variantDecisionTrace),
                            speciesEvidence = speciesEvidence
                        )
                    )
                }
                val identity = RecognitionIdentityFactory.build(
                    RecognitionIdentityFactory.Input(
                        speciesEvidence = speciesEvidence,
                        scanAccepted = true,
                        lockedSpecies = acceptedSpecies,
                        classifierSpecies = final.variantDecisionTrace?.classifierSpecies,
                        fullMatchWinnerSpecies = classified.fullMatch?.winnerSpecies,
                        formCandidates = final.speciesResolverTrace?.formCandidates.orEmpty(),
                        mergedFeatures = scoringVisual,
                        phase2ShinyDemoted = shinyGuard.phase2ShinyDemoted,
                        sizeTag = sizeTag,
                        formEvidence = final.recognitionObservation?.formEvidence
                    )
                )
                finalVisual = scoringVisual
                ocrFieldStatuses = detailedDiagnostic?.structuredFields.orEmpty().map { field ->
                    mapOf(
                        "field" to field.field,
                        "status" to field.status,
                        "candidateCount" to field.candidateCount,
                        "reasonCode" to field.reasonCode
                    )
                }
                shinyDecisionReason = when {
                    unresolvedShinyForms -> "unresolved_shiny_form"
                    shinyGuard.phase2ShinyDemoted -> "phase2_explicit_negative_evidence"
                    identity.shiny == com.pokerarity.scanner.data.model.RecognitionTruth.TRUE ->
                        "typed_positive_evidence"
                    identity.shiny == com.pokerarity.scanner.data.model.RecognitionTruth.FALSE ->
                        "typed_explicit_negative_evidence"
                    else -> "typed_insufficient_evidence"
                }
                finalPokemon = final.copy(
                    scanDecision = scanDecision,
                    recognitionIdentity = identity
                )
            } finally {
                if (!stageBitmap.isRecycled) stageBitmap.recycle()
            }

        val identity = finalPokemon?.recognitionIdentity
        val publishable = scanDecision?.let { it.mayShowOverlay && it.maySaveScan } == true
        val shinyDiagnostics = finalVisual?.shinyDecisionDiagnostics
        val signatureCandidates = shinyDiagnostics?.signatureCandidates.orEmpty()
        val opposingNormalViews = signatureCandidates.filter { candidate ->
            candidate.scoreGap?.let { it <= -0.08f } == true &&
                candidate.colorGap?.let { it <= -0.08f } == true
        }
        val chosenSignature = signatureCandidates.firstOrNull {
            it.source == shinyDiagnostics?.chosenSignatureCandidate
        }
        return mapOf(
            "frame" to frameId,
            "frameIndex" to index,
            "route" to actualRoute,
            "ocrInvoked" to frameDiagnostics.isNotEmpty(),
            "fastMs" to fastMs,
            "detailedMs" to detailedMs,
            "variantStageEvents" to variantStageEvents,
            "fastRecognitionWidth" to fastDiagnostic.imageWidth,
            "detailedRecognitionWidth" to detailedDiagnostic?.imageWidth,
            "nativeResolutionRequested" to nativeResolutionRequested,
            "routeMs" to routeMs,
            "cpQuality" to cpQuality,
            "finalSpecies" to identity?.canonicalSpecies,
            // Numeric values are internal QA evidence only. Candidate fields are not
            // claimed as published values when the confidence gate refuses publication.
            "numericDecision" to mapOf(
                "status" to if (publishable) "PUBLISHABLE" else "NOT_PUBLISHABLE",
                "fast" to mapOf(
                    "cp" to numericReplayRead(fastCandidate.data.cp),
                    "hp" to numericReplayRead(fastCandidate.data.hp),
                    "maxHp" to numericReplayRead(fastCandidate.data.maxHp)
                ),
                "detailed" to detailedDiagnostic?.selected?.let { selected ->
                    mapOf(
                        "cp" to numericReplayRead(selected.cp),
                        "hp" to numericReplayRead(selected.hp),
                        "maxHp" to numericReplayRead(selected.maxHp)
                    )
                },
                "finalCandidate" to mapOf(
                    "cp" to numericReplayRead(finalPokemon?.cp),
                    "hp" to numericReplayRead(finalPokemon?.hp),
                    "maxHp" to numericReplayRead(finalPokemon?.maxHp)
                )
            ),
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
            "detailedScreenState" to detailedDiagnostic?.screenState,
            "ocrFields" to ocrFieldStatuses,
            "speciesNameOcr" to listOfNotNull(
                frameDiagnostics.firstOrNull()?.fieldCandidates
                    ?.firstOrNull { it.field == "NameTextual" }
                    ?.let { candidate ->
                        mapOf(
                            "pass" to "fast",
                            "fieldStatus" to candidate.status,
                            "cropAvailable" to (candidate.cropLeft != null &&
                                candidate.cropTop != null &&
                                candidate.cropRight != null &&
                                candidate.cropBottom != null),
                            "parserResult" to candidate.parserResult
                        )
                    },
                detailedDiagnostic?.fieldCandidates
                    ?.firstOrNull { it.field == "NameTextual" }
                    ?.let { candidate ->
                        mapOf(
                            "pass" to "detailed",
                            "fieldStatus" to candidate.status,
                            "cropAvailable" to (candidate.cropLeft != null &&
                                candidate.cropTop != null &&
                                candidate.cropRight != null &&
                                candidate.cropBottom != null),
                            "parserResult" to candidate.parserResult
                        )
                    }
            ),
            "speciesStatus" to identity?.speciesStatus?.name,
            "species" to identity?.canonicalSpecies,
            "speciesCandidate" to speciesEvidenceAfterReconcile?.selectedCanonicalSpecies,
            "shinyStatus" to if (publishable) identity?.shiny?.name ?: "UNKNOWN" else "NO_RESULT",
            "candidateShinyStatus" to identity?.shiny?.name,
            "shinyDecisionReason" to shinyDecisionReason,
            "visualDetectorShinyPositive" to visualDetectorShinyPositive,
            "variantMergedShinyPositive" to variantMergedShinyPositive,
            "visualShinyPositive" to finalVisual?.isShiny,
            "strongShinySignature" to finalVisual?.hasStrongShinySignatureEvidence,
            "signatureReferenceAvailable" to shinyDiagnostics?.signatureReferenceAvailable,
            "signatureSource" to shinyDiagnostics?.chosenSignatureCandidate,
            "signatureConfidence" to chosenSignature?.confidence,
            "signatureCandidates" to signatureCandidates.map { candidate ->
                mapOf(
                    "source" to candidate.source,
                    "referenceAvailable" to candidate.referenceAvailable,
                    "matched" to candidate.matched,
                    "confidence" to candidate.confidence,
                    "normalScore" to candidate.normalScore,
                    "shinyScore" to candidate.shinyScore,
                    "normalColorScore" to candidate.normalColorScore,
                    "shinyColorScore" to candidate.shinyColorScore,
                    "scoreGap" to candidate.scoreGap,
                    "colorGap" to candidate.colorGap,
                    "rejectionReason" to candidate.rejectionReason
                )
            },
            "normalCounterevidence" to mapOf(
                "strongOpposingViewCount" to opposingNormalViews.size,
                "maxNormalScoreAdvantage" to signatureCandidates.mapNotNull { it.scoreGap }
                    .maxOfOrNull { (-it).coerceAtLeast(0f) },
                "maxNormalColorAdvantage" to signatureCandidates.mapNotNull { it.colorGap }
                    .maxOfOrNull { (-it).coerceAtLeast(0f) }
            ),
            "bodyColor" to mapOf(
                "maskedMatched" to shinyDiagnostics?.maskedColorMatched,
                "maskedConfidence" to shinyDiagnostics?.maskedColorConfidence,
                "rawMatched" to shinyDiagnostics?.rawColorMatched,
                "rawConfidence" to shinyDiagnostics?.rawColorConfidence,
                "independentSupportAtPr195Threshold" to (
                    (shinyDiagnostics?.maskedColorMatched == true &&
                        (shinyDiagnostics.maskedColorConfidence ?: 0f) >= 0.78f) ||
                    (shinyDiagnostics?.rawColorMatched == true &&
                        (shinyDiagnostics.rawColorConfidence ?: 0f) >= 0.78f)
                ),
                "hueMatched" to shinyDiagnostics?.hueMatched,
                "hueConfidence" to shinyDiagnostics?.hueConfidence,
                "histogramHueMatched" to shinyDiagnostics?.histogramHueMatched,
                "histogramHueConfidence" to shinyDiagnostics?.histogramHueConfidence
            ),
            "form" to mapOf(
                "status" to identity?.formStatus?.name,
                "knownForm" to identity?.knownForm,
                "ambiguousForms" to identity?.ambiguousForms,
                "reasonCodes" to identity?.formReasonCodes,
                "variantCandidate" to finalPokemon?.variantDecisionTrace?.fullVariantForm
            ),
            "costume" to mapOf(
                "status" to identity?.costume?.name,
                "visualPositive" to finalVisual?.hasCostume,
                "decisionReason" to finalVisual?.costumeDecisionDiagnostics?.decisionReason,
                "confidence" to finalVisual?.costumeDecisionDiagnostics?.finalConfidence,
                "variantCandidate" to finalPokemon?.variantDecisionTrace?.fullVariantCostume
            ),
            "variantTrace" to finalPokemon?.variantDecisionTrace?.let { trace ->
                mapOf(
                    "classifierShiny" to trace.classifierShiny,
                    "classifierCostume" to trace.classifierCostume,
                    "classifierConfidence" to trace.classifierConfidence,
                    "fullVariantShiny" to trace.fullVariantShiny,
                    "fullVariantShinyConfidence" to trace.fullVariantShinyConfidence,
                    "fullVariantCostume" to trace.fullVariantCostume,
                    "fullVariantForm" to trace.fullVariantForm,
                    "fullVariantVariantConfidence" to trace.fullVariantVariantConfidence
                )
            },
            "phase2Predictions" to phase2Predictions,
            "scanDecisionDetails" to scanDecision?.let {
                mapOf(
                    "decision" to it.decision.name,
                    "confidence" to it.confidence,
                    "developerReasons" to it.developerReasons,
                    "evidenceUsed" to it.evidenceUsed,
                    "evidenceMissing" to it.evidenceMissing,
                    "mayShowOverlay" to it.mayShowOverlay,
                    "maySaveScan" to it.maySaveScan
                )
            },
            "unresolvedShinyForms" to unresolvedShinyForms
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

    private fun seedVariantBatch(context: Context, directory: File) {
        val fixtureRoot = "scan_fixtures/live_variant_batch_20260318/"
        val orderedFixtures = listOf(
            "00_F03_cold.png" to "scan_1773798028504_0.png",
            "01_Butterfree_shiny_01.png" to "scan_1773798087894_0.png",
            "02_Slowpoke_regular.png" to "scan_1773797298959_0.png",
            "03_Slowpoke_costume.png" to "scan_1773797985802_0.png",
            "04_F03_warm_01.png" to "scan_1773798028504_0.png",
            "05_Butterfree_shiny_02.png" to "scan_1773798087894_0.png",
            "06_F03_warm_02.png" to "scan_1773798028504_0.png",
            "07_Butterfree_shiny_03.png" to "scan_1773798087894_0.png"
        )
        directory.deleteRecursively()
        check(directory.mkdirs() || directory.isDirectory)
        orderedFixtures.forEach { (targetName, assetName) ->
            File(directory, targetName).outputStream().use { output ->
                context.assets.open(fixtureRoot + assetName).use { input -> input.copyTo(output) }
            }
        }
    }

}
