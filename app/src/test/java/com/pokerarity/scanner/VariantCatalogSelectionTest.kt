package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.repository.VariantCatalogLoader
import com.pokerarity.scanner.data.repository.VariantCatalogSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VariantCatalogSelectionTest {

    private fun catalogFile(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            val candidate = File(dir, "app/src/main/assets/data/variant_catalog.json")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        return File("app/src/main/assets/data/variant_catalog.json")
    }

    private fun loadBySprite() =
        VariantCatalogLoader.indexBySpriteKey(VariantCatalogLoader.parseJson(catalogFile().readText()).entries)

    @Test
    fun prefersFullVariantObjectForExactMetadata() {
        val bySprite = loadBySprite()
        val selection = VariantCatalogSelection.selectForExplanation(
            finalSpecies = "Pikachu",
            fullMatch = FullVariantMatch(
                finalSpecies = "Pikachu",
                finalSpriteKey = "025_00_12",
                resolvedVariantClass = "costume",
                resolvedCostume = true,
                variantConfidence = 0.81f,
                explanationMode = "exact_authoritative"
            ),
            bySprite = bySprite
        )

        assertNotNull(selection.entry)
        assertEquals("Pikachu", selection.entry!!.species)
        assertTrue(selection.allowExactMetadata)
        assertTrue(selection.allowDerivedMetadata)
    }

    @Test
    fun fullVariantObjectDerivedModeAllowsOnlyDerivedMetadata() {
        val bySprite = loadBySprite()
        val selection = VariantCatalogSelection.selectForExplanation(
            finalSpecies = "Raichu",
            fullMatch = FullVariantMatch(
                finalSpecies = "Raichu",
                finalSpriteKey = "026_00_27",
                resolvedVariantClass = "costume",
                resolvedCostume = true,
                variantConfidence = 0.76f,
                explanationMode = "derived_authoritative"
            ),
            bySprite = bySprite
        )

        assertNotNull(selection.entry)
        assertEquals("Raichu", selection.entry!!.species)
        assertFalse(selection.allowExactMetadata)
        assertTrue(selection.allowDerivedMetadata)
    }

    @Test
    fun genericSpeciesOnlyDoesNotAllowMetadataEvenWhenSpriteExists() {
        val bySprite = loadBySprite()
        val selection = VariantCatalogSelection.selectForExplanation(
            finalSpecies = "Raichu",
            fullMatch = FullVariantMatch(
                finalSpecies = "Raichu",
                finalSpriteKey = "026_00",
                resolvedVariantClass = "base",
                variantConfidence = 0.81f,
                explanationMode = "generic_species_only"
            ),
            bySprite = bySprite
        )

        assertNotNull(selection.entry)
        assertEquals("Raichu", selection.entry!!.species)
        assertFalse(selection.allowExactMetadata)
        assertFalse(selection.allowDerivedMetadata)
    }

    @Test
    fun moderateExactMatchAllowsDerivedMetadataForDisplay() {
        val bySprite = loadBySprite()
        val selection = VariantCatalogSelection.selectForExplanation(
            finalSpecies = "Pikachu",
            fullMatch = FullVariantMatch(
                finalSpecies = "Pikachu",
                finalSpriteKey = "025_00_FFLYING_03",
                resolvedVariantClass = "costume",
                resolvedCostume = true,
                variantConfidence = 0.37f,
                explanationMode = "exact_authoritative"
            ),
            bySprite = bySprite
        )

        assertNotNull(selection.entry)
        assertFalse(selection.allowExactMetadata)
        assertTrue(selection.allowDerivedMetadata)
    }

    @Test
    fun rejectsMismatchedSpeciesSpriteEvenWhenFullMatchExists() {
        val bySprite = loadBySprite()
        val selection = VariantCatalogSelection.selectForExplanation(
            finalSpecies = "Raichu",
            fullMatch = FullVariantMatch(
                finalSpecies = "Raichu",
                finalSpriteKey = "025_00_12",
                resolvedVariantClass = "costume",
                resolvedCostume = true,
                variantConfidence = 0.81f,
                explanationMode = "exact_authoritative"
            ),
            bySprite = bySprite
        )

        assertNull(selection.entry)
        assertFalse(selection.allowExactMetadata)
        assertFalse(selection.allowDerivedMetadata)
    }

    @Test
    fun returnsEmptySelectionWhenNoFullMatchExists() {
        val bySprite = loadBySprite()
        val selection = VariantCatalogSelection.selectForExplanation(
            finalSpecies = "Pikachu",
            fullMatch = null,
            bySprite = bySprite
        )

        assertNull(selection.entry)
        assertFalse(selection.allowExactMetadata)
        assertFalse(selection.allowDerivedMetadata)
    }
}
