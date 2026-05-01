package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.buildAnalysisItems
import org.junit.Assert.assertEquals
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

        assertEquals(3, items.size)
        assertEquals("Costume: Fall 2019 costume", items[0].title)
        assertEquals("Released through Fall 2019", items[0].detail)
        assertEquals("Event: Fall 2019", items[1].title)
        assertEquals("First seen Oct 17, 2019", items[1].detail)
        assertEquals("Caught on Jan 05, 2017", items[2].title)
        assertEquals("Legacy collector date", items[2].detail)
    }

    @Test
    fun buildAnalysisItems_formatsEventPokemonWithDateCompactly() {
        val items = buildAnalysisItems(
            breakdownKeys = emptyList(),
            breakdownValues = emptyList(),
            explanations = listOf(
                "Caught during Pokemon Air Adventures||Jul 21-27, 2023",
                "Shiny",
                "Costume Pokemon"
            ),
            fallbackScore = 38,
        )

        assertEquals(3, items.size)
        assertEquals("Caught during Pokemon Air Adventures", items[0].title)
        assertEquals("Jul 21-27, 2023", items[0].detail)
        assertEquals("Shiny", items[1].title)
        assertEquals("Costume Pokemon", items[2].title)
    }
}
