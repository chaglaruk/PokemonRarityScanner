package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.Rarity
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStateStoreTest {

    @Test
    fun resetToIdle_clearsPreviousResultState() {
        val pokemon = Pokemon(
            id = 1,
            sourceId = 1,
            name = "Pikachu",
            cp = 500,
            hp = 50,
            iv = null,
            rarityScore = 10,
            rarity = Rarity.COMMON,
            type = "Electric",
            displayDate = "Apr 13, 2026",
            caughtDate = "Apr 13, 2026",
            tags = emptyList(),
            analysis = emptyList()
        )

        OverlayStateStore.dispatch(OverlayIntent.ShowResult(pokemon))
        OverlayStateStore.resetToIdle()

        assertTrue(OverlayStateStore.state.value is OverlayState.Idle)
    }
}
