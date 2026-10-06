package com.pokerarity.scanner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.OcrConfidenceReasons
import com.pokerarity.scanner.data.model.OcrConfidenceReasonsBuilder
import com.pokerarity.scanner.data.model.RecognitionIdentityCompat
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator
import com.pokerarity.scanner.ui.result.ResultActivity
import com.pokerarity.scanner.util.ScanError
import com.pokerarity.scanner.util.ScanResult
import com.pokerarity.scanner.util.ocr.OcrDiagnosticsExporter
import com.pokerarity.scanner.util.ocr.OCRProcessor
import com.pokerarity.scanner.util.ocr.ConfidenceReasonDiagnostic
import com.pokerarity.scanner.util.ocr.FrameDiagnostic
import com.pokerarity.scanner.util.ocr.FrameRouteDiagnostic
import com.pokerarity.scanner.util.ocr.RecognitionIdentityFactory
import com.pokerarity.scanner.util.ocr.ScreenRouteAction
import com.pokerarity.scanner.util.ocr.aggregateFrameRoutes
import com.pokerarity.scanner.util.ocr.ScreenRouteOutcome
import com.pokerarity.scanner.util.ocr.ScreenStateRouter
import com.pokerarity.scanner.util.ocr.CalibrationDiagnostic
import com.pokerarity.scanner.util.ocr.CalibrationResolution
import com.pokerarity.scanner.util.ocr.CALIBRATED_BAR_ANCHOR_REASON
import com.pokerarity.scanner.util.ocr.DisplayGeometrySignature
import com.pokerarity.scanner.util.ocr.FrameGeometry
import com.pokerarity.scanner.util.ocr.FrameOcrRequest
import com.pokerarity.scanner.util.ocr.FrameResolution
import com.pokerarity.scanner.util.ocr.RoutedScreen
import com.pokerarity.scanner.util.ocr.NormalizedRect
import com.pokerarity.scanner.util.ocr.ScreenCalibrationManager
import com.pokerarity.scanner.util.ocr.ScreenGeometryBuilder
import com.pokerarity.scanner.util.ocr.RecognitionSnapshotHolder
import com.pokerarity.scanner.util.ocr.ScreenCalibrationStore
import com.pokerarity.scanner.util.ocr.OcrFrameResult
import com.pokerarity.scanner.util.ocr.PokemonSummary
import com.pokerarity.scanner.util.ocr.RecognitionObservation
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import com.pokerarity.scanner.util.ocr.ScanConfidenceGate
import com.pokerarity.scanner.util.ocr.ScanConfidenceInput
import com.pokerarity.scanner.util.ocr.ScanDiagnosticReport
import com.pokerarity.scanner.util.ocr.ScanDecision
import com.pokerarity.scanner.util.ocr.ScanDecisionType
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import com.pokerarity.scanner.util.ocr.SpeciesRefiner
import com.pokerarity.scanner.util.ocr.SpeciesRefinerConfig
import com.pokerarity.scanner.util.ocr.StageTimingDiagnostic
import com.pokerarity.scanner.util.ocr.VariantVisualSummary
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.util.vision.Phase2VariantFeatureMerger
import com.pokerarity.scanner.util.vision.VariantDecisionEngine
import com.pokerarity.scanner.util.vision.VisualFeatureDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Date
import com.pokerarity.scanner.util.DateParseUtils
import com.pokerarity.scanner.util.DateParseUtils.formatDate


/**
 * Orchestrates the full scan pipeline:
 *   Screenshot → OCR → Visual Detection → Rarity Calculation → Save → Show Result
 *
 * Register with [start] from an Activity / Application and unregister with [stop].
 */
class ScanManager(private val context: Context) {

    companion object {
        private const val TAG = "ScanManager"
        private const val IV_DIAGNOSTIC_BROAD_THRESHOLD = 20
        private const val MAX_SCREENSHOT_FRAMES = 3
        private const val LIVE_BAR_ANCHOR_NAME = "hp_bar"

        internal fun shouldRunDetailedPassForAuthoritative(
            pokemon: PokemonData,
            cpQuality: Double,
            speciesEvidence: SpeciesEvidence
        ): Boolean {
            return ScanFrameFusion.shouldRunDetailedPass(
                pokemon,
                cpQuality,
                speciesEvidence
            )
        }

        internal fun deriveSpeciesEvidence(
            fieldCandidates: List<com.pokerarity.scanner.util.ocr.FieldCandidateDiagnostic>,
            pokemon: PokemonData,
            rarityCalculator: RarityCalculator
        ): SpeciesEvidence {
            val observation = pokemon.recognitionObservation
            return if (observation == null) {
                // Legacy/imported path: name candidates carry textual authority directly.
                val evidence = SpeciesEvidence.fromFieldCandidates(fieldCandidates)
                evidence.withProfileStatus(
                    profileStatus(pokemon, evidence.selectedCanonicalSpecies, rarityCalculator)
                )
            } else {
                // Anchored path: the resolver-driven "Name" candidate mirrors the
                // structured result and is never double-counted as textual evidence;
                // textual authority comes only from the dedicated NameTextual candidate.
                val textual = SpeciesEvidence.fromFieldCandidates(
                    fieldCandidates.filter { it.field == "NameTextual" })
                guardedAnchoredObservationEvidence(textual, observation) ?: run {
                    val identity = com.pokerarity.scanner.util.ocr.FamilySpeciesResolver(
                        rarityCalculator.recognitionSnapshot, rarityCalculator
                    ).resolveWithEvaluation(
                        pokemon,
                        com.pokerarity.scanner.util.ocr.FamilySpeciesResolver.Observation(
                            candySpecies = observation.candySpecies,
                            exactCandyLabel = observation.candySpecies != null,
                            powerUpStardust = observation.powerUpStardust,
                            anchoredPowerUpCost = observation.powerUpStardust != null,
                            types = observation.types,
                            evolutionCandyCost = observation.evolutionCandyCost
                        )
                    )
                    composeIdentityEvidence(textual, identity)
                }
            }
        }

        /** Phase 1D authority composition: structured vs textual sources stay distinct. */
        private fun composeIdentityEvidence(
            textual: SpeciesEvidence,
            identity: com.pokerarity.scanner.util.ocr.CandidateEvaluation
        ): SpeciesEvidence {
            val structuredSpecies = identity.acceptedSpecies
            val textualSpecies = textual.selectedCanonicalSpecies
            val textualHard = textual.hasHardAuthority && !textualSpecies.isNullOrBlank()
            if (identity.outcome == com.pokerarity.scanner.util.ocr.EvaluationOutcome.UNIQUE_SUPPORTED) {
                return structuredUniqueEvidence(textual, textualHard, textualSpecies, structuredSpecies!!)
            }
            val profileStatus = when {
                identity.outcome == com.pokerarity.scanner.util.ocr.EvaluationOutcome.CONTRADICTION ->
                    SpeciesProfileStatus.CONTRADICTORY
                textualSpeciesContradicted(textualSpecies, identity) -> SpeciesProfileStatus.CONTRADICTORY
                else -> SpeciesProfileStatus.INDETERMINATE
            }
            val outcomeReason = structuredOutcomeReason(identity.outcome)
            // An editable title never chooses between independently plausible family
            // members: while another distinct species survives the structured evaluator,
            // the exact title stays diagnostic provenance only (non-agreeing authority).
            val competingSurvivors = identity.survivingCandidates
                .map { it.species }.distinct()
                .any { !it.equals(textualSpecies, ignoreCase = true) }
            return if (textualHard) {
                textual.copy(selectedCanonicalSpecies = textualSpecies)
                    .withProfileStatus(profileStatus)
                    .let {
                        it.copy(
                            observationsAgree = it.observationsAgree && !competingSurvivors,
                            reasonCodes = it.reasonCodes + outcomeReason
                        )
                    }
            } else {
                SpeciesEvidence(
                    selectedCanonicalSpecies = textualSpecies,
                    authority = textual.authority,
                    profileStatus = profileStatus,
                    reasonCodes = listOf(textualAuthorityReason(textual.authority), outcomeReason),
                    observationsAgree = false,
                    authorityConflict = false
                )
            }
        }

        private fun structuredUniqueEvidence(
            textual: SpeciesEvidence,
            textualHard: Boolean,
            textualSpecies: String?,
            structuredSpecies: String
        ): SpeciesEvidence {
            val disagrees = textualHard && !textualSpecies.equals(structuredSpecies, true)
            val reasons = buildList {
                add(com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason.INDEPENDENT_PROFILE)
                if (textualHard && !disagrees) addAll(textual.reasonCodes)
                if (disagrees) add(com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason.AUTHORITY_CONFLICT)
            }
            return SpeciesEvidence(
                selectedCanonicalSpecies = structuredSpecies,
                authority = SpeciesAuthority.INDEPENDENT_PROFILE,
                profileStatus = SpeciesProfileStatus.COMPATIBLE,
                reasonCodes = reasons,
                observationsAgree = !disagrees,
                authorityConflict = disagrees
            )
        }

        /** The textual species is contradicted only when EVERY family row of that
         * species was eliminated by observed structured constraints (a species may
         * have several form rows; an unknown-metadata row is not an elimination). */
        private fun textualSpeciesContradicted(
            textualSpecies: String?,
            identity: com.pokerarity.scanner.util.ocr.CandidateEvaluation
        ): Boolean {
            val familyRows = if (textualSpecies.isNullOrBlank()) {
                emptyList()
            } else {
                identity.initialCandidates.filter { it.species == textualSpecies }
            }
            val eliminatedRows = identity.evaluations.flatMap { it.eliminated }.toSet()
            return familyRows.isNotEmpty() && familyRows.all { it in eliminatedRows }
        }

        internal fun profileStatus(
            pokemon: PokemonData,
            species: String?,
            rarityCalculator: RarityCalculator
        ): SpeciesProfileStatus {
            val hasNoProfile = pokemon.cp == null || pokemon.cp <= 0 ||
                pokemon.maxHp == null
            val status = if (hasNoProfile) {
                SpeciesProfileStatus.MISSING
            } else if (species.isNullOrBlank()) {
                SpeciesProfileStatus.INDETERMINATE
            } else {
                resolveProfileFit(pokemon, species, rarityCalculator)
            }
            return status
        }

        internal fun sanitizeScreenshotPaths(paths: List<String>, cacheDir: File): List<String> {
            val cacheRoot = runCatching { cacheDir.canonicalFile }.getOrElse { return emptyList() }
            return paths.asSequence()
                .mapNotNull { rawPath ->
                    val file = runCatching { File(rawPath).canonicalFile }.getOrNull() ?: return@mapNotNull null
                    val isInCache = file.parentFile == cacheRoot
                    val isScanImage = file.name.startsWith("scan_") && file.name.endsWith(".png")
                    file.takeIf { isInCache && isScanImage && it.isFile }
                }
                .take(MAX_SCREENSHOT_FRAMES)
                .map { it.absolutePath }
                .toList()
        }

        internal fun resolvePhase2AuthorityGate(
            speciesEvidence: SpeciesEvidence,
            candidateSpecies: String?,
            retryRequested: Boolean
        ): Phase2AuthorityGate = com.pokerarity.scanner.service.resolvePhase2AuthorityGate(
            speciesEvidence,
            candidateSpecies,
            retryRequested
        )
    }

