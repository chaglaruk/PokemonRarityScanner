package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.ReleaseWindow
import com.pokerarity.scanner.data.model.VariantCatalogEntry
import com.pokerarity.scanner.data.repository.AuthoritativeVariantEventFallback
import com.pokerarity.scanner.data.repository.VariantCatalogSelection
import com.pokerarity.scanner.data.repository.VariantExplanationMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class LegacyVariantPathRemovalTest {

    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Test
    fun noExactMetadataWithoutFullVariantDecision() {
        val selection = VariantCatalogSelection.selectForExplanation(
            finalSpecies = "Pikachu",
            fullMatch = null,
            bySprite = mapOf("025_00_12" to sampleVariantEntry())
        )

        val resolved = VariantExplanationMetadata.resolve(
            selection = selection,
            fullMatch = null,
            authoritativeBySprite = mapOf("025_00_12" to sampleAuthoritativeEntry())
        )

        val fallback = AuthoritativeVariantEventFallback.resolve(
            finalSpecies = "Pikachu",
            caughtDate = isoDate.parse("2022-08-20"),
            costumeLike = false,
            shiny = false,
            bySpecies = mapOf("Pikachu" to listOf(sampleAuthoritativeEntry()))
        )

        assertNull(resolved.variantLabel)
        assertNull(resolved.eventLabel)
        assertNull(resolved.releaseWindow)
        assertNull(fallback)
    }

    @Test
    fun genericVariantMatchCannotPullExactEventTextFromCatalog() {
        val fullMatch = FullVariantMatch(
            finalSpecies = "Pikachu",
            finalSpriteKey = "025_00_12",
            resolvedVariantClass = "costume",
            resolvedCostume = true,
            resolvedEventLabel = null,
            resolvedEventWindow = null,
            variantConfidence = 0.74f,
            explanationMode = "generic_variant"
        )

        val selection = VariantCatalogSelection.selectForExplanation(
            finalSpecies = "Pikachu",
            fullMatch = fullMatch,
            bySprite = mapOf("025_00_12" to sampleVariantEntry())
        )

        val resolved = VariantExplanationMetadata.resolve(
            selection = selection,
            fullMatch = fullMatch,
            authoritativeBySprite = mapOf("025_00_12" to sampleAuthoritativeEntry()),
            caughtDate = isoDate.parse("2022-08-20")
        )

        assertNull(resolved.variantLabel)
        assertNull(resolved.eventLabel)
        assertNull(resolved.releaseWindow)
    }

    @Test
    fun exactAuthoritativeMatchCanStillEmitExactMetadata() {
        val fullMatch = FullVariantMatch(
            finalSpecies = "Pikachu",
            finalSpriteKey = "025_00_12",
            resolvedVariantClass = "costume",
            resolvedCostume = true,
            resolvedEventLabel = "2022 World Championships Celebration",
            resolvedEventWindow = ReleaseWindow("2022-08-18", "2022-08-23"),
            variantConfidence = 0.81f,
            explanationMode = "exact_authoritative"
        )

        val selection = VariantCatalogSelection.selectForExplanation(
            finalSpecies = "Pikachu",
            fullMatch = fullMatch,
            bySprite = mapOf("025_00_12" to sampleVariantEntry())
        )

        val resolved = VariantExplanationMetadata.resolve(
            selection = selection,
            fullMatch = fullMatch,
            authoritativeBySprite = mapOf("025_00_12" to sampleAuthoritativeEntry()),
            caughtDate = isoDate.parse("2022-08-20")
        )

        assertEquals("World Championships costume", resolved.variantLabel)
        assertEquals("2022 World Championships Celebration", resolved.eventLabel)
        assertEquals("2022-08-18", resolved.releaseWindow?.firstSeen)
    }

    private fun sampleVariantEntry() = VariantCatalogEntry(
        dex = 25,
        species = "Pikachu",
        formId = "00",
        variantId = "12",
        assetKey = "GO0025WCS2022",
        spriteKey = "025_00_12",
        isShiny = false,
        variantClass = "costume",
        isCostumeLike = true,
        eventTags = listOf("Some local event"),
        primaryEventLabel = "Some local event",
        hasEventMetadata = true,
        releaseWindow = ReleaseWindow(firstSeen = "2022-08-18", lastSeen = "2022-08-23"),
        variantLabel = "Local variant label"
    )

    private fun sampleAuthoritativeEntry() = AuthoritativeVariantEntry(
        species = "Pikachu",
        dex = 25,
        formId = "00",
        variantId = "12",
        spriteKey = "025_00_12",
        variantClass = "costume",
        isShiny = false,
        isCostumeLike = true,
        variantLabel = "World Championships costume",
        eventLabel = "2022 World Championships Celebration",
        eventStart = "2022-08-18",
        eventEnd = "2022-08-23"
    )
}
