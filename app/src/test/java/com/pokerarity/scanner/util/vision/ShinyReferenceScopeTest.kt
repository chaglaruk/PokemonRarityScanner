package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.FullVariantMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShinyReferenceScopeTest {

    @Test
    fun sameSpeciesResolvedFormSuppliesExactAppearanceKey() {
        val match = FullVariantMatch(
            finalSpecies = "Meowth",
            winnerSpecies = "Meowth",
            finalSpriteKey = "052_00_FALOLA_shiny",
            resolvedForm = true
        )

        assertEquals("052_00_FALOLA_shiny", ShinyReferenceScope.resolvedFormSpriteKey(match, "Meowth"))
    }

    @Test
    fun unresolvedOrCrossSpeciesMatchCannotScopeShinyReference() {
        val crossSpecies = FullVariantMatch(
            finalSpecies = "Meowth",
            winnerSpecies = "Persian",
            finalSpriteKey = "052_00_FALOLA",
            resolvedForm = true
        )

        assertNull(ShinyReferenceScope.resolvedFormSpriteKey(crossSpecies, "Meowth"))
        assertNull(ShinyReferenceScope.resolvedFormSpriteKey(crossSpecies.copy(resolvedForm = false), "Meowth"))
        assertNull(ShinyReferenceScope.resolvedFormSpriteKey(crossSpecies, null))
    }
}
