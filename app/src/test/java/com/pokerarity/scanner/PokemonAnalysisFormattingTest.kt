package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.buildAnalysisItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PokemonAnalysisFormattingTest {

    @Test
    fun buildAnalysisItems_prefersHumanReadableExplanationRows() {
        val items = buildAnalysisItems(
            breakdownKeys = listOf("Base"),
            breakdownValues = listOf(12),
            explanations = listOf(
                "Costume: Fall 2019 costume||Released through Fall 2019",
                "Event: Fall 2019||First seen Oct 17, 2019",
                "Caught on Jan 05, 2017||Legacy collector date"
            ),
            fallbackScore = 61,
        )

        assertEquals(1, items.size)
        assertEquals(
            "This Pokemon stands out because it matches the Fall 2019 costume, it ties back to the Fall 2019 event, and it was caught on Jan 05, 2017. Those collection signals make it more distinctive than a regular catch. Together they place it at a rarity score of 61.",
            items[0].title
        )
        assertNull(items[0].detail)
    }
}
