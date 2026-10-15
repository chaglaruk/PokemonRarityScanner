package com.pokerarity.scanner

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.util.ocr.ScreenClassificationResult
import com.pokerarity.scanner.util.ocr.ScreenRouteAction
import com.pokerarity.scanner.util.ocr.ScreenRouteDecision
import com.pokerarity.scanner.util.ocr.ScreenStateRouter
import com.pokerarity.scanner.util.ocr.ScreenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

/**
 * Phase 2A screen-state router contract: routing authority and species authority are
 * different. The router only answers whether a frame enters detail recognition; terminal
 * routes can never show overlay, save, or produce a collection-safe acceptance.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ScreenStateRouterTest {

    private fun classification(
        screenType: ScreenType,
        confidence: Float = 0.9f,
        safeFallback: Boolean = false
    ) = ScreenClassificationResult(
        screenType = screenType,
        confidence = confidence,
        reasons = listOf("test_reason"),
        anchors = emptyList(),
        safeFallback = safeFallback
    )

    @Test
    fun highConfidenceStorageListIsRejectedAsTerminalNonDetail() {
        val decision = ScreenStateRouter().fromClassification(classification(ScreenType.StorageList, 1.0f))

        assertEquals(ScreenRouteAction.REJECT_NON_DETAIL, decision.action)
        assertFalse(decision.mayShowOverlay)
        assertFalse(decision.maySaveScan)
    }

    @Test
    fun encounterAndMapLikeScreensAreRejectedAsTerminalNonDetail() {
        val decision = ScreenStateRouter().fromClassification(classification(ScreenType.Encounter, 1.0f))

        assertEquals(ScreenRouteAction.REJECT_NON_DETAIL, decision.action)
        assertFalse(decision.mayShowOverlay)
        assertFalse(decision.maySaveScan)
    }

    @Test
    fun transitionScreensAreRetryableAndNeverAccepted() {
        val decision = ScreenStateRouter().fromClassification(classification(ScreenType.Transition, 0.55f))

        assertEquals(ScreenRouteAction.RETRY_UNSTABLE, decision.action)
        assertFalse(decision.mayShowOverlay)
        assertFalse(decision.maySaveScan)
    }

    @Test
    fun unknownScreensFailClosedAsRetryable() {
        val decision = ScreenStateRouter().fromClassification(
            classification(ScreenType.Unknown, 0.34f, safeFallback = true))

        assertEquals(ScreenRouteAction.RETRY_UNKNOWN, decision.action)
        assertFalse(decision.mayShowOverlay)
        assertFalse(decision.maySaveScan)
    }

    @Test
    fun pokemonDetailProceedsToDetailRecognition() {
        val decision = ScreenStateRouter().fromClassification(classification(ScreenType.PokemonDetail, 0.9f))

        assertEquals(ScreenRouteAction.PROCEED_DETAIL, decision.action)
        assertTrue(decision.mayShowOverlay)
        assertTrue(decision.maySaveScan)
        assertFalse(decision.requiresContentCorroboration)
    }

    @Test
    fun scrolledDetailProceedsOnlyWithContentCorroboration() {
        val decision = ScreenStateRouter().fromClassification(classification(ScreenType.PokemonDetailScrolled))

        assertEquals(ScreenRouteAction.PROCEED_DETAIL, decision.action)
        assertTrue(decision.requiresContentCorroboration)
    }

    @Test
    fun appraisalProceedsOnlyWithContentCorroboration() {
        val decision = ScreenStateRouter().fromClassification(classification(ScreenType.Appraisal))

        assertEquals(ScreenRouteAction.PROCEED_DETAIL, decision.action)
        assertTrue(decision.requiresContentCorroboration)
    }

    @Test
    fun storageGridProbeIsProvisionalAndRequiresMeasuredGridAndNameHp() {
        fun storage(score: Float, screen: ScreenType = ScreenType.StorageList) =
            ScreenClassificationResult(screen, score, listOf("measured_grid"),
                listOf(
                    com.pokerarity.scanner.util.ocr.ScreenAnchor(
                        com.pokerarity.scanner.util.ocr.ScreenAnchorName.StorageGrid,
                        android.graphics.Rect(0, 400, 900, 1800), score, "repetitive_grid_cells"),
                    com.pokerarity.scanner.util.ocr.ScreenAnchor(
                        com.pokerarity.scanner.util.ocr.ScreenAnchorName.DetailCard,
                        android.graphics.Rect(0, 585, 900, 1149), .74f, "bright_panel"),
                    com.pokerarity.scanner.util.ocr.ScreenAnchor(
                        com.pokerarity.scanner.util.ocr.ScreenAnchorName.CpHeader,
                        android.graphics.Rect(90, 100, 810, 200), .78f, "header"),
                    com.pokerarity.scanner.util.ocr.ScreenAnchor(
                        com.pokerarity.scanner.util.ocr.ScreenAnchorName.NameHpBand,
                        android.graphics.Rect(90, 650, 810, 950), .70f, "band")
                ), safeFallback = true)
        val router = ScreenStateRouter()
        for (score in listOf(9f / 12f, 11f / 12f, 1f)) {
            val result = storage(score)
            assertTrue("grid=$score", router.mayProbeConflictingStorage(result))
            // Eligibility alone never changes this decision into species authority.
            val decision = router.fromClassification(result)
            assertEquals(ScreenRouteAction.REJECT_NON_DETAIL, decision.action)
            assertFalse(decision.maySaveScan)
            assertFalse(decision.mayShowOverlay)
        }
        assertFalse(router.mayProbeConflictingStorage(storage(8f / 12f)))
        assertFalse(router.mayProbeConflictingStorage(storage(5f / 12f, ScreenType.Unknown)))
        val withoutDetailCard = storage(1f).copy(anchors = storage(1f).anchors.filterNot {
            it.name == com.pokerarity.scanner.util.ocr.ScreenAnchorName.DetailCard
        })
        assertTrue(router.mayProbeConflictingStorage(withoutDetailCard))
        val withoutNameHp = storage(1f).copy(anchors = storage(1f).anchors.filterNot {
            it.name == com.pokerarity.scanner.util.ocr.ScreenAnchorName.NameHpBand
        })
        assertFalse(router.mayProbeConflictingStorage(withoutNameHp))
        assertFalse(router.mayProbeConflictingStorage(storage(1f).copy(safeFallback = false)))
    }

    @Test
    fun routeUsesTheInjectedClassifierAndNeverCreatesSpeciesAuthority() {
        val router = ScreenStateRouter { _ -> classification(ScreenType.StorageList, 1.0f) }
        val decision = router.route(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))

        assertEquals(ScreenRouteAction.REJECT_NON_DETAIL, decision.action)
        assertEquals(ScreenType.StorageList, decision.screenType)
    }

    @Test
    fun routeDecisionNeverCarriesSpeciesAuthorityFields() {
        // Routing authority must not be convertible into species authority: the typed
        // decision exposes no species/authority surface at all.
        val decision: ScreenRouteDecision =
            ScreenStateRouter().fromClassification(classification(ScreenType.PokemonDetail))
        assertFalse(decision.maySaveScan == true && decision.action != ScreenRouteAction.PROCEED_DETAIL)
    }
}
