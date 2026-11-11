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
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.service.resolvePhase2AuthorityGate
import com.pokerarity.scanner.util.ocr.FrameRouteDiagnostic
import com.pokerarity.scanner.util.ocr.FrameOcrRequest
import com.pokerarity.scanner.util.ocr.OCRProcessor
import com.pokerarity.scanner.util.ocr.RecognitionIdentityFactory
import com.pokerarity.scanner.util.ocr.ScanConfidenceGate
import com.pokerarity.scanner.util.ocr.ScanConfidenceInput
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import com.pokerarity.scanner.util.ocr.ScreenRouteAction
import com.pokerarity.scanner.util.ocr.SpeciesRefiner
import com.pokerarity.scanner.util.ocr.VariantVisualSummary
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
 * Input: cacheDir/replay_in (staged PNGs). Args: -e scale baseline900|native,
 * -e targetRevision <full-git-sha>, and optionally -e expectedFrameCount <count>.
 * Output: hash-keyed redacted cacheDir/router_replay_out/report.json.
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
        val excludedFrame = args.getString("exclude")?.trim()?.takeIf { it.isNotEmpty() }
        val targetRevision = args.getString("targetRevision")?.trim()?.takeIf { it.isNotEmpty() }
        val expectedFrameCount = args.getString("expectedFrameCount")?.toIntOrNull()
        val inDir = File(appContext.cacheDir, "replay_in")
        val outDir = File(appContext.cacheDir, "router_replay_out")
        outDir.deleteRecursively()
        outDir.mkdirs()
        appContext.getExternalFilesDir(null)?.let { File(it, "router_replay_out").deleteRecursively() }

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
        var finalVisual: VisualFeatures? = null
        var phase2Predictions: List<Map<String, Any?>> = emptyList()
        var shinyDecisionReason: String? = null
        var unresolvedShinyForms = false
        var ocrFieldStatuses: List<Map<String, Any?>> = emptyList()

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
                val detailedCandidate = ScanFrameCandidate(file.absolutePath, detailed.pokemon, cpQuality, detailedEvidence,
                    fieldCandidates = detailed.diagnostic.fieldCandidates)
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
                    detailed = detailed.pokemon,
                    validCpList = ScanFrameFusion.validCpCandidates(listOf(fastCandidate)),
                    bestCpQuality = cpQuality
                )
                var speciesEvidence = anchoredSelection?.let {
                    ScanManager.finalAnchoredSpeciesEvidence(it, fused, rarityCalculator)
                } ?: fastCandidate.speciesEvidence

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
                val phase2AuthorityGate = resolvePhase2AuthorityGate(
                    speciesEvidence = speciesEvidence,
                    candidateSpecies = authorityPokemon.realName ?: authorityPokemon.name,
                    retryRequested = productionStoppedBeforeConfidence
                )

                val classified = variantDecisionEngine.classify(stageBitmap, authorityPokemon)
                val classifiedPokemon = classified.pokemon
                val sizeTag = extractRawField(classifiedPokemon.rawOcrText, "SizeTag").ifBlank { null }
                val visualBase = visualDetector.detect(stageBitmap, classifiedPokemon.name, sizeTag)
                val visual = variantDecisionEngine.mergeVisualFeaturesForLockedSpecies(
                    visualFeatures = applyOcrOverrides(classifiedPokemon, visualBase),
                    lockedSpecies = phase2AuthorityGate.acceptedSpecies,
                    fullMatch = classified.fullMatch,
                    fallbackMatch = classified.resolvedMatch ?: classified.globalMatch
                )

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
                            frames = listOf(fastDiagnostic, detailed.diagnostic),
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
                ocrFieldStatuses = detailed.diagnostic.structuredFields.map { field ->
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
        } else {
            bitmap.recycle()
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
            "detailedScreenState" to detailedDiagnostic?.screenState,
            "ocrFields" to ocrFieldStatuses,
            "speciesStatus" to identity?.speciesStatus?.name,
            "species" to identity?.canonicalSpecies,
            "speciesCandidate" to (finalPokemon?.realName ?: finalPokemon?.name ?: refined?.realName ?: refined?.name),
            "shinyStatus" to if (publishable) identity?.shiny?.name ?: "UNKNOWN" else "NO_RESULT",
            "candidateShinyStatus" to identity?.shiny?.name,
            "shinyDecisionReason" to shinyDecisionReason,
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

}
