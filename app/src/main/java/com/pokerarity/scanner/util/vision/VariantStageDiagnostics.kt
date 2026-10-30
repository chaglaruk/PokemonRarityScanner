package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.google.gson.GsonBuilder
import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.data.model.FullVariantCandidate
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.RecognitionIdentity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

private const val MAX_QA_CANDIDATE_COUNT = 100_000

internal data class QaBuildIdentity(
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val sourceSha: String
)

internal data class QaMetadataAsset(val id: String, val sha256: String?)

internal data class QaMetadataRevision(
    val source: String,
    val revisionSha256: String,
    val assets: List<QaMetadataAsset>
)

internal data class QaSelectedFrameReference(
    val ref: String?,
    val algorithm: String,
    val status: String
)

internal data class QaBooleanConfidence(val decision: Boolean, val confidence: Float)

internal data class QaShinySignatureMatch(
    val matched: Boolean,
    val confidence: Float,
    val normalScore: Float?,
    val shinyScore: Float?,
    val normalColorScore: Float?,
    val shinyColorScore: Float?,
    val scoreGap: Float?,
    val colorGap: Float?,
    val histogramSource: String,
    val strictMode: Boolean,
    val reasonCode: String,
    val referenceAsset: String = "data/shiny_signatures.json",
    val normalComponents: QaShinyComponents? = null,
    val shinyComponents: QaShinyComponents? = null
)

internal data class QaShinyComponents(
    val weightedScore: Float?,
    val colorDistance: Float?,
    val dHashDistance: Float?,
    val aHashDistance: Float?,
    val edgeDistance: Float?
)

internal data class QaExactFrameImage(
    val fileName: String,
    val sha256: String,
    val byteSize: Int,
    val width: Int,
    val height: Int
)

internal data class QaExactFrameCrop(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val analysisWidth: Int,
    val analysisHeight: Int,
    val detectorInputWidth: Int,
    val detectorInputHeight: Int,
    val shadowMaskApplied: Boolean
)

internal data class QaCostumeReferenceDistance(
    val key: String,
    val referenceAsset: String = "data/master_pokedex.json",
    val costume: Boolean,
    val aHashDistance: Float,
    val dHashDistance: Float,
    val edgeDistance: Float,
    val pHashDistance: Float,
    val headPHashDistance: Float,
    val weightedScore: Float,
    val rank: Int
)

internal data class QaExactFrameEvidence(
    val status: String,
    val relativeDirectory: String? = null,
    val selectedFrameSourceSha256Prefix: String? = null,
    val crop: QaExactFrameCrop? = null,
    val images: List<QaExactFrameImage> = emptyList(),
    val costumeReferenceDistances: List<QaCostumeReferenceDistance> = emptyList(),
    val replayShinyPrimary: QaShinySignatureMatch? = null,
    val replayShinyAlternates: List<QaShinySignatureMatch> = emptyList(),
    val replayHueEvidence: QaExactFrameHueEvidence? = null,
    val scoreDefinitions: QaExactFrameScoreDefinitions = QaExactFrameScoreDefinitions.DEFAULT,
    val replayCostumeDecisionReason: String? = null,
    val failureReasonCode: String? = null
)

internal data class QaExactFrameScoreDefinitions(
    val costumeWeightedFormula: String,
    val costumeGate: String,
    val shinyWeightedFormula: String,
    val shinyGate: String
) {
    companion object {
        val DEFAULT = QaExactFrameScoreDefinitions(
            costumeWeightedFormula = "0.28*dHash+0.20*aHash+0.18*edge+0.14*pHash+0.20*headPHash",
            costumeGate = "sparse: bestCostume+0.02<bestNormal and (bestCostume<0.30 or scoreGap>=0.03); dense: bestCostume<=0.30 and scoreGap>=0.04",
            shinyWeightedFormula = "0.60*color+0.20*dHash+0.15*aHash+0.05*edge",
            shinyGate = "absolute shiny<0.38 and shiny+0.01<normal; relative score/color gaps plus strict-mode bounds; fallbacks reported by reasonCode"
        )
    }
}

internal data class QaPendingExactFrame(
    val framePng: ByteArray,
    val maskedSpritePng: ByteArray,
    val analysisRegionPng: ByteArray,
    val crop: QaExactFrameCrop,
    val frameWidth: Int,
    val frameHeight: Int,
    val maskedWidth: Int,
    val maskedHeight: Int,
    val regionWidth: Int,
    val regionHeight: Int
)

internal data class QaShinySignatureStage(
    val referenceStatus: String,
    val referenceKey: String?,
    val strictShiny: Boolean,
    val primary: QaShinySignatureMatch?,
    val alternates: List<QaShinySignatureMatch>,
    val selectedSource: String,
    val selectedMatched: Boolean,
    val selectedConfidence: Float,
    val rejectionReason: String?
)

internal data class QaCostumeStage(
    val source: String,
    val referenceAvailable: Boolean,
    val candidateCount: Int,
    val costumeCandidateCount: Int,
    val bestNormalKey: String?,
    val bestNormalScore: Float?,
    val bestCostumeKey: String?,
    val bestCostumeScore: Float?,
    val scoreGap: Float?,
    val confidence: Float?,
    val matched: Boolean?,
    val denseVariantSpecies: Boolean?,
    val rejectionReason: String,
    val heuristicPath: String? = null,
    val signatureDecisionPath: String? = null,
    val heuristicCalled: Boolean = false,
    val heuristicMatched: Boolean? = null,
    val heuristicConfidence: Float? = null,
    val strictShiny: Boolean? = null,
    val strictShinyReason: String? = null,
    val finalConfidence: Float? = null,
    val finalMatched: Boolean? = null
)

internal data class QaCostumeDecision(
    val matched: Boolean,
    val confidence: Float,
    val heuristicPath: String,
    val signatureDecisionPath: String,
    val heuristicCalled: Boolean,
    val heuristicResult: Pair<Boolean, Float>?,
    val strictShiny: Boolean,
    val strictShinyReason: String
)