    /** Diagnostic-report tail shared by the final and retry report builders. */
    internal data class ScanReportContext(
        val frames: List<FrameDiagnostic>,
        val variantSummary: VariantVisualSummary?,
        val stageTimings: List<StageTimingDiagnostic>,
        val frameRoutes: List<FrameRouteDiagnostic>,
        val fallbackReason: String? = null,
        /** Phase 2F bounded request-ownership metadata for the diagnostic report. */
        val ownership: com.pokerarity.scanner.util.ocr.RequestOwnershipDiagnostic? = null
    )

    internal data class DecodedFrame(
        val index: Int,
        val path: String,
        val bitmap: Bitmap,
        val cpQuality: Double,
        val pooled: Boolean,
        /** Source screenshot geometry, preserved before the 900-wide recognition downscale. */
        val sourceWidth: Int = 0,
        val sourceHeight: Int = 0
    )

    /**
     * Routes one decoded frame, then runs the normal species OCR step only for eligible
     * detail routes. Terminal non-detail, unstable, and unknown frames never reach OCR
     * and never enter species fusion; their route record stays in diagnostics.
     *
     * @return true when the caller may stop processing further frames (early exit).
     */
    internal suspend fun processRoutedFrame(
        frame: DecodedFrame,
        results: MutableList<ScanFrameCandidate>,
        frameDiagnostics: MutableList<FrameDiagnostic>,
        frameRoutes: MutableList<FrameRouteDiagnostic>
    ): Boolean {
        val shouldStop = try {
            routeAndRecognize(frame, results, frameDiagnostics, frameRoutes)
        } catch (e: Exception) {
            Log.e(TAG, "Frame OCR failed: framePath=${SafeDebugLogValue.localFileReference(frame.path)}", e)
            false
        } finally {
            releaseBitmap(frame.bitmap, frame.pooled)
        }
        return shouldStop
    }

    private suspend fun routeAndRecognize(
        frame: DecodedFrame,
        results: MutableList<ScanFrameCandidate>,
        frameDiagnostics: MutableList<FrameDiagnostic>,
        frameRoutes: MutableList<FrameRouteDiagnostic>
    ): Boolean {
        val routed = screenRouter.routeWithClassification(frame.bitmap)
        val route = routed.decision
        frameRoutes += FrameRouteDiagnostic(
            frameIndex = frame.index,
            path = frame.path,
            action = route.action,
            screenType = route.screenType.name,
            confidence = route.confidence,
            safeFallback = route.safeFallback,
            reason = route.reason
        )
        if (route.action != ScreenRouteAction.PROCEED_DETAIL) {
            Log.d(TAG, "Frame routed away from species OCR: index=${frame.index} " +
                "action=${route.action} screenType=${route.screenType} reason=${route.reason}")
            return false
        }

        val recognition = recognizeFrameWithCalibration(frame, routed)
        frameDiagnostics += recognition.diagnostic
        results.add(
            ScanFrameCandidate(
                frame.path,
                recognition.frameResult.pokemon,
                frame.cpQuality,
                recognition.speciesEvidence,
                recognition.context
            )
        )
        val shouldStop = ScanFrameFusion.isHighConfidence(results)
        if (shouldStop) {
            Log.d(TAG, "Early exit: high-confidence OCR frame found after ${results.size} frames")
        }
        return shouldStop
    }

    /**
     * One detail-routed frame's species OCR with Phase 2B persistent calibration: the
     * routed classification is turned into [ScreenGeometry] by the geometry authority
     * (no re-classification), that geometry feeds the calibration lookup/validation, and
     * the resolved hint reaches the OCR request.
     */
    private suspend fun recognizeFrameWithCalibration(
        frame: DecodedFrame,
        routed: RoutedScreen
    ): FrameRecognition {
        val screenGeometry = screenGeometryBuilder.build(frame.bitmap, routed.classification)
        val preOcrGeometry = FrameGeometry(
            detailCard = screenGeometry.detailCardRect
                ?.let { NormalizedRect.fromRect(it, frame.bitmap.width, frame.bitmap.height) },
            frameWidth = frame.bitmap.width,
            frameHeight = frame.bitmap.height
        )
        val signature = displaySignatureOrNull(frame)
        val preResolution = signature?.let { screenCalibration.resolveForFrame(it, preOcrGeometry) }
            ?: FrameResolution(resolution = CalibrationResolution.UNAVAILABLE)

        val frameResult = frameOcr.recognize(
            FrameOcrRequest(
                bitmap = frame.bitmap,
                includeSecondaryFields = false,
                frameIndex = frame.index,
                frameRole = "fast",
                estimatedCpCropQuality = frame.cpQuality,
                calibration = preResolution.toHint(),
                geometry = screenGeometry
            )
        )

        val liveBar = liveCalibrationBar(frameResult.diagnostic)
        val finalResolution = signature?.let {
            screenCalibration.onFrameGeometryObserved(
                signature = it,
                pre = preResolution,
                geometry = preOcrGeometry.copy(liveBarRect = liveBar)
            )
        } ?: preResolution
        val barSource = when {
            liveBar != null -> CalibrationDiagnostic.BAR_SOURCE_LIVE
            preResolution.hasHint -> CalibrationDiagnostic.BAR_SOURCE_CALIBRATED
            else -> null
        }
        val diagnostic = attachCalibrationDiagnostics(frameResult.diagnostic, finalResolution, barSource)
        val speciesEvidence = deriveSpeciesEvidence(
            diagnostic.fieldCandidates,
            frameResult.pokemon,
            rarityCalculator
        )
        return FrameRecognition(
            frameResult,
            diagnostic,
            speciesEvidence,
            RecognitionContext(screenGeometry, preResolution.toHint())
        )
    }

    private data class FrameRecognition(
        val frameResult: OcrFrameResult,
        val diagnostic: FrameDiagnostic,
        val speciesEvidence: SpeciesEvidence,
        val context: RecognitionContext
    )

    /** Source-vs-recognition signature: source dims survive the 900-wide downscale. */
    private fun displaySignatureOrNull(frame: DecodedFrame): DisplayGeometrySignature? =
        DisplayGeometrySignature.from(
            sourceWidth = frame.sourceWidth,
            sourceHeight = frame.sourceHeight,
            densityDpi = context.resources.configuration.densityDpi,
            recognitionWidth = frame.bitmap.width,
            recognitionHeight = frame.bitmap.height
        )

    private fun liveCalibrationBar(diagnostic: FrameDiagnostic): android.graphics.Rect? =
        diagnostic.anchors
            .firstOrNull {
                it.name == LIVE_BAR_ANCHOR_NAME && it.reason != CALIBRATED_BAR_ANCHOR_REASON
            }
            ?.let { android.graphics.Rect(it.left, it.top, it.right, it.bottom) }

    /** Completes the recognizer's partial calibration block with resolution + timings. */
    private fun attachCalibrationDiagnostics(
        diagnostic: FrameDiagnostic,
        resolution: FrameResolution,
        barSource: String?
    ): FrameDiagnostic {
        val base = diagnostic.calibration ?: CalibrationDiagnostic(
            signatureKey = resolution.signatureKey,
            schemaRevision = resolution.schemaRevision,
            resolution = resolution.resolution.name,
            provenance = CalibrationDiagnostic.PROVENANCE_NONE,
            barSource = barSource,
            reasonCodes = emptyList(),
            lookupMs = null,
            validationMs = null
        )
        val provenance = when (resolution.resolution) {
            CalibrationResolution.REBUILT,
            CalibrationResolution.INVALIDATED_REBUILT -> CalibrationDiagnostic.PROVENANCE_REBUILT
            CalibrationResolution.UNAVAILABLE,
            CalibrationResolution.INVALIDATED,
            CalibrationResolution.SCHEMA_INVALIDATED -> CalibrationDiagnostic.PROVENANCE_NONE
            else -> if (resolution.record != null) {
                CalibrationDiagnostic.PROVENANCE_PERSISTED
            } else {
                CalibrationDiagnostic.PROVENANCE_NONE
            }
        }
        val timings = diagnostic.stageTimings +
            listOfNotNull(
                resolution.lookupMs?.let { StageTimingDiagnostic("calibration_lookup", it) },
                resolution.validationMs?.let { StageTimingDiagnostic("calibration_validate", it) }
            )
        return diagnostic.copy(
            calibration = base.copy(
                resolution = resolution.resolution.name,
                provenance = provenance,
                schemaRevision = resolution.schemaRevision ?: base.schemaRevision,
                barSource = barSource ?: base.barSource,
                reasonCodes = base.reasonCodes + resolution.reasonCodes,
                lookupMs = resolution.lookupMs,
                validationMs = resolution.validationMs
            ),
            stageTimings = timings
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanMutex = Mutex()
    private val decodeBitmapPool = BitmapPool(maxSize = 2)

    private val ocrProcessor by lazy { OCRProcessor(context) }

    /**
     * Phase 2A screen-state router: cheap bitmap/anchor routing BEFORE any species OCR.
     * Routing authority is not species authority; content corroboration still applies
     * after OCR through the unchanged Phase 1 evidence path.
     */
    internal var screenRouter: ScreenStateRouter = ScreenStateRouter()

    /**
     * Phase 2B geometry authority: builds the frame's [ScreenGeometry] from the routed
     * Phase 2A classification (no second classification) and feeds the calibration path.
     */
    internal var screenGeometryBuilder: ScreenGeometryBuilder = ScreenGeometryBuilder()

    /**
     * Phase 2B persistent screen calibration: per-display-configuration geometry store
     * consulted only for detail-routed frames, validated against live anchors each time.
     */
    internal var screenCalibration: ScreenCalibrationManager =
        ScreenCalibrationManager(ScreenCalibrationStore(context))

    /** OCR seam for diagnostics; production forwards to [OCRProcessor.processImageWithDiagnostics]. */
    internal var frameOcr: FrameOcr = FrameOcr { request ->
        ocrProcessor.processImageWithDiagnostics(request)
    }
    private val visualDetector by lazy { VisualFeatureDetector(context) }
    private val variantDecisionEngine by lazy { VariantDecisionEngine(context) }
    private val phase2VariantClassifier by lazy { Phase2VariantClassifier(context) }
    private val repository by lazy { PokemonRepository(AppDatabase.getInstance(context)) }
    private val rarityCalculator by lazy { RarityCalculator(context) }
    private val speciesRefiner by lazy { SpeciesRefiner(context, rarityCalculator) }
    private val consistencyGate by lazy { ScanConsistencyGate(context, rarityCalculator) }
    private val scanConfidenceGate by lazy { ScanConfidenceGate() }
    private val telemetryCoordinator by lazy { ScanTelemetryCoordinator.getInstance(context) }

    /** Phase 2F seam over the repository-backed rarity inputs (test override only). */
    private inner class ProductionScanRarityInputs : ScanRarityInputs {
        override suspend fun baseRarity(name: String?): Int =
            repository.getPokemonBaseRarity(name ?: "Unknown")

        override suspend fun eventBonus(
            pokemon: PokemonData,
            features: com.pokerarity.scanner.data.model.VisualFeatures
        ): Int = repository.resolveEventBonus(pokemon, features)

        override suspend fun liveEventContext(
            pokemon: PokemonData,
            features: com.pokerarity.scanner.data.model.VisualFeatures
        ) = repository.resolveLiveEventContext(pokemon, features)
    }

    /**
     * Phase 2F publication seam: every externally visible/persistent terminal side
     * effect funnels through one sink so stale-suppression has a single choke point and
     * tests can observe exactly-once publication.
     */
    internal var rarityInputs: ScanRarityInputs = ProductionScanRarityInputs()

    /** Phase 2F seam: telemetry upload-id allocation (test override only). */
    internal var telemetryUploadIdProvider: () -> String? = { telemetryCoordinator.newUploadIdOrNull() }

    internal var publicationSink: ScanPublicationSink = object : ScanPublicationSink {
        override fun showResultOverlay(intent: Intent) {
            context.startService(intent)
        }

        override suspend fun saveScan(
            pokemon: PokemonData,
            features: com.pokerarity.scanner.data.model.VisualFeatures,
            rarityScore: com.pokerarity.scanner.data.model.RarityScore
        ) {
            repository.saveScan(pokemon, features, rarityScore)
        }

        override fun enqueueTelemetry(payload: TelemetryPayload) {
            telemetryCoordinator.enqueueAndFlush(
                uploadId = payload.uploadId,
                pokemonData = payload.pokemon,
                features = payload.features,
                rarityScore = payload.rarityScore,
                screenshotPath = null,
                pipelineMs = payload.pipelineMs,
                phase2Result = payload.phase2Result
            )
        }
    }

    // ── BroadcastReceiver for screenshot-ready events ────────────────────

    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            Log.d(TAG, "onReceive: action=${intent.action}, extras=${intent.extras?.keySet()?.joinToString()}")
            handleScreenshotReadyBroadcast(intent)
        }
    }

