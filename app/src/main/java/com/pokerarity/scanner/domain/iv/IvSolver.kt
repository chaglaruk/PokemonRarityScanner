package com.pokerarity.scanner.domain.iv

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PokemonBaseStats(val attack: Int, val defense: Int, val stamina: Int)

data class IvEvidence(
    val cp: Int? = null,
    val hp: Int? = null,
    val level: Double? = null,
    val levelRange: ClosedFloatingPointRange<Double>? = null,
    val stardust: Int? = null,
    val appraisalAttack: Int? = null,
    val appraisalDefense: Int? = null,
    val appraisalStamina: Int? = null,
    val starRating: AppraisalStarRating? = null
)

enum class AppraisalStarRating(val totalRange: IntRange) {
    ZERO(0..22),
    ONE(23..29),
    TWO(30..36),
    THREE(37..44),
    FOUR(45..45)
}

enum class IvSolveMode {
    EXACT,
    RANGE,
    INSUFFICIENT
}

data class IvCandidate(
    val attackIv: Int,
    val defenseIv: Int,
    val staminaIv: Int,
    val level: Double,
    val cp: Int,
    val hp: Int,
    val ivPercent: Int
) {
    val ivTotal: Int = attackIv + defenseIv + staminaIv
}

data class IvResult(
    val mode: IvSolveMode,
    val candidates: List<IvCandidate>,
    val confidence: Float,
    val signalsUsed: List<String>,
    val warnings: List<String> = emptyList()
) {
    val candidateCount: Int = candidates.size
    val exactCandidate: IvCandidate? = candidates.singleOrNull().takeIf { mode == IvSolveMode.EXACT }
    val ivPercentMin: Int? = candidates.minOfOrNull { it.ivPercent }
    val ivPercentMax: Int? = candidates.maxOfOrNull { it.ivPercent }
    val levelMin: Double? = candidates.minOfOrNull { it.level }
    val levelMax: Double? = candidates.maxOfOrNull { it.level }
}

object IvSolver {
    private val cpmByLevel = linkedMapOf(
        1.0 to 0.094, 1.5 to 0.135137432, 2.0 to 0.16639787, 2.5 to 0.192650919,
        3.0 to 0.21573247, 3.5 to 0.236572661, 4.0 to 0.25572005, 4.5 to 0.273530381,
        5.0 to 0.29024988, 5.5 to 0.306057378, 6.0 to 0.3210876, 6.5 to 0.335445036,
        7.0 to 0.34921268, 7.5 to 0.362457751, 8.0 to 0.3752356, 8.5 to 0.387592416,
        9.0 to 0.39956728, 9.5 to 0.411193551, 10.0 to 0.4225, 10.5 to 0.432926409,
        11.0 to 0.44310755, 11.5 to 0.453059959, 12.0 to 0.4627984, 12.5 to 0.472336093,
        13.0 to 0.48168495, 13.5 to 0.4908558, 14.0 to 0.49985844, 14.5 to 0.508701765,
        15.0 to 0.51739395, 15.5 to 0.525942511, 16.0 to 0.5343543, 16.5 to 0.542635738,
        17.0 to 0.5507927, 17.5 to 0.558830586, 18.0 to 0.5667545, 18.5 to 0.574569133,
        19.0 to 0.5822789, 19.5 to 0.589887907, 20.0 to 0.5974, 20.5 to 0.604823665,
        21.0 to 0.6121573, 21.5 to 0.619404122, 22.0 to 0.6265671, 22.5 to 0.633649143,
        23.0 to 0.64065295, 23.5 to 0.647580967, 24.0 to 0.65443563, 24.5 to 0.661219252,
        25.0 to 0.667934, 25.5 to 0.674581896, 26.0 to 0.6811649, 26.5 to 0.687684904,
        27.0 to 0.69414365, 27.5 to 0.70054287, 28.0 to 0.7068842, 28.5 to 0.713169109,
        29.0 to 0.7193991, 29.5 to 0.725575614, 30.0 to 0.7317, 30.5 to 0.734741009,
        31.0 to 0.7377695, 31.5 to 0.740785594, 32.0 to 0.74378943, 32.5 to 0.746781211,
        33.0 to 0.74976104, 33.5 to 0.752729087, 34.0 to 0.7556855, 34.5 to 0.758630368,
        35.0 to 0.76156384, 35.5 to 0.764486065, 36.0 to 0.76739717, 36.5 to 0.770297266,
        37.0 to 0.7731865, 37.5 to 0.776064962, 38.0 to 0.77893275, 38.5 to 0.781790055,
        39.0 to 0.784637, 39.5 to 0.787473608, 40.0 to 0.7903, 40.5 to 0.792803968,
        41.0 to 0.79530001, 41.5 to 0.797800015, 42.0 to 0.8003, 42.5 to 0.802799995,
        43.0 to 0.8053, 43.5 to 0.8078, 44.0 to 0.81029999, 44.5 to 0.812799985,
        45.0 to 0.81529999, 45.5 to 0.81779999, 46.0 to 0.82029999, 46.5 to 0.82279999,
        47.0 to 0.82529999, 47.5 to 0.82779999, 48.0 to 0.83029999, 48.5 to 0.83279999,
        49.0 to 0.83529999, 49.5 to 0.83779999, 50.0 to 0.84029999
    )

