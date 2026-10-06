package com.pokerarity.scanner

import com.pokerarity.scanner.util.ocr.PowerUpCostModifier
import com.pokerarity.scanner.util.ocr.PowerUpStardustRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3A extraction parity: the shared [PowerUpStardustRules] must reproduce the exact
 * semantics of the former private FamilySpeciesResolver PowerUpTiers logic. The replica
 * below is a verbatim copy of that original algorithm; both are swept over every level of
 * the recognition half-level domain and every canonical displayed cost plus adversarial
 * neighbors. The existing FamilySpeciesResolver tests pin the resolver-side behavior on
 * top of this.
 */
@Suppress("MagicNumber")
class PowerUpStardustRulesParityTest {

    /** Verbatim replica of the original private resolver implementation. */
    private fun originalCostMatches(observed: Int, level: Double): Boolean {
        val costs = listOf(
            200, 400, 600, 800, 1000, 1300, 1600, 1900, 2200, 2500,
            3000, 3500, 4000, 4500, 5000, 6000, 7000, 8000, 9000, 10000,
            11000, 12000, 13000, 14000, 15000
        )
        val modifiers = listOf(1.0, .5, .9, .45, 1.2)
        fun canonicalDisplayedCosts(base: Int, modifier: Double): Set<Int> {
            val exact = kotlin.math.ceil(base * modifier).toInt()
            if (modifier != 1.2) return setOf(exact)
            val float32 = kotlin.math.ceil((base.toFloat() * 1.2f).toDouble()).toInt()
            return setOf(exact, float32)
        }
        return listOf(level, level - 1).filter { it >= 1 && it < 50 }.any { baseLevel ->
            val base = costs[((baseLevel - 1) / 2).toInt()]
            modifiers.any { modifier -> observed in canonicalDisplayedCosts(base, modifier) }
        }
    }

    private val sweepLevels: List<Double> =
        generateSequence(0.5) { it + 0.5 }.takeWhile { it <= 51.5 }.toList()

    private val sweepCosts: List<Int> = buildList {
        PowerUpStardustRules.TIER_COSTS.forEach { tier ->
            PowerUpCostModifier.entries.forEach { modifier ->
                PowerUpStardustRules.displayedCosts(
                    PowerUpStardustRules.TIER_COSTS.indexOf(tier), modifier
                ).forEach { displayed ->
                    add(displayed)
                    add(displayed - 1)
                    add(displayed + 1)
                }
            }
        }
        addAll(listOf(0, 1, 50, 99, 101, 199, 201, 1234, 9999, 14_999, 16_000, 17_999,
            18_000, 18_001, 29_999, 30_000, 123_456))
    }.distinct()

    @Test
    fun extractedRulesMatchTheOriginalAlgorithmOverTheWholeDomain() {
        sweepCosts.forEach { cost ->
            sweepLevels.forEach { level ->
                assertEquals(
                    "cost=$cost level=$level",
                    originalCostMatches(cost, level),
                    PowerUpStardustRules.witnessedLevelCostMatches(cost, level)
                )
            }
        }
    }

    @Test
    fun fullModifierSetIsTheDefaultAndMatchesTheOriginal() {
        sweepCosts.forEach { cost ->
            sweepLevels.forEach { level ->
                assertEquals(
                    "cost=$cost level=$level",
                    originalCostMatches(cost, level),
                    PowerUpStardustRules.witnessedLevelCostMatches(
                        cost, level, PowerUpStardustRules.SUPPORTED_MODIFIERS)
                )
            }
        }
    }

    @Test
    fun tierIndexMatchesTheOriginalFormula() {
        generateSequence(1.0) { it + 0.5 }.takeWhile { it < 50.0 }.forEach { baseLevel ->
            val expected = ((baseLevel - 1) / 2).toInt()
            assertEquals(expected, PowerUpStardustRules.tierIndexOf(baseLevel))
        }
        // Outside the supported base domain no tier exists (no 50.0/50.5/51.0 tier).
        assertNull(PowerUpStardustRules.tierIndexOf(0.5))
        assertNull(PowerUpStardustRules.tierIndexOf(50.0))
        assertNull(PowerUpStardustRules.tierIndexOf(51.0))
    }

