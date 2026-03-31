package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.GlobalRarityLegacyEntry
import com.pokerarity.scanner.data.repository.GlobalLegacyExplanationFallback
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalLegacyExplanationFallbackTest {

    @Test
    fun usesActiveEventWhenHistoricalEventIsMissing() {
        val resolved = GlobalLegacyExplanationFallback.resolve(
            GlobalRarityLegacyEntry(
                species = "Butterfree",
                dex = 12,
                formId = "01",
                spriteKey = "012_01",
                variantClass = "costume",
                isShiny = false,
                isCostumeLike = true,
                variantLabel = null,
                eventLabel = null,
                eventStart = null,
                eventEnd = null,
                firstSeen = null,
                lastSeen = null,
                lastKnownEvent = null,
                activeEventLabel = "Fashion Raid Day",
                activeEventStart = "2026-04-04",
                activeEventEnd = "2026-04-04"
            )
        )

        assertEquals("Fashion Raid Day", resolved.eventLabel)
        assertEquals("2026-04-04", resolved.releaseWindow?.firstSeen)
        assertEquals("2026-04-04", resolved.releaseWindow?.lastSeen)
    }

    @Test
    fun prefersHistoricalEventWhenItExists() {
        val resolved = GlobalLegacyExplanationFallback.resolve(
            GlobalRarityLegacyEntry(
                species = "Pikachu",
                dex = 25,
                formId = "00",
                spriteKey = "025_00_01",
                variantClass = "costume",
                isShiny = false,
                isCostumeLike = true,
                variantLabel = "Festive hat costume",
                eventLabel = "Holiday 2016",
                eventStart = "2016-12-12",
                eventEnd = "2017-01-03",
                firstSeen = "2016-12-12",
                lastSeen = "2017-01-03",
                lastKnownEvent = "Holiday 2016",
                activeEventLabel = "Fashion Raid Day",
                activeEventStart = "2026-04-04",
                activeEventEnd = "2026-04-04"
            )
        )

        assertEquals("Holiday 2016", resolved.eventLabel)
        assertEquals("2016-12-12", resolved.releaseWindow?.firstSeen)
        assertEquals("2017-01-03", resolved.releaseWindow?.lastSeen)
    }
}
