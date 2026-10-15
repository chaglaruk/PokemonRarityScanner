package com.pokerarity.scanner.data.model

import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDecisionSupportTest {
    @Test fun reviewOnlyGuidanceIsVisibleUiContent() {
        val review = ScanDecisionSupport("", "", "", 70, "Review recommended", "CP missing")
        assertTrue(review.hasVisibleUiContent())
    }

    @Test fun historicalAbsentGuidanceRemainsUnknown() {
        val legacy = ScanHistoryEntity(
            pokemonName = "Pikachu", cp = null, hp = null, caughtDate = null, rawOcrText = ""
        )
        assertNull(legacy.toScanDecisionSupport())
        assertNull(legacy.toUiPokemon().decisionSupport)
    }

    @Test fun persistedReviewSurvivesUiMappingWithoutChangingUnknownCpHp() {
        val row = ScanHistoryEntity(
            pokemonName = "Farfetch'd", cp = null, hp = null, caughtDate = null,
            rawOcrText = "", scanConfidenceScore = 70,
            scanConfidenceLabel = "Review recommended", scanConfidenceDetail = "CP missing, HP missing"
        )
        val ui = row.toUiPokemon()
        assertEquals(70, ui.decisionSupport?.scanConfidenceScore)
        assertEquals("Review recommended", ui.decisionSupport?.scanConfidenceLabel)
        assertEquals("Unknown", ui.cpDisplayText)
        assertNull(ui.hp)
        assertTrue(ui.decisionSupport!!.hasVisibleUiContent())
    }
}
