package com.pokerarity.scanner.util.ocr

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Phase 3B bounded tuple-feasibility authority (plan §7.3).
 *
 * The one recognition-domain enumerator of legal same-witness tuples
 * `(profile row, underlying level, witnessed/effective level, IVs)` over the
 * RecognitionSnapshot CPM domain. All numeric recognition consumers (the common
 * candidate/profile evaluation and profile-fit reconciliation) delegate here so a
 * snapshot-backed observation can never carry different numeric semantics in
 * different consumers. Enumeration is bounded to ONE profile row's stats, the
 * provided level domain, and the 0..15 IV ranges — never an all-species sweep.
 *
 * Level semantics (the highest-risk part of this contract):
 * - POWER UP stardust describes the UNDERLYING/base level (Phase 3A oracle output);
 * - displayed CP/maxHP are rendered at the WITNESSED/effective level, which equals
 *   the underlying level, or underlying + 1 when an active Best Buddy boost is
 *   possible. Best Buddy is not independently established as a positive state, so
 *   both interpretations are preserved wherever mathematically legal;
 * - the underlying level is capped at 50.0: no visible POWER UP cost can invent a
 *   base level of 50.0 or above, while effective level 51.0 remains reachable as
 *   underlying 50.0 with Best Buddy when no stardust constrains it.
 */
@Suppress("MagicNumber")
internal object ProfileTupleFeasibility {

    private const val MIN_LEVEL = 1.0

    /** Highest legal underlying level; the POWER UP tier domain stops below 50.0. */
    const val MAX_UNDERLYING_LEVEL = 50.0

    private const val MIN_DISPLAY_VALUE = 10
    private const val MAX_IV = 15

    /**
     * Bounded proof that at least one IV tuple jointly satisfies every applied
     * numeric constraint at this level pair. [ivTupleCount] counts the full IV
     * tuples (stamina, plus attack/defense when CP is observed); [minStaminaIv]/
     * [maxStaminaIv]/[staminaIvCount] describe the stamina IVs that participate in
     * at least one such tuple. Counts only — no IV candidate sets are persisted.
     */
    data class LevelWitness(
        val underlyingLevel: Double,
        val effectiveLevel: Double,
        val bestBuddyOffset: Boolean,
        val staminaIvCount: Int,
        val minStaminaIv: Int,
        val maxStaminaIv: Int,
        val ivTupleCount: Int
    )

    /**
     * Optional exact-IV seam for later appraisal authority (Phase 3D). Production
     * Phase 3B passes none; pure tests may exercise the seam. Legacy untyped
     * `PokemonData.appraisal*` fields must not be routed here as hard authority.
     */
    data class ExactIvConstraints(
        val attack: Int? = null,
        val defense: Int? = null,
        val stamina: Int? = null
    )

    /**
     * Every legal same-witness tuple for one profile row: CP/maxHP (maxHP alone
     * when CP is absent) must be jointly achievable by one IV tuple at the SAME
     * effective level, projected onto the legal underlying/effective level pairs.
     * Empty when maxHP is missing (CP alone is never a same-witness base) or when
     * no joint IV witness exists.
     */
    fun legalWitnesses(
        stats: RecognitionSnapshot.ProfileStats,
        cp: Int?,
        maxHp: Int?,
        cpMultipliers: Map<Double, Double>,
        appraisal: ExactIvConstraints? = null
    ): List<LevelWitness> {
        if (maxHp == null) return emptyList()
        val witnesses = mutableListOf<LevelWitness>()
        for (effectiveLevel in cpMultipliers.keys.sorted()) {
            val cpm = cpMultipliers.getValue(effectiveLevel)
            val ivs = jointIvWitness(stats, cp, maxHp, cpm, appraisal) ?: continue
            // Normal interpretation: underlying level == effective level.
            if (effectiveLevel <= MAX_UNDERLYING_LEVEL) {
                witnesses += LevelWitness(
                    underlyingLevel = effectiveLevel,
                    effectiveLevel = effectiveLevel,
                    bestBuddyOffset = false,
                    staminaIvCount = ivs.staminaIvCount,
                    minStaminaIv = ivs.minStaminaIv,
                    maxStaminaIv = ivs.maxStaminaIv,
                    ivTupleCount = ivs.ivTupleCount
                )
            }
            // Best Buddy interpretation: effective level == underlying level + 1.
            val buddyUnderlying = effectiveLevel - 1.0
            if (buddyUnderlying >= MIN_LEVEL) {
                witnesses += LevelWitness(
                    underlyingLevel = buddyUnderlying,
                    effectiveLevel = effectiveLevel,
                    bestBuddyOffset = true,
                    staminaIvCount = ivs.staminaIvCount,
                    minStaminaIv = ivs.minStaminaIv,
                    maxStaminaIv = ivs.maxStaminaIv,
                    ivTupleCount = ivs.ivTupleCount
                )
            }
        }
        return witnesses
    }

