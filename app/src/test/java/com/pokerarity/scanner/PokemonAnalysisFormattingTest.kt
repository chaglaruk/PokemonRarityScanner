package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.buildAnalysisItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

class PokemonAnalysisFormattingTest {
    private val originalLocale = Locale.getDefault()

    @Before
    fun setLocale() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

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
            "Valuable because it matches the Fall 2019 costume, it ties back to the Fall 2019 event, and it was caught on Jan 05, 2017.",
            items[0].title
        )
        assertNull(items[0].detail)
    }

    @Test
    fun buildAnalysisItems_formatsEventPokemonWithDateCompactly() {
        val items = buildAnalysisItems(
            breakdownKeys = emptyList(),
            breakdownValues = emptyList(),
            explanations = listOf(
                "Event Pokemon: Pokemon Air Adventures||Jul 21-27, 2023",
                "Shiny Pokemon",
                "Costume Pokemon"
            ),
            fallbackScore = 38,
        )

        assertEquals(1, items.size)
        assertEquals(
            "Valuable because it comes from Pokemon Air Adventures (Jul 21-27, 2023), it is shiny, and it is costumed.",
            items[0].title
        )
        assertNull(items[0].detail)
    }
}
