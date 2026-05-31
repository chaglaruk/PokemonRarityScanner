package com.pokerarity.scanner.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [RarityTier] boundary classification.
 * Verifies that [RarityTier.fromScore] correctly maps score boundaries
 * and that tier ordering is internally consistent.
 */
class RarityTierBoundaryTest {

    // ── Boundary classification ──────────────────────────────────────────

    @Test
    fun fromScore_returnsCommonForZero() {
        assertEquals(RarityTier.COMMON, RarityTier.fromScore(0))
    }

    @Test(expected = NoSuchElementException::class)
    fun fromScore_throwsForNegative() {
        // Negative scores are below COMMON's minScore (0) — no tier matches.
        // The caller is responsible for clamping scores to 0..100.
        RarityTier.fromScore(-1)
    }

    @Test
    fun fromScore_returnsCommonJustBelowUncommon() {
        assertEquals(RarityTier.COMMON, RarityTier.fromScore(19))
    }

    @Test
    fun fromScore_returnsUncommonAtExactBoundary() {
        assertEquals(RarityTier.UNCOMMON, RarityTier.fromScore(20))
    }

    @Test
    fun fromScore_returnsRareAtExactBoundary() {
        assertEquals(RarityTier.RARE, RarityTier.fromScore(40))
    }

    @Test
    fun fromScore_returnsEpicAtExactBoundary() {
        assertEquals(RarityTier.EPIC, RarityTier.fromScore(60))
    }

    @Test
    fun fromScore_returnsLegendaryAtExactBoundary() {
        assertEquals(RarityTier.LEGENDARY, RarityTier.fromScore(75))
    }

    @Test
    fun fromScore_returnsMythicalAtExactBoundary() {
        assertEquals(RarityTier.MYTHICAL, RarityTier.fromScore(88))
    }

    @Test
    fun fromScore_returnsGodTierAtExactBoundary() {
        assertEquals(RarityTier.GOD_TIER, RarityTier.fromScore(96))
    }

    @Test
    fun fromScore_returnsGodTierAt100() {
        assertEquals(RarityTier.GOD_TIER, RarityTier.fromScore(100))
    }

    @Test
    fun fromScore_returnsGodTierAbove100() {
        // Score capping is done elsewhere; fromScore should still map correctly
        assertEquals(RarityTier.GOD_TIER, RarityTier.fromScore(150))
    }

    // ── One-below-boundary tests ─────────────────────────────────────────

    @Test
    fun fromScore_justBelowRare() {
        assertEquals(RarityTier.UNCOMMON, RarityTier.fromScore(39))
    }

    @Test
    fun fromScore_justBelowEpic() {
        assertEquals(RarityTier.RARE, RarityTier.fromScore(59))
    }

    @Test
    fun fromScore_justBelowLegendary() {
        assertEquals(RarityTier.EPIC, RarityTier.fromScore(74))
    }

    @Test
    fun fromScore_justBelowMythical() {
        assertEquals(RarityTier.LEGENDARY, RarityTier.fromScore(87))
    }

    @Test
    fun fromScore_justBelowGodTier() {
        assertEquals(RarityTier.MYTHICAL, RarityTier.fromScore(95))
    }

    // ── Tier ordering consistency ─────────────────────────────────────────

    @Test
    fun tiers_areOrderedByAscendingMinScore() {
        val tiers = RarityTier.entries
        for (i in 1 until tiers.size) {
            assertTrue(
                "${tiers[i - 1].name}.minScore (${tiers[i - 1].minScore}) should be <= ${tiers[i].name}.minScore (${tiers[i].minScore})",
                tiers[i - 1].minScore <= tiers[i].minScore
            )
        }
    }

    @Test
    fun allTiers_haveNonBlankLabel() {
        for (tier in RarityTier.entries) {
            assertTrue(
                "Tier ${tier.name} should have a non-blank label",
                tier.label.isNotBlank()
            )
        }
    }

    @Test
    fun allTiers_haveValidHexColor() {
        val hexColorPattern = Regex("^#[0-9A-Fa-f]{6}$")
        for (tier in RarityTier.entries) {
            assertTrue(
                "Tier ${tier.name} color '${tier.color}' should be a valid hex color",
                hexColorPattern.matches(tier.color)
            )
        }
    }

    // ── Exhaustive coverage ──────────────────────────────────────────────

    @Test
    fun fromScore_coversEntireRange0to100() {
        for (score in 0..100) {
            val tier = RarityTier.fromScore(score)
            assertTrue(
                "Score $score should map to a tier with minScore <= $score",
                tier.minScore <= score
            )
            // Verify it's the highest-matching tier
            val higherTiers = RarityTier.entries.filter { it.minScore > score }
            for (higher in higherTiers) {
                assertTrue(
                    "Score $score should not map to ${higher.name} (minScore=${higher.minScore})",
                    tier != higher
                )
            }
        }
    }
}
