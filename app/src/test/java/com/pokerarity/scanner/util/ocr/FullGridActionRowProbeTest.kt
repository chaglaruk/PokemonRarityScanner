package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
        val bitmap = Bitmap.createBitmap(900, 1950, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        for ((top, bottom) in heights) {
            paint.color = Color.rgb(35, 206, 57)
            canvas.drawRect(45f, top.toFloat(), 440f, bottom.toFloat(), paint)
            if (panels) {
                paint.color = Color.rgb(220, 220, 220)
                canvas.drawRect(468f, top.toFloat(), 828f, bottom.toFloat(), paint)
            }
        }
        return bitmap
    }

    @Test fun uniquePairAcrossThreeBandsAllowsOnlyProvisionalPermission() {
        val bitmap = image()
        try {
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