    /**
     * How the Phase 3A typed stardust evidence constrains a witness set:
     * - Levels: keep only witnesses whose UNDERLYING level lies in the discrete
     *   set (disjoint sets stay discrete; an empty result is a contradiction);
     * - Missing/Unreadable/Unsupported: never constrain, never support;
     * - Invalid: the anchored read is no displayed power-up value, i.e. no legal
     *   underlying level exists for it — every same-witness numeric row is
     *   contradicted and the read never becomes a guessed level or a wildcard;
     * - Conflict: fail closed with the bounded evidence reason codes.
     */
    fun constrainToUnderlyingLevels(
        witnesses: List<LevelWitness>,
        stardust: StardustLevelEvidence?
    ): StardustIntersection = when (stardust) {
        null,
        is StardustLevelEvidence.Missing,
        is StardustLevelEvidence.Unreadable,
        is StardustLevelEvidence.Unsupported -> StardustIntersection.NotConstraining
        is StardustLevelEvidence.Conflict -> StardustIntersection.Conflict(stardust.reasonCodes)
        is StardustLevelEvidence.Invalid -> StardustIntersection.Constrained(emptyList())
        is StardustLevelEvidence.Levels -> {
            val legalUnderlying = stardust.levels.toSet()
            StardustIntersection.Constrained(
                witnesses.filter { it.underlyingLevel in legalUnderlying })
        }
    }

    /**
     * How trusted typed arc evidence constrains a witness set (Phase 3C seam). The arc's
     * underlying-vs-effective semantics are NOT established by the development corpus,
     * so a range keeps witnesses whose UNDERLYING or EFFECTIVE level falls inside the
     * window — both Best Buddy interpretations stay alive. Arc evidence can only filter
     * existing legal witnesses: it can never resurrect an impossible tuple, invent an
     * IV, or override stardust/CP/HP contradiction.
     */
    fun constrainToArcLevels(
        witnesses: List<LevelWitness>,
        arc: ArcLevelEvidence?
    ): ArcIntersection = when (arc) {
        null,
        is ArcLevelEvidence.Unknown,
        is ArcLevelEvidence.Unsupported -> ArcIntersection.NotConstraining
        is ArcLevelEvidence.Conflict -> ArcIntersection.Conflict(arc.reasonCodes)
        is ArcLevelEvidence.Range -> ArcIntersection.Constrained(
            witnesses.filter { witness ->
                witness.underlyingLevel in arc.minLevel..arc.maxLevel ||
                    witness.effectiveLevel in arc.minLevel..arc.maxLevel
            })
        is ArcLevelEvidence.Alternatives -> ArcIntersection.Constrained(
            witnesses.filter { witness ->
                arc.ranges.any { range ->
                    witness.underlyingLevel in range || witness.effectiveLevel in range
                }
            })
    }

    sealed interface ArcIntersection {
        /** Witnesses surviving the trusted arc window; empty = contradiction. */
        data class Constrained(val witnesses: List<LevelWitness>) : ArcIntersection

