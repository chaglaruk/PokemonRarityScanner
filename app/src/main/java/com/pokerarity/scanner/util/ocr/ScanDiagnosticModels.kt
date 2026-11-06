package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.data.model.CostumeDecisionDiagnostics
import com.pokerarity.scanner.data.model.OcrConfidenceReasons
import com.pokerarity.scanner.data.model.OcrFieldConfidence
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VariantDecisionTrace
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier

data class ScanDiagnosticReport(
    val diagnosticId: String,
    val generatedAtEpochMs: Long = System.currentTimeMillis(),
    val screenState: String = "Unknown",
    val screenConfidence: Float? = null,
    val stageTimings: List<StageTimingDiagnostic> = emptyList(),
    val frames: List<FrameDiagnostic> = emptyList(),
    val finalPokemon: PokemonSummary? = null,
    val rarityBreakdown: Map<String, Int> = emptyMap(),
    val confidenceReasons: List<ConfidenceReasonDiagnostic> = emptyList(),
    val retryReason: String? = null,
    val fallbackReason: String? = null,
    val resolverTrace: SpeciesResolverTrace? = null,
    val variantSummary: VariantVisualSummary? = null,
    val scanDecision: ScanDecision? = null,
    val frameRoutes: List<FrameRouteDiagnostic> = emptyList(),
    /** Bounded revision identity of the recognition snapshot behind this report. */
    val recognitionSnapshotRevision: String? = null,
    /**
     * Phase 2E bounded identity summary: species/form status, tri-state variant values
     * and classifier-mismatch diagnostics. Contains no screenshots, raw asset data,
     * local paths or unbounded classifier candidates.
     */
    val recognitionIdentity: com.pokerarity.scanner.data.model.RecognitionIdentity? = null,
    /**
     * Phase 2F bounded request-ownership metadata: logical request/attempt ids,
     * projection epoch, capture sequence, origin and terminal outcome. Opaque
     * process-local identifiers only; no tokens, paths or device identifiers.
     */
    val requestOwnership: RequestOwnershipDiagnostic? = null
)

/** Phase 2F bounded request-ownership summary for diagnostics. */
data class RequestOwnershipDiagnostic(
    val requestId: Long,
    val attemptId: Int,
    val projectionEpoch: Long,
    val captureSequenceId: Long? = null,
    val origin: String,
    val terminalOutcome: String? = null,
    val coalescedRequests: Int = 0
)

data class StageTimingDiagnostic(
    val stage: String,
    val durationMs: Long
)

data class FrameDiagnostic(
    val frameIndex: Int,
    val role: String = "fast",
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val imageWidth: Int,
    val imageHeight: Int,
    val estimatedCpCropQuality: Double? = null,
    val screenState: String = "Unknown",
    val screenConfidence: Float? = null,
    val anchors: List<AnchorDiagnostic> = emptyList(),
    val geometryFallbackReasons: List<String> = emptyList(),
    val crops: List<CropDiagnostic> = emptyList(),
    val ocrBlocks: List<OcrBlockDiagnostic> = emptyList(),
    val fieldCandidates: List<FieldCandidateDiagnostic> = emptyList(),
    val stageTimings: List<StageTimingDiagnostic> = emptyList(),
    val selected: PokemonSummary,
    /** Phase 2B persistent-calibration participation; null when calibration did not run. */
    val calibration: CalibrationDiagnostic? = null,
    /** Phase 2C structured per-field extraction states (never sensitive values). */
    val structuredFields: List<FieldReadDiagnostic> = emptyList()
)

/** Typed Phase 2C field-extraction state for diagnostics: why a value is present or absent. */
data class FieldReadDiagnostic(
    val field: String,
    val status: String,
    val candidateCount: Int,
    val reasonCode: String,
    val value: String? = null
)

data class CropDiagnostic(
    val field: String,
    val source: String,
    val left: Int?,
    val top: Int?,
    val right: Int?,
    val bottom: Int?,
    val status: String,
    val provenance: String? = null,
    val confidence: Float? = null,
    val reasons: List<String> = emptyList()
)

data class AnchorDiagnostic(
    val name: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float,
    val reason: String
)

data class OcrBlockDiagnostic(
    val text: String,
    val left: Int?,
    val top: Int?,
    val right: Int?,
    val bottom: Int?
)

data class FieldCandidateDiagnostic(
    val field: String,
    val source: String,
    val rawText: String?,
    val parsedValue: String?,
    val status: String,
    val cropName: String? = null,
    val cropLeft: Int? = null,
    val cropTop: Int? = null,
    val cropRight: Int? = null,
    val cropBottom: Int? = null,
    val cropProvenance: String? = null,
    val cropConfidence: Float? = null,
    val preprocessing: String? = null,
    val normalizedText: String? = null,
    val parserResult: String? = null,
    val candidateScore: Float? = null,
    val winner: Boolean = false,
    val reason: String? = null,
    val selectedValue: String? = null
)