internal data class QaPrototypeMatch(
    val role: String,
    val species: String,
    val assetKey: String,
    val spriteKey: String,
    val variantType: String,
    val shiny: Boolean,
    val costume: Boolean,
    val score: Float,
    val confidence: Float,
    val speciesMargin: Float,
    val variantMargin: Float,
    val bestBaseScore: Float?,
    val bestBaseSpriteKey: String?,
    val bestNonBaseScore: Float?,
    val bestNonBaseSpriteKey: String?,
    val bestNonBaseType: String?,
    val bestShinyPeerScore: Float?,
    val bestShinyPeerSpriteKey: String?,
    val bestBaseShinyPeerScore: Float?,
    val bestBaseShinyPeerSpriteKey: String?,
    val rescueKind: String?,
    val topSpecies: List<String>
)

internal data class QaPrototypeStageStatus(
    val role: String,
    val resultAvailable: Boolean,
    val reasonCode: String?
)

internal data class QaFullVariantCandidate(
    val species: String,
    val spriteKey: String,
    val variantClass: String,
    val shiny: Boolean,
    val costume: Boolean,
    val source: String,
    val score: Float,
    val confidence: Float,
    val rescueKind: String?
)

internal data class QaFullVariantStage(
    val lockedSpecies: String?,
    val selectedSpecies: String?,
    val selectedSpriteKey: String?,
    val selectedClass: String?,
    val selectionStatus: String,
    val shiny: Boolean?,
    val costume: Boolean?,
    val form: Boolean?,
    val decisionConfidence: Float?,
    val shinyConfidence: Float?,
    val explanationMode: String?,
    val candidateCount: Int,
    val candidates: List<QaFullVariantCandidate>
)

internal data class QaMergeDecision(
    val stage: String,
    val target: String,
    val before: Boolean,
    val after: Boolean,
    val action: String,
    val reasonCode: String
)

internal data class QaPhase2Decision(
    val target: String,
    val prediction: Boolean,
    val confidence: Float,
    val margin: Float,
    val positiveScore: Float,
    val negativeScore: Float,
    val positiveCount: Int?,
    val negativeCount: Int?,
    val source: String,
    val decisionCapable: Boolean,
    val passedThreshold: Boolean,
    val action: String,
    val reasonCode: String
)

internal data class QaVisualStage(
    val inputWidth: Int? = null,
    val inputHeight: Int? = null,
    val spriteCropWidth: Int? = null,
    val spriteCropHeight: Int? = null,
    val maskedColor: QaBooleanConfidence? = null,
    val rawColor: QaBooleanConfidence? = null,
    val hue: QaBooleanConfidence? = null,
    val histogramHue: QaBooleanConfidence? = null,
    val shiny: Boolean? = null,
    val shinyConfidence: Float? = null,
    val overallConfidence: Float? = null,
    val costume: Boolean? = null,
    val costumeConfidence: Float? = null,
    val costumeSignaturePath: String? = null,
    val costumeHeuristicPath: String? = null,
    val strictShiny: Boolean? = null,
    val strictShinyReason: String? = null,
    val colorFallbackCalled: Boolean? = null,
    val colorFallbackReason: String? = null,
    val shinyDecisionPath: String? = null
)

internal data class QaIdentityStage(
    val species: String?,
    val formStatus: String,
    val knownForm: String?,
    val ambiguousForms: List<String>,
    val shiny: String,
    val costume: String,
    val formTruth: String
)

internal data class QaVariantDiagnosticReport(
    val schemaVersion: Int,
    val scanId: String,
    val capturedAtEpochMs: Long,
    val build: QaBuildIdentity,
    val metadata: QaMetadataRevision,
    val selectedFrame: QaSelectedFrameReference,
    val lockedSpecies: String?,
    val detectorInputSpecies: String?,
    val visualDetector: QaVisualStage,
    val shinySignature: QaShinySignatureStage?,
    val costumeSignatures: List<QaCostumeStage>,
    val prototypeStageStatus: List<QaPrototypeStageStatus>,
    val prototypeClassifier: List<QaPrototypeMatch>,
    val fullVariantMatcher: QaFullVariantStage?,
    val variantMerge: List<QaMergeDecision>,
    val phase2Status: String?,
    val phase2Merge: List<QaPhase2Decision>,
    val finalIdentity: QaIdentityStage?,
    val exactFrameEvidence: QaExactFrameEvidence,
    val instrumentationFailures: List<String>
)

private data class QaExactFrameManifest(
    val schemaVersion: Int,
    val scanId: String,
    val build: QaBuildIdentity,
    val metadata: QaMetadataRevision,
    val selectedFrame: QaSelectedFrameReference,
    val crop: QaExactFrameCrop,
    val images: List<QaExactFrameImage>,
    val costumeReferenceDistances: List<QaCostumeReferenceDistance>,
    val replayShinyPrimary: QaShinySignatureMatch?,
    val replayShinyAlternates: List<QaShinySignatureMatch>,
    val replayHueEvidence: QaExactFrameHueEvidence,
    val scoreDefinitions: QaExactFrameScoreDefinitions,
    val replayCostumeDecisionReason: String,
    val privacyScope: String = "app_private_no_upload_no_history"
)

internal data class QaDiagnosticWriteResult(val file: File?, val failureReason: String?)

