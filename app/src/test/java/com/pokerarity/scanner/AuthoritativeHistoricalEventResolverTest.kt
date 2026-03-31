package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry
import com.pokerarity.scanner.data.model.HistoricalEventAppearance
import com.pokerarity.scanner.data.repository.AuthoritativeHistoricalEventResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class AuthoritativeHistoricalEventResolverTest {

    @Test
    fun selectsHistoricalAppearanceThatMatchesCaughtDate() {
        val entry = AuthoritativeVariantEntry(
            species = "Pikachu",
            dex = 25,
            formId = "00",
            variantId = "02",
            spriteKey = "025_00_02",
            variantClass = "costume",
            isShiny = false,
            isCostumeLike = true,
            variantLabel = "Party hat costume",
            eventLabel = "Spotlight Hour",
            eventStart = "2025-07-01",
            eventEnd = "2025-07-01",
            historicalEvents = listOf(
                HistoricalEventAppearance("Pokemon Day 2017", "2017-02-26", "2017-03-06"),
                HistoricalEventAppearance("Pokemon Day 2018", "2018-02-26", "2018-02-28"),
                HistoricalEventAppearance("Spotlight Hour", "2025-07-01", "2025-07-01")
            )
        )

        val caughtDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2017-03-01")
        val resolved = AuthoritativeHistoricalEventResolver.resolve(entry, caughtDate)

        assertEquals("Party hat costume", resolved?.variantLabel)
        assertEquals("Pokemon Day 2017", resolved?.eventLabel)
        assertEquals("2017-02-26", resolved?.releaseWindow?.firstSeen)
    }

    @Test
    fun returnsNullWhenCaughtDateDoesNotMatchAnyHistoricalAppearance() {
        val entry = AuthoritativeVariantEntry(
            species = "Pikachu",
            dex = 25,
            formId = "00",
            variantId = "02",
            spriteKey = "025_00_02",
            variantClass = "costume",
            isShiny = false,
            isCostumeLike = true,
            variantLabel = "Party hat costume",
            eventLabel = "Spotlight Hour",
            eventStart = "2025-07-01",
            eventEnd = "2025-07-01",
            historicalEvents = listOf(
                HistoricalEventAppearance("Pokemon Day 2017", "2017-02-26", "2017-03-06"),
                HistoricalEventAppearance("Pokemon Day 2018", "2018-02-26", "2018-02-28")
            )
        )

        val caughtDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2024-01-07")
        val resolved = AuthoritativeHistoricalEventResolver.resolve(entry, caughtDate)

        assertNull(resolved)
    }
}
