package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.CollectionAxisScore
import com.pokerarity.scanner.data.model.Rarity
import com.pokerarity.scanner.data.model.ScoreAxis
import com.pokerarity.scanner.data.model.buildAnalysisItems
import com.pokerarity.scanner.data.model.pokemonFromScanExtras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun pokemonFromScanExtras_keepsCollectionSignalTags() {
        val pokemon = pokemonFromScanExtras(
            name = "Mewtwo",
            cp = 1500,
            hp = 120,
            score = 76,
            tier = "ULTRA_RARE",
            isShiny = false,
            isLucky = true,
            hasCostume = false,
            hasSpecialForm = true,
            isShadow = true,
            isPurified = true,
            hasLocationCard = true,
            dateText = "Jun 5, 2026",
            isEdited = true
        )

        assertTrue("LUCKY" in pokemon.tags)
        assertTrue("FORM" in pokemon.tags)
        assertTrue("SHADOW" in pokemon.tags)
        assertTrue("PURIFIED" in pokemon.tags)
        assertTrue("LOCATION" in pokemon.tags)
        assertTrue("EDITED" in pokemon.tags)
        assertEquals("Ultra Rare", pokemon.rarityTierLabel)
    }

    @Test
    fun trophyAndUltraRareTiersDoNotImplyLegendarySpeciesTag() {
        val ultraRare = pokemonFromScanExtras(
            name = "Pidgey",
            cp = 500,
            hp = 40,
            score = 76,
            tier = "ULTRA_RARE",
            isShiny = false,
            isLucky = false,
            hasCostume = false,
            hasSpecialForm = false,
            isShadow = false,
            dateText = "Jun 5, 2026",
        )
        val trophy = pokemonFromScanExtras(
            name = "Pidgey",
            cp = 500,
            hp = 40,
            score = 95,
            tier = "TROPHY",
            isShiny = false,
            isLucky = false,
            hasCostume = false,
            hasSpecialForm = false,
            isShadow = false,
            dateText = "Jun 5, 2026",
        )

        assertTrue("ULTRA RARE" in ultraRare.tags)
        assertTrue("TROPHY" in trophy.tags)
        assertFalse("LEGENDARY" in ultraRare.tags)
        assertFalse("LEGENDARY" in trophy.tags)
        assertEquals(Rarity.RARE, ultraRare.rarity)
        assertEquals(Rarity.RARE, trophy.rarity)
    }

    @Test
    fun actualLegendaryCatalogCategoryAddsLegendarySpeciesTag() {
        val pokemon = pokemonFromScanExtras(
            name = "Mewtwo",
            cp = 1500,
            hp = 120,
            score = 76,
            tier = "ULTRA_RARE",
            isShiny = false,
            isLucky = false,
            hasCostume = false,
            hasSpecialForm = false,
            isShadow = false,
            dateText = "Jun 5, 2026",
            collectionAxes = listOf(
                CollectionAxisScore(
                    axis = ScoreAxis.BASE_SPECIES,
                    score = 18,
                    maxScore = 20,
                    details = listOf("legendary")
                )
            )
        )

        assertTrue("ULTRA RARE" in pokemon.tags)
        assertTrue("LEGENDARY" in pokemon.tags)
        assertEquals(Rarity.LEGENDARY, pokemon.rarity)
    }
}
