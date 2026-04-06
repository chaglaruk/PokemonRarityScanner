package com.pokerarity.scanner.data.repository

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

object IvCostSolver {

    data class BaseStats(val atk: Int, val def: Int, val sta: Int)

    enum class PokemonState {
        REGULAR,
        LUCKY,
        SHADOW,
        PURIFIED,
        UNKNOWN
    }

    enum class SolveMode {
        EXACT,
        RANGE,
        INSUFFICIENT
    }

    data class Candidate(
        val level: Double,
        val ivAtk: Int,
        val ivDef: Int,
        val ivSta: Int,
        val state: PokemonState
    ) {
        val ivSum: Int get() = ivAtk + ivDef + ivSta
        val ivPercent: Int get() = (ivSum * 100) / 45
    }

    data class Result(
        val ivExact: Int?,
        val ivMin: Int?,
        val ivMax: Int?,
        val ivCandidateCount: Int,
        val levelMin: Double?,
        val levelMax: Double?,
        val ivSolveMode: SolveMode,
        val ivSolveSignalsUsed: List<String>,
        val candidates: List<Candidate> = emptyList(),
        // Arc diagnostic fields - help visualize arc usage policy
        val arcDetected: Boolean = false,
        val arcEstimatedLevel: Double? = null,
        val arcConfidence: Float = 0f,
        val arcAppliedToNarrow: Boolean = false,
        val arcIgnoredReason: String? = null,
        val candidateCountBeforeArc: Int = ivCandidateCount,
        val candidateCountAfterArc: Int = ivCandidateCount
    ) {
        val displayText: String?
            get() = when (ivSolveMode) {
                SolveMode.EXACT -> ivExact?.let { "$it%" }
                SolveMode.RANGE -> if (ivMin != null && ivMax != null) {
                    if (ivMin == ivMax) "$ivMin%" else "$ivMin% - $ivMax%"
                } else {
                    null
                }
                SolveMode.INSUFFICIENT -> null
            }
    }

    fun solve(
        species: String?,
        stats: BaseStats?,
        cp: Int?,
        hp: Int?,
        stardustCost: Int?,
        candyCost: Int?,
        arcLevel: Float?,
        stateHint: PokemonState
    ): Result {
        if (species.isNullOrBlank() || stats == null || cp == null || cp <= 0 || hp == null || hp <= 0) {
            return Result(
                ivExact = null,
                ivMin = null,
                ivMax = null,
                ivCandidateCount = 0,
                levelMin = null,
                levelMax = null,
                ivSolveMode = SolveMode.INSUFFICIENT,
                ivSolveSignalsUsed = buildSignalsUsed(cp, hp, stardustCost, candyCost, stateHint, false)
            )
        }

        // Validate candy against stardust-level expectations
        // If stardust determines a level range, candy must match that range
        // Otherwise, discard invalid candy to avoid false INSUFFICIENT
        val validatedCandyCost = if (stardustCost != null && candyCost != null) {
            val stardustLevels = halfLevels(1.0, 50.0)
                .filter { regularStardustCostForLevel(it) == stardustCost }
                .toList()
            
            if (stardustLevels.isNotEmpty()) {
                val expectedCandyCosts = stardustLevels
                    .flatMap { regularCandyCostsForLevel(it) }
                    .toSet()
                
                if (expectedCandyCosts.isNotEmpty() && 
                    !expectedCandyCosts.contains(candyCost) &&
                    !expectedCandyCosts.any { scaledCostSet(it, 1.0).contains(candyCost) }
                ) {
                    // Candy doesn't match stardust's level range - likely OCR error
                    // Use stardust alone instead
                    null
                } else {
                    candyCost
                }
            } else {
                candyCost
            }
        } else {
            candyCost
        }

        val candidateStates = when (stateHint) {
            PokemonState.UNKNOWN -> listOf(PokemonState.REGULAR, PokemonState.PURIFIED, PokemonState.LUCKY, PokemonState.SHADOW)
            else -> listOf(stateHint)
        }

        val solvePass = solveWithRelaxation(
            stats = stats,
            cp = cp,
            hp = hp,
            stardustCost = stardustCost,
            candyCost = validatedCandyCost,
            candidateStates = candidateStates
        )

        if (solvePass.candidates.isEmpty()) {
            return Result(
                ivExact = null,
                ivMin = null,
                ivMax = null,
                ivCandidateCount = 0,
                levelMin = null,
                levelMax = null,
                ivSolveMode = SolveMode.INSUFFICIENT,
                ivSolveSignalsUsed = buildSignalsUsed(
                    cp = cp,
                    hp = hp,
                    stardustCost = null,
                    candyCost = null,
                    stateHint = stateHint,
                    arcApplied = false
                )
            )
        }

        val arcFiltered = applyArcPolicy(
            baseCandidates = solvePass.candidates,
            arcLevel = arcLevel,
            usedStardustCost = solvePass.usedStardustCost,
            usedCandyCost = solvePass.usedCandyCost
        )

        val finalCandidates = arcFiltered.narrowedCandidates
        val uniquePercents = finalCandidates.map { it.ivPercent }.distinct().sorted()
        val levelValues = finalCandidates.map { it.level }.sorted()
        val finalMode = if (uniquePercents.size == 1) SolveMode.EXACT else SolveMode.RANGE

        return Result(
            ivExact = if (finalMode == SolveMode.EXACT) uniquePercents.singleOrNull() else null,
            ivMin = uniquePercents.minOrNull(),
            ivMax = uniquePercents.maxOrNull(),
            ivCandidateCount = finalCandidates.size,
            levelMin = levelValues.minOrNull(),
            levelMax = levelValues.maxOrNull(),
            ivSolveMode = finalMode,
            ivSolveSignalsUsed = buildSignalsUsed(
                cp = cp,
                hp = hp,
                stardustCost = solvePass.usedStardustCost,
                candyCost = solvePass.usedCandyCost,
                stateHint = stateHint,
                arcApplied = arcFiltered.arcApplied
            ),
            candidates = finalCandidates,
            arcDetected = arcFiltered.arcDetected,
            arcEstimatedLevel = arcFiltered.arcEstimatedLevel,
            arcConfidence = arcFiltered.arcConfidence,
            arcAppliedToNarrow = arcFiltered.arcApplied,
            arcIgnoredReason = arcFiltered.arcIgnoredReason,
            candidateCountBeforeArc = solvePass.candidates.size,
            candidateCountAfterArc = finalCandidates.size
        )
    }