    @Test
    fun shadowDualRepresentationIsPreserved() {
        // 800 -> 960 exact and 961 float32; 1600 -> 1920/1921; 2200 and 4000 stay exact.
        assertEquals(setOf(960, 961), PowerUpStardustRules.displayedCosts(3, PowerUpCostModifier.SHADOW))
        assertEquals(setOf(1920, 1921), PowerUpStardustRules.displayedCosts(6, PowerUpCostModifier.SHADOW))
        assertEquals(setOf(2640), PowerUpStardustRules.displayedCosts(8, PowerUpCostModifier.SHADOW))
        assertEquals(setOf(4800), PowerUpStardustRules.displayedCosts(12, PowerUpCostModifier.SHADOW))
        assertTrue(PowerUpStardustRules.witnessedLevelCostMatches(961, 8.0))
        assertFalse(PowerUpStardustRules.witnessedLevelCostMatches(962, 8.0))
    }

    @Test
    fun witnessedLevelModifierSubsetsPreserveLegacyBestBuddyCompatibility() {
        // 200 is a normal tier-0 cost and a lucky tier-1 cost; each alone sees only its own window.
        assertTrue(PowerUpStardustRules.witnessedLevelCostMatches(200, 1.0, setOf(PowerUpCostModifier.NORMAL)))
        assertFalse(PowerUpStardustRules.witnessedLevelCostMatches(200, 5.0, setOf(PowerUpCostModifier.NORMAL)))
        assertTrue(PowerUpStardustRules.witnessedLevelCostMatches(200, 5.0, setOf(PowerUpCostModifier.LUCKY)))
        assertFalse(PowerUpStardustRules.witnessedLevelCostMatches(200, 1.0, setOf(PowerUpCostModifier.SHADOW)))
        // An empty modifier set must never match: unknown-to-the-rules context stays fail-closed.
        assertFalse(PowerUpStardustRules.witnessedLevelCostMatches(200, 1.0, emptySet()))
    }


    @Test
    fun baseLevelMatchingDoesNotInventBestBuddyOffset() {
        assertTrue(PowerUpStardustRules.baseLevelCostMatches(
            200, 1.0, setOf(PowerUpCostModifier.NORMAL)))
        assertTrue(PowerUpStardustRules.baseLevelCostMatches(
            200, 2.5, setOf(PowerUpCostModifier.NORMAL)))
        assertFalse(PowerUpStardustRules.baseLevelCostMatches(
            200, 3.0, setOf(PowerUpCostModifier.NORMAL)))

        assertTrue(PowerUpStardustRules.baseLevelCostMatches(
            200, 3.0, setOf(PowerUpCostModifier.LUCKY)))
        assertTrue(PowerUpStardustRules.baseLevelCostMatches(
            200, 4.5, setOf(PowerUpCostModifier.LUCKY)))
        assertFalse(PowerUpStardustRules.baseLevelCostMatches(
            200, 5.0, setOf(PowerUpCostModifier.LUCKY)))

        // Legacy CP/HP witnessed-level compatibility intentionally allows L-1.
        assertTrue(PowerUpStardustRules.witnessedLevelCostMatches(
            200, 5.0, setOf(PowerUpCostModifier.LUCKY)))
    }

    @Test
    fun tierTableShapeIsTheCanonicalTwentyFiveTierList() {
        assertEquals(25, PowerUpStardustRules.TIER_COSTS.size)
        assertEquals(200, PowerUpStardustRules.TIER_COSTS.first())
        assertEquals(15_000, PowerUpStardustRules.TIER_COSTS.last())
        assertEquals(PowerUpStardustRules.TIER_COSTS, PowerUpStardustRules.TIER_COSTS.distinct())
        assertTrue(PowerUpStardustRules.TIER_COSTS.zipWithNext().all { (a, b) -> a < b })
    }
}
