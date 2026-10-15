package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The preflight score only permits tentative OCR. Full 12/12 grids stay terminal:
 * the same recorded anchors occur on real storage lists and verified detail screens.
 */
class StorageDetailProbeRoutingTest {
    private val router = ScreenStateRouter()

    private fun signature(
        gridScore: Float,
        includeNameHp: Boolean = true,
        screenType: ScreenType = ScreenType.StorageList,
        safeFallback: Boolean = true
    ): ScreenClassificationResult {
        val anchors = listOfNotNull(
            ScreenAnchor(
                ScreenAnchorName.StorageGrid, Rect(45, 390, 855, 1794),
                gridScore, "repetitive_grid_cells"
            ),
            if (includeNameHp) {
                ScreenAnchor(
                    ScreenAnchorName.NameHpBand, Rect(90, 682, 810, 1033),
                    0.70f, "name_hp_area_contrast"
                )
            } else null
        )
        return ScreenClassificationResult(
            screenType, gridScore, listOf("storage_grid_evidence"), anchors, safeFallback
        )
    }

    @Test
    fun measuredDetailGridSignaturesCanOnlyBeProbed() {
        // 9/12: shifted Torchic; 11/12: scrolled Chikorita.
        // Full 12/12 positives still need a separately qualified cheap discriminator.
        for (grid in listOf(0.75f, 11f / 12f)) {
            val result = signature(grid)
            assertTrue("Grid=$grid", router.mayProbeConflictingStorage(result))
            val defaultRoute = router.fromClassification(result)
            assertEquals(ScreenRouteAction.REJECT_NON_DETAIL, defaultRoute.action)
            assertFalse(defaultRoute.maySaveScan)
            assertFalse(defaultRoute.mayShowOverlay)
            assertEquals("storage_list_terminal", defaultRoute.reason)
        }
    }

    @Test
    fun preflightNeverProbesMissingNameHpOrUnmeasuredGrid() {
        assertFalse(router.mayProbeConflictingStorage(signature(0.75f, includeNameHp = false)))
        assertFalse(router.mayProbeConflictingStorage(signature(8f / 12f)))
        assertFalse(router.mayProbeConflictingStorage(signature(1f)))
        assertFalse(router.mayProbeConflictingStorage(signature(0.75f, safeFallback = false)))
        assertFalse(router.mayProbeConflictingStorage(signature(
            1f, screenType = ScreenType.Encounter
        )))
    }

    @Test
    fun fullGridStorageNeverProbesDespiteIdenticalDetailLikeAnchors() {
        // Four measured StorageList controls all had a 12/12 grid. Treat
        // full-grid as terminal despite the same generic anchors as positives.
        val result = signature(1f).copy(anchors = signature(1f).anchors + listOf(
            ScreenAnchor(ScreenAnchorName.CpHeader, Rect(90, 87, 810, 214), 0.78f, "header"),
            ScreenAnchor(ScreenAnchorName.DetailCard, Rect(0, 585, 900, 1839), 0.74f, "card")
        ))
        assertFalse(router.mayProbeConflictingStorage(result))
        val decision = router.fromClassification(result)
        assertEquals(ScreenRouteAction.REJECT_NON_DETAIL, decision.action)
        assertFalse(decision.maySaveScan)
    }
}
