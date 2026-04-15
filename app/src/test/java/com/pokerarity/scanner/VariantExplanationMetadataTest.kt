package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.ReleaseWindow
import com.pokerarity.scanner.data.model.VariantCatalogEntry
import com.pokerarity.scanner.data.repository.VariantExplanationMetadata
import com.pokerarity.scanner.data.repository.VariantExplanationSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class VariantExplanationMetadataTest {
    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Test
    fun suppressesAuthoritativeExactLabelsWhenSelectionDoesNotAllowMetadata() {
        val selection = VariantExplanationSelection(
            entry = sampleVariantEntry(),
            allowExactMetadata = false,
            allowDerivedMetadata = false
        )

        val resolved = VariantExplanationMetadata.resolve(
            selection = selection,
            fullMatch = null,
            authoritativeBySprite = mapOf(
                "025_00_12" to sampleAuthoritativeEntry()
            )
        )

        assertNull(resolved.variantLabel)
        assertNull(resolved.eventLabel)
        assertNull(resolved.releaseWindow)
    }

    @Test
    fun prefersAuthoritativeLabelsWhenSelectionAllowsMetadata() {
        val selection = VariantExplanationSelection(
            entry = sampleVariantEntry(),
            allowExactMetadata = true,
            allowDerivedMetadata = true
        )

        val resolved = VariantExplanationMetadata.resolve(
            selection = selection,
            fullMatch = null,
            authoritativeBySprite = mapOf(
                "025_00_12" to sampleAuthoritativeEntry()
            ),
            caughtDate = iso.parse("2022-08-19")
        )

        assertEquals("World Championships costume", resolved.variantLabel)
        assertEquals("2022 World Championships Celebration", resolved.eventLabel)
        assertEquals("2022-08-18", resolved.releaseWindow?.firstSeen)
    }

    @Test
    fun exactMetadataRequiresCaughtDateToExposeHistoricalEvent() {
        val selection = VariantExplanationSelection(
            entry = sampleVariantEntry(),
            allowExactMetadata = true,
            allowDerivedMetadata = true
        )

        val resolved = VariantExplanationMetadata.resolve(
            selection = selection,
            fullMatch = null,
            authoritativeBySprite = mapOf(
                "025_00_12" to sampleAuthoritativeEntry()
            ),
            caughtDate = null
        )

        assertEquals("World Championships costume", resolved.variantLabel)
        assertNull(resolved.eventLabel)
        assertNull(resolved.releaseWindow)
    }

    @Test
    fun fallsBackToDerivedLabelsWhenExactMetadataIsBlocked() {
        val selection = VariantExplanationSelection(
            entry = sampleVariantEntry(),
            allowExactMetadata = false,
            allowDerivedMetadata = true
        )

        val resolved = VariantExplanationMetadata.resolve(
            selection = selection,
            fullMatch = null,
            authoritativeBySprite = mapOf(
                "025_00_12" to sampleAuthoritativeEntry()
            )
        )

        assertEquals("World Championships costume", resolved.variantLabel)
        assertNull(resolved.eventLabel)
        assertNull(resolved.releaseWindow)
    }

    @Test
    fun genericVariantModeDoesNotLeakExactEventMetadata() {
        val selection = VariantExplanationSelection(
            entry = sampleVariantEntry(),
            allowExactMetadata = false,
            allowDerivedMetadata = true
        )

        val resolved = VariantExplanationMetadata.resolve(
            selection = selection,
            fullMatch = FullVariantMatch(
                finalSpecies = "Pikachu",
                finalSpriteKey = "025_00_FALL_2018",
                resolvedVariantClass = "costume",
                resolvedCostume = true,
                resolvedEventLabel = "H.F. Custom Tie-in",
                resolvedEventWindow = ReleaseWindow("2018-10-05", "2018-10-07"),
                variantConfidence = 0.61f,
                explanationMode = "generic_variant"
            ),
            authoritativeBySprite = mapOf(
                "025_00_12" to sampleAuthoritativeEntry()
            ),
            caughtDate = iso.parse("2016-12-20")
        )

        assertEquals("Local variant label", resolved.variantLabel)
        assertNull(resolved.eventLabel)
        assertNull(resolved.releaseWindow)
    }

    @Test
    fun suppressesResolvedEventMetadataWhenItConflictsWithSelectedAuthoritativeWindow() {
        val selection = VariantExplanationSelection(
            entry = sampleVariantEntry(),
            allowExactMetadata = true,
            allowDerivedMetadata = true
        )

        val resolved = VariantExplanationMetadata.resolve(
            selection = selection,
            fullMatch = FullVariantMatch(
                finalSpecies = "Pikachu",
                finalSpriteKey = "025_00_12",
                resolvedVariantClass = "costume",
                resolvedCostume = true,
                resolvedEventLabel = "Holiday 2016",
                resolvedEventWindow = ReleaseWindow("2016-12-12", "2017-01-03"),
                variantConfidence = 0.81f,
                explanationMode = "exact_authoritative"
            ),
            authoritativeBySprite = mapOf(
                "025_00_12" to sampleAuthoritativeEntry()
            )
        )

        assertEquals("World Championships costume", resolved.variantLabel)
        assertNull(resolved.eventLabel)
        assertNull(resolved.releaseWindow)
    }

    @Test
    fun exactAuthoritativeWithoutWindowDoesNotExposeEventWhenCaughtDateExists() {
        val selection = VariantExplanationSelection(
            entry = sampleVariantEntry(),
            allowExactMetadata = true,
            allowDerivedMetadata = true
        )

        val resolved = VariantExplanationMetadata.resolve(
            selection = selection,
            fullMatch = FullVariantMatch(
                finalSpecies = "Pikachu",
                finalSpriteKey = "025_00_FFLYING_03",
                resolvedVariantClass = "costume",
                resolvedCostume = true,
                resolvedEventLabel = "Pikachu Flying 03",
                resolvedEventWindow = null,
                variantConfidence = 0.82f,
                explanationMode = "exact_authoritative"
            ),
            authoritativeBySprite = mapOf(
                "025_00_12" to sampleAuthoritativeEntry()
            ),
            caughtDate = iso.parse("2022-06-21")
        )

        assertEquals("Local variant label", resolved.variantLabel)
        assertNull(resolved.eventLabel)
        assertNull(resolved.releaseWindow)
    }

    @Test
    fun exactAuthoritativeDoesNotExposeEventWhenCaughtDateIsOutsideWindow() {
        val selection = VariantExplanationSelection(
            entry = sampleVariantEntry(),
            allowExactMetadata = true,
            allowDerivedMetadata = true
        )

        val resolved = VariantExplanationMetadata.resolve(
            selection = selection,
            fullMatch = FullVariantMatch(
                finalSpecies = "Pikachu",
                finalSpriteKey = "025_00_12",
                resolvedVariantClass = "costume",
                resolvedCostume = true,
                resolvedEventLabel = "2022 World Championships Celebration",
                resolvedEventWindow = ReleaseWindow("2022-08-18", "2022-08-23"),
                variantConfidence = 0.82f,
                explanationMode = "exact_authoritative"
            ),
            authoritativeBySprite = mapOf(
                "025_00_12" to sampleAuthoritativeEntry()
            ),
            caughtDate = iso.parse("2017-01-03")
        )

        assertEquals("World Championships costume", resolved.variantLabel)
        assertNull(resolved.eventLabel)
        assertNull(resolved.releaseWindow)
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

    @Test
    fun derivedAuthoritativeDoesNotLeakEventLabelWhenCaughtDateIsOutsideWindow() {
        val selection = VariantExplanationSelection(
            entry = sampleVariantEntry(),
            allowExactMetadata = true,
            allowDerivedMetadata = true
        )

        val resolved = VariantExplanationMetadata.resolve(
            selection = selection,
            fullMatch = FullVariantMatch(
                finalSpecies = "Pikachu",
                finalSpriteKey = "025_00_12",
                resolvedVariantClass = "costume",
                resolvedCostume = true,
                resolvedEventLabel = "2022 World Championships Celebration",
                resolvedEventWindow = ReleaseWindow("2022-08-18", "2022-08-23"),
                variantConfidence = 0.82f,
                explanationMode = "derived_authoritative"
            ),
            authoritativeBySprite = mapOf(
                "025_00_12" to sampleAuthoritativeEntry()
            ),
            caughtDate = iso.parse("2017-03-01")
        )

        assertEquals("World Championships costume", resolved.variantLabel)
        assertNull(resolved.eventLabel)
        assertNull(resolved.releaseWindow)
    }
}