    /**
     * Phase 2F owned screenshot-ready intake. Returns false when the broadcast is
     * rejected fail-closed: missing ownership metadata, unknown/superseded request, or
     * an old projection/session epoch. An unowned production screenshot is never
     * accepted as the current request merely because it arrived last.
     */
    internal fun handleScreenshotReadyBroadcast(intent: Intent): Boolean {
        if (intent.action != ScreenCaptureService.ACTION_SCREENSHOT_READY) return false
        val ownership = intent.parseOwnership() ?: run {
            Log.w(TAG, "Screenshot-ready rejected (fail closed: missing/malformed ownership)")
            return false
        }
        val captureSequenceId = intent.parseCaptureSequenceId() ?: run {
            Log.w(TAG, "Screenshot-ready rejected (fail closed: missing/malformed capture sequence)")
            return false
        }
        if (!ScanRequests.coordinator.acceptScreenshotReady(ownership, captureSequenceId)) {
            Log.w(TAG, "Screenshot-ready rejected (fail closed: unknown/stale/old-attempt/old-epoch)")
            // Safe only for the exact currently tracked attempt. The coordinator makes
            // this a no-op for an older attempt of a still-live retry.
            ScanRequests.coordinator.suppressAsStale(ownership)
            return false
        }
        return dispatchOwnedScan(intent, ownership)
    }

    private fun dispatchOwnedScan(intent: Intent, ownership: ScanRequestToken): Boolean {
        val paths = intent.getStringArrayListExtra(ScreenCaptureService.EXTRA_SCREENSHOT_PATHS)
        val safePaths = paths?.takeIf { it.isNotEmpty() }
            ?.let { sanitizeScreenshotPaths(it, context.cacheDir) }
            .orEmpty()
        if (safePaths.isEmpty()) {
            Log.e(TAG, "onReceive: no usable screenshot paths (raw=${paths?.size ?: 0})")
            handleError(ownership, ScanResult.Failure(ScanError.CAPTURE_FAILED))
            return true
        }
        Log.d(
            TAG,
            "onReceive: paths size=${safePaths.size} " +
                "requestId=${ownership.requestId} attempt=${ownership.attemptId}"
        )
        processScanSequence(safePaths, ownership)
        return true
    }

    // ── Public API ───────────────────────────────────────────────────────

