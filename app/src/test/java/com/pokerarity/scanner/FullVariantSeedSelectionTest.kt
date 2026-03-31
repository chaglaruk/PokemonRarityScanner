package com.pokerarity.scanner

import com.pokerarity.scanner.util.vision.FullVariantSeedSelection
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class FullVariantSeedSelectionTest {

    @Test
    fun prefersResolvedMatchWhenItMatchesFinalSpecies() {
        val speciesMatch = match(species = "Butterfree", variantType = "base", isCostumeLike = false)
        val resolvedMatch = match(species = "Butterfree", variantType = "costume", isCostumeLike = true)

        val selected = FullVariantSeedSelection.chooseSpeciesSeed(
            finalSpecies = "Butterfree",
            speciesMatch = speciesMatch,
            resolvedMatch = resolvedMatch
        )

        assertEquals("costume", selected?.variantType)
        assertEquals(true, selected?.isCostumeLike)
    }

    @Test
    fun keepsSpeciesMatchWhenResolvedMatchPointsToDifferentSpecies() {
        val speciesMatch = match(species = "Raichu", variantType = "costume", isCostumeLike = true)
        val resolvedMatch = match(species = "Pichu", variantType = "costume", isCostumeLike = true)

        val selected = FullVariantSeedSelection.chooseSpeciesSeed(
            finalSpecies = "Raichu",
            speciesMatch = speciesMatch,
            resolvedMatch = resolvedMatch
        )

        assertEquals("Raichu", selected?.species)
    }

    private fun match(
        species: String,
        variantType: String,
        isCostumeLike: Boolean
    ) = VariantPrototypeClassifier.MatchResult(
        species = species,
        assetKey = "asset",
        spriteKey = "sprite",
        variantType = variantType,
        isShiny = false,
        isCostumeLike = isCostumeLike,
        scope = "species",
        score = 0.4f,
        confidence = 0.52f,
        speciesMargin = 0.1f,
        variantMargin = 0.1f,
        topSpecies = listOf(species)
    )
}
