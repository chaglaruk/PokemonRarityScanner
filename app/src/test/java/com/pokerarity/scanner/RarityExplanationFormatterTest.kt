package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.ReleaseWindow
import com.pokerarity.scanner.data.repository.RarityExplanationFormatter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

class RarityExplanationFormatterTest {
    private val originalLocale = Locale.getDefault()

    @Before
    fun setLocale() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun buildVariantReasons_includesEventAndReleaseContext() {
        val reasons = RarityExplanationFormatter.buildVariantReasons(
            species = "Pikachu",
            variantClass = "costume",
            isShiny = true,
            isCostumeLike = true,
            variantLabel = "Winter 2023 hat costume",
            primaryEventLabel = "Winter Holiday 2023",
            eventTags = listOf("Winter Holiday 2023"),
            releaseWindow = ReleaseWindow(
                firstSeen = "2023-12-18",
                lastSeen = "2023-12-31"
            )
        )

        assertTrue(reasons.any { it.contains("Winter Holiday 2023") })
        assertTrue(reasons.any { it.contains("Costume: Winter 2023 hat costume") })
        assertTrue(reasons.any { it.contains("Dec 18, 2023") })
    }

    @Test
    fun buildVariantReasons_suppressesTokenLikeEventNames() {
        val reasons = RarityExplanationFormatter.buildVariantReasons(
            species = "Pikachu",
            variantClass = "costume",
            isShiny = false,
            isCostumeLike = true,
            variantLabel = "Flying 03 costume",
            primaryEventLabel = "Pikachu Flying 03",
            eventTags = emptyList(),
            releaseWindow = null
        )

        assertFalse(reasons.any { it.contains("Pikachu Flying 03") })
    }

    @Test
    fun buildValueReasons_usesDateBackedEventWhenAvailable() {
        val reasons = RarityExplanationFormatter.buildValueReasons(
            isShiny = true,
            isCostumeLike = true,
            hasLocationCard = false,
            hasSpecialForm = false,
            variantLabel = "Winter 2023 hat costume",
            eventLabel = "Winter Holiday 2023",
            releaseWindow = ReleaseWindow(
                firstSeen = "2023-12-18",
                lastSeen = "2023-12-31"
            ),
            caughtDate = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2023-12-24")
        )

        assertTrue(reasons.first().contains("Event Pokemon: Winter Holiday 2023"))
        assertTrue(reasons.first().contains("Dec 18-31, 2023"))
        assertTrue(reasons.any { it.contains("Shiny Pokemon") })
    }

    @Test
    fun buildValueReasons_keepsCostumeSimpleWithoutEvent() {
        val reasons = RarityExplanationFormatter.buildValueReasons(
            isShiny = true,
            isCostumeLike = true,
            hasLocationCard = false,
            hasSpecialForm = false,
            variantLabel = null,
            eventLabel = null,
            releaseWindow = null,
            caughtDate = null
        )

        assertTrue(reasons.any { it.contains("Costume Pokemon") })
        assertTrue(reasons.any { it.contains("Shiny Pokemon") })
        assertFalse(reasons.any { it.contains("Event Pokemon") })
    }

    @Test
    fun buildValueReasons_suppressesEventWhenCaughtDateMissesWindow() {
        val reasons = RarityExplanationFormatter.buildValueReasons(
            isShiny = true,
            isCostumeLike = true,
            hasLocationCard = false,
            hasSpecialForm = false,
            variantLabel = "Winter 2023 hat costume",
            eventLabel = "Winter Holiday 2023",
            releaseWindow = ReleaseWindow(
                firstSeen = "2023-12-18",
                lastSeen = "2023-12-31"
            ),
            caughtDate = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2020-12-24")
        )

        assertFalse(reasons.any { it.contains("Event Pokemon") })
        assertTrue(reasons.any { it.contains("Costume Pokemon") })
        assertTrue(reasons.any { it.contains("Shiny Pokemon") })
    }

    @Test
    fun buildValueReasons_keepsSingleWindowEventWhenCaughtDateIsMissing() {
        val reasons = RarityExplanationFormatter.buildValueReasons(
            isShiny = true,
            isCostumeLike = true,
            hasLocationCard = false,
            hasSpecialForm = false,
            variantLabel = "Orange balloon Flying Pikachu costume",
            eventLabel = "Pokemon Air Adventures",
            releaseWindow = ReleaseWindow(
                firstSeen = "2023-07-21",
                lastSeen = "2023-07-27"
            ),
            caughtDate = null
        )

        assertTrue(reasons.first().contains("Event Pokemon: Pokemon Air Adventures"))
        assertTrue(reasons.first().contains("Jul 21-27, 2023"))
        assertTrue(reasons.any { it.contains("Shiny Pokemon") })
    }

    @Test
    fun buildValueReasons_explainsOlderNonVariantCatch() {
        val reasons = RarityExplanationFormatter.buildValueReasons(
            isShiny = false,
            isCostumeLike = false,
            hasLocationCard = false,
            hasSpecialForm = false,
            variantLabel = null,
            eventLabel = null,
            releaseWindow = null,
            caughtDate = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2017-11-05"),
            totalScore = 31,
            baseScore = 7,
            variantScore = 0,
            ageScore = 18,
            collectorScore = 6
        )

        assertTrue(reasons.any { it.contains("Older catch") })
        assertTrue(reasons.any { it.contains("Nov 05, 2017") })
    }
}
