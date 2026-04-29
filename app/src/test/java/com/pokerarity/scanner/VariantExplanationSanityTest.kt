package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.ReleaseWindow
import com.pokerarity.scanner.data.repository.VariantExplanationSanity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class VariantExplanationSanityTest {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Test
    fun suppressesExactEventMetadataWhenCaughtBeforeEventStart() {
        val result = VariantExplanationSanity.sanitize(
            caughtDate = dateFormat.parse("2017-01-05"),
            variantLabel = "World Championships costume",
            eventLabel = "2022 World Championships Celebration",
            releaseWindow = ReleaseWindow(
                firstSeen = "2022-08-18",
                lastSeen = "2022-08-23"
            )
        )

        assertNull(result.variantLabel)
        assertNull(result.eventLabel)
        assertNull(result.releaseWindow)
    }

    @Test
    fun keepsExactEventMetadataWhenCaughtInsideReleaseWindow() {
        val result = VariantExplanationSanity.sanitize(
            caughtDate = dateFormat.parse("2022-08-19"),
            variantLabel = "World Championships costume",
            eventLabel = "2022 World Championships Celebration",
            releaseWindow = ReleaseWindow(
                firstSeen = "2022-08-18",
                lastSeen = "2022-08-23"
            )
        )

        assertEquals("World Championships costume", result.variantLabel)
        assertEquals("2022 World Championships Celebration", result.eventLabel)
        assertEquals("2022-08-18", result.releaseWindow?.firstSeen)
    }

    @Test
    fun suppressesExactEventMetadataWhenCaughtAfterEventEnd() {
        val result = VariantExplanationSanity.sanitize(
            caughtDate = dateFormat.parse("2025-02-27"),
            variantLabel = "World Championships costume",
            eventLabel = "2022 World Championships Celebration",
            releaseWindow = ReleaseWindow(
                firstSeen = "2022-08-18",
                lastSeen = "2022-08-23"
            )
        )

        assertNull(result.variantLabel)
        assertNull(result.eventLabel)
        assertNull(result.releaseWindow)
    }

    @Test
    fun keepsUnambiguousEventMetadataWhenCaughtDateIsMissing() {
        val result = VariantExplanationSanity.sanitize(
            caughtDate = null,
            variantLabel = "Scarf costume",
            eventLabel = "Water Festival 2020",
            releaseWindow = ReleaseWindow(
                firstSeen = "2020-11-12",
                lastSeen = "2020-11-19"
            )
        )

        assertEquals("Scarf costume", result.variantLabel)
        assertEquals("Water Festival 2020", result.eventLabel)
        assertEquals("2020-11-12", result.releaseWindow?.firstSeen)
    }
}
