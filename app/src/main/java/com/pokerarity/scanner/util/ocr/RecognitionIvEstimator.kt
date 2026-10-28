package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.IvSolveDetails
import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
import com.pokerarity.scanner.data.model.canonicalIvLevelAlternatives
import kotlin.math.roundToInt

/**
 * Produces bounded user-facing IV and level estimates from the same numeric
 * authority used during recognition. If IV solving cannot start, valid typed stardust
 * may still preserve a contiguous level-only window; IV output remains insufficient.
 */
internal object RecognitionIvEstimator {

    private const val MAX_IV_TOTAL = 45
    private const val PERCENT_SCALE = 100.0
    private const val HALF_LEVEL_STEP = 0.5
    private const val MAX_UNDERLYING_LEVEL = 49.5

    private data class EstimationInput(
        val snapshot: RecognitionSnapshot,
        val profiles: List<RecognitionSnapshot.Profile>,
        val maxHp: Int
    )

    fun estimate(
        snapshot: RecognitionSnapshot?,
        identity: RecognitionIdentity?,
        cp: Int?,
        maxHp: Int?,
        stardust: StardustLevelEvidence?
    ): IvSolveDetails {
        val input = createInput(snapshot, identity, maxHp)
        val witnesses = input?.let { collectWitnesses(it, cp, stardust) }.orEmpty()
        val result = when {
            input == null -> insufficient(cp, maxHp, stardust, stardustOnlyLevelBounds(stardust))
            witnesses.isEmpty() -> insufficient(cp, maxHp, stardust)
            else -> summarize(witnesses, cp, input.maxHp, stardust)
        }
        return result
    }

    private fun stardustOnlyLevelBounds(stardust: StardustLevelEvidence?): Pair<Float, Float>? {
        val levels = (stardust as? StardustLevelEvidence.Levels)?.levels
        val validLevels = levels
            ?.takeIf { it.isNotEmpty() }
            ?.takeIf { candidates -> candidates.all { it.isFinite() } }
            ?.takeIf { candidates -> candidates.all { it >= 1.0 && it <= MAX_UNDERLYING_LEVEL } }
            ?.takeIf { candidates -> candidates.all { it % HALF_LEVEL_STEP == 0.0 } }
        val possibleEffectiveLevels = validLevels
            ?.flatMap { listOf(it, it + 1.0) }
            ?.distinct()
            ?.sorted()
        return possibleEffectiveLevels?.takeIf { candidates ->
            candidates.zipWithNext().all { (first, next) -> next - first == HALF_LEVEL_STEP }
        }?.let { candidates ->
            candidates.first().toFloat() to candidates.last().toFloat()
        }
    }

    private fun createInput(
        snapshot: RecognitionSnapshot?,
        identity: RecognitionIdentity?,
        maxHp: Int?
    ): EstimationInput? {
        val authority = snapshot
        val knownIdentity = identity?.takeIf { it.speciesStatus == RecognitionSpeciesStatus.KNOWN }
        val species = knownIdentity?.canonicalSpecies?.takeIf { it.isNotBlank() }
        val input = if (authority == null || species == null) {
            null
        } else {
            val profiles = SameSpeciesFormDecision.profilesSupportedByIdentity(
                authority.forSpecies(species),
                requireNotNull(knownIdentity)
            )
            if (hasValidDomain(profiles, maxHp, authority.cpMultipliers)) {
                EstimationInput(authority, profiles, requireNotNull(maxHp))
            } else {
                null
            }
        }
        return input
    }

    private fun hasValidDomain(
        profiles: List<RecognitionSnapshot.Profile>,
        maxHp: Int?,
        cpMultipliers: Map<Double, Double>
    ): Boolean = profiles.isNotEmpty() && maxHp?.let { it > 0 } == true && cpMultipliers.isNotEmpty()

    private fun collectWitnesses(
        input: EstimationInput,
        cp: Int?,
        stardust: StardustLevelEvidence?
    ): List<ProfileTupleFeasibility.LevelWitness> = input.profiles.flatMap { profile ->
        val legal = ProfileTupleFeasibility.legalWitnesses(
            stats = profile.stats,
            cp = cp?.takeIf { it > 0 },
            maxHp = input.maxHp,
            cpMultipliers = input.snapshot.cpMultipliers
        )
        when (val intersection = ProfileTupleFeasibility.constrainToUnderlyingLevels(legal, stardust)) {
            is ProfileTupleFeasibility.StardustIntersection.Constrained -> intersection.witnesses
            ProfileTupleFeasibility.StardustIntersection.NotConstraining -> legal
            is ProfileTupleFeasibility.StardustIntersection.Conflict -> emptyList()
        }
    }

    private fun summarize(
        witnesses: List<ProfileTupleFeasibility.LevelWitness>,
        cp: Int?,
        maxHp: Int,
        stardust: StardustLevelEvidence?
    ): IvSolveDetails {
        val minPercent = percent(witnesses.minOf { it.minIvTotal })
        val maxPercent = percent(witnesses.maxOf { it.maxIvTotal })
        val candidateCount = witnesses.sumOf { it.ivTupleCount.toLong() }
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val levels = witnesses.map { it.effectiveLevel }.distinct().sorted()
        val contiguousLevels = levels.takeIf { candidates ->
            candidates.zipWithNext().all { (first, next) -> next - first == HALF_LEVEL_STEP }
        }
        val exact = minPercent == maxPercent

        return IvSolveDetails(
            ivExact = minPercent.takeIf { exact },
            ivMin = minPercent,
            ivMax = maxPercent,
            ivCandidateCount = candidateCount,
            levelMin = contiguousLevels?.firstOrNull()?.toFloat(),
            levelMax = contiguousLevels?.lastOrNull()?.toFloat(),
            levelAlternatives = canonicalIvLevelAlternatives(levels.map { it.toFloat() }),
            ivSolveMode = if (exact) IvSolveMode.EXACT else IvSolveMode.RANGE,
            ivSolveSignalsUsed = usedSignals(cp, maxHp, stardust)
        )
    }

    private fun insufficient(
        cp: Int?,
        maxHp: Int?,
        stardust: StardustLevelEvidence?,
        levelBounds: Pair<Float, Float>? = null
    ) = IvSolveDetails(
        ivCandidateCount = 0,
        ivSolveMode = IvSolveMode.INSUFFICIENT,
        levelMin = levelBounds?.first,
        levelMax = levelBounds?.second,
        ivSolveSignalsUsed = if (levelBounds != null) {
            listOf("POWER_UP_STARDUST")
        } else {
            usedSignals(cp, maxHp, stardust)
        }
    )

    private fun usedSignals(
        cp: Int?,
        maxHp: Int?,
        stardust: StardustLevelEvidence?
    ): List<String> = buildList {
        if (cp != null && cp > 0) add("CP")
        if (maxHp != null && maxHp > 0) add("MAX_HP")
        if (stardust.isConstrainingOrContradictory()) add("POWER_UP_STARDUST")
    }

    private fun percent(total: Int): Int =
        (total / MAX_IV_TOTAL.toDouble() * PERCENT_SCALE).roundToInt()

    private fun StardustLevelEvidence?.isConstrainingOrContradictory(): Boolean =
        this is StardustLevelEvidence.Levels ||
            this is StardustLevelEvidence.Invalid ||
            this is StardustLevelEvidence.Conflict
}