@Suppress("TooManyFunctions") // One bounded recorder owns the per-scan decision evidence.
internal class VariantStageDiagnosticRecorder(
    val scanId: String = UUID.randomUUID().toString().replace("-", ""),
    private val exactFrameCaptureOptIn: Boolean = false
) {
    private val lock = Any()
    private var lockedSpecies: String? = null
    private var detectorInputSpecies: String? = null
    private var visual = QaVisualStage()
    private var shinySignature: QaShinySignatureStage? = null
    private val costumeStages = mutableListOf<QaCostumeStage>()
    private val prototypeStatuses = mutableListOf<QaPrototypeStageStatus>()
    private val prototypeMatches = mutableListOf<QaPrototypeMatch>()
    private var fullVariantMatcher: QaFullVariantStage? = null
    private var selectedFrameReference: QaSelectedFrameReference? = null
    private val variantMerge = mutableListOf<QaMergeDecision>()
    private var phase2Status: String? = null
    private val phase2Merge = mutableListOf<QaPhase2Decision>()
    private var finalIdentity: QaIdentityStage? = null
    private var exactFrameEvidence = QaExactFrameEvidence(
        status = if (exactFrameCaptureOptIn) "armed_waiting_for_detector" else "opt_in_disabled"
    )
    private var pendingExactFrame: QaPendingExactFrame? = null
    private var exactFrameCaptureAttempted = false
    private val failures = mutableListOf<String>()

    fun recordSpeciesAuthority(value: String?) = synchronized(lock) {
        lockedSpecies = short(value)
    }

    fun recordDetectorInput(species: String?, width: Int, height: Int) = synchronized(lock) {
        detectorInputSpecies = short(species)
        visual = visual.copy(inputWidth = width.coerceAtLeast(0), inputHeight = height.coerceAtLeast(0))
    }

    fun recordSelectedFrameReference(reference: QaSelectedFrameReference) = synchronized(lock) {
        selectedFrameReference = reference.copy(
            ref = short(reference.ref),
            algorithm = short(reference.algorithm) ?: "unknown",
            status = short(reference.status) ?: "unknown"
        )
    }

    fun recordSpriteCrop(width: Int, height: Int) = synchronized(lock) {
        visual = visual.copy(spriteCropWidth = width.coerceAtLeast(0), spriteCropHeight = height.coerceAtLeast(0))
    }

    fun captureExactFrame(
        detectorInput: Bitmap,
        maskedSprite: Bitmap,
        analysisRegion: Bitmap,
        crop: Rect,
        analysisWidth: Int,
        analysisHeight: Int,
        shadowMaskApplied: Boolean
    ) = synchronized(lock) {
        if (!exactFrameCaptureOptIn || exactFrameCaptureAttempted) return@synchronized
        exactFrameCaptureAttempted = true
        exactFrameEvidence = QaExactFrameEvidence(status = "capture_encoding")
        try {
            requireValidCaptureBitmap(detectorInput, MAX_FRAME_PIXELS)
            requireValidCaptureBitmap(maskedSprite, MAX_REGION_PIXELS)
            requireValidCaptureBitmap(analysisRegion, MAX_REGION_PIXELS)
            val frame = detectorInput.toBoundedPng(MAX_FRAME_PNG_BYTES)
            val masked = maskedSprite.toBoundedPng(MAX_REGION_PNG_BYTES)
            val region = analysisRegion.toBoundedPng(MAX_REGION_PNG_BYTES)
            require(frame.size + masked.size + region.size <= MAX_EXACT_FRAME_SET_BYTES) {
                "exact_frame_set_size_exceeded"
            }
            pendingExactFrame = QaPendingExactFrame(
                framePng = frame,
                maskedSpritePng = masked,
                analysisRegionPng = region,
                crop = QaExactFrameCrop(
                    left = crop.left,
                    top = crop.top,
                    right = crop.right,
                    bottom = crop.bottom,
                    analysisWidth = analysisWidth.coerceAtMost(MAX_IMAGE_DIMENSION),
                    analysisHeight = analysisHeight.coerceAtMost(MAX_IMAGE_DIMENSION),
                    detectorInputWidth = detectorInput.width,
                    detectorInputHeight = detectorInput.height,
                    shadowMaskApplied = shadowMaskApplied
                ),
                frameWidth = detectorInput.width,
                frameHeight = detectorInput.height,
                maskedWidth = maskedSprite.width,
                maskedHeight = maskedSprite.height,
                regionWidth = analysisRegion.width,
                regionHeight = analysisRegion.height
            )
            exactFrameEvidence = QaExactFrameEvidence(status = "captured_in_memory")
        } catch (_: Exception) {
            pendingExactFrame = null
            exactFrameEvidence = QaExactFrameEvidence(
                status = "capture_failed",
                failureReasonCode = "bounded_png_capture_failed"
            )
        }
    }

    internal fun pendingExactFrame(): QaPendingExactFrame? = synchronized(lock) { pendingExactFrame }

    internal fun selectedFrameReferenceForEvidence(): QaSelectedFrameReference? = synchronized(lock) { selectedFrameReference }

    internal fun recordExactFrameEvidence(value: QaExactFrameEvidence) = synchronized(lock) {
        exactFrameEvidence = value.copy(
            relativeDirectory = short(value.relativeDirectory),
            selectedFrameSourceSha256Prefix = short(value.selectedFrameSourceSha256Prefix),
            images = value.images.take(MAX_EXACT_FRAME_IMAGES).map { it.copy(fileName = short(it.fileName) ?: "unknown") },
            costumeReferenceDistances = value.costumeReferenceDistances.take(MAX_CANDIDATES).map(QaCostumeReferenceDistance::bounded),
            replayShinyAlternates = value.replayShinyAlternates.take(3).map(QaShinySignatureMatch::bounded),
            replayShinyPrimary = value.replayShinyPrimary?.bounded(),
            replayHueEvidence = value.replayHueEvidence?.bounded(),
            scoreDefinitions = value.scoreDefinitions.bounded(),
            replayCostumeDecisionReason = short(value.replayCostumeDecisionReason),
            failureReasonCode = short(value.failureReasonCode)
        )
        pendingExactFrame = null
    }

    fun recordColorEvidence(
        masked: Pair<Boolean, Float>,
        raw: Pair<Boolean, Float>,
        hue: Pair<Boolean, Float>,
        histogramHue: Pair<Boolean, Float>
    ) = synchronized(lock) {
        visual = visual.copy(
            maskedColor = masked.toQaBooleanConfidence(),
            rawColor = raw.toQaBooleanConfidence(),
            hue = hue.toQaBooleanConfidence(),
            histogramHue = histogramHue.toQaBooleanConfidence()
        )
    }

    fun recordShinySignature(stage: QaShinySignatureStage) = synchronized(lock) {
        shinySignature = stage.bounded()
    }

    fun recordCostumeSignature(stage: QaCostumeStage) = synchronized(lock) {
        if (costumeStages.size < MAX_STAGE_RECORDS) costumeStages += stage.bounded()
    }

    fun recordCostumeDecision(decision: QaCostumeDecision) = synchronized(lock) {
        val visualIndex = costumeStages.indexOfLast { it.source == VISUAL_COSTUME_SOURCE }
        if (visualIndex >= 0) {
            val visualStage = costumeStages[visualIndex]
            costumeStages[visualIndex] = visualStage.copy(
                finalMatched = decision.matched,
                finalConfidence = decision.confidence.finiteOrNull(),
                heuristicPath = short(decision.heuristicPath),
                signatureDecisionPath = short(decision.signatureDecisionPath),
                heuristicCalled = decision.heuristicCalled,
                heuristicMatched = decision.heuristicResult?.first,
                heuristicConfidence = decision.heuristicResult?.second?.finiteOrNull(),
                strictShiny = decision.strictShiny,
                strictShinyReason = short(decision.strictShinyReason)
            ).bounded()
        }
        visual = visual.copy(
            costume = decision.matched,
            costumeConfidence = decision.confidence.finiteOrNull(),
            costumeSignaturePath = short(decision.signatureDecisionPath),
            costumeHeuristicPath = short(decision.heuristicPath),
            strictShiny = decision.strictShiny,
            strictShinyReason = short(decision.strictShinyReason)
        )
    }

    fun recordShinyChoice(
        matched: Boolean,
        confidence: Float,
        fallbackCalled: Boolean,
        fallbackReason: String,
        decisionPath: String
    ) = synchronized(lock) {
        visual = visual.copy(
            shiny = matched,
            shinyConfidence = confidence.finiteOrNull(),
            colorFallbackCalled = fallbackCalled,
            colorFallbackReason = short(fallbackReason),
            shinyDecisionPath = short(decisionPath)
        )
    }

    fun recordOverallVisualConfidence(confidence: Float) = synchronized(lock) {
        visual = visual.copy(overallConfidence = confidence.finiteOrNull())
    }

    fun recordPrototypeMatch(
        role: String,
        match: VariantPrototypeClassifier.MatchResult?,
        noMatchReason: String = "classifier_returned_no_match"
    ) = synchronized(lock) {
        if (prototypeStatuses.size < MAX_CANDIDATES) {
            prototypeStatuses += QaPrototypeStageStatus(
                role = short(role) ?: "unknown",
                resultAvailable = match != null,
                reasonCode = if (match == null) short(noMatchReason) else null
            )
        }
        if (match == null || prototypeMatches.size >= MAX_STAGE_RECORDS) return@synchronized
        prototypeMatches += QaPrototypeMatch(
            role = short(role) ?: "unknown",
            species = short(match.species) ?: "unknown",
            assetKey = short(match.assetKey) ?: "unknown",
            spriteKey = short(match.spriteKey) ?: "unknown",
            variantType = short(match.variantType) ?: "unknown",
            shiny = match.isShiny,
            costume = match.isCostumeLike,
            score = match.score.finiteOrZero(),
            confidence = match.confidence.finiteOrZero(),
            speciesMargin = match.speciesMargin.finiteOrZero(),
            variantMargin = match.variantMargin.finiteOrZero(),
            bestBaseScore = match.bestBaseScore.finiteOrNull(),
            bestBaseSpriteKey = short(match.bestBaseSpriteKey),
            bestNonBaseScore = match.bestNonBaseScore.finiteOrNull(),
            bestNonBaseSpriteKey = short(match.bestNonBaseSpriteKey),
            bestNonBaseType = short(match.bestNonBaseVariantType),
            bestShinyPeerScore = match.bestShinyPeerScore.finiteOrNull(),
            bestShinyPeerSpriteKey = short(match.bestShinyPeerSpriteKey),
            bestBaseShinyPeerScore = match.bestBaseShinyPeerScore.finiteOrNull(),
            bestBaseShinyPeerSpriteKey = short(match.bestBaseShinyPeerSpriteKey),
            rescueKind = short(match.rescueKind),
            topSpecies = match.topSpecies.take(MAX_CANDIDATES).mapNotNull(::short)
        )
    }

    fun recordFullVariant(match: FullVariantMatch?, candidates: List<FullVariantCandidate>) = synchronized(lock) {
        fullVariantMatcher = QaFullVariantStage(
            lockedSpecies = short(match?.finalSpecies),
            selectedSpecies = short(match?.winnerSpecies),
            selectedSpriteKey = short(match?.finalSpriteKey),
            selectedClass = short(match?.resolvedVariantClass),
            selectionStatus = when {
                match == null -> "matcher_returned_no_match"
                match.winnerSpecies == null -> "no_candidate_selected"
                else -> "candidate_selected"
            },
            shiny = match?.resolvedShiny,
            costume = match?.resolvedCostume,
            form = match?.resolvedForm,
            decisionConfidence = match?.variantConfidence?.finiteOrNull(),
            shinyConfidence = match?.shinyConfidence?.finiteOrNull(),
            explanationMode = short(match?.explanationMode),
            candidateCount = candidates.size.coerceIn(0, MAX_QA_CANDIDATE_COUNT),
            candidates = candidates.take(MAX_CANDIDATES).map(::candidateSnapshot)
        )
    }

    fun recordVariantMerge(decisions: List<QaMergeDecision>) = synchronized(lock) {
        variantMerge += decisions.take(MAX_CANDIDATES).map(QaMergeDecision::bounded)
        if (variantMerge.size > MAX_STAGE_RECORDS) {
            variantMerge.subList(MAX_STAGE_RECORDS, variantMerge.size).clear()
        }
    }

    fun recordPhase2Merge(decisions: List<QaPhase2Decision>) = synchronized(lock) {
        phase2Merge.clear()
        phase2Merge += decisions.take(MAX_CANDIDATES).map(QaPhase2Decision::bounded)
        phase2Status = if (decisions.isEmpty()) "merge_ran_no_predictions" else "merge_ran"
    }

    fun recordPhase2Status(status: String) = synchronized(lock) {
        phase2Status = short(status)
    }

    fun recordIdentity(identity: RecognitionIdentity) = synchronized(lock) {
        finalIdentity = QaIdentityStage(
            species = short(identity.canonicalSpecies),
            formStatus = short(identity.formStatus.name) ?: "UNKNOWN",
            knownForm = short(identity.knownForm),
            ambiguousForms = identity.ambiguousForms.take(MAX_CANDIDATES).mapNotNull(::short),
            shiny = short(identity.shiny.name) ?: "UNKNOWN",
            costume = short(identity.costume.name) ?: "UNKNOWN",
            formTruth = short(identity.specialForm.name) ?: "UNKNOWN"
        )
    }

    fun recordFailure(reasonCode: String) = synchronized(lock) {
        if (failures.size < MAX_STAGE_RECORDS) failures += short(reasonCode) ?: "unknown_failure"
    }

    fun snapshot(
        build: QaBuildIdentity,
        metadata: QaMetadataRevision
    ): QaVariantDiagnosticReport = synchronized(lock) {
        QaVariantDiagnosticReport(
            schemaVersion = SCHEMA_VERSION,
            scanId = scanId,
            capturedAtEpochMs = System.currentTimeMillis(),
            build = build,
            metadata = metadata,
            selectedFrame = selectedFrameReference
                ?: QaSelectedFrameReference(null, "sha256-prefix-96bit", "not_recorded"),
            lockedSpecies = lockedSpecies,
            detectorInputSpecies = detectorInputSpecies,
            visualDetector = visual,
            shinySignature = shinySignature,
            costumeSignatures = costumeStages.toList(),
            prototypeStageStatus = prototypeStatuses.toList(),
            prototypeClassifier = prototypeMatches.toList(),
            fullVariantMatcher = fullVariantMatcher,
            variantMerge = variantMerge.toList(),
            phase2Status = phase2Status,
            phase2Merge = phase2Merge.toList(),
            finalIdentity = finalIdentity,
            exactFrameEvidence = exactFrameEvidence,
            instrumentationFailures = failures.toList()
        )
    }

    private fun candidateSnapshot(candidate: FullVariantCandidate) = QaFullVariantCandidate(
        species = short(candidate.species) ?: "",
        spriteKey = short(candidate.spriteKey) ?: "",
        variantClass = short(candidate.variantClass) ?: "",
        shiny = candidate.isShiny,
        costume = candidate.isCostumeLike,
        source = short(candidate.source) ?: "",
        score = candidate.matchScore.finiteOrZero(),
        confidence = candidate.classifierConfidence.finiteOrZero(),
        rescueKind = short(candidate.rescueKind)
    )

    companion object {
        const val MAX_CANDIDATES = 8
        const val MAX_STAGE_RECORDS = 12
        const val SCHEMA_VERSION = 2
        const val VISUAL_COSTUME_SOURCE = "visual_detector_signature"
        const val MAX_EXACT_FRAME_IMAGES = 3
        const val MAX_FRAME_PIXELS = 4_000_000
        const val MAX_REGION_PIXELS = 1_500_000
        const val MAX_IMAGE_DIMENSION = 4096
        const val MAX_FRAME_PNG_BYTES = 8 * 1024 * 1024
        const val MAX_REGION_PNG_BYTES = 2 * 1024 * 1024
        const val MAX_EXACT_FRAME_SET_BYTES = 12 * 1024 * 1024

        private fun requireValidCaptureBitmap(bitmap: Bitmap, maxPixels: Int) {
            require(bitmap.width in 1..MAX_IMAGE_DIMENSION && bitmap.height in 1..MAX_IMAGE_DIMENSION)
            require(bitmap.width.toLong() * bitmap.height <= maxPixels.toLong())
        }

        private fun Bitmap.toBoundedPng(maxBytes: Int): ByteArray {
            val output = BoundedByteArrayOutputStream(maxBytes)
            check(compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG encoding failed" }
            return output.toByteArray()
        }
    }
}

private class BoundedByteArrayOutputStream(private val maxBytes: Int) : OutputStream() {
    private val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))

    override fun write(value: Int) {
        ensureCapacity(1)
        output.write(value)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        ensureCapacity(length)
        output.write(buffer, offset, length)
    }

    fun toByteArray(): ByteArray = output.toByteArray()

    private fun ensureCapacity(additional: Int) {
        if (additional < 0 || output.size().toLong() + additional > maxBytes) {
            throw IOException("bounded evidence image exceeded configured limit")
        }
    }
}

