package com.pokerarity.scanner.data.model

import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IvEstimatePresentationTest {
    @Test
    fun exactRequiresMatchingValidBounds() {
        assertEquals("87%", formatIvEstimateText("EXACT", 87, 87))
        assertEquals(87, exactIvValue("EXACT", 87, 87))

        listOf(
            null to 87,
            87 to null,
            87 to 88,
            -1 to -1,
            101 to 101,
            88 to 87
        ).forEach { (min, max) ->
            assertEquals("Unknown", formatIvEstimateText("EXACT", min, max))
            assertNull(exactIvValue("EXACT", min, max))
        }
    }

    @Test
    fun historyUiDoesNotPromoteInconsistentExactBounds() {
        val pokemon = ScanHistoryEntity(
            pokemonName = "Skiddo",
            cp = 500,
            hp = 60,
            caughtDate = null,
            rawOcrText = "",
            ivMin = 87,
            ivMax = 88,
            ivSolveMode = "EXACT"
        ).toUiPokemon()

        assertNull(pokemon.iv)
        assertEquals("Unknown", pokemon.ivText)
    }

    @Test
    fun rangesRequireCompleteOrderedPercentBounds() {
        assertEquals("43%–87%", formatIvEstimateText("RANGE", 43, 87))
        assertEquals("87%", formatIvEstimateText("RANGE", 87, 87))
        assertEquals("Unknown", formatIvEstimateText("RANGE", 43, null))
        assertEquals("Unknown", formatIvEstimateText("RANGE", 88, 87))
        assertEquals("Unknown", formatIvEstimateText("RANGE", 0, 101))
    }

    @Test
    fun legacyAndInsufficientStatesRemainDistinct() {
        assertNull(formatIvEstimateText(null, null, null))
        assertNull(formatIvEstimateText("FUTURE_MODE", 80, 80))
        assertEquals("Unknown", formatIvEstimateText("INSUFFICIENT", null, null))
    }
}
