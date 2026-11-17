package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.FullVariantMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShinyReferenceScopeTest {
    @Test
    fun sameSpeciesResolvedFormProvidesExactVariantKey() {
        val match = FullVariantMatch(
            finalSpecies = "Meowth",
            winnerSpecies = "Meowth",
            finalSpriteKey = "052_00_FALOLA_shiny",
            resolvedVariantClass = "form",
            resolvedForm = true
        )

        assertEquals(
            "052_00_FALOLA_shiny",
            ShinyReferenceScope.resolvedFormSpriteKey(match, "Meowth")
        )
    }

    @Test
    fun unresolvedOrCrossSpeciesMatchCannotScopeSignature() {
        val crossSpecies = FullVariantMatch(
            finalSpecies = "Meowth",
            winnerSpecies = "Persian",
            finalSpriteKey = "052_00_FALOLA",
            resolvedForm = true
        )
        val unresolvedForm = crossSpecies.copy(winnerSpecies = "Meowth", resolvedForm = false)

        assertNull(ShinyReferenceScope.resolvedFormSpriteKey(crossSpecies, "Meowth"))
        assertNull(ShinyReferenceScope.resolvedFormSpriteKey(unresolvedForm, "Meowth"))
        assertNull(ShinyReferenceScope.resolvedFormSpriteKey(crossSpecies, null))
    }
}
