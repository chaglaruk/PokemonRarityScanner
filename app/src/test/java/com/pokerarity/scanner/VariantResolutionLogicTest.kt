package com.pokerarity.scanner

import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier
import com.pokerarity.scanner.util.vision.VariantResolutionLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VariantResolutionLogicTest {

    @Test
    fun sameSpeciesBaseShinyNearTieRescuesCostumeShiny() {
        val speciesMatch = VariantPrototypeClassifier.MatchResult(
            species = "Blastoise",
            assetKey = "009_00_shiny",
            spriteKey = "009_00_shiny",
            variantType = "base",
            isShiny = true,
            isCostumeLike = false,
            scope = "species",
            score = 0.392f,
            confidence = 0.428f,
            speciesMargin = 0f,
            variantMargin = 0.001f,
            bestBaseScore = 0.392f,
            bestNonBaseScore = 0.393f,
            bestNonBaseSpecies = "Blastoise",
            bestNonBaseAssetKey = "009_00_05_shiny",
            bestNonBaseSpriteKey = "009_00_05_shiny",
            bestNonBaseVariantType = "costume",
            bestNonBaseIsShiny = true,
            bestNonBaseIsCostumeLike = true,
            topSpecies = listOf("009_00_shiny:0.392", "009_00_05_shiny:0.393")
        )

        val resolved = VariantResolutionLogic.resolve(
            globalMatch = speciesMatch,
            speciesMatch = speciesMatch,
            sameFamilyGlobalNonBase = false
        )!!

        assertEquals("costume", resolved.variantType)
        assertTrue(resolved.isShiny)
        assertTrue(resolved.isCostumeLike)
        assertEquals("same_species_shiny_costume_rescue", resolved.rescueKind)
    }
}
