package com.pokerarity.scanner.util.ocr

import kotlin.math.ceil

/**
 * Cost-modifier semantics of the POWER UP stardust row (Phase 3A extraction of the
 * former private FamilySpeciesResolver tier logic; one shared authority for every
 * consumer). The multipliers are the canonical game cost states, not heuristics:
 * ordinary power-ups cost the tier value, Lucky halves it, Purified gives 10% off,
 * Lucky+Purified combines to 55% off, and Shadow adds 20%. An active Best Buddy
 * bonus changes the CP/HP-witnessed level but never the underlying upgrade tier.
 *
 * Public because it appears inside the public typed [StardustLevelEvidence] contract.
 */
@Suppress("MagicNumber")
enum class PowerUpCostModifier(val multiplier: Double) {
    NORMAL(1.0),
    LUCKY(0.5),
    PURIFIED(0.9),
    LUCKY_PURIFIED(0.45),
    SHADOW(1.2)
}

/**
 * Canonical power-up stardust tier rules — the one game-math authority for turning an
 * observed POWER UP stardust value into level-consistency evidence. The tier list and
 * level mapping are intentionally explicit (detekt MagicNumber is acknowledged once for
 * the data table as a whole).
 *
 * Semantics (characterized against the Phase 1/2 resolver behavior):
 * - tier k covers base levels [2k+1, 2k+3) in half-level steps, so each tier is a
 *   two-full-level window and tier index [k] powers up for [TIER_COSTS][k] stardust;
 * - base levels are bounded to [1, 50): the 15000 tier terminates there and no 50.5/51.0
 *   power-up tier exists;
 * - a CP/HP-witnessed level L may belong to base level L (no buddy bonus) or L-1 (active
 *   Best Buddy bonus: CP/HP render one full level above the underlying level);
 * - the displayed cost of a base level is ceil(tier cost * modifier); the Shadow
 *   multiplier is additionally evaluated in single precision because the game can render
 *   its costs from float32 multiplication (e.g. 800 -> 961 and 1600 -> 1921), while other
 *   tiers stay exact (2200 -> 2640, 4000 -> 4800).
 */
@Suppress("MagicNumber")
internal object PowerUpStardustRules {

    /** Canonical ordinary power-up stardust tiers, indexed by [tierIndexOf]. */
    val TIER_COSTS: List<Int> = listOf(
        200, 400, 600, 800, 1000, 1300, 1600, 1900, 2200, 2500,
        3000, 3500, 4000, 4500, 5000, 6000, 7000, 8000, 9000, 10000,
        11000, 12000, 13000, 14000, 15000
    )

    /** All cost modifiers whose semantics this component models. */
    val SUPPORTED_MODIFIERS: Set<PowerUpCostModifier> = PowerUpCostModifier.entries.toSet()

    private const val SHADOW_MULTIPLIER_FLOAT = 1.2f
    private const val MAX_BASE_LEVEL = 50.0

    /** Tier index of a base level in [1, 50); null outside the supported base domain. */
    fun tierIndexOf(baseLevel: Double): Int? {
        if (baseLevel < 1.0 || baseLevel >= MAX_BASE_LEVEL) return null
        val index = ((baseLevel - 1) / 2).toInt()
        return index.takeIf { it in TIER_COSTS.indices }
    }

    /** Canonical displayed stardust values of one base tier under one cost modifier. */
    fun displayedCosts(tierIndex: Int, modifier: PowerUpCostModifier): Set<Int> {
        val base = TIER_COSTS[tierIndex]
        val exact = ceil(base * modifier.multiplier).toInt()
        if (modifier != PowerUpCostModifier.SHADOW) return setOf(exact)
        val float32 = ceil((base.toFloat() * SHADOW_MULTIPLIER_FLOAT).toDouble()).toInt()
        return setOf(exact, float32)
    }

    /**
     * Whether an observed POWER UP stardust value is consistent with a CP/HP-witnessed
     * level. [modifiers] restricts the cost-modifier interpretation; callers without
     * trustworthy modifier provenance must pass [SUPPORTED_MODIFIERS] so the ambiguity
     * is preserved instead of silently assumed away.
     */
    fun costMatches(
        observed: Int,
        level: Double,
        modifiers: Set<PowerUpCostModifier> = SUPPORTED_MODIFIERS
    ): Boolean {
        if (modifiers.isEmpty()) return false
        val candidateBaseLevels = listOf(level, level - 1).filter { it >= 1 && it < MAX_BASE_LEVEL }
        return candidateBaseLevels.any { baseLevel ->
            val tierIndex = tierIndexOf(baseLevel) ?: return@any false
            modifiers.any { modifier -> observed in displayedCosts(tierIndex, modifier) }
        }
    }
}