    private val regularStardustLevels = mapOf(
        200 to (1.0..2.5), 400 to (3.0..4.5), 600 to (5.0..6.5), 800 to (7.0..8.5),
        1000 to (9.0..10.5), 1300 to (11.0..12.5), 1600 to (13.0..14.5), 1900 to (15.0..16.5),
        2200 to (17.0..18.5), 2500 to (19.0..20.5), 3000 to (21.0..22.5), 3500 to (23.0..24.5),
        4000 to (25.0..26.5), 4500 to (27.0..28.5), 5000 to (29.0..30.5), 6000 to (31.0..32.5),
        7000 to (33.0..34.5), 8000 to (35.0..36.5), 9000 to (37.0..38.5), 10000 to (39.0..40.5),
        11000 to (41.0..42.5), 12000 to (43.0..44.5), 13000 to (45.0..46.5), 14000 to (47.0..48.5),
        15000 to (49.0..50.0)
    )

    fun calculateCp(stats: PokemonBaseStats, attackIv: Int, defenseIv: Int, staminaIv: Int, level: Double): Int {
        val cpm = cpmByLevel[level] ?: return 0
        val raw = (stats.attack + attackIv) *
            sqrt((stats.defense + defenseIv).toDouble()) *
            sqrt((stats.stamina + staminaIv).toDouble()) *
            cpm.pow(2.0) / 10.0
        return floor(raw).toInt().coerceAtLeast(10)
    }

    fun calculateHp(stats: PokemonBaseStats, staminaIv: Int, level: Double): Int {
        val cpm = cpmByLevel[level] ?: return 0
        return floor((stats.stamina + staminaIv) * cpm).toInt().coerceAtLeast(10)
    }

    fun solve(stats: PokemonBaseStats, evidence: IvEvidence): IvResult {
        val signals = evidence.signalsUsed()
        if (stats.attack <= 0 || stats.defense <= 0 || stats.stamina <= 0) {
            return insufficient(signals, "Base stats must be positive")
        }
        if (!evidence.hasUsefulConstraint()) {
            return insufficient(signals, "At least one CP, HP, level, stardust, or appraisal constraint is required")
        }

        val warnings = mutableListOf<String>()
        val levels = levelsFor(evidence, warnings)
        val attackRange = evidence.appraisalAttack?.let { it..it } ?: 0..15
        val defenseRange = evidence.appraisalDefense?.let { it..it } ?: 0..15
        val staminaRange = evidence.appraisalStamina?.let { it..it } ?: 0..15
        val candidates = mutableListOf<IvCandidate>()

        for (level in levels) {
            for (staminaIv in staminaRange) {
                val hp = calculateHp(stats, staminaIv, level)
                if (evidence.hp != null && hp != evidence.hp) continue

                for (attackIv in attackRange) {
                    for (defenseIv in defenseRange) {
                        val total = attackIv + defenseIv + staminaIv
                        if (evidence.starRating != null && total !in evidence.starRating.totalRange) continue

                        val cp = calculateCp(stats, attackIv, defenseIv, staminaIv, level)
                        if (evidence.cp != null && cp != evidence.cp) continue

                        candidates += IvCandidate(
                            attackIv = attackIv,
                            defenseIv = defenseIv,
                            staminaIv = staminaIv,
                            level = level,
                            cp = cp,
                            hp = hp,
                            ivPercent = ((total / 45.0) * 100.0).roundToInt()
                        )
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            warnings += "No IV candidates matched evidence"
            return IvResult(IvSolveMode.INSUFFICIENT, emptyList(), 0f, signals, warnings)
        }

        val mode = if (candidates.size == 1) IvSolveMode.EXACT else IvSolveMode.RANGE
        val confidence = min(1f, (if (mode == IvSolveMode.EXACT) 0.55f else 0.25f) + signals.size * 0.08f)
        return IvResult(mode, candidates, confidence, signals, warnings)
    }

    private fun levelsFor(evidence: IvEvidence, warnings: MutableList<String>): List<Double> {
        var levels = cpmByLevel.keys.toList()
        evidence.stardust?.let { dust ->
            val range = regularStardustLevels[dust]
            if (range == null) {
                warnings += "Unknown regular stardust cost $dust; level was not narrowed by stardust"
            } else {
                levels = levels.filter { it in range }
            }
        }
        evidence.levelRange?.let { range -> levels = levels.filter { it in range } }
        evidence.level?.let { exact -> levels = levels.filter { it == exact } }
        return levels
    }

    private fun IvEvidence.signalsUsed(): List<String> = buildList {
        if (cp != null) add("cp")
        if (hp != null) add("hp")
        if (level != null) add("level")
        if (levelRange != null) add("levelRange")
        if (stardust != null) add("stardust")
        if (appraisalAttack != null) add("appraisalAttack")
        if (appraisalDefense != null) add("appraisalDefense")
        if (appraisalStamina != null) add("appraisalStamina")
        if (starRating != null) add("starRating")
    }

    private fun IvEvidence.hasUsefulConstraint(): Boolean =
        cp != null || hp != null || level != null || levelRange != null || stardust != null ||
            appraisalAttack != null || appraisalDefense != null || appraisalStamina != null

    private fun insufficient(signals: List<String>, warning: String): IvResult =
        IvResult(IvSolveMode.INSUFFICIENT, emptyList(), 0f, signals, listOf(warning))
}