    private data class SolvePass(
        val candidates: List<Candidate>,
        val usedStardustCost: Int?,
        val usedCandyCost: Int?
    )

    private fun solveWithRelaxation(
        stats: BaseStats,
        cp: Int,
        hp: Int,
        stardustCost: Int?,
        candyCost: Int?,
        candidateStates: List<PokemonState>
    ): SolvePass {
        val passes = listOf(
            stardustCost to candyCost,
            stardustCost to null,
            null to candyCost,
            null to null
        ).distinct()

        passes.forEach { (dust, candy) ->
            val candidates = solvePass(
                stats = stats,
                cp = cp,
                hp = hp,
                stardustCost = dust,
                candyCost = candy,
                candidateStates = candidateStates
            )
            if (candidates.isNotEmpty()) {
                return SolvePass(
                    candidates = candidates,
                    usedStardustCost = dust,
                    usedCandyCost = candy
                )
            }
        }

        return SolvePass(emptyList(), null, null)
    }

    private fun solvePass(
        stats: BaseStats,
        cp: Int,
        hp: Int,
        stardustCost: Int?,
        candyCost: Int?,
        candidateStates: List<PokemonState>
    ): List<Candidate> {
        val baseCandidates = linkedSetOf<Candidate>()
        candidateStates.forEach { state ->
            val levelCandidates = candidateLevels(stardustCost, candyCost, state)
            if (levelCandidates.isEmpty()) return@forEach

            for (level in levelCandidates) {
                val cpm = CPM_MAP[level] ?: continue
                for (ivSta in 0..15) {
                    val calcHp = floor((stats.sta + ivSta) * cpm).toInt()
                    if (calcHp != hp) continue
                    for (ivAtk in 0..15) {
                        for (ivDef in 0..15) {
                            val calcCp = calculateCp(stats, ivAtk, ivDef, ivSta, level)
                            if (calcCp == cp) {
                                baseCandidates += Candidate(level, ivAtk, ivDef, ivSta, state)
                            }
                        }
                    }
                }
            }
        }
        return baseCandidates.toList()
    }

    private fun candidateLevels(stardustCost: Int?, candyCost: Int?, state: PokemonState): List<Double> {
        return halfLevels(1.0, 50.0)
            .filter { level ->
                matchesObservedStardust(level, stardustCost, state) &&
                    matchesObservedCandy(level, candyCost, state)
            }
            .toList()
    }

    private data class ArcPolicy(
        val narrowedCandidates: List<Candidate>,
        val arcDetected: Boolean,
        val arcEstimatedLevel: Double?,
        val arcConfidence: Float,
        val arcApplied: Boolean,
        val arcIgnoredReason: String? = null
    )