        /** Arc evidence absent/unknown/unsupported: tuples unchanged. */
        data object NotConstraining : ArcIntersection

        /** Independent credible arc observations disagree; never silently resolved. */
        data class Conflict(val reasonCodes: List<String>) : ArcIntersection
    }

    sealed interface StardustIntersection {
        /** Witnesses surviving the anchored underlying-level set; empty = contradiction. */
        data class Constrained(val witnesses: List<LevelWitness>) : StardustIntersection

        /** Evidence present but not constraining: widens nothing, supports nothing. */
        data object NotConstraining : StardustIntersection

        /** Conflicting credible observations; never silently ignored. */
        data class Conflict(val reasonCodes: List<String>) : StardustIntersection
    }

    private data class IvFeasibility(
        val staminaIvCount: Int,
        val minStaminaIv: Int,
        val maxStaminaIv: Int,
        val ivTupleCount: Int
    )

    /**
     * Joint IV feasibility at ONE effective level: maxHP via one stamina IV, and
     * CP — when observed — at the SAME level and SAME stamina IV via some
     * attack/defense IVs. Pruning order: stamina/maxHP first, then CP attack/defense.
     */
    private fun jointIvWitness(
        stats: RecognitionSnapshot.ProfileStats,
        cp: Int?,
        maxHp: Int,
        cpm: Double,
        appraisal: ExactIvConstraints?
    ): IvFeasibility? {
        val staminaRange = appraisal?.stamina?.let { it..it } ?: 0..MAX_IV
        val hpMatchingStamina = staminaRange
            .filter { staminaIv -> max(MIN_DISPLAY_VALUE, floor((stats.sta + staminaIv) * cpm).toInt()) == maxHp }
        val witness = when {
            hpMatchingStamina.isEmpty() -> null
            cp == null -> IvFeasibility(
                staminaIvCount = hpMatchingStamina.size,
                minStaminaIv = hpMatchingStamina.first(),
                maxStaminaIv = hpMatchingStamina.last(),
                ivTupleCount = hpMatchingStamina.size
            )
            else -> cpWitness(stats, cp, hpMatchingStamina, cpm, appraisal)
        }
        return witness
    }

    private fun cpWitness(
        stats: RecognitionSnapshot.ProfileStats,
        cp: Int,
        hpMatchingStamina: List<Int>,
        cpm: Double,
        appraisal: ExactIvConstraints?
    ): IvFeasibility? {
        val attackRange = appraisal?.attack?.let { it..it } ?: 0..MAX_IV
        val defenseRange = appraisal?.defense?.let { it..it } ?: 0..MAX_IV
        val tuplesPerStamina = hpMatchingStamina.associateWith { staminaIv ->
            attackRange.sumOf { attackIv ->
                defenseRange.count { defenseIv ->
                    cpAt(stats, attackIv, defenseIv, staminaIv, cpm) == cp
                }
            }
        }
        val witnessingStamina = tuplesPerStamina.filterValues { it > 0 }
        return if (witnessingStamina.isEmpty()) {
            null
        } else {
            IvFeasibility(
                staminaIvCount = witnessingStamina.size,
                minStaminaIv = witnessingStamina.keys.first(),
                maxStaminaIv = witnessingStamina.keys.last(),
                ivTupleCount = witnessingStamina.values.sum()
            )
        }
    }

    private fun cpAt(
        stats: RecognitionSnapshot.ProfileStats,
        attackIv: Int,
        defenseIv: Int,
        staminaIv: Int,
        cpm: Double
    ): Int = max(
        MIN_DISPLAY_VALUE,
        floor(
            (stats.atk + attackIv) *
                sqrt((stats.def + defenseIv).toDouble()) *
                sqrt((stats.sta + staminaIv).toDouble()) *
                cpm * cpm / 10.0
        ).toInt()
    )
}
