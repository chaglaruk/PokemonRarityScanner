// Purpose: Verify collection filter labels and empty states remain predictable.
package com.pokerarity.scanner.ui.screens

import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.Rarity
import com.pokerarity.scanner.data.model.RarityAnalysisItem
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionScreenFilterTest {
    @Test
    fun rareFilterIncludesRareAndLegendaryPokemonOnly() {
        val scans = listOf(
            pokemon(id = 1, rarity = Rarity.COMMON),
            pokemon(id = 2, rarity = Rarity.RARE),
            pokemon(id = 3, rarity = Rarity.LEGENDARY),
            pokemon(id = 4, rarity = Rarity.SHINY, tags = listOf("SHINY")),
        )

        val filtered = filteredPokemonForOption(FilterOption.RARE, scans)

        assertEquals(listOf(2, 3), filtered.map { it.id })
    }

    @Test
    fun filterLabelsExposeCountsWithoutChangingFilterLogic() {
        val scans = listOf(
            pokemon(id = 1, rarity = Rarity.COMMON),
            pokemon(id = 2, rarity = Rarity.LEGENDARY),
            pokemon(id = 3, rarity = Rarity.SHINY, tags = listOf("SHINY", "LUCKY")),
        )

        assertEquals("All (3)", filterOptionDisplayLabel(FilterOption.ALL, scans))
        assertEquals("Legendary (1)", filterOptionDisplayLabel(FilterOption.LEGENDARY, scans))
        assertEquals("Rare (1)", filterOptionDisplayLabel(FilterOption.RARE, scans))
        assertEquals("Shiny (1)", filterOptionDisplayLabel(FilterOption.SHINY, scans))
        assertEquals("Lucky (1)", filterOptionDisplayLabel(FilterOption.LUCKY, scans))
    }

    @Test
    fun emptyStateExplainsNoScansAndFilteredNoMatchesDifferently() {
        assertEquals("No scans yet", collectionEmptyTitle(FilterOption.ALL))
        assertEquals("No shiny matches", collectionEmptyTitle(FilterOption.SHINY))
        assertEquals("Press Scan Now to start the overlay.", collectionEmptyMessage(FilterOption.ALL, false))
        assertEquals("Use the floating scan button in Pokemon GO.", collectionEmptyMessage(FilterOption.ALL, true))
        assertEquals(
            "Try another filter or scan more Pokemon to fill this view.",
            collectionEmptyMessage(FilterOption.LUCKY, false)
        )
    }

    private fun pokemon(
        id: Int,
        rarity: Rarity,
        tags: List<String> = emptyList(),
    ): Pokemon {
        return Pokemon(
            id = id,
            sourceId = id.toLong(),
            name = "Pokemon $id",
            cp = 100 + id,
            hp = 50,
            rarityScore = 10,
            rarity = rarity,
            rarityTierCode = rarity.name,
            type = "normal",
            displayDate = "Today",
            caughtDate = "Today",
            tags = tags,
            analysis = listOf(RarityAnalysisItem("test", null, false)),
        )
    }
}