data class ConfidenceReasonDiagnostic(
    val field: String,
    val status: String,
    val source: String,
    val confidence: Float?,
    val reasonCodes: List<String>
) {
    companion object {
        fun from(reasons: OcrConfidenceReasons?): List<ConfidenceReasonDiagnostic> {
            return reasons?.fields.orEmpty().map { it.toDiagnostic() }
        }

        private fun OcrFieldConfidence.toDiagnostic(): ConfidenceReasonDiagnostic =
            ConfidenceReasonDiagnostic(
                field = field.name,
                status = status.name,
                source = source.name,
                confidence = confidence,
                reasonCodes = reasonCodes
            )
    }
}

data class PokemonSummary(
    val cp: Int?,
    val hp: Int?,
    val maxHp: Int?,
    val name: String?,
    val realName: String?,
    val candyName: String?,
    val stardust: Int?,
    val arcLevel: Float?,
    val caughtDateEpochMs: Long?,
    val appraisalAttack: Int?,
    val appraisalDefense: Int?,
    val appraisalStamina: Int?
) {
    companion object {
        fun from(pokemon: PokemonData): PokemonSummary =
            PokemonSummary(
                cp = pokemon.cp,
                hp = pokemon.hp,
                maxHp = pokemon.maxHp,
                name = pokemon.name,
                realName = pokemon.realName,
                candyName = pokemon.candyName,
                stardust = pokemon.stardust,
                arcLevel = pokemon.arcLevel,
                caughtDateEpochMs = pokemon.caughtDate?.time,
                appraisalAttack = pokemon.appraisalAttack,
                appraisalDefense = pokemon.appraisalDefense,
                appraisalStamina = pokemon.appraisalStamina
            )
    }
}

data class VariantVisualSummary(
    val isShiny: Boolean,
    val isShadow: Boolean,
    val isPurified: Boolean,
    val isLucky: Boolean,
    val hasCostume: Boolean,
    val hasSpecialForm: Boolean,
    val isXXS: Boolean,
    val isXXL: Boolean,
    val hasLocationCard: Boolean,
    val confidence: Float,
    val classifierSpecies: String?,
    val classifierScope: String?,
    val classifierConfidence: Float?,
    val fullVariantSpecies: String?,
    val fullVariantClass: String?,
    /** Bounded stage provenance emitted only by the isolated QA build. */
    val qaStageDiagnostics: QaVariantStageDiagnostics? = null,
    /** Present only in the isolated QA build; omitted from ordinary debug/release summaries. */
    val costumeDecisionDiagnostics: CostumeDecisionDiagnostics? = null
) {
    companion object {
        internal fun from(
            features: VisualFeatures?,
            trace: VariantDecisionTrace?,
            includeQaDiagnostics: Boolean = QaVariantDiagnosticsPolicy.isEnabled(BuildConfig.BUILD_TYPE),
            phase2Input: QaVariantPhase2Input? = null
        ): VariantVisualSummary? {
            if (features == null && trace == null) return null
            val safeFeatures = features ?: VisualFeatures()
            return VariantVisualSummary(
                isShiny = safeFeatures.isShiny,
                isShadow = safeFeatures.isShadow,
                isPurified = safeFeatures.isPurified,
                isLucky = safeFeatures.isLucky,
                hasCostume = safeFeatures.hasCostume,
                hasSpecialForm = safeFeatures.hasSpecialForm,
                isXXS = safeFeatures.isXXS,
                isXXL = safeFeatures.isXXL,
                hasLocationCard = safeFeatures.hasLocationCard,
                confidence = safeFeatures.confidence,
                classifierSpecies = trace?.classifierSpecies,
                classifierScope = trace?.classifierScope,
                classifierConfidence = trace?.classifierConfidence,
                fullVariantSpecies = trace?.fullVariantSpecies,
                fullVariantClass = trace?.fullVariantClass,
                qaStageDiagnostics = if (includeQaDiagnostics) {
                    QaVariantStageDiagnostics.from(trace, phase2Input)
                } else {
                    null
                },
                costumeDecisionDiagnostics = safeFeatures.costumeDecisionDiagnostics.takeIf { includeQaDiagnostics }
            )
        }
    }
}

/** Input provenance captured at the visual/Phase 2 merge boundary. */
internal data class QaVariantPhase2Input(
    val visualCostumeBeforePhase2: Boolean,
    val mayApply: Boolean,
    val result: Phase2VariantClassifier.Result?
)

