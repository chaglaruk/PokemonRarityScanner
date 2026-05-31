// Purpose: Verify crash-safe UI theme lookup and fallback behavior.
package com.pokerarity.scanner.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PokeThemeRegistryTest {
    @Test
    fun safeThemeIdFallsBackForBlankUnknownAndCorruptValues() {
        assertEquals(PokeThemeId.CLASSIC, safeThemeId(null))
        assertEquals(PokeThemeId.CLASSIC, safeThemeId(""))
        assertEquals(PokeThemeId.CLASSIC, safeThemeId("not-a-theme"))
        assertEquals(PokeThemeId.CLASSIC, safeThemeId("\u0000\u0001bad"))
    }

    @Test
    fun safeThemeIdAcceptsStorageValueAndEnumName() {
        assertEquals(PokeThemeId.MYSTIC_BLUE, safeThemeId("mystic_blue"))
        assertEquals(PokeThemeId.MYSTIC_BLUE, safeThemeId("MYSTIC_BLUE"))
        assertEquals(PokeThemeId.AURORA_VIOLET, safeThemeId(" aurora_violet "))
    }

    @Test
    fun getThemeByRawIdNeverReturnsNullAndFallsBackToClassic() {
        val fallback = PokeThemeRegistry.getThemeByRawId("missing")

        assertEquals(PokeThemeId.CLASSIC, fallback.id)
        assertEquals(PokeThemeRegistry.classic.background, fallback.background)
        assertEquals(PokeThemeRegistry.classic.accent, fallback.accent)
    }

    @Test
    fun allRequiredThemesAreRegistered() {
        val registeredIds = PokeThemeRegistry.allThemes.map { it.id }.toSet()

        assertTrue(registeredIds.contains(PokeThemeId.CLASSIC))
        assertTrue(registeredIds.contains(PokeThemeId.OBSIDIAN_RARITY))
        assertTrue(registeredIds.contains(PokeThemeId.POKEDEX_RED))
        assertTrue(registeredIds.contains(PokeThemeId.MYSTIC_BLUE))
        assertTrue(registeredIds.contains(PokeThemeId.FOREST_RESEARCH))
        assertTrue(registeredIds.contains(PokeThemeId.AURORA_VIOLET))
    }

    @Test
    fun mergeWithClassicBackfillsMissingTokens() {
        val merged = PokeThemeRegistry.mergeWithClassic(
            PokeThemeOverrides(
                id = PokeThemeId.MYSTIC_BLUE,
                background = Color(0xFF123456),
                spacingLg = 20.dp
            )
        )

        assertEquals(Color(0xFF123456), merged.background)
        assertEquals(20.dp, merged.spacingLg)
        assertEquals(PokeThemeRegistry.classic.textPrimary, merged.textPrimary)
        assertEquals(PokeThemeRegistry.classic.radiusMd, merged.radiusMd)
        assertEquals(PokeThemeRegistry.classic.rarityLegendary, merged.rarityLegendary)
    }
}
