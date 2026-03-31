package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.ReleaseWindow
import com.pokerarity.scanner.data.repository.RarityExplanationFormatter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RarityExplanationFormatterTest {

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
}