    /**
     * Apply arc signal with a principled, confidence-aware policy:
     * - Arc is a secondary narrowing signal only
     * - Arc must never be enough by itself to force EXACT
     * - Arc only narrows when confidence is sufficient and it doesn't conflict with stronger signals
     * - Arc is ignored if it conflicts with stardust/candy derived levels
     * - Clear audit trail shows why arc was accepted/rejected
     */
    private fun applyArcPolicy(
        baseCandidates: List<Candidate>,
        arcLevel: Float?,
        usedStardustCost: Int?,
        usedCandyCost: Int?
    ): ArcPolicy {
        // Arc not present
        if (arcLevel == null || arcLevel.isNaN()) {
            return ArcPolicy(
                narrowedCandidates = baseCandidates,
                arcDetected = false,
                arcEstimatedLevel = null,
                arcConfidence = 0f,
                arcApplied = false,
                arcIgnoredReason = "Arc not detected"
            )
        }

        // Arc present - estimate level from arc progress (0.0-1.0 -> level 1-50)
        val arcEstimatedLevel = (arcLevel.toDouble() * 49.0 + 1.0).coerceIn(1.0, 50.0)
        
        // POLICY: Only use arc if primary constraints (stardust/candy) were actually used
        // This prevents arc from being the only signal
        val hasStrongPrimarySignals = usedStardustCost != null || usedCandyCost != null
        
        if (!hasStrongPrimarySignals) {
            return ArcPolicy(
                narrowedCandidates = baseCandidates,
                arcDetected = true,
                arcEstimatedLevel = arcEstimatedLevel,
                arcConfidence = 0.3f,  // Arc alone is low confidence
                arcApplied = false,
                arcIgnoredReason = "Arc not applied: No strong primary signals (stardust/candy) to validate against"
            )
        }

        // POLICY: Arc narrows by ±1.0 level — tight window for precise matching
        val arcNarrowWindow = 1.0
        val narrowedByArc = baseCandidates.filter { 
            abs(it.level - arcEstimatedLevel) <= arcNarrowWindow 
        }

        // No candidates match arc estimate — try wider ±2.0 fallback
        if (narrowedByArc.isEmpty()) {
            val widerNarrowed = baseCandidates.filter {
                abs(it.level - arcEstimatedLevel) <= 2.0
            }
            if (widerNarrowed.isNotEmpty() && widerNarrowed.size < baseCandidates.size) {
                return ArcPolicy(
                    narrowedCandidates = widerNarrowed,
                    arcDetected = true,
                    arcEstimatedLevel = arcEstimatedLevel,
                    arcConfidence = 0.50f,
                    arcApplied = true,
                    arcIgnoredReason = null
                )
            }
            return ArcPolicy(
                narrowedCandidates = baseCandidates,
                arcDetected = true,
                arcEstimatedLevel = arcEstimatedLevel,
                arcConfidence = 0.2f,
                arcApplied = false,
                arcIgnoredReason = "Arc ignored: Estimated level $arcEstimatedLevel does not match any candidate within ±${arcNarrowWindow}"
            )
        }

        // Arc narrows candidates — always apply if it reduces the set
        val reductionPercent = (1.0 - narrowedByArc.size.toDouble() / baseCandidates.size) * 100.0
        val arcConfidence = when {
            reductionPercent > 50.0 -> 0.95f
            reductionPercent > 30.0 -> 0.80f
            reductionPercent > 10.0 -> 0.60f
            else -> 0.35f
        }

        val applyArc = narrowedByArc.size < baseCandidates.size

        return if (applyArc) {
            ArcPolicy(
                narrowedCandidates = narrowedByArc,
                arcDetected = true,
                arcEstimatedLevel = arcEstimatedLevel,
                arcConfidence = arcConfidence,
                arcApplied = true,
                arcIgnoredReason = null
            )
        } else {
            val reason = when {
                arcConfidence < 0.50f -> "Arc narrowing too weak (${reductionPercent.toInt()}% reduction, confidence=${String.format("%.2f", arcConfidence)})"
                narrowedByArc.size >= baseCandidates.size -> "Arc does not narrow candidates"
                else -> "Arc confidence insufficient"
            }
            ArcPolicy(
                narrowedCandidates = baseCandidates,
                arcDetected = true,
                arcEstimatedLevel = arcEstimatedLevel,
                arcConfidence = arcConfidence,
                arcApplied = false,
                arcIgnoredReason = reason
            )
        }
    }

    private fun buildSignalsUsed(
        cp: Int?,
        hp: Int?,
        stardustCost: Int?,
        candyCost: Int?,
        stateHint: PokemonState,
        arcApplied: Boolean
    ): List<String> = buildList {
        if (cp != null) add("cp")
        if (hp != null) add("hp")
        if (stardustCost != null) add("stardust")
        if (candyCost != null) add("candy")
        if (stateHint != PokemonState.UNKNOWN) add("state")
        if (arcApplied) add("arc")
    }