internal object QaVariantDiagnosticPolicy {
    const val LEGACY_DIAGNOSTIC_APPLICATION_ID = "com.pokerarity.scanner.sprint6qa.diag1"
    const val EXACT_FRAME_DIAGNOSTIC_APPLICATION_ID = "com.pokerarity.scanner.sprint6qa.diag2"

    fun enabled(isDebug: Boolean, qaFlag: Boolean, applicationId: String, sourceSha: String): Boolean =
        isDebug &&
            qaFlag &&
            applicationId in setOf(LEGACY_DIAGNOSTIC_APPLICATION_ID, EXACT_FRAME_DIAGNOSTIC_APPLICATION_ID) &&
            sourceSha.matches(Regex("[a-fA-F0-9]{40}"))

    fun exactFrameCaptureEnabled(
        isDebug: Boolean,
        qaFlag: Boolean,
        applicationId: String,
        sourceSha: String,
        captureBuildFlag: Boolean,
        runtimeOptIn: Boolean
    ): Boolean = enabled(isDebug, qaFlag, applicationId, sourceSha) &&
        applicationId == EXACT_FRAME_DIAGNOSTIC_APPLICATION_ID &&
        captureBuildFlag &&
        runtimeOptIn
}

internal object QaVariantDiagnosticWriter {
    const val MAX_REPORT_BYTES = 32 * 1024
    const val MAX_REPORTS = 8
    private const val REPORT_DIRECTORY = "variant_diagnostics"
    private const val EXACT_FRAME_DIRECTORY = "exact_frames"
    private const val EXACT_FRAME_CAPTURE_PREFERENCES = "qa_variant_diagnostics"
    private const val EXACT_FRAME_CAPTURE_OPT_IN_KEY = "capture_exact_frames_once"
    private const val MAX_EXACT_FRAME_SETS = 3
    private const val MAX_EXACT_FRAME_TOTAL_STORAGE_BYTES = 36L * 1024L * 1024L
    private const val FRAME_DIGEST_PREFIX_LENGTH = 24
    private const val SHA256_BUFFER_SIZE_BYTES = 8_192
    private const val BYTE_MASK = 0xff
    private const val HEX_RADIX = 16
    private const val HEX_WIDTH = 2
    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    private val metadataAssets = listOf(
        "shiny_signatures" to "data/shiny_signatures.json",
        "master_pokedex" to "data/master_pokedex.json",
        "variant_catalog" to "data/variant_catalog.json",
        "authoritative_variant_db" to "data/authoritative_variant_db.json",
        "costume_signatures" to "data/costume_signatures.json",
        "variant_prototypes" to "data/variant_classifier_model.json",
        "phase2_variant_model" to "data/variant_phase2_model.json",
        "legacy_rarity" to "data/global_rarity_legacy_db.json"
    )

