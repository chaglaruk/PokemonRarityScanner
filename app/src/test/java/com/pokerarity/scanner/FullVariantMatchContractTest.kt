package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.FullVariantCandidate
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.ReleaseWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullVariantMatchContractTest {

    @Test
    fun fullVariantMatchCarriesAuthoritativeDecisionState() {
        val candidate = FullVariantCandidate(
            species = "Pikachu",
            spriteKey = "025_00_12",
            variantClass = "costume",
            isShiny = false,
            isCostumeLike = true,
            eventLabel = "2022 World Championships Celebration",
            eventStart = "2022-08-18",
            eventEnd = "2022-08-23",
            matchScore = 0.355f,
            rescueKind = "exact_non_base_consensus",
            source = "classifier_species"
        )

        val match = FullVariantMatch(
            finalSpecies = "Pikachu",
            finalSpriteKey = "025_00_12",
            resolvedVariantClass = "costume",
            resolvedShiny = false,
            resolvedCostume = true,
            resolvedForm = false,
            resolvedEventLabel = "2022 World Championships Celebration",
            resolvedEventWindow = ReleaseWindow("2022-08-18", "2022-08-23"),
            speciesConfidence = 0.91f,
            variantConfidence = 0.74f,
            shinyConfidence = 0.12f,
            eventConfidence = 0.82f,
            explanationMode = "exact_authoritative",
            candidates = listOf(candidate),
            debugSummary = "species exact, costume exact, event exact"
        )

        assertEquals("Pikachu", match.finalSpecies)
        assertTrue(match.resolvedCostume)
        assertFalse(match.resolvedShiny)
        assertEquals("2022 World Championships Celebration", match.resolvedEventLabel)
        assertEquals("exact_authoritative", match.explanationMode)
        assertEquals(1, match.candidates.size)
        assertEquals("classifier_species", match.candidates.single().source)
    }
}