    fun start() {
        // Phase 2F: a scanner (re)start begins a fresh ownership session; old callbacks
        // from before the restart can no longer publish.
        ScanRequests.coordinator.onScannerLifecycle(started = true)
        val filter = IntentFilter(ScreenCaptureService.ACTION_SCREENSHOT_READY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(screenshotReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                context,
                screenshotReceiver,
                filter,
                ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION,
                null,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        Log.d(TAG, "ScanManager started, receiver registered for ${ScreenCaptureService.ACTION_SCREENSHOT_READY}")
    }

    fun stop() {
        ScanRequests.coordinator.onScannerLifecycle(started = false)
        try { context.unregisterReceiver(screenshotReceiver) } catch (_: Exception) { Log.w(TAG, "screenshotReceiver not registered during stop") }
        ocrProcessor.release()
        scope.cancel()
        Log.d(TAG, "ScanManager stopped")
    }

    // ── Pipeline ─────────────────────────────────────────────────────────

    private fun processScanSequence(paths: List<String>, ownership: ScanRequestToken) {
        Log.d(TAG, "processScanSequence: starting with ${paths.size} frames")
        scope.launch {
            scanMutex.withLock {
                // Phase 2F early ownership check: a request waiting for the mutex can be
                // stale before any expensive OCR/classifier work happens.
                if (!ScanRequests.coordinator.hasPublicationRights(ownership)) {
                    ScanRequests.coordinator.suppressAsStale(ownership)
                    Log.w(
                        TAG,
                        "Stale request suppressed before processing: requestId=${ownership.requestId} " +
                            "attempt=${ownership.attemptId}"
                    )
                    return@withLock
                }
                val shared = PipelineSharedState(ownership, paths)
                val variantStage = ScanVariantStageRunner(shared)
                val publicationStage = ScanPublicationStage(shared)
                ScanRecognitionStages(shared, variantStage, publicationStage).execute()
            }
        }
    }

    /** Mutable per-execution pipeline state shared by the recognition stage classes. */
    private inner class PipelineSharedState(
        val ownership: ScanRequestToken,
        val paths: List<String>
    ) {
        val pipelineStart = System.currentTimeMillis()
        val pipelineTimings = mutableListOf<StageTimingDiagnostic>()
        val results = mutableListOf<ScanFrameCandidate>()
        val frameDiagnostics = mutableListOf<FrameDiagnostic>()
        val frameRoutes = mutableListOf<FrameRouteDiagnostic>()
    }

    /** Recognition stages: decode, OCR, fusion, refiner and the consistency gate. */
    private inner class ScanRecognitionStages(
        private val state: PipelineSharedState,
        private val variantStage: ScanVariantStageRunner,
        private val publicationStage: ScanPublicationStage
    ) {
        suspend fun execute() {
            try {
                val decodedFrames = decodeAndPreprocessFrames()
                runSequentialOcr(decodedFrames)
                val bestEntry = if (state.results.isEmpty()) {
                    handleRoutingSkip()
                    null
                } else {
                    ScanFrameFusion.selectBestFrame(state.results)
                }
                if (bestEntry == null) {
                    if (state.results.isNotEmpty()) Log.w(TAG, "No valid scan results after filtering")
                } else {
                    runStagesFor(bestEntry)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline error", e)
                handleError(state.ownership, ScanResult.Failure(ScanError.UNKNOWN, e))
            }
        }

        private suspend fun runStagesFor(bestEntry: ScanFrameCandidate) {
            val gate = fuseAndGate(bestEntry) ?: return
            val variant = variantStage.runVariantAndIdentityStage(gate, bestEntry) ?: return
            publicationStage.publishAcceptedResult(gate, variant, bestEntry.path)
        }

        private suspend fun decodeAndPreprocessFrames(): List<DecodedFrame> = coroutineScope {
            val decodeStart = System.currentTimeMillis()
            val frameJobs = state.paths.mapIndexed { index, path ->
                async(Dispatchers.Default) {
                    val bitmap = decodeBitmapPool.decodeFile(path) ?: return@async null
                    try {
                        // Source screenshot geometry is captured BEFORE the
                        // recognition downscale: two different displays can both
                        // scale to the same recognition width and must never share
                        // a calibration identity.
                        val sourceWidth = bitmap.width
                        val sourceHeight = bitmap.height
                        val scaled = if (bitmap.width > 900) {
                            Bitmap.createScaledBitmap(bitmap, 900, (bitmap.height * (900f / bitmap.width)).toInt(), true)
                        } else bitmap
                        val cpQuality = estimateCpQuality(scaled)
                        if (scaled !== bitmap) {
                            decodeBitmapPool.release(bitmap)
                        }
                        DecodedFrame(
                            index,
                            path,
                            scaled,
                            cpQuality,
                            scaled === bitmap,
                            sourceWidth,
                            sourceHeight
                        )
                    } catch (e: Exception) {
                        decodeBitmapPool.release(bitmap)
                        null
                    }
                }
            }
            val decodedFrames = frameJobs.awaitAll().filterNotNull()
            val decodeTime = System.currentTimeMillis() - decodeStart
            state.pipelineTimings += StageTimingDiagnostic("decode", decodeTime)
            Log.d(
                TAG,
                "Parallel decode + preprocess: ${decodedFrames.size} frames in ${decodeTime}ms " +
                    "(avg ${if (decodedFrames.isNotEmpty()) decodeTime / decodedFrames.size else 0}ms/frame)"
            )
            decodedFrames
        }

        private suspend fun runSequentialOcr(decodedFrames: List<DecodedFrame>) {
            val ocrStart = System.currentTimeMillis()
            var processedFrameCount = 0
            try {
                for (frame in decodedFrames) {
                    val shouldStop = processRoutedFrame(
                        frame,
                        state.results,
                        state.frameDiagnostics,
                        state.frameRoutes
                    )
                    processedFrameCount++
                    if (shouldStop) {
                        break
                    }
                }
            } finally {
                decodedFrames.drop(processedFrameCount).forEach { frame ->
                    releaseBitmap(frame.bitmap, frame.pooled)
                }
            }
            val ocrTime = System.currentTimeMillis() - ocrStart
            state.pipelineTimings += StageTimingDiagnostic("ocr_fast_total", ocrTime)
            Log.d(
                TAG,
                "Sequential OCR: ${state.results.size} frames in ${ocrTime}ms " +
                    "(avg ${if (state.results.isNotEmpty()) ocrTime / state.results.size else 0}ms/frame)"
            )
        }

        private fun handleRoutingSkip() {
            // An intentional routing skip must not surface as OCR_FAILED.
            handleError(
                state.ownership,
                when (val outcome = aggregateFrameRoutes(state.frameRoutes)) {
                    null -> ScanResult.Failure(ScanError.OCR_FAILED)
                    ScreenRouteOutcome.NOT_POKEMON_SCREEN ->
                        ScanResult.Failure(ScanError.NOT_POKEMON_SCREEN)
                    ScreenRouteOutcome.RETRY_UNSTABLE ->
                        ScanResult.Failure(ScanError.LOW_CONFIDENCE_RESULT)
                    ScreenRouteOutcome.RETRY_UNKNOWN ->
                        ScanResult.Failure(ScanError.LOW_CONFIDENCE_RESULT)
                }
            )
        }

        /** Fuses frames and evaluates the consistency gate; null when a retry was handled. */
        private suspend fun fuseAndGate(bestEntry: ScanFrameCandidate): GateProceed? {
            val detailedDeferred = if (shouldRunDetailedPassForAuthoritative(
                    bestEntry.data,
                    bestEntry.cpQuality,
                    bestEntry.speciesEvidence
                )
            ) {
                scope.async(Dispatchers.Default) {
                    runDetailedPassIfNeeded(bestEntry.path, bestEntry.recognitionContext)
                }
            } else {
                null
            }
            val fused = fuseWithDetailedPass(bestEntry, detailedDeferred?.await())
            val refinedSpecies = refineSpeciesEvidence(fused)
            val consistencyStart = System.currentTimeMillis()
            val consistencyDecision = consistencyGate.evaluate(
                fused.fused,
                refinedSpecies.refined,
                refinedSpecies.evidence
            )
            state.pipelineTimings += StageTimingDiagnostic("consistency_gate", System.currentTimeMillis() - consistencyStart)
            val candidateSpecies = consistencyDecision.pokemon.realName ?: consistencyDecision.pokemon.name
            val phase2AuthorityGate = resolvePhase2AuthorityGate(
                speciesEvidence = refinedSpecies.evidence,
                candidateSpecies = candidateSpecies,
                retryRequested = consistencyDecision.shouldRetry
            )
            val underlyingAuthorityReason = resolvePhase2AuthorityGate(
                speciesEvidence = refinedSpecies.evidence,
                candidateSpecies = candidateSpecies,
                retryRequested = false
            ).reason.code
            if (consistencyDecision.shouldRetry) {
                logAndExportConsistencyRetry(
                    bestEntry, fused, refinedSpecies.refined, consistencyDecision.reason, underlyingAuthorityReason
                )
                handleError(state.ownership, ScanResult.Failure(ScanError.LOW_CONFIDENCE_RESULT))
                return null
            }
            if (consistencyDecision.reason != "accepted") {
                Log.i(TAG, "Consistency gate applied: ${consistencyDecision.reason}")
            }
            return GateProceed(
                finalBase = consistencyDecision.pokemon,
                finalSpeciesEvidence = refinedSpecies.evidence,
                consistencyReason = consistencyDecision.reason,
                fallbackReason = consistencyDecision.reason.takeUnless { it == "accepted" },
                phase2AuthorityGate = phase2AuthorityGate
            )
        }

        private suspend fun fuseWithDetailedPass(
            bestEntry: ScanFrameCandidate,
            detailedFrameResult: OcrFrameResult?
        ): FusedFrames {
            val detailedAwaitStart = System.currentTimeMillis()
            if (detailedFrameResult != null) {
                state.pipelineTimings += StageTimingDiagnostic(
                    "ocr_detailed_total",
                    System.currentTimeMillis() - detailedAwaitStart
                )
            }
            val detailedBestResult = detailedFrameResult?.pokemon ?: bestEntry.data
            val reportFrames = if (detailedFrameResult != null) {
                state.frameDiagnostics + detailedFrameResult.diagnostic
            } else {
                state.frameDiagnostics.toList()
            }
            val detailedCandidate = detailedFrameResult?.let { detailed ->
                ScanFrameCandidate(
                    path = bestEntry.path,
                    data = detailed.pokemon,
                    cpQuality = bestEntry.cpQuality,
                    speciesEvidence = deriveSpeciesEvidence(
                        detailed.diagnostic.fieldCandidates,
                        detailed.pokemon,
                        rarityCalculator
                    ),
                    recognitionContext = bestEntry.recognitionContext
                )
            }
            val anchoredSelection = ScanFrameFusion.resolveAnchoredFrames(
                frames = state.results,
                authoritative = bestEntry,
                detailed = detailedCandidate
            )
            val fused = anchoredSelection?.frame?.data
                ?: ScanFrameFusion.fuse(
                    state.results,
                    bestEntry.data,
                    detailedBestResult,
                    ScanFrameFusion.validCpCandidates(state.results),
                    bestEntry.cpQuality
                )
            var finalSpeciesEvidence = anchoredSelection?.speciesEvidence
                ?: aggregateFastEvidence(state.results.map { it.speciesEvidence })
            detailedFrameResult?.takeIf { anchoredSelection == null }?.let { detailedResult ->
                val detailedEvidence = SpeciesEvidence.fromFieldCandidates(
                    detailedResult.diagnostic.fieldCandidates
                )
                val fastSpecies = finalSpeciesEvidence.selectedCanonicalSpecies
                val detailedSpecies = detailedEvidence.selectedCanonicalSpecies
                val detailedConflict = detailedEvidence.hasHardAuthority &&
                    !fastSpecies.isNullOrBlank() &&
                    !detailedSpecies.isNullOrBlank() &&
                    !fastSpecies.equals(detailedSpecies, ignoreCase = true)
                if (detailedConflict) {
                    finalSpeciesEvidence = conflictingEvidence()
                }
            }
            val resolverStart = System.currentTimeMillis()
            return FusedFrames(fused, reportFrames, finalSpeciesEvidence, resolverStart)
        }

        private fun refineSpeciesEvidence(fused: FusedFrames): RefinedSpecies {
            val refined = speciesRefiner.refine(fused.fused, fused.reportFrames.flatMap { it.fieldCandidates })
            state.pipelineTimings += StageTimingDiagnostic(
                "species_resolver",
                System.currentTimeMillis() - fused.resolverStart
            )
            val evidence = reconcileSpeciesProfileEvidence(
                fused.finalSpeciesEvidence,
                profileStatus(refined, fused.finalSpeciesEvidence.selectedCanonicalSpecies, rarityCalculator)
            )
            return RefinedSpecies(refined, evidence)
        }

        private fun logAndExportConsistencyRetry(
            bestEntry: ScanFrameCandidate,
            fused: FusedFrames,
            refined: PokemonData,
            reason: String,
            underlyingAuthorityReason: String
        ) {
            Log.w(
                TAG,
                "Consistency gate requested retry: $reason " +
                    "(phase2AuthorityReason=retry, phase2UnderlyingAuthorityReason=$underlyingAuthorityReason)"
            )
            exportRetryDiagnostics(
                screenshotPath = bestEntry.path,
                pokemon = refined,
                reason = reason,
                reportContext = ScanReportContext(
                    frames = fused.reportFrames,
                    variantSummary = null,
                    stageTimings = state.pipelineTimings + StageTimingDiagnostic(
                        "total",
                        System.currentTimeMillis() - state.pipelineStart
                    ),
                    frameRoutes = state.frameRoutes.toList(),
                    ownership = ownershipDiagnostic(state.ownership)
                )
            )
        }
    }

    /** Variant classification/visual stages and the identity/confidence decision. */
    private inner class ScanVariantStageRunner(
        private val state: PipelineSharedState
    ) {
        /** CP validation, variant classification, identity build and confidence gating. */
        suspend fun runVariantAndIdentityStage(
            gate: GateProceed,
            bestEntry: ScanFrameCandidate
        ): VariantStage? {
            val bestBitmap = decodeBitmapPool.decodeFile(bestEntry.path)
            try {
                if (bestBitmap == null) {
                    Log.e(TAG, "Best frame decode failed: framePath=${SafeDebugLogValue.localFileReference(bestEntry.path)}")
                }
                val provisionalSizeTag = gate.finalBase.rawOcrText
                    .split("|")
                    .find { it.startsWith("SizeTag:") }
                    ?.substringAfter(":")
                val classification = classifyVariants(gate.finalBase, bestBitmap)
                val variants = detectAndMergeVariants(gate, classification, bestBitmap, provisionalSizeTag)
                val phase2Result = runPhase2Classifier(gate, bestBitmap)
                return evaluateScanDecision(gate, bestEntry, variants, phase2Result, provisionalSizeTag)
            } finally {
                bestBitmap?.let { decodeBitmapPool.release(it) }
            }
        }

        private suspend fun classifyVariants(
            finalBase: PokemonData,
            bestBitmap: Bitmap?
        ): VariantDecisionEngine.ClassificationResult {
            val classifierStart = System.currentTimeMillis()
            val result = coroutineScope {
                val classificationDeferred = async(Dispatchers.Default) {
                    try {
                        if (bestBitmap != null) {
                            variantDecisionEngine.classify(bestBitmap, finalBase)
                        } else {
                            VariantDecisionEngine.ClassificationResult(finalBase, null, null, null, null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Variant classifier failed", e)
                        VariantDecisionEngine.ClassificationResult(finalBase, null, null, null, null)
                    }
                }
                classificationDeferred.await()
            }
            state.pipelineTimings += StageTimingDiagnostic(
                "variant_classifier",
                System.currentTimeMillis() - classifierStart
            )
            result.globalMatch?.let {
                Log.d(
                    TAG,
                    "Variant classifier(${it.scope}): species=${it.species}, sprite=${it.spriteKey}, type=${it.variantType}, shiny=${it.isShiny}, costume=${it.isCostumeLike}, score=${it.score}, confidence=${it.confidence}, top=${it.topSpecies}"
                )
            }
            result.speciesMatch?.let {
                Log.d(
                    TAG,
                    "Variant classifier(${it.scope}): species=${it.species}, sprite=${it.spriteKey}, type=${it.variantType}, shiny=${it.isShiny}, costume=${it.isCostumeLike}, score=${it.score}, confidence=${it.confidence}, top=${it.topSpecies}"
                )
            }
            result.resolvedMatch?.let {
                if (it !== result.speciesMatch) {
                    Log.d(
                        TAG,
                        "Variant classifier rescue(${it.scope}): species=${it.species}, sprite=${it.spriteKey}, type=${it.variantType}, shiny=${it.isShiny}, costume=${it.isCostumeLike}, score=${it.score}, confidence=${it.confidence}"
                    )
                }
            }
            return result
        }

        private suspend fun detectAndMergeVariants(
            gate: GateProceed,
            classification: VariantDecisionEngine.ClassificationResult,
            bestBitmap: Bitmap?,
            provisionalSizeTag: String?
        ): MergedVariants {
            val visualStart = System.currentTimeMillis()
            val visualFeatures = coroutineScope {
                val visualDeferred = async(Dispatchers.Default) {
                    try {
                        if (bestBitmap != null) {
                            visualDetector.detect(bestBitmap, classification.pokemon.name, provisionalSizeTag)
                        } else {
                            com.pokerarity.scanner.data.model.VisualFeatures()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Visual detection failed", e)
                        com.pokerarity.scanner.data.model.VisualFeatures()
                    }
                }
                visualDeferred.await()
            }
            val visualElapsed = System.currentTimeMillis() - visualStart
            state.pipelineTimings += StageTimingDiagnostic("visual_detector", visualElapsed)
            val tracedBase = classification.pokemon
            val ocrLucky = tracedBase.rawOcrText.split("|")
                .find { it.startsWith("LuckyDetected:") }
                ?.substringAfter(":")
                ?.equals("true", ignoreCase = true) == true
            val luckyMergedVisualFeatures = if (ocrLucky && !visualFeatures.isLucky) {
                Log.d(TAG, "Lucky override applied from OCR label")
                visualFeatures.copy(
                    isLucky = true,
                    hasLocationCard = false,
                    confidence = maxOf(visualFeatures.confidence, 0.75f)
                )
            } else {
                visualFeatures
            }
            val mergedVisualFeatures = variantDecisionEngine.mergeVisualFeaturesForLockedSpecies(
                visualFeatures = luckyMergedVisualFeatures,
                lockedSpecies = gate.phase2AuthorityGate.acceptedSpecies,
                fullMatch = classification.fullMatch,
                fallbackMatch = classification.resolvedMatch ?: classification.globalMatch
            )
            return MergedVariants(
                tracedBase = tracedBase,
                mergedVisualFeatures = mergedVisualFeatures,
                resolvedMatch = classification.resolvedMatch,
                fullMatch = classification.fullMatch,
                visualElapsed = visualElapsed
            )
        }

        private suspend fun runPhase2Classifier(
            gate: GateProceed,
            bestBitmap: Bitmap?
        ): Phase2VariantClassifier.Result? {
            val phase2Result = try {
                val phase2Start = System.currentTimeMillis()
                val acceptedSpecies = gate.phase2AuthorityGate.acceptedSpecies
                val result = if (
                    bestBitmap != null &&
                    gate.phase2AuthorityGate.mayRunSpeciesScopedPhase2 &&
                    !acceptedSpecies.isNullOrBlank()
                ) {
                    phase2VariantClassifier.classify(bestBitmap, acceptedSpecies)
                } else {
                    null
                }
                state.pipelineTimings += StageTimingDiagnostic(
                    "phase2_variant_classifier",
                    System.currentTimeMillis() - phase2Start
                )
                result
            } catch (e: Exception) {
                Log.w(TAG, "Phase 2 variant classifier failed", e)
                null
            }
            phase2Result?.let { result ->
                Log.d(
                    TAG,
                    "Phase2 variant: species=${result.species} supported=${result.supportedTargets.joinToString(",")} applied=${result.appliedTargets.joinToString(",")}"
                )
                result.predictions.forEach { prediction ->
                    Log.d(
                        TAG,
                        "Phase2 target=${prediction.target} predicted=${prediction.predictedValue} confidence=${prediction.confidence} margin=${prediction.margin} passed=${prediction.passedThreshold}"
                    )
                }
            }
            return phase2Result
        }

        /** CP validation, identity build and the scan-confidence gate. Null = blocked. */
        private suspend fun evaluateScanDecision(
            gate: GateProceed,
            bestEntry: ScanFrameCandidate,
            variants: MergedVariants,
            phase2Result: Phase2VariantClassifier.Result?,
            provisionalSizeTag: String?
        ): VariantStage? {
            val finalResult = withValidatedCp(variants)
            val scoringVisualFeatures =
                if (gate.phase2AuthorityGate.mayApplyPhase2 && phase2Result != null) {
                    Phase2VariantFeatureMerger.merge(variants.mergedVisualFeatures, phase2Result)
                } else {
                    variants.mergedVisualFeatures
                }
            // Phase 2E explicit-negative shiny contract: true only when the trained
            // classifier demoted an existing positive under the merge's own rules.
            val phase2ShinyDemoted = gate.phase2AuthorityGate.mayApplyPhase2 &&
                Phase2VariantFeatureMerger.shinyDemotionApplied(variants.mergedVisualFeatures, phase2Result)
            val variantSummary = VariantVisualSummary.from(scoringVisualFeatures, finalResult.variantDecisionTrace)
            val scanDecision = scanConfidenceGate.evaluate(
                ScanConfidenceInput(
                    pokemon = finalResult,
                    frames = state.frameDiagnostics.toList(),
                    consistencyReason = gate.consistencyReason,
                    consistencyRequestedRetry = false,
                    cpCropQuality = bestEntry.cpQuality,
                    visualSummary = variantSummary,
                    speciesEvidence = gate.finalSpeciesEvidence
                )
            )
            val identity = buildIdentityStage(
                IdentityInputs(gate, finalResult, variants, phase2Result, scoringVisualFeatures, provisionalSizeTag)
            )
            val finalWithIdentity = finalResult.copy(
                scanDecision = scanDecision,
                recognitionIdentity = identity.recognitionIdentity
            )
            if (!scanDecision.mayShowOverlay || !scanDecision.maySaveScan) {
                handleConfidenceBlocked(scanDecision, finalWithIdentity, variantSummary, bestEntry)
                return null
            }
            return VariantStage(
                finalResult = finalWithIdentity,
                scoringVisualFeatures = scoringVisualFeatures,
                compatFeatures = identity.compatFeatures,
                variantSummary = variantSummary,
                phase2Result = phase2Result,
                visualElapsed = variants.visualElapsed
            )
        }

        private fun withValidatedCp(variants: MergedVariants): PokemonData {
            var finalResult = variants.tracedBase
            val fixedCP = rarityCalculator.validateAndFixCP(
                variants.tracedBase,
                ScanFrameFusion.validCpCandidates(state.results),
                variants.mergedVisualFeatures
            )
            if (fixedCP != null && fixedCP > 0) {
                if (variants.tracedBase.cp == null || variants.tracedBase.cp == 0) {
                    Log.i(TAG, "CP was missing, using mathematical estimate: $fixedCP")
                    finalResult = variants.tracedBase.copy(cp = fixedCP)
                } else if (fixedCP != variants.tracedBase.cp) {
                    Log.i(TAG, "CP OCR was likely wrong (${variants.tracedBase.cp}), fixing to: $fixedCP")
                    finalResult = variants.tracedBase.copy(cp = fixedCP)
                }
            }
            return finalResult
        }

        private fun buildIdentityStage(inputs: IdentityInputs): IdentityStage {
            // Phase 2E: explicit recognition identity built once from the locked
            // authority + final variant evidence; weak classifiers never rewrite it.
            val recognitionIdentity = RecognitionIdentityFactory.build(
                RecognitionIdentityFactory.Input(
                    speciesEvidence = inputs.gate.finalSpeciesEvidence,
                    scanAccepted = true,
                    lockedSpecies = inputs.gate.phase2AuthorityGate.acceptedSpecies,
                    classifierSpecies = inputs.finalResult.variantDecisionTrace?.classifierSpecies,
                    fullMatchWinnerSpecies = inputs.variants.fullMatch?.winnerSpecies,
                    formCandidates = inputs.finalResult.speciesResolverTrace?.formCandidates.orEmpty(),
                    mergedFeatures = inputs.scoringVisualFeatures,
                    phase2ShinyDemoted = inputs.gate.phase2AuthorityGate.mayApplyPhase2 &&
                        Phase2VariantFeatureMerger.shinyDemotionApplied(
                            inputs.variants.mergedVisualFeatures,
                            inputs.phase2Result
                        ),
                    sizeTag = inputs.provisionalSizeTag
                )
            )
            // Compatibility booleans for legacy consumers come FROM the explicit
            // contract (TRUE -> true; FALSE/UNKNOWN -> false) and are never fed back.
            val compatFeatures = RecognitionIdentityCompat.toVisualFeatures(
                recognitionIdentity,
                confidence = inputs.scoringVisualFeatures.confidence
            )
            return IdentityStage(recognitionIdentity, compatFeatures)
        }

        private fun handleConfidenceBlocked(
            scanDecision: com.pokerarity.scanner.util.ocr.ScanDecision,
            finalResult: PokemonData,
            variantSummary: VariantVisualSummary?,
            bestEntry: ScanFrameCandidate
        ) {
            Log.w(
                TAG,
                "Scan confidence gate blocked result: decision=${scanDecision.decision} " +
                    "score=${scanDecision.confidence} reasons=${scanDecision.developerReasons.joinToString(",")}"
            )
            exportRetryDiagnostics(
                screenshotPath = bestEntry.path,
                pokemon = finalResult,
                reason = "${scanDecision.decision}: ${scanDecision.userSafeReason}",
                scanDecision = scanDecision,
                reportContext = ScanReportContext(
                    frames = state.frameDiagnostics.toList(),
                    variantSummary = variantSummary,
                    stageTimings = state.pipelineTimings + StageTimingDiagnostic(
                        "total",
                        System.currentTimeMillis() - state.pipelineStart
                    ),
                    frameRoutes = state.frameRoutes.toList(),
                    ownership = ownershipDiagnostic(state.ownership)
                )
            )
            val error = if (scanDecision.decision == ScanDecisionType.REJECT_NOT_POKEMON_SCREEN) {
                ScanError.NOT_POKEMON_SCREEN
            } else {
                ScanError.LOW_CONFIDENCE_RESULT
            }
            handleError(state.ownership, ScanResult.Failure(error))
        }
    }

    /** Rarity scoring, terminal publication claim and the accepted side effects. */
    private inner class ScanPublicationStage(
        private val state: PipelineSharedState
    ) {
        suspend fun publishAcceptedResult(
            gate: GateProceed,
            stage: VariantStage,
            bestPath: String
        ) {
            val rarityScore = scoreRarity(stage)
            val pipelineElapsed = System.currentTimeMillis() - state.pipelineStart
            state.pipelineTimings += StageTimingDiagnostic("total", pipelineElapsed)
            val decisionSummary = PipelineDecisionSummary.build(
                pokemon = stage.finalResult,
                features = stage.scoringVisualFeatures,
                rarityScore = rarityScore,
                phase2Result = stage.phase2Result,
                screenshotPath = bestPath,
                pipelineMs = pipelineElapsed
            )
            Log.d(TAG, decisionSummary.toLogLine())

            // Phase 2F THE stale-result publication guard and linearization point:
            // the first atomically accepted terminal claim owns publication. A
            // request that lost ownership (newer request accepted, epoch moved,
            // scanner stopped) must not show overlay, save, telemeter, or emit a
            // late error over a newer request. It runs before ANY publication prep
            // so no side-effecting resource is touched for a stale result.
            val ownershipDiagnostic = ownershipDiagnostic(state.ownership)
            if (!ScanRequests.coordinator.claimTerminal(
                    state.ownership,
                    TerminalOutcome.SUCCESS_PUBLISHED
                )
            ) {
                ScanRequests.coordinator.suppressAsStale(state.ownership)
                Log.w(
                    TAG,
                    "Stale result suppressed before publication: requestId=${state.ownership.requestId} " +
                        "attempt=${state.ownership.attemptId}"
                )
                return
            }

            val displayDate = stage.finalResult.caughtDate
                ?.let { formatDate(it, DateParseUtils.MMM_DD_YYYY_FORMATTER) } ?: "Unknown"
            val telemetryUploadId = telemetryUploadIdProvider()
            val diagnosticId = telemetryUploadId ?: "local-${System.currentTimeMillis()}"
            val overlayIntent = buildResultOverlayIntent(stage, rarityScore, displayDate, telemetryUploadId)
            // Show result first so UI is not blocked by disk writes
            scope.launch(Dispatchers.Main) {
                publicationSink.showResultOverlay(overlayIntent)
            }
            dispatchPersistence(
                PersistenceInputs(
                    gate = gate,
                    stage = stage,
                    rarityScore = rarityScore,
                    bestPath = bestPath,
                    pipelineElapsed = pipelineElapsed,
                    diagnosticId = diagnosticId,
                    telemetryUploadId = telemetryUploadId
                )
            )
            Log.d(TAG, "processScanSequence: overlay dispatched in ${pipelineElapsed}ms")
            cleanOldScreenshots()
        }

        private suspend fun scoreRarity(stage: VariantStage): com.pokerarity.scanner.data.model.RarityScore {
            val baseRarity = rarityInputs.baseRarity(stage.finalResult.realName ?: stage.finalResult.name ?: "Unknown")
            val eventWeight = rarityInputs.eventBonus(stage.finalResult, stage.scoringVisualFeatures)
            val liveEventContext = rarityInputs.liveEventContext(stage.finalResult, stage.scoringVisualFeatures)
            val solverStart = System.currentTimeMillis()
            val rarityScore = rarityCalculator.calculate(
                stage.finalResult,
                stage.scoringVisualFeatures,
                baseRarity,
                eventWeight,
                liveEventContext
            )
            state.pipelineTimings += StageTimingDiagnostic(
                "rarity_scoring",
                System.currentTimeMillis() - solverStart
            )
            return rarityScore
        }

        private fun buildResultOverlayIntent(
            stage: VariantStage,
            rarityScore: com.pokerarity.scanner.data.model.RarityScore,
            displayDate: String,
            telemetryUploadId: String?
        ): Intent {
            val finalResult = stage.finalResult
            return Intent(context, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW_RESULT
                putExtra(ResultActivity.EXTRA_POKEMON_NAME, finalResult.name ?: "Unknown")
                putExtra(ResultActivity.EXTRA_CP, finalResult.cp ?: 0)
                putExtra(ResultActivity.EXTRA_HP, finalResult.hp ?: 0)
                putExtra(ResultActivity.EXTRA_SCORE, rarityScore.totalScore)
                putExtra(ResultActivity.EXTRA_TIER, rarityScore.tier.name)
                putExtra(ResultActivity.EXTRA_IS_SHINY, stage.compatFeatures.isShiny)
                putExtra(ResultActivity.EXTRA_IS_SHADOW, stage.compatFeatures.isShadow)
                putExtra(ResultActivity.EXTRA_IS_LUCKY, stage.compatFeatures.isLucky)
                putExtra(ResultActivity.EXTRA_HAS_COSTUME, stage.compatFeatures.hasCostume)
                putExtra(ResultActivity.EXTRA_HAS_SPECIAL_FORM, stage.compatFeatures.hasSpecialForm)
                putStringArrayListExtra(ResultActivity.EXTRA_EXPLANATIONS, ArrayList(rarityScore.explanation))
                putStringArrayListExtra(
                    ResultActivity.EXTRA_BREAKDOWN_KEYS,
                    ArrayList(rarityScore.breakdown.keys.toList())
                )
                putIntegerArrayListExtra(
                    ResultActivity.EXTRA_BREAKDOWN_VALUES,
                    ArrayList(rarityScore.breakdown.values.toList())
                )
                putExtra(ResultActivity.EXTRA_DATE, displayDate)
                putExtra(ResultActivity.EXTRA_TELEMETRY_UPLOAD_ID, telemetryUploadId)
                rarityScore.decisionSupport?.let { support ->
                    putExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_CODE, support.eventConfidenceCode)
                    putExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_LABEL, support.eventConfidenceLabel)
                    putExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_DETAIL, support.eventConfidenceDetail)
                    putExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_SCORE, support.scanConfidenceScore)
                    putExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_LABEL, support.scanConfidenceLabel)
                    putExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_DETAIL, support.scanConfidenceDetail)
                    putExtra(ResultActivity.EXTRA_MISMATCH_GUARD_TITLE, support.mismatchGuardTitle)
                    putExtra(ResultActivity.EXTRA_MISMATCH_GUARD_DETAIL, support.mismatchGuardDetail)
                    putExtra(ResultActivity.EXTRA_RECOGNITION_SUMMARY, support.recognitionSummary ?: support.whyNotExact)
                }
            }
        }

        private suspend fun dispatchPersistence(inputs: PersistenceInputs) {
            val gate = inputs.gate
            val stage = inputs.stage
            val rarityScore = inputs.rarityScore
            val bestPath = inputs.bestPath
            val pipelineElapsed = inputs.pipelineElapsed
            val diagnosticId = inputs.diagnosticId
            val telemetryUploadId = inputs.telemetryUploadId
            val finalResult = attachRecognitionDiagnostics(
                pokemon = stage.finalResult,
                rarityScore = rarityScore,
                screenshotPath = bestPath,
                diagnosticId = diagnosticId,
                reportContext = ScanReportContext(
                    frames = state.frameDiagnostics.toList(),
                    variantSummary = stage.variantSummary,
                    stageTimings = state.pipelineTimings,
                    frameRoutes = state.frameRoutes.toList(),
                    fallbackReason = gate.fallbackReason,
                    ownership = ownershipDiagnostic(state.ownership)
                )
            )
            // Save in background after result is already visible
            scope.launch {
                publicationSink.saveScan(finalResult, stage.compatFeatures, rarityScore)
            }
            publicationSink.enqueueTelemetry(
                TelemetryPayload(
                    uploadId = telemetryUploadId,
                    pokemon = finalResult,
                    features = stage.scoringVisualFeatures,
                    rarityScore = rarityScore,
                    pipelineMs = pipelineElapsed,
                    phase2Result = stage.phase2Result
                )
            )
        }
    }