    fun isEnabled(): Boolean = QaVariantDiagnosticPolicy.enabled(
        isDebug = BuildConfig.DEBUG,
        qaFlag = BuildConfig.QA_VARIANT_DIAGNOSTICS,
        applicationId = BuildConfig.APPLICATION_ID,
        sourceSha = BuildConfig.QA_SOURCE_SHA
    )

    fun newRecorder(context: Context): VariantStageDiagnosticRecorder = VariantStageDiagnosticRecorder(
        exactFrameCaptureOptIn = isExactFrameCaptureOptedIn(context)
    )

    fun isExactFrameCaptureOptedIn(context: Context): Boolean = QaVariantDiagnosticPolicy.exactFrameCaptureEnabled(
        isDebug = BuildConfig.DEBUG,
        qaFlag = BuildConfig.QA_VARIANT_DIAGNOSTICS,
        applicationId = BuildConfig.APPLICATION_ID,
        sourceSha = BuildConfig.QA_SOURCE_SHA,
        captureBuildFlag = BuildConfig.QA_EXACT_FRAME_CAPTURE_BUILD,
        runtimeOptIn = context.getSharedPreferences(EXACT_FRAME_CAPTURE_PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(EXACT_FRAME_CAPTURE_OPT_IN_KEY, false)
    )

    fun serialize(report: QaVariantDiagnosticReport): String {
        val json = gson.toJson(report)
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_REPORT_BYTES) {
            "bounded diagnostic report exceeded maximum size"
        }
        return json
    }

