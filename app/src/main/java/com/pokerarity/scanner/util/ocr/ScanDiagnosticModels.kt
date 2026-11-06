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
    val classifierVariantType: String? = null,
    val classifierIsCostume: Boolean? = null,
    val classifierSpriteKey: String? = null,
    val classifierScore: Float? = null,
    val classifierVariantMargin: Float? = null,
    val classifierBestBaseScore: Float? = null,
    val classifierBestNonBaseScore: Float? = null,
    val classifierBestNonBaseType: String? = null,
    val classifierRescueKind: String? = null,
    val fullVariantIsCostume: Boolean? = null,
    val fullVariantSpriteKey: String? = null,
    val fullVariantVariantConfidence: Float? = null,
    val fullVariantExplanationMode: String? = null,
    val visualCostumeBeforePhase2: Boolean? = null,
    val phase2MayApply: Boolean? = null,
    val phase2CostumePredictions: List<Phase2CostumePredictionDiagnostic>? = null,
    /** Present only in the isolated QA build; omitted from ordinary debug/release summaries. */
    val costumeDecisionDiagnostics: CostumeDecisionDiagnostics? = null
) {
    companion object {
        internal fun from(
            features: VisualFeatures?,
            trace: VariantDecisionTrace?,
            includeQaDiagnostics: Boolean = QaVariantDiagnosticsPolicy.isEnabled(BuildConfig.BUILD_TYPE),
            visualCostumeBeforePhase2: Boolean? = null,
            phase2MayApply: Boolean? = null,
            phase2Result: Phase2VariantClassifier.Result? = null
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
                classifierVariantType = trace?.classifierVariantType
                    ?.takeIf { includeQaDiagnostics }
                    ?.take(64),
                classifierIsCostume = trace?.classifierCostume.takeIf { includeQaDiagnostics },
                classifierSpriteKey = trace?.classifierSpriteKey
                    ?.takeIf { includeQaDiagnostics }
                    ?.take(80),
                classifierScore = trace?.classifierScore.takeIf { includeQaDiagnostics },
                classifierVariantMargin = trace?.classifierVariantMargin.takeIf { includeQaDiagnostics },
                classifierBestBaseScore = trace?.classifierBestBaseScore.takeIf { includeQaDiagnostics },
                classifierBestNonBaseScore = trace?.classifierBestNonBaseScore.takeIf { includeQaDiagnostics },
                classifierBestNonBaseType = trace?.classifierBestNonBaseType
                    ?.takeIf { includeQaDiagnostics }
                    ?.take(64),
                classifierRescueKind = trace?.classifierRescueKind
                    ?.takeIf { includeQaDiagnostics }
                    ?.take(64),
                fullVariantIsCostume = trace?.fullVariantCostume.takeIf { includeQaDiagnostics },
                fullVariantSpriteKey = trace?.fullVariantSpriteKey
                    ?.takeIf { includeQaDiagnostics }
                    ?.take(80),
                fullVariantVariantConfidence = trace?.fullVariantVariantConfidence
                    .takeIf { includeQaDiagnostics },
                fullVariantExplanationMode = trace?.fullVariantExplanationMode
                    ?.takeIf { includeQaDiagnostics }
                    ?.take(64),
                visualCostumeBeforePhase2 = visualCostumeBeforePhase2.takeIf { includeQaDiagnostics },
                phase2MayApply = phase2MayApply.takeIf { includeQaDiagnostics },
                phase2CostumePredictions = if (includeQaDiagnostics) {
                    phase2Result?.predictions.orEmpty()
                        .asSequence()
                        .filter { it.target == "hasCostume" }
                        .take(2)
                        .map(Phase2CostumePredictionDiagnostic::from)
                        .toList()
                } else {
                    null
                },
                costumeDecisionDiagnostics = safeFeatures.costumeDecisionDiagnostics.takeIf { includeQaDiagnostics }
            )
        }
    }
}

/** Fixed-size Phase 2 evidence for separating a classifier promotion from an earlier merge. */
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
        internal fun from(prediction: Phase2VariantClassifier.Prediction) =
            Phase2CostumePredictionDiagnostic(
                source = prediction.source.take(32),
                predictedValue = prediction.predictedValue,
                confidence = prediction.confidence,
                margin = prediction.margin,
                positiveScore = prediction.positiveScore,
                negativeScore = prediction.negativeScore,
                positiveCount = prediction.capability.positiveCount,
                negativeCount = prediction.capability.negativeCount,
                passedThreshold = prediction.passedThreshold,
                decisionCapable = prediction.capability.decisionCapable,
                capabilityReason = prediction.capability.reason.code.take(48)
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
