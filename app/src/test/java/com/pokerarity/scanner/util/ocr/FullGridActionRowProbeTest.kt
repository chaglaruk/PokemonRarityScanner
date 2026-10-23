package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

/** Synthetic shape checks; these do not replace the preserved 45-image S25 replay. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class FullGridActionRowProbeTest {
    private fun fullGrid(
        type: ScreenType = ScreenType.StorageList,
        fallback: Boolean = true,
        grid: Float = 1.0f,
        includeName: Boolean = true
    ): RoutedScreen {
        val anchors = mutableListOf(ScreenAnchor(
            ScreenAnchorName.StorageGrid, Rect(45, 390, 855, 1794), grid, "grid"))
        if (includeName) anchors += ScreenAnchor(
            ScreenAnchorName.NameHpBand, Rect(90, 682, 810, 1033), 0.7f, "name")
        val classification = ScreenClassificationResult(
            type, 1.0f, emptyList(), anchors, fallback
        )
        return RoutedScreen(ScreenStateRouter().fromClassification(classification), classification)
    }

    private fun image(
        heights: List<Pair<Int, Int>> = listOf(572 to 684, 702 to 814, 1118 to 1228),
        panels: Boolean = true
    ): Bitmap {
        val width = 900
        val height = 1950
        val pixels = IntArray(width * height) { Color.BLACK }
        val green = Color.rgb(35, 206, 57)
        val neutral = Color.rgb(220, 220, 220)
        for ((top, bottom) in heights) {
            for (y in top until bottom) {
                java.util.Arrays.fill(pixels, y * width + 45, y * width + 440, green)
                if (panels) {
                    java.util.Arrays.fill(pixels, y * width + 468, y * width + 828, neutral)
                }
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    @Test fun exactGreenCountThresholdControlsTheSafeProvisionalDecision() {
        // 900px: x0=45, columns=198. Original >=0.55 requires 109
        // qualifying green samples; 108 must remain a terminal rejection.
        fun borderline(samples: Int): Bitmap {
            val width = 900
            val height = 1950
            val pixels = IntArray(width * height) { Color.BLACK }
            val green = Color.rgb(35, 206, 57)
            val neutral = Color.rgb(220, 220, 220)
            for ((top, bottom) in listOf(572 to 684, 702 to 814)) {
                for (y in top until bottom) {
                    for (sample in 0 until samples) {
                        pixels[y * width + 45 + sample * 2] = green
                    }
                    java.util.Arrays.fill(pixels, y * width + 468, y * width + 828, neutral)
                }
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }

        for ((samples, expected) in listOf(
            108 to ActionRowStatus.ABSENT,
            109 to ActionRowStatus.PRESENT,
            198 to ActionRowStatus.PRESENT
        )) {
            val bitmap = borderline(samples)
            try {
                val result = FullGridActionRowProbe.evaluate(bitmap, fullGrid())
                assertEquals("green samples=$samples", expected, result.status)
            } finally { bitmap.recycle() }
        }
    }

    @Test fun uniquePairAcrossThreeBandsAllowsOnlyProvisionalPermission() {
        val bitmap = image()
        try {
            // Exact source pixels: reject a fake green/pale Canvas fixture before
            // treating any detector ABSENT as an algorithmic outcome.
            assertEquals(Color.rgb(35, 206, 57), bitmap.getPixel(45, 572))
            assertEquals(Color.rgb(35, 206, 57), bitmap.getPixel(439, 682))
            assertEquals(Color.BLACK, bitmap.getPixel(440, 572))
            assertEquals(Color.rgb(220, 220, 220), bitmap.getPixel(468, 572))
            assertEquals(Color.rgb(220, 220, 220), bitmap.getPixel(827, 683))
            assertEquals(Color.BLACK, bitmap.getPixel(828, 572))
            // The production kernel reads bulk getPixels(), not just getPixel().
            val sampled = IntArray(792)
            bitmap.getPixels(sampled, 0, 792, 45, 572, 792, 1)
            assertEquals(Color.rgb(35, 206, 57), sampled[0])
            assertEquals(Color.rgb(35, 206, 57), sampled[394])
            assertEquals(Color.rgb(220, 220, 220), sampled[423])
            val routed = fullGrid()
            val result = FullGridActionRowProbe.evaluate(bitmap, routed)
            assertEquals(ActionRowStatus.PRESENT, result.status)
            assertEquals(ActionBand(572, 684) to ActionBand(702, 814), result.pair)
            assertEquals(ScreenRouteAction.REJECT_NON_DETAIL, routed.decision.action)
            assertFalse(routed.decision.maySaveScan)
            assertFalse(routed.decision.mayShowOverlay)
        } finally { bitmap.recycle() }
    }

    @Test fun missingOrDarkActionPanelsFailClosed() {
        val bitmap = image(panels = false)
        try {
            val result = FullGridActionRowProbe.evaluate(bitmap, fullGrid())
            assertEquals(ActionRowStatus.ABSENT, result.status)
            assertNull(result.pair)
        } finally { bitmap.recycle() }
    }

    @Test fun multipleValidPairsAreAmbiguousRatherThanBestGuess() {
        val bitmap = image(listOf(572 to 684, 702 to 814, 832 to 944))
        try {
            val result = FullGridActionRowProbe.evaluate(bitmap, fullGrid())
            assertEquals(ActionRowStatus.AMBIGUOUS, result.status)
            assertNull(result.pair)
        } finally { bitmap.recycle() }
    }

    @Test fun otherScreenTypesAndPartialGridsSkipWithoutPixelAccess() {
        val bitmap = image()
        bitmap.recycle()
        assertEquals(ActionRowStatus.NOT_APPLICABLE,
            FullGridActionRowProbe.evaluate(bitmap, fullGrid(type = ScreenType.Encounter)).status)
        assertEquals(ActionRowStatus.NOT_APPLICABLE,
            FullGridActionRowProbe.evaluate(bitmap, fullGrid(grid = 11f / 12f)).status)
        assertEquals(ActionRowStatus.NOT_APPLICABLE,
            FullGridActionRowProbe.evaluate(bitmap, fullGrid(includeName = false)).status)
        assertEquals(ActionRowStatus.NOT_APPLICABLE,
            FullGridActionRowProbe.evaluate(bitmap, fullGrid(fallback = false)).status)
    }

    @Test fun duplicateEvidenceAndInvalidBitmapsNeverProbe() {
        val bitmap = image()
        try {
            val routed = fullGrid()
            val duplicate = routed.classification.copy(anchors =
                routed.classification.anchors + routed.classification.anchors.last())
            assertFalse(FullGridActionRowProbe.isApplicable(
                RoutedScreen(routed.decision, duplicate)))
            val mismatchedDecision = routed.decision.copy(screenType = ScreenType.Unknown)
            assertFalse(FullGridActionRowProbe.isApplicable(
                RoutedScreen(mismatchedDecision, routed.classification)))
            val broken = routed.classification.copy(anchors =
                routed.classification.anchors.map {
                    if (it.name == ScreenAnchorName.StorageGrid) it.copy(rect = Rect(0, 0, 5000, 200))
                    else it
                })
            assertEquals(ActionRowStatus.INVALID, FullGridActionRowProbe.evaluate(
                bitmap, RoutedScreen(routed.decision, broken)).status)
        } finally { bitmap.recycle() }
        assertEquals(ActionRowStatus.INVALID,
            FullGridActionRowProbe.evaluate(bitmap, fullGrid()).status)
    }

    @Test fun greenOnlyInActionRowsIsInsufficientWithoutNeutralCosts() {
        val bitmap = image(panels = false)
        try {
            assertTrue(FullGridActionRowProbe.isApplicable(fullGrid()))
            assertEquals(ActionRowStatus.ABSENT,
                FullGridActionRowProbe.evaluate(bitmap, fullGrid()).status)
        } finally { bitmap.recycle() }
    }

    @Test fun noBandIsAbsentAndValidOneHasActualBandCoordinates() {
        val bitmap = image(emptyList())
        try {
            val result = FullGridActionRowProbe.evaluate(bitmap, fullGrid())
            assertEquals(ActionRowStatus.ABSENT, result.status)
            assertNull(result.pair)
        } finally { bitmap.recycle() }
        val positive = image()
        try {
            assertNotNull(FullGridActionRowProbe.evaluate(positive, fullGrid()).pair)
        } finally { positive.recycle() }
    }
}
