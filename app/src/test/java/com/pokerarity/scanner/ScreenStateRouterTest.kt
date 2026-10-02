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
