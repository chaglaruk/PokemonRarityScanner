package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityResultTier
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.VisualFeatures
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RarityResultMapperTest {
    @Test
    fun unknownSpeciesDoesNotOverclaimRarity() {
        val result = RarityResultMapper.build(
            pokemon = pokemon(name = "Unknown"),
            features = VisualFeatures(isShiny = true, confidence = 0.95f),
            score = score(tier = RarityTier.LEGENDARY),
            scanConfidence = 0.95f
        )

        assertEquals(RarityResultTier.UNKNOWN, result.tier)
        assertEquals(0.20f, result.confidence, 0.001f)
        assertTrue(result.warnings.contains("unknown_species"))
        assertTrue(result.reasons.any { it.contains("not identified", ignoreCase = true) })
    }

    @Test
    fun missingCoreEvidenceProducesInsufficientData() {
        val result = RarityResultMapper.build(
            pokemon = pokemon(cp = null, hp = null, maxHp = null, caughtDate = null),
            features = VisualFeatures(),
            score = score(tier = RarityTier.MYTHICAL),
            scanConfidence = 0.90f
        )

        assertEquals(RarityResultTier.INSUFFICIENT_DATA, result.tier)
        assertEquals(0.40f, result.confidence, 0.001f)
        assertTrue(result.warnings.contains("missing_cp"))
        assertTrue(result.warnings.contains("missing_hp"))
        assertTrue(result.warnings.contains("missing_caught_date"))
    }

    @Test
    fun reliableScanMapsExistingTierAndReasons() {
        val result = RarityResultMapper.build(
            pokemon = pokemon(),
            features = VisualFeatures(isShiny = true, confidence = 0.92f),
            score = score(tier = RarityTier.EPIC, explanation = listOf("Shiny variant")),
            scanConfidence = 0.92f
        )

        assertEquals(RarityResultTier.EPIC, result.tier)
        assertEquals(0.92f, result.confidence, 0.001f)
        assertFalse(result.warnings.contains("low_scan_confidence"))
        assertFalse(result.warnings.contains("low_variant_confidence"))
        assertTrue(result.reasons.contains("Shiny visual signal"))
    }

    @Test
    fun lowScanConfidenceDowngradesRareClaimToInsufficientData() {
        val result = RarityResultMapper.build(
            pokemon = pokemon(hp = null, maxHp = null, caughtDate = null),
            features = VisualFeatures(),
            score = score(tier = RarityTier.RARE),
            scanConfidence = 0.40f
        )

        assertEquals(RarityResultTier.INSUFFICIENT_DATA, result.tier)
        assertEquals(0.40f, result.confidence, 0.001f)
        assertTrue(result.warnings.contains("low_scan_confidence"))
    }

    @Test
    fun weakVisualVariantSignalAddsWarningWithoutChangingExistingScoreTier() {
        val result = RarityResultMapper.build(
            pokemon = pokemon(),
            features = VisualFeatures(hasCostume = true, confidence = 0.35f),
            score = score(tier = RarityTier.RARE),
            scanConfidence = 0.74f
        )

        assertEquals(RarityResultTier.RARE, result.tier)
        assertTrue(result.warnings.contains("low_variant_confidence"))
        assertFalse(result.warnings.contains("low_scan_confidence"))
    }

    private fun score(
        tier: RarityTier = RarityTier.RARE,
        explanation: List<String> = listOf("Rare species family")
    ) = RarityScore(
        totalScore = tier.minScore,
        tier = tier,
        breakdown = emptyMap(),
        explanation = explanation
    )

    private fun pokemon(
        cp: Int? = 500,
        hp: Int? = 70,
        maxHp: Int? = 70,
        name: String? = "Pikachu",
        caughtDate: Date? = Date(1_700_000_000_000L)
    ) = PokemonData(
        cp = cp,
        hp = hp,
        maxHp = maxHp,
        name = name,
        realName = name,
        candyName = name,
        megaEnergy = null,
        weight = null,
        height = null,
        stardust = null,
        caughtDate = caughtDate
    )
}
