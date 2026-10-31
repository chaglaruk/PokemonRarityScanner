package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.ReleaseWindow
import com.pokerarity.scanner.data.model.VariantCatalogEntry
import com.pokerarity.scanner.data.repository.VariantCatalogLoader
import com.pokerarity.scanner.data.repository.VariantCatalogSelection
import com.pokerarity.scanner.data.repository.eventTagsOrEmpty
import com.pokerarity.scanner.data.repository.primaryEventLabelOrNull
import com.pokerarity.scanner.data.repository.releaseWindowOrNull
import com.pokerarity.scanner.data.repository.variantLabelOrNull
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

    @Test
    fun unverifiedFormKeepsEventEvidenceButSuppressesFormScopedMetadata() {
        val entry = VariantCatalogEntry(
            dex = 386,
            species = "Deoxys",
            formId = "11",
            assetKey = "386_11",
            spriteKey = "386_11",
            isShiny = false,
            variantClass = "form",
            isCostumeLike = false,
            eventTags = listOf("Verified event tag"),
            primaryEventLabel = "Verified event",
            releaseWindow = ReleaseWindow("2024-01-01", "2024-01-02"),
            variantLabel = "Attack Forme"
        )
        val fullMatch = FullVariantMatch(
            finalSpecies = "Deoxys",
            finalSpriteKey = entry.spriteKey,
            resolvedVariantClass = "form",
            variantConfidence = 0.9f,
            explanationMode = "exact_authoritative"
        )
        val unverified = VariantCatalogSelection.selectForExplanation(
            finalSpecies = "Deoxys",
            fullMatch = fullMatch,
            bySprite = mapOf(entry.spriteKey to entry)
        )

        assertFalse(unverified.allowFormScopedMetadata)
        assertNull(unverified.variantLabelOrNull())
        assertNull(unverified.releaseWindowOrNull())
        assertEquals("Verified event", unverified.primaryEventLabelOrNull())
        assertEquals(listOf("Verified event tag"), unverified.eventTagsOrEmpty())

        val verified = VariantCatalogSelection.selectForExplanation(
            finalSpecies = "Deoxys",
            fullMatch = fullMatch,
            bySprite = mapOf(entry.spriteKey to entry),
            hasVerifiedForm = true
        )
        assertEquals("Attack Forme", verified.variantLabelOrNull())
        assertEquals("2024-01-01", verified.releaseWindowOrNull()?.firstSeen)
    }
}
