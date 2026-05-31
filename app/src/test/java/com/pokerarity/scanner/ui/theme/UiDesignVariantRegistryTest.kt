// Purpose: Verify crash-safe UI design variant lookup and fallback behavior.
package com.pokerarity.scanner.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiDesignVariantRegistryTest {
    @Test
    fun safeDesignVariantIdFallsBackForBlankUnknownAndCorruptValues() {
        assertEquals(UiDesignVariantId.CLASSIC, safeDesignVariantId(null))
        assertEquals(UiDesignVariantId.CLASSIC, safeDesignVariantId(""))
        assertEquals(UiDesignVariantId.CLASSIC, safeDesignVariantId("not-a-variant"))
        assertEquals(UiDesignVariantId.CLASSIC, safeDesignVariantId("\u0000\u0001bad"))
    }

    @Test
    fun safeDesignVariantIdAcceptsStorageValueAndEnumName() {
        assertEquals(UiDesignVariantId.DEX_CONSOLE, safeDesignVariantId("dex_console"))
        assertEquals(UiDesignVariantId.DEX_CONSOLE, safeDesignVariantId("DEX_CONSOLE"))
        assertEquals(UiDesignVariantId.AURORA_SHOWCASE, safeDesignVariantId(" aurora_showcase "))
    }

    @Test
    fun registryContainsAllRequiredDesignVariants() {
        val ids = UiDesignVariantRegistry.allVariants.map { it.id }.toSet()

        assertTrue(ids.contains(UiDesignVariantId.CLASSIC))
        assertTrue(ids.contains(UiDesignVariantId.DEX_CONSOLE))
        assertTrue(ids.contains(UiDesignVariantId.COLLECTOR_ALBUM))
        assertTrue(ids.contains(UiDesignVariantId.RESEARCH_LAB))
        assertTrue(ids.contains(UiDesignVariantId.BATTLE_HUD))
        assertTrue(ids.contains(UiDesignVariantId.AURORA_SHOWCASE))
    }

    @Test
    fun getDesignVariantByRawFallsBackToClassic() {
        val fallback = UiDesignVariantRegistry.getDesignVariantByRaw("missing")

        assertEquals(UiDesignVariantId.CLASSIC, fallback.id)
        assertEquals("Classic", fallback.displayName)
    }

    @Test
    fun themeAndDesignVariantParsingAreIndependent() {
        assertEquals(PokeThemeId.CLASSIC, safeThemeId("bad-theme"))
        assertEquals(UiDesignVariantId.CLASSIC, safeDesignVariantId("bad-variant"))
    }
}
