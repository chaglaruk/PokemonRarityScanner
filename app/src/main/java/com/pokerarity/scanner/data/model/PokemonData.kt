package com.pokerarity.scanner.data.model

import com.pokerarity.scanner.util.ocr.SpeciesResolverTrace
import com.pokerarity.scanner.util.ocr.ScanDecision
import java.util.Date

/**
 * Data extracted from a Pokemon GO screenshot.
 */
data class PokemonData(
    val cp: Int?,
    val hp: Int?,
    val maxHp: Int?,
    val name: String?,
    val realName: String?, // From candy name for verification
    val candyName: String?,
    val megaEnergy: Int?,
    val weight: Float?,
    val height: Float?,
    val gender: String? = null, // "Male", "Female", "Genderless"
    val stardust: Int?,
    val arcLevel: Float? = null, // % of arc filled (0.0 - 1.0)
    val caughtDate: Date?,
    val rawOcrText: String = "",
    val fullVariantMatch: FullVariantMatch? = null,
    val powerUpCandyCost: Int? = null,
    val powerUpCandySource: String? = null,
    val powerUpStardustSource: String? = null,
    val appraisalAttack: Int? = null,
    val appraisalDefense: Int? = null,
    val appraisalStamina: Int? = null,
    val appraisalConfidence: Float? = null,
    val arcEstimatedLevel: Float? = null,
    val arcSource: String? = null,
    val ocrDiagnosticsDir: String? = null,
    val ocrDiagnosticsFiles: Map<String, String> = emptyMap(),
    val ocrConfidenceReasons: OcrConfidenceReasons? = null,
    val variantDecisionTrace: VariantDecisionTrace? = null,
    val speciesResolverTrace: SpeciesResolverTrace? = null,
    val scanDecision: ScanDecision? = null,
    /**
     * Phase 2E explicit recognition authority. After this contract exists, recognition
     * identity is represented here — never inferred from the nullable compatibility
     * strings [name]/[realName], and weak classifier evidence may not override it.
     * Null only on legacy/imported results predating the contract.
     */
    val recognitionIdentity: RecognitionIdentity? = null,
    @Transient val recognitionObservation: com.pokerarity.scanner.util.ocr.RecognitionObservation? = null
)
