// Purpose: Verify rarity tier visuals stay stable for Classic and theme-aware elsewhere.
package com.pokerarity.scanner.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import com.pokerarity.scanner.ui.theme.PokeThemeId
import com.pokerarity.scanner.ui.theme.PokeThemeRegistry

class RarityTierVisualsTest {
    @Test
    fun classicTierVisualsPreserveExistingLegendaryColors() {
        val visuals = tierVisuals("LEGENDARY")

        assertEquals(Color(0x26FF9800), visuals.bg)
        assertEquals(Color(0xFFFF9800), visuals.border)
        assertEquals(Color(0xFFFFC266), visuals.text)
    }

    @Test
    fun themeTierVisualsUseThemeRarityColors() {
        val theme = PokeThemeRegistry.getThemeById(PokeThemeId.AURORA_VIOLET)
        val visuals = tierVisuals("MYTHICAL", theme)

        assertEquals(theme.rarityMythical.copy(alpha = 0.14f), visuals.bg)
        assertEquals(theme.rarityMythical, visuals.border)
        assertEquals(theme.rarityMythical, visuals.text)
    }

    @Test
    fun unsupportedTierFallsBackToCommonThemeColor() {
        val theme = PokeThemeRegistry.getThemeById(PokeThemeId.FOREST_RESEARCH)
        val visuals = tierVisuals("not-a-tier", theme)

        assertEquals(theme.rarityCommon.copy(alpha = 0.14f), visuals.bg)
        assertEquals(theme.rarityCommon, visuals.border)
        assertEquals(theme.rarityCommon, visuals.text)
    }
}
