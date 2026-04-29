package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.Rarity
import com.pokerarity.scanner.util.ClipboardService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardServiceTest {
    @Test
    fun nicknamePayloadFitsPokemonGoLimit() {
        val pokemon = Pokemon(
            id = 1,
            sourceId = 1,
            name = "Butterfree",
            cp = 1234,
            hp = 120,
            rarityScore = 58,
            rarity = Rarity.RARE,
            rarityTierCode = "RARE",
            type = "bug",
            displayDate = "Apr 29, 2026",
            caughtDate = "Nov 05, 2017",
            tags = listOf("SHINY", "COSTUME"),
            analysis = emptyList()
        )

        val payload = ClipboardService.buildNicknameSafePayload(pokemon)

        assertTrue(payload.length <= ClipboardService.MAX_POKEMON_GO_NICKNAME_LENGTH)
        assertEquals("Butterf58SCO", payload)
    }
}
