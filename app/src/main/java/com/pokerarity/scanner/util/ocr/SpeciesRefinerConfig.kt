// Amaç: SpeciesRefiner'ın kullandığı sayısal eşik değerleri ve ağırlıkları dinamik olarak saklamak.
package com.pokerarity.scanner.util.ocr

/**
 * Configuration data class wrapping all numerical thresholds and weights used in [SpeciesRefiner].
 * This allows decoupling constants and future dynamic calibrations.
 */
data class SpeciesRefinerConfig(
    val weakNameConfidence: Double = 0.56,
    val nicknameScoreThreshold: Double = 0.40,
    val trustedRankScore: Double = 0.48,
    val anchorConfidence: Double = 0.32,
    val priorFloor: Double = 0.28,

    val weakWeights: Triple<Double, Double, Double> = Triple(0.16, 0.46, 0.22),
    val strongWeights: Triple<Double, Double, Double> = Triple(0.34, 0.40, 0.14),
    val observedWeight: Double = 0.08,
    val physicalWeight: Double = 0.10,

    val candyBonus: Double = 0.10,
    val candyExactBonus: Double = 0.26,
    val familyBonus: Double = 0.04,
    val shortExtensionBonus: Double = 0.12,

    val movePenalty: Double = 0.05,
    val nicknamePenalty: Double = 0.08,
    val profileMismatchPenalty: Double = 0.18,

    val priorNickname: Double = 0.10,
    val priorWeak: Double = 0.18,

    val profileMismatchScore: Double = 0.20,
    val arcDiffThreshold: Double = 10.0,

    val fitLockThreshold: Double = 0.32,
    val fitGap: Double = 0.12,
    val fitGapLarge: Double = 0.18,
    val fitGapSmall: Double = 0.08,
    val sizeGap: Double = 0.12,
    val sizeGapLarge: Double = 0.20,
    val totalGap: Double = 0.03,
    val totalGapSmall: Double = 0.02,
    val totalGapLarge: Double = 0.04,

    val familyFitOverrideMin: Double = 0.60,
    val nicknameFitOverrideMin: Double = 0.62,
    val candyAuthorityFit: Double = 0.45,
    val candyAuthorityTotal: Double = 0.25,
    val uniqueCandyFit: Double = 0.48,
) {
    companion object {
        fun default(): SpeciesRefinerConfig = SpeciesRefinerConfig()
    }
}