/** Fixed-size evidence separating visual merge, full match and Phase 2 decisions. */
data class QaVariantStageDiagnostics(
    val classifierVariantType: String?,
    val classifierIsCostume: Boolean?,
    val classifierSpriteKey: String?,
    val classifierScore: Float?,
    val classifierVariantMargin: Float?,
    val classifierBestBaseScore: Float?,
    val classifierBestNonBaseScore: Float?,
    val classifierBestNonBaseType: String?,
    val classifierRescueKind: String?,
    val fullVariantIsCostume: Boolean?,
    val fullVariantSpriteKey: String?,
    val fullVariantVariantConfidence: Float?,
    val fullVariantExplanationMode: String?,
    val visualCostumeBeforePhase2: Boolean?,
    val phase2MayApply: Boolean?,
    val phase2CostumePredictions: List<Phase2CostumePredictionDiagnostic>?
) {
    companion object {
        private const val MAX_LABEL_LENGTH = 64
        private const val MAX_SPRITE_KEY_LENGTH = 80
        private const val MAX_PHASE2_PREDICTIONS = 2

        internal fun from(trace: VariantDecisionTrace?, input: QaVariantPhase2Input?) =
            QaVariantStageDiagnostics(
                classifierVariantType = trace?.classifierVariantType?.take(MAX_LABEL_LENGTH),
                classifierIsCostume = trace?.classifierCostume,
                classifierSpriteKey = trace?.classifierSpriteKey?.take(MAX_SPRITE_KEY_LENGTH),
                classifierScore = trace?.classifierScore,
                classifierVariantMargin = trace?.classifierVariantMargin,
                classifierBestBaseScore = trace?.classifierBestBaseScore,
                classifierBestNonBaseScore = trace?.classifierBestNonBaseScore,
                classifierBestNonBaseType = trace?.classifierBestNonBaseType?.take(MAX_LABEL_LENGTH),
                classifierRescueKind = trace?.classifierRescueKind?.take(MAX_LABEL_LENGTH),
                fullVariantIsCostume = trace?.fullVariantCostume,
                fullVariantSpriteKey = trace?.fullVariantSpriteKey?.take(MAX_SPRITE_KEY_LENGTH),
                fullVariantVariantConfidence = trace?.fullVariantVariantConfidence,
                fullVariantExplanationMode = trace?.fullVariantExplanationMode?.take(MAX_LABEL_LENGTH),
                visualCostumeBeforePhase2 = input?.visualCostumeBeforePhase2,
                phase2MayApply = input?.mayApply,
                phase2CostumePredictions = input?.result?.predictions.orEmpty()
                    .asSequence()
                    .filter { it.target == "hasCostume" }
                    .take(MAX_PHASE2_PREDICTIONS)
                    .map(Phase2CostumePredictionDiagnostic::from)
                    .toList()
                    .takeIf { it.isNotEmpty() }
            )
    }
}

data class Phase2CostumePredictionDiagnostic(
    val source: String,
    val predictedValue: Boolean,
    val confidence: Float,
    val margin: Float,
    val positiveScore: Float,
    val negativeScore: Float,
    val positiveCount: Int?,
    val negativeCount: Int?,
    val passedThreshold: Boolean,
    val decisionCapable: Boolean,
    val capabilityReason: String
) {
    companion object {
        private const val MAX_SOURCE_LENGTH = 32
        private const val MAX_CAPABILITY_REASON_LENGTH = 48

        internal fun from(prediction: Phase2VariantClassifier.Prediction) =
            Phase2CostumePredictionDiagnostic(
                source = prediction.source.take(MAX_SOURCE_LENGTH),
                predictedValue = prediction.predictedValue,
                confidence = prediction.confidence,
                margin = prediction.margin,
                positiveScore = prediction.positiveScore,
                negativeScore = prediction.negativeScore,
                positiveCount = prediction.capability.positiveCount,
                negativeCount = prediction.capability.negativeCount,
                passedThreshold = prediction.passedThreshold,
                decisionCapable = prediction.capability.decisionCapable,
                capabilityReason = prediction.capability.reason.code.take(MAX_CAPABILITY_REASON_LENGTH)
            )
    }
}

internal object QaVariantDiagnosticsPolicy {
    fun isEnabled(buildType: String): Boolean = buildType == "qa"
}

data class OcrFrameResult(
    val pokemon: PokemonData,
    val diagnostic: FrameDiagnostic,
    /** UI-only witness for guarded routing; never establishes species authority. */
    val detailStorageUiCorroborated: Boolean = false
)
