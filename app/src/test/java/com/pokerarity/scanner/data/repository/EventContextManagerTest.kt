package com.pokerarity.scanner.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventContextManagerTest {

    @Test
    fun parseCommunityDays_mapsBoostedSpeciesIntoEventPokemonEntities() {
        val payload = """
            [
              {
                "community_day_number": 99,
                "start_date": "2026-04-13",
                "end_date": "2026-04-13",
                "boosted_pokemon": ["Spinda", "Pikachu"]
              }
            ]
        """.trimIndent()

        val entries = EventContextManager.parseCommunityDaysPayload(payload)

        assertEquals(2, entries.size)
        assertTrue(entries.all { it.source == "pogoapi_community_days" })
        assertEquals("Community Day #99 Spinda", entries.first().eventName)
    }
}