    fun write(context: Context, recorder: VariantStageDiagnosticRecorder): QaDiagnosticWriteResult {
        if (!isEnabled()) return QaDiagnosticWriteResult(null, "diagnostics_disabled")
        var phase = "directory"
        val result = try {
            val directory = File(context.filesDir, REPORT_DIRECTORY)
            check(directory.exists() || directory.mkdirs())
            val existing = directory.listFiles { file -> file.isFile && file.extension == "json" }.orEmpty()
            if (existing.size >= MAX_REPORTS) {
                QaDiagnosticWriteResult(null, "report_retention_cap_reached")
            } else {
                phase = "metadata_hash"
                val sourceSha = BuildConfig.QA_SOURCE_SHA
                val build = QaBuildIdentity(
                    applicationId = BuildConfig.APPLICATION_ID,
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    sourceSha = sourceSha
                )
                val metadata = metadataRevision(context)
                val exactFrameEvidence = recorder.pendingExactFrame()?.let { pending ->
                    runCatching { persistExactFrameEvidence(context, directory, recorder, pending, build, metadata) }
                        .getOrElse {
                            QaExactFrameEvidence(
                                status = "not_saved",
                                failureReasonCode = "frame_persistence_failed"
                            )
                        }
                } ?: recorder.snapshot(build, metadata).exactFrameEvidence
                recorder.recordExactFrameEvidence(exactFrameEvidence)
                val report = recorder.snapshot(build, metadata)
                phase = "serialize"
                val json = serialize(report)
                val target = File(directory, "variant-${recorder.scanId}.json")
                val temporary = File(directory, "${target.name}.tmp")
                phase = "write"
                FileOutputStream(temporary).use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
                phase = "rename"
                check(temporary.renameTo(target))
                QaDiagnosticWriteResult(target, null)
            }
        } catch (_: Exception) {
            QaDiagnosticWriteResult(null, "diagnostic_${phase}_failed")
        } finally {
            if (recorder.pendingExactFrame() != null || isExactFrameCaptureOptedIn(context)) {
                context.getSharedPreferences(EXACT_FRAME_CAPTURE_PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(EXACT_FRAME_CAPTURE_OPT_IN_KEY, false)
                    .commit()
            }
        }
        return result
    }

    private fun persistExactFrameEvidence(
        context: Context,
        reportDirectory: File,
        recorder: VariantStageDiagnosticRecorder,
        pending: QaPendingExactFrame,
        build: QaBuildIdentity,
        metadata: QaMetadataRevision
    ): QaExactFrameEvidence {
        val parent = File(reportDirectory, EXACT_FRAME_DIRECTORY)
        check(parent.exists() || parent.mkdirs())
        val existingSets = parent.listFiles().orEmpty().filter { it.isDirectory && File(it, "manifest.json").isFile }
        if (existingSets.size >= MAX_EXACT_FRAME_SETS) {
            return QaExactFrameEvidence(status = "not_saved", failureReasonCode = "frame_set_retention_cap_reached")
        }
        val existingBytes = parent.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        if (existingBytes + pending.framePng.size + pending.maskedSpritePng.size + pending.analysisRegionPng.size >
            MAX_EXACT_FRAME_TOTAL_STORAGE_BYTES
        ) {
            return QaExactFrameEvidence(status = "not_saved", failureReasonCode = "frame_storage_cap_reached")
        }

        val temp = File(parent, ".${recorder.scanId}.pending")
        check(temp.mkdir())
        val frameFile = File(temp, "detector-input.png")
        val maskedFile = File(temp, "masked-signature-sprite.png")
        val regionFile = File(temp, "analysis-source-region.png")
        writeSynced(frameFile, pending.framePng)
        writeSynced(maskedFile, pending.maskedSpritePng)
        writeSynced(regionFile, pending.analysisRegionPng)

        val images = listOf(
            imageRecord(frameFile.name, pending.framePng, pending.frameWidth, pending.frameHeight),
            imageRecord(maskedFile.name, pending.maskedSpritePng, pending.maskedWidth, pending.maskedHeight),
            imageRecord(regionFile.name, pending.analysisRegionPng, pending.regionWidth, pending.regionHeight)
        )

        val replay = runCatching {
            replaySavedFrameEvidence(context, maskedFile, regionFile, recorder.snapshot(build, metadata))
        }.getOrElse {
            QaExactFrameReplayResult(
                emptyList(),
                null,
                emptyList(),
                "replay_failed",
                QaExactFrameHueEvidence(emptyList(), emptyList(), null, null)
            )
        }
        val relativeDirectory = "$REPORT_DIRECTORY/$EXACT_FRAME_DIRECTORY/${recorder.scanId}"
        val summary = QaExactFrameEvidence(
            status = if (replay.costumeDecisionReason == "replay_failed") "saved_replay_failed" else "saved",
            relativeDirectory = relativeDirectory,
            selectedFrameSourceSha256Prefix = recorder.selectedFrameReferenceForEvidence()?.ref,
            crop = pending.crop,
            images = images,
            costumeReferenceDistances = replay.costumeReferenceDistances,
            replayShinyPrimary = replay.shinyPrimary,
            replayShinyAlternates = replay.shinyAlternates,
            replayHueEvidence = replay.hueEvidence,
            scoreDefinitions = QaExactFrameScoreDefinitions.DEFAULT,
            replayCostumeDecisionReason = replay.costumeDecisionReason,
            failureReasonCode = if (replay.costumeDecisionReason == "replay_failed") "saved_pixel_replay_failed" else null
        )
        val manifest = QaExactFrameManifest(
            schemaVersion = 1,
            scanId = recorder.scanId,
            build = build,
            metadata = metadata,
            selectedFrame = recorder.selectedFrameReferenceForEvidence()
                ?: QaSelectedFrameReference(null, "sha256-prefix-96bit", "not_recorded"),
            crop = pending.crop,
            images = images,
            costumeReferenceDistances = replay.costumeReferenceDistances,
            replayShinyPrimary = replay.shinyPrimary,
            replayShinyAlternates = replay.shinyAlternates,
            replayHueEvidence = replay.hueEvidence,
            scoreDefinitions = QaExactFrameScoreDefinitions.DEFAULT,
            replayCostumeDecisionReason = replay.costumeDecisionReason
        )
        val manifestBytes = gson.toJson(manifest).toByteArray(Charsets.UTF_8)
        check(manifestBytes.size <= MAX_REPORT_BYTES) { "bounded exact-frame manifest exceeded maximum size" }
        writeSynced(File(temp, "manifest.json"), manifestBytes)
        val target = File(parent, recorder.scanId)
        check(temp.renameTo(target))
        return summary
    }

    private fun replaySavedFrameEvidence(
        context: Context,
        maskedFile: File,
        regionFile: File,
        report: QaVariantDiagnosticReport
    ): QaExactFrameReplayResult {
        val masked = BitmapFactory.decodeFile(maskedFile.absolutePath)
            ?: error("saved masked frame could not be decoded")
        val sourceRegion = BitmapFactory.decodeFile(regionFile.absolutePath)
            ?: run { masked.recycle(); error("saved source region could not be decoded") }
        try {
            return QaExactFrameReplay.evaluate(
                context = context,
                species = report.lockedSpecies ?: report.detectorInputSpecies,
                maskedSprite = masked,
                analysisRegion = sourceRegion,
                strictShiny = report.visualDetector.strictShiny == true
            )
        } finally {
            masked.recycle()
            sourceRegion.recycle()
        }
    }

    private fun writeSynced(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
    }

    private fun imageRecord(name: String, bytes: ByteArray, width: Int, height: Int) = QaExactFrameImage(
        fileName = name,
        sha256 = sha256(bytes),
        byteSize = bytes.size,
        width = width,
        height = height
    )

    internal fun metadataRevision(context: Context): QaMetadataRevision {
        val assets = metadataAssets.map { (id, path) -> QaMetadataAsset(id, assetSha256(context, path)) }
        val material = assets.joinToString("|") { "${it.id}:${it.sha256.orEmpty()}" }
        return QaMetadataRevision(
            source = "qa_apk_bundled_assets",
            revisionSha256 = sha256(material.toByteArray(Charsets.UTF_8)),
            assets = assets
        )
    }

    internal fun selectedFrameReference(path: String): QaSelectedFrameReference {
        val file = File(path)
        val digest = runCatching { FileInputStream(file).use(::sha256) }.getOrNull()
        return QaSelectedFrameReference(
            ref = digest?.take(FRAME_DIGEST_PREFIX_LENGTH)?.let { "sha256:$it" },
            algorithm = "sha256-prefix-96bit",
            status = if (digest == null) "selected_frame_file_unavailable" else "available"
        )
    }

    private fun assetSha256(context: Context, path: String): String? = runCatching {
        context.assets.open(path).use(::sha256)
    }.getOrNull()

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(SHA256_BUFFER_SIZE_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(HEX_WIDTH, '0')
    }
}

internal fun QaShinySignatureMatch.bounded() = copy(
    confidence = confidence.finiteOrZero(),
    normalScore = normalScore.finiteOrNull(),
    shinyScore = shinyScore.finiteOrNull(),
    normalColorScore = normalColorScore.finiteOrNull(),
    shinyColorScore = shinyColorScore.finiteOrNull(),
    scoreGap = scoreGap.finiteOrNull(),
    colorGap = colorGap.finiteOrNull(),
    histogramSource = short(histogramSource) ?: "unknown",
    reasonCode = short(reasonCode) ?: "unknown",
    normalComponents = normalComponents?.bounded(),
    shinyComponents = shinyComponents?.bounded()
)

private fun QaShinyComponents.bounded() = copy(
    weightedScore = weightedScore.finiteOrNull(),
    colorDistance = colorDistance.finiteOrNull(),
    dHashDistance = dHashDistance.finiteOrNull(),
    aHashDistance = aHashDistance.finiteOrNull(),
    edgeDistance = edgeDistance.finiteOrNull()
)

private fun QaExactFrameHueEvidence.bounded() = copy(
    maskedBodyHistogram = maskedBodyHistogram.take(12).map { it.finiteOrZero() },
    sourceBodyHistogram = sourceBodyHistogram.take(12).map { it.finiteOrZero() },
    maskedBodyHueCentroid = maskedBodyHueCentroid.finiteOrNull(),
    sourceBodyHueCentroid = sourceBodyHueCentroid.finiteOrNull()
)

private fun QaExactFrameScoreDefinitions.bounded() = copy(
    costumeWeightedFormula = costumeWeightedFormula.take(160),
    costumeGate = costumeGate.take(160),
    shinyWeightedFormula = shinyWeightedFormula.take(160),
    shinyGate = shinyGate.take(160)
)

private fun QaCostumeReferenceDistance.bounded() = copy(
    key = short(key) ?: "unknown",
    referenceAsset = short(referenceAsset) ?: "unknown",
    aHashDistance = aHashDistance.finiteOrZero(),
    dHashDistance = dHashDistance.finiteOrZero(),
    edgeDistance = edgeDistance.finiteOrZero(),
    pHashDistance = pHashDistance.finiteOrZero(),
    headPHashDistance = headPHashDistance.finiteOrZero(),
    weightedScore = weightedScore.finiteOrZero(),
    rank = rank.coerceIn(1, VariantStageDiagnosticRecorder.MAX_CANDIDATES)
)

private fun QaShinySignatureStage.bounded() = copy(
    referenceStatus = short(referenceStatus) ?: "unknown",
    referenceKey = short(referenceKey),
    primary = primary?.bounded(),
    alternates = alternates.take(VariantStageDiagnosticRecorder.MAX_CANDIDATES).map(QaShinySignatureMatch::bounded),
    selectedSource = short(selectedSource) ?: "unknown",
    selectedConfidence = selectedConfidence.finiteOrZero(),
    rejectionReason = short(rejectionReason)
)

private fun QaCostumeStage.bounded() = copy(
    source = short(source) ?: "unknown",
    candidateCount = candidateCount.coerceIn(0, MAX_QA_CANDIDATE_COUNT),
    costumeCandidateCount = costumeCandidateCount.coerceIn(0, MAX_QA_CANDIDATE_COUNT),
    bestNormalKey = short(bestNormalKey),
    bestNormalScore = bestNormalScore.finiteOrNull(),
    bestCostumeKey = short(bestCostumeKey),
    bestCostumeScore = bestCostumeScore.finiteOrNull(),
    scoreGap = scoreGap.finiteOrNull(),
    confidence = confidence.finiteOrNull(),
    finalConfidence = finalConfidence.finiteOrNull(),
    rejectionReason = short(rejectionReason) ?: "unknown",
    heuristicPath = short(heuristicPath),
    signatureDecisionPath = short(signatureDecisionPath),
    heuristicConfidence = heuristicConfidence.finiteOrNull(),
    strictShinyReason = short(strictShinyReason)
)

private fun QaMergeDecision.bounded() = copy(
    stage = short(stage) ?: "unknown",
    target = short(target) ?: "unknown",
    action = short(action) ?: "unknown",
    reasonCode = short(reasonCode) ?: "unknown"
)

private fun QaPhase2Decision.bounded() = copy(
    confidence = confidence.finiteOrZero(),
    margin = margin.finiteOrZero(),
    positiveScore = positiveScore.finiteOrZero(),
    negativeScore = negativeScore.finiteOrZero(),
    source = short(source) ?: "unknown",
    action = short(action) ?: "unknown",
    reasonCode = short(reasonCode) ?: "unknown"
)

private fun Pair<Boolean, Float>.toQaBooleanConfidence() =
    QaBooleanConfidence(first, second.finiteOrZero())

private fun Float?.finiteOrNull(): Float? = this?.takeIf { it.isFinite() }

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f

private fun short(value: String?): String? = value?.take(MAX_TEXT_CHARS)

private const val MAX_TEXT_CHARS = 96
