package com.pokerarity.scanner

import com.pokerarity.scanner.util.ocr.FrameRouteDiagnostic
import com.pokerarity.scanner.util.ocr.ScreenRouteAction
import com.pokerarity.scanner.util.ocr.ScreenRouteOutcome
import com.pokerarity.scanner.util.ocr.aggregateFrameRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Bounded multi-frame routing semantics for a single capture request. A non-detail frame
 * must not poison a legitimate stable detail frame, and a single accidental detail-like
 * frame must not override a stable non-detail set. (Per-request frame routing only;
 * request ownership belongs to Phase 2F.)
 */
class ScreenRouteAggregationTest {

    private fun route(action: ScreenRouteAction, index: Int = 0) = FrameRouteDiagnostic(
        frameIndex = index,
        path = "route-test-$index",
        action = action,
        screenType = action.name,
        confidence = 0.9f,
        safeFallback = false,
        reason = "test"
    )

    @Test
    fun noRoutedFramesYieldNoAggregate() {
        assertNull(aggregateFrameRoutes(emptyList()))
    }

    @Test
    fun detailAttemptsAreRealOcrFailuresNotRoutingSkips() {
        // When any frame entered detail recognition, an empty result set is a real OCR
        // failure (no usable detail result), not an intentional routing skip.
        assertNull(
            aggregateFrameRoutes(
                listOf(route(ScreenRouteAction.PROCEED_DETAIL, 0), route(ScreenRouteAction.PROCEED_DETAIL, 1))
            )
        )
    }

    @Test
    fun transitionPlusDetailDefersToTheDetailPipeline() {
        // The detail frame proceeded; the transition frame may not poison it, and an
        // empty result stays the caller's real-OCR-failure path, never a terminal route.
        assertNull(
            aggregateFrameRoutes(
                listOf(route(ScreenRouteAction.RETRY_UNSTABLE, 0), route(ScreenRouteAction.PROCEED_DETAIL, 1))
            )
        )
    }

    @Test
    fun stableStorageSetIsTerminalNotPokemonScreen() {
        assertEquals(
            ScreenRouteOutcome.NOT_POKEMON_SCREEN,
            aggregateFrameRoutes(
                listOf(route(ScreenRouteAction.REJECT_NON_DETAIL, 0), route(ScreenRouteAction.REJECT_NON_DETAIL, 1))
            )
        )
    }

    @Test
    fun unknownSetFailsClosedAsRetryable() {
        assertEquals(
            ScreenRouteOutcome.RETRY_UNKNOWN,
            aggregateFrameRoutes(
                listOf(route(ScreenRouteAction.RETRY_UNKNOWN, 0), route(ScreenRouteAction.RETRY_UNKNOWN, 1))
            )
        )
    }

    @Test
    fun stableNonDetailPlusFalseDetailLikeListFrameStaysTerminal() {
        // A false-detail-like list frame is rejected by the same storage rule, so the
        // whole set stays terminal non-detail and can never produce species acceptance.
        assertEquals(
            ScreenRouteOutcome.NOT_POKEMON_SCREEN,
            aggregateFrameRoutes(
                listOf(route(ScreenRouteAction.REJECT_NON_DETAIL, 0), route(ScreenRouteAction.REJECT_NON_DETAIL, 1))
            )
        )
    }

    @Test
    fun unknownFrameDominatesATransitionFrameForFailClosedRetry() {
        assertEquals(
            ScreenRouteOutcome.RETRY_UNKNOWN,
            aggregateFrameRoutes(
                listOf(route(ScreenRouteAction.RETRY_UNSTABLE, 0), route(ScreenRouteAction.RETRY_UNKNOWN, 1))
            )
        )
    }
}