    private fun matchesObservedStardust(level: Double, observedCost: Int?, state: PokemonState): Boolean {
        if (observedCost == null) return true
        val regularCost = regularStardustCostForLevel(level) ?: return false
        return adjustedObservedStardustCosts(regularCost, state).contains(observedCost)
    }

    private fun matchesObservedCandy(level: Double, observedCost: Int?, state: PokemonState): Boolean {
        if (observedCost == null) return true
        val regularCosts = regularCandyCostsForLevel(level)
        if (regularCosts.isEmpty()) return false
        return regularCosts.any { adjustedObservedCandyCosts(it, state).contains(observedCost) }
    }

    private fun regularStardustCostForLevel(level: Double): Int? {
        return REGULAR_STARDUST_TO_LEVEL.entries
            .firstOrNull { level in it.value }
            ?.key
    }

    private fun regularCandyCostsForLevel(level: Double): Set<Int> = when {
        level <= 10.5 -> setOf(1)
        level <= 20.5 -> setOf(2)
        level <= 25.5 -> setOf(3)
        level <= 30.5 -> setOf(4)
        level <= 32.5 -> setOf(6)
        level <= 34.5 -> setOf(8)
        level <= 36.5 -> setOf(10)
        level <= 38.5 -> setOf(12)
        level <= 39.5 -> setOf(15)
        level <= 41.5 -> setOf(10)
        level <= 43.5 -> setOf(12)
        level <= 45.5 -> setOf(15)
        level <= 47.5 -> setOf(17)
        level <= 49.5 -> setOf(20)
        else -> emptySet()
    }

    private fun adjustedObservedStardustCosts(regularCost: Int, state: PokemonState): Set<Int> = when (state) {
        PokemonState.LUCKY -> setOf(regularCost / 2)
        PokemonState.SHADOW -> scaledCostSet(regularCost, 1.2)
        PokemonState.PURIFIED -> scaledCostSet(regularCost, 0.9)
        PokemonState.REGULAR,
        PokemonState.UNKNOWN -> setOf(regularCost)
    }

    private fun adjustedObservedCandyCosts(regularCost: Int, state: PokemonState): Set<Int> = when (state) {
        PokemonState.LUCKY -> setOf(regularCost)
        PokemonState.SHADOW -> scaledCostSet(regularCost, 1.2)
        PokemonState.PURIFIED -> scaledCostSet(regularCost, 0.9)
        PokemonState.REGULAR,
        PokemonState.UNKNOWN -> setOf(regularCost)
    }

    private fun scaledCostSet(base: Int, multiplier: Double): Set<Int> {
        val raw = base * multiplier
        return setOf(floor(raw).toInt(), raw.roundToInt(), ceil(raw).toInt())
            .filter { it > 0 }
            .toSet()
    }

    private fun calculateCp(
        stats: BaseStats,
        ivAtk: Int,
        ivDef: Int,
        ivSta: Int,
        level: Double
    ): Int {
        val cpm = CPM_MAP[level] ?: return 0
        val raw = ((stats.atk + ivAtk) * sqrt((stats.def + ivDef).toDouble()) * sqrt((stats.sta + ivSta).toDouble()) * cpm * cpm) / 10.0
        return maxOf(10, floor(raw).toInt())
    }

    private fun halfLevels(start: Double, end: Double): Sequence<Double> =
        generateSequence(start) { it + 0.5 }.takeWhile { it <= end }

    private val REGULAR_STARDUST_TO_LEVEL = linkedMapOf(
        200 to (1.0..2.5),
        400 to (3.0..4.5),
        600 to (5.0..6.5),
        800 to (7.0..8.5),
        1000 to (9.0..10.5),
        1300 to (11.0..12.5),
        1600 to (13.0..14.5),
        1900 to (15.0..16.5),
        2200 to (17.0..18.5),
        2500 to (19.0..20.5),
        3000 to (21.0..22.5),
        3500 to (23.0..24.5),
        4000 to (25.0..26.5),
        4500 to (27.0..28.5),
        5000 to (29.0..30.5),
        6000 to (31.0..32.5),
        7000 to (33.0..34.5),
        8000 to (35.0..36.5),
        9000 to (37.0..38.5),
        10000 to (39.0..40.5),
        11000 to (41.0..42.5),
        12000 to (43.0..44.5),
        13000 to (45.0..46.5),
        14000 to (47.0..48.5),
        15000 to (49.0..50.0)
    )

    private val CPM_MAP = mapOf(
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
}
