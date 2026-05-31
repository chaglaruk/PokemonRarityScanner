// Purpose: Verify result-card stat formatting is safe for partial scan results.
package com.pokerarity.scanner.ui.overlay

import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.Rarity
import com.pokerarity.scanner.data.model.RarityAnalysisItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanResultOverlayCardTest {
    @Test
    fun resultStatsExposeCpHpAndUppercaseType() {
        val stats = resultStatsFor(pokemon(cp = 1510, hp = 124, type = "electric"))

        assertEquals(
            listOf(
                ResultStat("CP", "1510"),
                ResultStat("HP", "124"),
                ResultStat("TYPE", "ELECTRIC"),
            ),
            stats
        )
    }

    @Test
    fun resultStatsUseSafeFallbacksForMissingValues() {
        val stats = resultStatsFor(pokemon(cp = 0, hp = null, type = ""))

        assertEquals(
            listOf(
                ResultStat("CP", "-"),
                ResultStat("HP", "-"),
                ResultStat("TYPE", "UNKNOWN"),
            ),
            stats
        )
    }

    private fun pokemon(
        cp: Int,
        hp: Int?,
        type: String,
    ): Pokemon {
        return Pokemon(
            id = 1,
            sourceId = 1L,
            name = "Pikachu",
            cp = cp,
            hp = hp,
            rarityScore = 40,
            rarity = Rarity.RARE,
            rarityTierCode = "RARE",
            type = type,
            displayDate = "Today",
            caughtDate = "Today",
            tags = emptyList(),
            analysis = listOf(RarityAnalysisItem("test", null, true)),
        )
    }
}