    /** Bundled persistence/telemetry inputs for the publication stage. */
    private data class PersistenceInputs(
        val gate: GateProceed,
        val stage: VariantStage,
        val rarityScore: com.pokerarity.scanner.data.model.RarityScore,
        val bestPath: String,
        val pipelineElapsed: Long,
        val diagnosticId: String,
        val telemetryUploadId: String?
    )

    /** Inputs for the Phase 2E identity stage build. */
    private data class IdentityInputs(
        val gate: GateProceed,
        val finalResult: PokemonData,
        val variants: MergedVariants,
        val phase2Result: Phase2VariantClassifier.Result?,
        val scoringVisualFeatures: com.pokerarity.scanner.data.model.VisualFeatures,
        val provisionalSizeTag: String?
    )

    /** Species-refiner outcome: refined data plus reconciled species evidence. */
    private data class RefinedSpecies(
        val refined: PokemonData,
        val evidence: SpeciesEvidence
    )

    private data class FusedFrames(
        val fused: PokemonData,
        val reportFrames: List<FrameDiagnostic>,
        val finalSpeciesEvidence: SpeciesEvidence,
        val resolverStart: Long
    )

    private data class GateProceed(
        val finalBase: PokemonData,
        val finalSpeciesEvidence: SpeciesEvidence,
        val consistencyReason: String,
        val fallbackReason: String?,
        val phase2AuthorityGate: Phase2AuthorityGate
    )

