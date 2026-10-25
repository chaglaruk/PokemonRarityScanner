package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.IvSolveDetails
import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
import kotlin.math.roundToInt

/**
 * Produces bounded user-facing IV and level estimates from the same numeric
 * authority used during recognition. Unknown identity, missing max HP, invalid
 * stardust, or contradictory evidence stays explicitly insufficient.
 */
internal object RecognitionIvEstimator {

    fun estimate(
        snapshot: RecognitionSnapshot?,
        identity: RecognitionIdentity?,
        cp: Int?,
        maxHp: Int?,
        stardust: StardustLevelEvidence?
    ): IvSolveDetails {
        val species = identity?.takeIf { it.speciesStatus == RecognitionSpeciesStatus.KNOWN }
            ?.canonicalSpecies?.takeIf { it.isNotBlank() }
            ?: return insufficient(cp, maxHp, stardust)
        val authority = snapshot ?: return insufficient(cp, maxHp, stardust)
        val profiles = authority.forSpecies(species)
        if (profiles.isEmpty() || maxHp == null || maxHp <= 0 || authority.cpMultipliers.isEmpty()) {
            return insufficient(cp, maxHp, stardust)
        }

        val witnesses = profiles.flatMap { profile ->
            val legal = ProfileTupleFeasibility.legalWitnesses(
                stats = profile.stats,
                cp = cp?.takeIf { it > 0 },
                maxHp = maxHp,
                cpMultipliers = authority.cpMultipliers
            )
            when (val intersection = ProfileTupleFeasibility.constrainToUnderlyingLevels(legal, stardust)) {
                is ProfileTupleFeasibility.StardustIntersection.Constrained -> intersection.witnesses
                ProfileTupleFeasibility.StardustIntersection.NotConstraining -> legal
                is ProfileTupleFeasibility.StardustIntersection.Conflict -> emptyList()
            }
        }
        if (witnesses.isEmpty()) return insufficient(cp, maxHp, stardust)

        val minPercent = witnesses.minOf { percent(it.minIvTotal) }
        val maxPercent = witnesses.maxOf { percent(it.maxIvTotal) }
        val candidateCount = witnesses.sumOf { it.ivTupleCount.toLong() }
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val levels = witnesses.map { it.effectiveLevel }.distinct().sorted()
        // The UI contract only has min/max. Do not present that as one level range
        // when typed stardust leaves a gap between separate legal windows.
        val contiguousLevelRange = levels.takeIf { candidates ->
            candidates.zipWithNext().all { (first, next) -> next - first == HALF_LEVEL_STEP }
        }
        val signals = buildList {
            if (cp != null && cp > 0) add("CP")
            add("MAX_HP")
            if (stardust.isConstrainingOrContradictory()) add("POWER_UP_STARDUST")
        }
        val exact = minPercent == maxPercent

        return IvSolveDetails(
            ivExact = minPercent.takeIf { exact },
            ivMin = minPercent,
            ivMax = maxPercent,
            ivCandidateCount = candidateCount,
            levelMin = contiguousLevelRange?.firstOrNull()?.toFloat(),
            levelMax = contiguousLevelRange?.lastOrNull()?.toFloat(),
            ivSolveMode = if (exact) IvSolveMode.EXACT else IvSolveMode.RANGE,
            ivSolveSignalsUsed = signals
        )
    }

    private fun insufficient(cp: Int?, maxHp: Int?, stardust: StardustLevelEvidence?) = IvSolveDetails(
        ivCandidateCount = 0,
        ivSolveMode = IvSolveMode.INSUFFICIENT,
        ivSolveSignalsUsed = buildList {
            if (cp != null && cp > 0) add("CP")
            if (maxHp != null && maxHp > 0) add("MAX_HP")
            if (stardust.isConstrainingOrContradictory()) add("POWER_UP_STARDUST")
        }
    )

    private fun percent(total: Int): Int = (total / 45.0 * 100.0).roundToInt()

    private const val HALF_LEVEL_STEP = 0.5

    private fun StardustLevelEvidence?.isConstrainingOrContradictory(): Boolean =
        this is StardustLevelEvidence.Levels ||
            this is StardustLevelEvidence.Invalid ||
            this is StardustLevelEvidence.Conflict
}
