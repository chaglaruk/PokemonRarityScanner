package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry
import com.pokerarity.scanner.data.model.HistoricalEventAppearance
import com.pokerarity.scanner.data.repository.AuthoritativeVariantEventFallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class AuthoritativeVariantEventFallbackTest {
    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Test
    fun resolvesSpeciesDateMatchedCostumeEvent() {
        val result = AuthoritativeVariantEventFallback.resolve(
            finalSpecies = "Raichu",
            caughtDate = isoDate.parse("2017-01-03"),
            costumeLike = true,
            shiny = false,
            bySpecies = mapOf(
                "Raichu" to listOf(
                    entry(
                        species = "Raichu",
                        spriteKey = "026_00_01",
                        variantLabel = "Festive hat costume",
                        eventLabel = "Holiday 2016",
                        eventStart = "2016-12-12",
                        eventEnd = "2017-01-03"
                    )
                )
            )
        )

        assertNotNull(result)
        assertEquals("Festive hat costume", result?.variantLabel)
        assertEquals("Holiday 2016", result?.eventLabel)
    }

    @Test
    fun returnsNullWhenNoDateMatchedEventExists() {
        val result = AuthoritativeVariantEventFallback.resolve(
            finalSpecies = "Raichu",
            caughtDate = isoDate.parse("2017-01-11"),
            costumeLike = true,
            shiny = false,
            bySpecies = mapOf(
                "Raichu" to listOf(
                    entry(
                        species = "Raichu",
                        spriteKey = "026_00_01",
                        variantLabel = "Festive hat costume",
                        eventLabel = "Holiday 2016",
                        eventStart = "2016-12-12",
                        eventEnd = "2017-01-03"
                    )
                )
            )
        )

        assertNull(result)
    }

    @Test
    fun returnsNullWhenResultIsNotCostumeLike() {
        val result = AuthoritativeVariantEventFallback.resolve(
            finalSpecies = "Raichu",
            caughtDate = isoDate.parse("2017-01-03"),
            costumeLike = false,
            shiny = false,
            bySpecies = mapOf(
                "Raichu" to listOf(
                    entry(
                        species = "Raichu",
                        spriteKey = "026_00_01",
                        variantLabel = "Festive hat costume",
                        eventLabel = "Holiday 2016",
                        eventStart = "2016-12-12",
                        eventEnd = "2017-01-03"
                    )
                )
            )
        )

        assertNull(result)
    }

    @Test
    fun resolvesShinyCostumeFromSharedNonShinyEventMetadata() {
        val result = AuthoritativeVariantEventFallback.resolve(
            finalSpecies = "Bulbasaur",
            caughtDate = isoDate.parse("2024-01-01"),
            costumeLike = true,
            shiny = true,
            bySpecies = mapOf(
                "Bulbasaur" to listOf(
                    entry(
                        species = "Bulbasaur",
                        spriteKey = "001_00_11",
                        variantLabel = "Party hat (red) costume",
                        eventLabel = "New Year's 2024",
                        eventStart = "2024-01-01",
                        eventEnd = "2024-01-03"
                    )
                )
            )
        )

        assertNotNull(result)
        assertEquals("Party hat (red) costume", result?.variantLabel)
        assertEquals("New Year's 2024", result?.eventLabel)
    }

    @Test
    fun prefersHistoricalAppearanceWhenTopLevelEventIsFuture() {
        val result = AuthoritativeVariantEventFallback.resolve(
            finalSpecies = "Hoothoot",
            caughtDate = isoDate.parse("2023-01-01"),
            costumeLike = true,
            shiny = true,
            bySpecies = mapOf(
                "Hoothoot" to listOf(
                    AuthoritativeVariantEntry(
                        species = "Hoothoot",
                        dex = 163,
                        formId = "00",
                        variantId = "JAN_2022_NOEVOLVE",
                        spriteKey = "163_00_JAN_2022_NOEVOLVE_shiny",
                        variantClass = "costume",
                        isShiny = true,
                        isCostumeLike = true,
                        variantLabel = "New Year's outfit costume",
                        eventLabel = "New Year's 2026",
                        eventStart = "2025-12-31",
                        eventEnd = "2026-01-04",
                        historicalEvents = listOf(
                            HistoricalEventAppearance("New Year's 2023", "2022-12-31", "2023-01-04"),
                            HistoricalEventAppearance("New Year's 2022", "2021-12-31", "2022-01-04")
                        )
                    )
                )
            )
        )

        assertNotNull(result)
        assertEquals("New Year's 2023", result?.eventLabel)
        assertEquals("2022-12-31", result?.releaseWindow?.firstSeen)
    }

    private fun entry(
        species: String,
        spriteKey: String,
        variantLabel: String,
        eventLabel: String,
        eventStart: String,
        eventEnd: String
    ) = AuthoritativeVariantEntry(
        species = species,
        dex = 26,
        formId = "00",
        variantId = "01",
        spriteKey = spriteKey,
        variantClass = "costume",
        isShiny = false,
        isCostumeLike = true,
        variantLabel = variantLabel,
        eventLabel = eventLabel,
        eventStart = eventStart,
        eventEnd = eventEnd
    )
}
