package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.OcrConfidenceReasons
import com.pokerarity.scanner.data.model.OcrFieldConfidence
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VariantDecisionTrace
import com.pokerarity.scanner.data.model.VisualFeatures

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
    val scanDecision: ScanDecision? = null
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
    val selected: PokemonSummary
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
    val fullVariantClass: String?
) {
    companion object {
        fun from(features: VisualFeatures?, trace: VariantDecisionTrace?): VariantVisualSummary? {
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
                fullVariantClass = trace?.fullVariantClass
            )
        }
    }
}

data class OcrFrameResult(
    val pokemon: PokemonData,
    val diagnostic: FrameDiagnostic
)