    private data class MergedVariants(
        val tracedBase: PokemonData,
        val mergedVisualFeatures: com.pokerarity.scanner.data.model.VisualFeatures,
        val resolvedMatch: VariantPrototypeClassifier.MatchResult?,
        val fullMatch: FullVariantMatch?,
        val visualElapsed: Long
    )

    /** Identity contract + compatibility view produced for the accepted result. */
    private data class IdentityStage(
        val recognitionIdentity: com.pokerarity.scanner.data.model.RecognitionIdentity,
        val compatFeatures: com.pokerarity.scanner.data.model.VisualFeatures
    )

    private data class VariantStage(
        val finalResult: PokemonData,
        val scoringVisualFeatures: com.pokerarity.scanner.data.model.VisualFeatures,
        val compatFeatures: com.pokerarity.scanner.data.model.VisualFeatures,
        val variantSummary: VariantVisualSummary?,
        val phase2Result: Phase2VariantClassifier.Result?,
        val visualElapsed: Long
    )

    // ── Error handling ───────────────────────────────────────────────────

    /**
     * Phase 2F per-request error handling. Retry attempts belong to the SAME logical
     * request (same requestId, attemptId+1 via the coordinator) and are refused when a
     * newer user request exists — a stale request can neither schedule a retry nor
     * publish a late error/toast over a newer request.
     */
    private fun handleError(ownership: ScanRequestToken?, failure: ScanResult.Failure) {
        val coordinator = ScanRequests.coordinator
        if (ownership == null || !coordinator.hasPublicationRights(ownership)) {
            ownership?.let {
                coordinator.suppressAsStale(it)
                Log.w(TAG, "Stale failure suppressed: requestId=${it.requestId} error=${failure.error}")
            }
            return
        }
        val retryToken = if (failure.error.isRetryable) coordinator.acceptRetry(ownership) else null
        if (retryToken != null) {
            Log.w(
                TAG,
                "Retryable error (${failure.error}), " +
                    "attempt ${retryToken.attemptId} of request ${retryToken.requestId}"
            )
            OverlayStateStore.dispatch(OverlayIntent.ShowError(failure.error.userMessage))
            scope.launch(Dispatchers.Main) {
                Toast.makeText(context, "Retrying scan…", Toast.LENGTH_SHORT).show()
            }
            // Re-trigger capture under the SAME logical request ownership.
            context.sendBroadcast(Intent(OverlayService.ACTION_CAPTURE_REQUESTED).apply {
                setPackage(context.packageName)
                putOwnershipExtras(retryToken)
            }, ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION)
        } else {
            if (coordinator.claimTerminal(ownership, TerminalOutcome.FINAL_FAILURE)) {
                OverlayStateStore.dispatch(OverlayIntent.ShowError(failure.error.userMessage))
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, failure.error.userMessage, Toast.LENGTH_LONG).show()
                }
            } else {
                coordinator.suppressAsStale(ownership)
            }
        }
    }

    /** Bounded ownership diagnostic for reports; null fields when no snapshot exists. */
    private fun ownershipDiagnostic(
        ownership: ScanRequestToken?
    ): com.pokerarity.scanner.util.ocr.RequestOwnershipDiagnostic? {
        ownership ?: return null
        val snapshot = ScanRequests.coordinator.snapshot(ownership)
        return com.pokerarity.scanner.util.ocr.RequestOwnershipDiagnostic(
            requestId = ownership.requestId,
            attemptId = ownership.attemptId,
            projectionEpoch = ownership.projectionEpoch,
            captureSequenceId = snapshot?.captureSequenceId,
            origin = ownership.origin.name,
            terminalOutcome = snapshot?.terminalOutcome?.name,
            coalescedRequests = snapshot?.coalescedRequests ?: 0
        )
    }

    // ── Utilities ────────────────────────────────────────────────────────

    private fun attachRecognitionDiagnostics(
        pokemon: PokemonData,
        rarityScore: com.pokerarity.scanner.data.model.RarityScore,
        screenshotPath: String?,
        diagnosticId: String,
        reportContext: ScanReportContext
    ): PokemonData {
        val confidenceReasons = ScanOcrConfidenceReasonFactory.create(pokemon, rarityScore)
        val bestScreenFrame = reportContext.frames.maxByOrNull { it.screenConfidence ?: 0f }
        val scanReport = ScanDiagnosticReport(
            diagnosticId = diagnosticId,
            screenState = bestScreenFrame?.screenState ?: "Unknown",
            screenConfidence = bestScreenFrame?.screenConfidence,
            stageTimings = reportContext.stageTimings,
            frames = reportContext.frames,
            finalPokemon = PokemonSummary.from(pokemon),
            rarityBreakdown = rarityScore.breakdown,
            confidenceReasons = ConfidenceReasonDiagnostic.from(confidenceReasons),
            fallbackReason = reportContext.fallbackReason,
            resolverTrace = pokemon.speciesResolverTrace,
            variantSummary = reportContext.variantSummary,
            scanDecision = pokemon.scanDecision,
            frameRoutes = reportContext.frameRoutes,
            recognitionSnapshotRevision = RecognitionSnapshotHolder.recognitionRevision(context),
            recognitionIdentity = pokemon.recognitionIdentity,
            requestOwnership = reportContext.ownership)
        val shouldDump = pokemon.cp == null || pokemon.caughtDate == null ||
            (pokemon.maxHp == null && pokemon.hp == null) ||
            (rarityScore.decisionSupport?.mismatchGuardTitle != null)
        val diagnosticBundle = if (shouldDump) {
            OcrDiagnosticsExporter.export(
                context = context,
                screenshotPath = screenshotPath,
                diagnosticId = diagnosticId,
                pokemon = pokemon,
                solve = null,
                whyNotExact = rarityScore.recognitionSummary ?: rarityScore.decisionSupport?.recognitionSummary,
                scanReport = scanReport,
                confidenceReasons = confidenceReasons
            )
        } else {
            null
        }
        return ScanRawOcrDiagnostics.attach(pokemon, rarityScore, diagnosticBundle)
    }

    private fun exportRetryDiagnostics(
        screenshotPath: String?,
        pokemon: PokemonData,
        reason: String,
        scanDecision: ScanDecision? = pokemon.scanDecision,
        reportContext: ScanReportContext
    ) {
        val diagnosticId = "local-retry-${System.currentTimeMillis()}"
        val bestScreenFrame = reportContext.frames.maxByOrNull { it.screenConfidence ?: 0f }
        val scanReport = ScanDiagnosticReport(
            diagnosticId = diagnosticId,
            screenState = bestScreenFrame?.screenState ?: "Unknown",
            screenConfidence = bestScreenFrame?.screenConfidence,
            stageTimings = reportContext.stageTimings,
            frames = reportContext.frames,
            finalPokemon = PokemonSummary.from(pokemon),
            retryReason = reason,
            resolverTrace = pokemon.speciesResolverTrace,
            variantSummary = reportContext.variantSummary,
            scanDecision = scanDecision,
            frameRoutes = reportContext.frameRoutes,
            recognitionSnapshotRevision = RecognitionSnapshotHolder.recognitionRevision(context),
            recognitionIdentity = pokemon.recognitionIdentity,
            requestOwnership = reportContext.ownership
        )
        OcrDiagnosticsExporter.export(
            context = context,
            screenshotPath = screenshotPath,
            diagnosticId = diagnosticId,
            pokemon = pokemon,
            solve = null,
            whyNotExact = reason,
            scanReport = scanReport
        )
    }

    private fun cleanOldScreenshots() {
        try {
            val cacheDir = context.cacheDir
            val screenshots = cacheDir.listFiles { f -> f.name.startsWith("scan_") && f.name.endsWith(".png") }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            if (screenshots.size > 20) {
                screenshots.drop(20).forEach { it.delete() }
            }
        } catch (_: Exception) {
            Log.e(TAG, "cleanOldScreenshots failed")
        }
    }

    private fun releaseBitmap(bitmap: Bitmap, pooled: Boolean) {
        if (pooled) {
            decodeBitmapPool.release(bitmap)
        } else if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    /**
     * Detailed pass over the SAME source screenshot and the same 900-wide transform
     * policy: it reuses the fast frame's already-derived recognition context (geometry +
     * compatible calibration hint) instead of re-classifying, so fast and detailed
     * structured extraction can never diverge over dropped context.
     */
    internal suspend fun runDetailedPassIfNeeded(path: String, context: RecognitionContext?): OcrFrameResult? {
        return runCatching {
            val bitmap = BitmapFactory.decodeFile(path) ?: return@runCatching null
            val scaled = if (bitmap.width > 900) {
                Bitmap.createScaledBitmap(bitmap, 900, (bitmap.height * (900f / bitmap.width)).toInt(), true)
            } else {
                bitmap
            }
            try {
                frameOcr.recognize(
                    FrameOcrRequest(
                        bitmap = scaled,
                        includeSecondaryFields = true,
                        frameIndex = -1,
                        frameRole = "detailed_best",
                        estimatedCpCropQuality = null,
                        calibration = context?.calibrationHint,
                        geometry = context?.geometry
                    )
                )
            } finally {
                if (scaled != bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }.getOrElse {
            Log.e(TAG, "Detailed OCR pass failed", it)
            null
        }
    }

    private fun aggregateFastEvidence(evidence: List<SpeciesEvidence>): SpeciesEvidence {
        val selected = evidence.mapNotNull { it.selectedCanonicalSpecies }.distinctBy { it.lowercase() }
        val precondition = when {
            evidence.isEmpty() -> SpeciesEvidence.failClosed()
            selected.size > 1 || hasConflictingAuthority(evidence) -> conflictingEvidence()
            else -> null
        }
        if (precondition != null) return precondition
        val authority = resolveAggregateAuthority(evidence)
        val reason = authorityReason(authority)
        return SpeciesEvidence(
            selectedCanonicalSpecies = selected.singleOrNull(),
            authority = authority,
            profileStatus = SpeciesProfileStatus.INDETERMINATE,
            reasonCodes = listOf(reason, SpeciesEvidenceReason.PROFILE_INDETERMINATE),
            observationsAgree = selected.size == 1,
            authorityConflict = false,
            topCandidateScore = evidence.mapNotNull { it.topCandidateScore }.maxOrNull(),
            runnerUpScore = evidence.mapNotNull { it.runnerUpScore }.maxOrNull(),
            candidatesClose = evidence.any { it.candidatesClose }
        )
    }

    private fun hasConflictingAuthority(evidence: List<SpeciesEvidence>): Boolean =
        evidence.any { it.authorityConflict || it.authority == SpeciesAuthority.CONFLICT }

    private fun resolveAggregateAuthority(evidence: List<SpeciesEvidence>): SpeciesAuthority {
        val blocking = when {
            evidence.any { it.authority == SpeciesAuthority.UNCERTAIN } -> SpeciesAuthority.UNCERTAIN
            evidence.any { it.authority == SpeciesAuthority.NO_MATCH } -> SpeciesAuthority.NO_MATCH
            else -> null
        }
        return blocking ?: when {
            evidence.any { it.authority == SpeciesAuthority.INDEPENDENT_PROFILE } ->
                SpeciesAuthority.INDEPENDENT_PROFILE
            evidence.any { it.authority == SpeciesAuthority.EXACT_CANONICAL } -> SpeciesAuthority.EXACT_CANONICAL
            evidence.any { it.authority == SpeciesAuthority.REVIEWED_ALIAS } -> SpeciesAuthority.REVIEWED_ALIAS
            else -> SpeciesAuthority.SAFE_FUZZY
        }
    }

    private fun authorityReason(authority: SpeciesAuthority): String = when (authority) {
        SpeciesAuthority.INDEPENDENT_PROFILE -> SpeciesEvidenceReason.INDEPENDENT_PROFILE
        SpeciesAuthority.EXACT_CANONICAL -> SpeciesEvidenceReason.EXACT
        SpeciesAuthority.REVIEWED_ALIAS -> SpeciesEvidenceReason.REVIEWED_ALIAS
        SpeciesAuthority.SAFE_FUZZY -> SpeciesEvidenceReason.SAFE_FUZZY
        SpeciesAuthority.UNCERTAIN -> SpeciesEvidenceReason.UNCERTAIN
        SpeciesAuthority.NO_MATCH -> SpeciesEvidenceReason.NO_MATCH
        SpeciesAuthority.CONFLICT -> SpeciesEvidenceReason.AUTHORITY_CONFLICT
    }

    private fun conflictingEvidence(): SpeciesEvidence = SpeciesEvidence(
        selectedCanonicalSpecies = null,
        authority = SpeciesAuthority.CONFLICT,
        profileStatus = SpeciesProfileStatus.INDETERMINATE,
        reasonCodes = listOf(
            SpeciesEvidenceReason.AUTHORITY_CONFLICT,
            SpeciesEvidenceReason.PROFILE_INDETERMINATE
        ),
        observationsAgree = false,
        authorityConflict = true
    )

    private fun estimateCpQuality(bitmap: Bitmap): Double {
        val mask = com.pokerarity.scanner.util.ocr.ImagePreprocessor.processWhiteMask(bitmap)
        val rect = com.pokerarity.scanner.util.ocr.ScreenRegions.getRectForRegion(mask, com.pokerarity.scanner.util.ocr.ScreenRegions.REGION_CP)
        val safeLeft = rect.left.coerceIn(0, mask.width - 1)
        val safeTop = rect.top.coerceIn(0, mask.height - 1)
        val safeWidth = rect.width().coerceAtMost(mask.width - safeLeft)
        val safeHeight = rect.height().coerceAtMost(mask.height - safeTop)
        if (safeWidth <= 0 || safeHeight <= 0) {
            if (!mask.isRecycled) mask.recycle()
            return 0.0
        }
        val cropped = Bitmap.createBitmap(mask, safeLeft, safeTop, safeWidth, safeHeight)
        if (cropped != mask && !mask.isRecycled) mask.recycle()

        val w = cropped.width
        val h = cropped.height
        val pixels = IntArray(w * h)
        cropped.getPixels(pixels, 0, w, 0, 0, w, h)
        if (!cropped.isRecycled) cropped.recycle()

        var blackCount = 0
        var rowsWithBlack = 0
        for (y in 0 until h) {
            var rowHasBlack = false
            val rowStart = y * w
            for (x in 0 until w) {
                val p = pixels[rowStart + x]
                if ((p and 0x00FFFFFF) == 0x000000) {
                    blackCount++
                    rowHasBlack = true
                }
            }
            if (rowHasBlack) rowsWithBlack++
        }

        val total = w * h
        if (total <= 0) return 0.0
        val blackRatio = blackCount.toDouble() / total.toDouble()
        val rowCoverage = rowsWithBlack.toDouble() / h.toDouble()

        val ratioScore = when {
            blackRatio < 0.005 -> 0.0
            blackRatio < 0.015 -> 0.5
            blackRatio <= 0.20 -> 1.0
            blackRatio <= 0.30 -> 0.5
            else -> 0.0
        }
        val rowScore = when {
            rowCoverage < 0.15 -> 0.0
            rowCoverage < 0.35 -> 0.5
            rowCoverage <= 0.85 -> 1.0
            else -> 0.5
        }

        return (ratioScore * 0.6) + (rowScore * 0.4)
    }

}

internal enum class Phase2AuthorityReason(val code: String) {
    INDEPENDENT_PROFILE("independent_family_profile"),
    RETRY("retry"),
    CONFLICT("conflict"),
    MISSING_AUTHORITY("missing_authority"),
    UNCERTAIN("uncertain"),
    NO_MATCH("no_match"),
    SPECIES_MISMATCH("species_mismatch"),
    EXACT_CANONICAL("exact_canonical"),
    REVIEWED_ALIAS("reviewed_alias"),
    SAFE_FUZZY("safe_fuzzy")
}

internal data class Phase2AuthorityGate(
    val acceptedSpecies: String?,
    val mayRunSpeciesScopedPhase2: Boolean,
    val mayApplyPhase2: Boolean,
    val reason: Phase2AuthorityReason
)

private fun guardedAnchoredObservationEvidence(
    textual: SpeciesEvidence,
    observation: RecognitionObservation
): SpeciesEvidence? {
    val guard = when {
        !observation.detailScreen ->
            SpeciesProfileStatus.INDETERMINATE to "detail_screen_unconfirmed"
        observation.numericConflict ->
            SpeciesProfileStatus.CONTRADICTORY to "numeric_observations_conflict"
        else -> return null
    }
    val guarded = textual.withProfileStatus(guard.first)
    return guarded.copy(
        observationsAgree = false,
        reasonCodes = (guarded.reasonCodes + guard.second).distinct()
    )
}

internal fun reconcileSpeciesProfileEvidence(
    evidence: SpeciesEvidence,
    genericStatus: SpeciesProfileStatus
): SpeciesEvidence {
    val reconciled = when (evidence.profileStatus) {
        SpeciesProfileStatus.IMPOSSIBLE -> SpeciesProfileStatus.IMPOSSIBLE
        SpeciesProfileStatus.CONTRADICTORY -> SpeciesProfileStatus.CONTRADICTORY
        SpeciesProfileStatus.MISSING -> when (genericStatus) {
            SpeciesProfileStatus.IMPOSSIBLE,
            SpeciesProfileStatus.CONTRADICTORY -> genericStatus
            else -> SpeciesProfileStatus.MISSING
        }
        SpeciesProfileStatus.INDETERMINATE -> when (genericStatus) {
            SpeciesProfileStatus.IMPOSSIBLE,
            SpeciesProfileStatus.CONTRADICTORY -> genericStatus
            else -> SpeciesProfileStatus.INDETERMINATE
        }
        SpeciesProfileStatus.COMPATIBLE -> when (genericStatus) {
            SpeciesProfileStatus.IMPOSSIBLE,
            SpeciesProfileStatus.CONTRADICTORY -> genericStatus
            else -> SpeciesProfileStatus.COMPATIBLE
        }
    }
    return evidence.withProfileStatus(reconciled)
}

private fun blockedPhase2Gate(reason: Phase2AuthorityReason): Phase2AuthorityGate =
    Phase2AuthorityGate(
        acceptedSpecies = null,
        mayRunSpeciesScopedPhase2 = false,
        mayApplyPhase2 = false,
        reason = reason
    )

private fun acceptedPhase2Gate(species: String, reason: Phase2AuthorityReason): Phase2AuthorityGate =
    Phase2AuthorityGate(
        acceptedSpecies = species,
        mayRunSpeciesScopedPhase2 = true,
        mayApplyPhase2 = true,
        reason = reason
    )

private fun checkBlockingPhase2Reason(
    speciesEvidence: SpeciesEvidence,
    candidateSpecies: String?,
    retryRequested: Boolean
): Phase2AuthorityReason? {
    val selectedCanonical = speciesEvidence.selectedCanonicalSpecies
    return when {
        retryRequested -> Phase2AuthorityReason.RETRY
        speciesEvidence.authorityConflict || speciesEvidence.authority == SpeciesAuthority.CONFLICT ->
            Phase2AuthorityReason.CONFLICT
        speciesEvidence.authority == SpeciesAuthority.UNCERTAIN ->
            Phase2AuthorityReason.UNCERTAIN
        speciesEvidence.authority == SpeciesAuthority.NO_MATCH ->
            Phase2AuthorityReason.NO_MATCH
        selectedCanonical.isNullOrBlank() || selectedCanonical.equals("Unknown", ignoreCase = true) ->
            Phase2AuthorityReason.MISSING_AUTHORITY
        candidateSpecies.isNullOrBlank() || candidateSpecies.equals("Unknown", ignoreCase = true) ->
            Phase2AuthorityReason.MISSING_AUTHORITY
        !speciesEvidence.observationsAgree || speciesEvidence.candidatesClose ->
            Phase2AuthorityReason.UNCERTAIN
        !selectedCanonical.equals(candidateSpecies, ignoreCase = true) ->
            Phase2AuthorityReason.SPECIES_MISMATCH
        else -> null
    }
}

private fun resolveAcceptedPhase2Reason(authority: SpeciesAuthority): Phase2AuthorityReason? =
    when (authority) {
        SpeciesAuthority.INDEPENDENT_PROFILE -> Phase2AuthorityReason.INDEPENDENT_PROFILE
        SpeciesAuthority.EXACT_CANONICAL -> Phase2AuthorityReason.EXACT_CANONICAL
        SpeciesAuthority.REVIEWED_ALIAS -> Phase2AuthorityReason.REVIEWED_ALIAS
        SpeciesAuthority.SAFE_FUZZY -> Phase2AuthorityReason.SAFE_FUZZY
        else -> null
    }

internal fun resolvePhase2AuthorityGate(
    speciesEvidence: SpeciesEvidence,
    candidateSpecies: String?,
    retryRequested: Boolean
): Phase2AuthorityGate {
    val blockingReason = checkBlockingPhase2Reason(speciesEvidence, candidateSpecies, retryRequested)
    if (blockingReason != null) {
        return blockedPhase2Gate(blockingReason)
    }

    val selectedCanonical = speciesEvidence.selectedCanonicalSpecies.orEmpty()
    val acceptedReason = resolveAcceptedPhase2Reason(speciesEvidence.authority)
    return if (acceptedReason != null) {
        acceptedPhase2Gate(selectedCanonical, acceptedReason)
    } else {
        blockedPhase2Gate(Phase2AuthorityReason.MISSING_AUTHORITY)
    }
}

    private fun structuredOutcomeReason(
        outcome: com.pokerarity.scanner.util.ocr.EvaluationOutcome
    ): String = when (outcome) {
        com.pokerarity.scanner.util.ocr.EvaluationOutcome.UNIQUE_SUPPORTED ->
            com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason.INDEPENDENT_PROFILE
        com.pokerarity.scanner.util.ocr.EvaluationOutcome.CONTRADICTION ->
            com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason.FAMILY_EVALUATOR_CONTRADICTION
        com.pokerarity.scanner.util.ocr.EvaluationOutcome.AMBIGUOUS ->
            com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason.FAMILY_EVALUATOR_AMBIGUOUS
        com.pokerarity.scanner.util.ocr.EvaluationOutcome.INSUFFICIENT_EVIDENCE ->
            com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason.FAMILY_EVALUATOR_INSUFFICIENT
        com.pokerarity.scanner.util.ocr.EvaluationOutcome.UNSUPPORTED_MECHANIC ->
            com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason.FAMILY_EVALUATOR_UNSUPPORTED
    }

    private fun textualAuthorityReason(authority: SpeciesAuthority): String = when (authority) {
        SpeciesAuthority.SAFE_FUZZY -> com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason.SAFE_FUZZY
        SpeciesAuthority.UNCERTAIN -> com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason.UNCERTAIN
        else -> com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason.NO_MATCH
    }

/** Injectable species-OCR seam for a single frame; production forwards to OCRProcessor. */
internal fun interface FrameOcr {
    suspend fun recognize(request: FrameOcrRequest): OcrFrameResult
}
