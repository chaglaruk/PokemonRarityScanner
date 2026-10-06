package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap

/**
 * Typed screen-routing decisions (Phase 2A screen-state router).
 *
 * Routing authority and species authority are different: the router only answers whether
 * a frame should enter detail recognition. It never establishes species identity and its
 * decisions carry no species-authority surface. The Phase 1 identity contract stays
 * authoritative after routing, and content corroboration still applies post-OCR.
 */
enum class ScreenRouteAction {
    /** Plausible supported detail-like state: enters the normal OCR/content path. */
    PROCEED_DETAIL,

    /** Stable non-detail (storage list, encounter/map): terminal, no species OCR. */
    REJECT_NON_DETAIL,

    /** Unstable in-between frame: retryable, no species OCR. */
    RETRY_UNSTABLE,

    /** Unrecognizable screen: fail closed, retryable, no species OCR. */
    RETRY_UNKNOWN
}

data class ScreenRouteDecision(
    val action: ScreenRouteAction,
    val screenType: ScreenType,
    val confidence: Float,
    val safeFallback: Boolean,
    val requiresContentCorroboration: Boolean,
    val reason: String
) {
    val mayShowOverlay: Boolean get() = action == ScreenRouteAction.PROCEED_DETAIL
    val maySaveScan: Boolean get() = action == ScreenRouteAction.PROCEED_DETAIL
}

/** Typed Phase 2A routing contract paired with the classification it was derived from. */
data class RoutedScreen(
    val decision: ScreenRouteDecision,
    val classification: ScreenClassificationResult
)

/** Terminal outcome when NO frame of a request entered detail recognition. */
enum class ScreenRouteOutcome {
    NOT_POKEMON_SCREEN,
    RETRY_UNSTABLE,
    RETRY_UNKNOWN
}

/** Per-frame routing record for diagnostics; non-OCR frames have no FrameDiagnostic. */
data class FrameRouteDiagnostic(
    val frameIndex: Int,
    val path: String,
    val action: ScreenRouteAction,
    val screenType: String,
    val confidence: Float,
    val safeFallback: Boolean,
    val reason: String
)

/**
 * Aggregates per-frame route decisions once it is clear that no frame produced a usable
 * detail result. Returns null when there is nothing to aggregate (no routed frames) or
 * when any frame entered detail recognition (those are real OCR failures, not routing
 * skips, so the caller keeps its existing failure semantics).
 *
 * Detail frames never appear here as terminal inputs: a request with any eligible detail
 * frame runs the normal detail pipeline, so one non-detail frame cannot poison a stable
 * detail frame and one detail-like frame cannot override a stable non-detail set — the
 * false-detail-like list frame is rejected by the same classifier rule as the stable set.
 */
internal fun aggregateFrameRoutes(routes: List<FrameRouteDiagnostic>): ScreenRouteOutcome? =
    when {
        routes.isEmpty() -> null
        routes.any { it.action == ScreenRouteAction.PROCEED_DETAIL } -> null
        routes.any { it.action == ScreenRouteAction.RETRY_UNKNOWN } -> ScreenRouteOutcome.RETRY_UNKNOWN
        routes.any { it.action == ScreenRouteAction.RETRY_UNSTABLE } -> ScreenRouteOutcome.RETRY_UNSTABLE
        else -> ScreenRouteOutcome.NOT_POKEMON_SCREEN
    }

/**
 * Routes a screen classification to a typed decision. Cheap bitmap/anchor evidence only;
 * runs before any OCR. PokemonDetailScrolled and Appraisal proceed but are marked as
 * requiring content corroboration: generic pixel geometry alone never justifies accepting
 * a scrolled or appraisal screen.
 */
class ScreenStateRouter(
    private val classify: (Bitmap) -> ScreenClassificationResult = { ScreenClassifier().classify(it) }
) {

    fun route(bitmap: Bitmap): ScreenRouteDecision = routeWithClassification(bitmap).decision

    /**
     * Routing plus the underlying classification in one pass (Phase 2B): persistent
     * calibration consumes the already-computed anchor evidence instead of re-classifying.
     */
    fun routeWithClassification(bitmap: Bitmap): RoutedScreen {
        val classification = classify(bitmap)
        return RoutedScreen(fromClassification(classification), classification)
    }

    fun fromClassification(result: ScreenClassificationResult): ScreenRouteDecision {
        val confidence = result.confidence
        val safeFallback = result.safeFallback
        return when (result.screenType) {
            ScreenType.PokemonDetail ->
                ScreenRouteDecision(
                    ScreenRouteAction.PROCEED_DETAIL, result.screenType, confidence, safeFallback,
                    requiresContentCorroboration = false, reason = "detail_route"
                )
            ScreenType.PokemonDetailScrolled ->
                ScreenRouteDecision(
                    ScreenRouteAction.PROCEED_DETAIL, result.screenType, confidence, safeFallback,
                    requiresContentCorroboration = true,
                    reason = "detail_scrolled_route_requires_content_corroboration"
                )
            ScreenType.Appraisal ->
                ScreenRouteDecision(
                    ScreenRouteAction.PROCEED_DETAIL, result.screenType, confidence, safeFallback,
                    requiresContentCorroboration = true,
                    reason = "appraisal_route_requires_content_corroboration"
                )
            ScreenType.StorageList ->
                ScreenRouteDecision(
                    ScreenRouteAction.REJECT_NON_DETAIL, result.screenType, confidence, safeFallback,
                    requiresContentCorroboration = false, reason = "storage_list_terminal"
                )
            ScreenType.Encounter ->
                ScreenRouteDecision(
                    ScreenRouteAction.REJECT_NON_DETAIL, result.screenType, confidence, safeFallback,
                    requiresContentCorroboration = false, reason = "encounter_non_detail_terminal"
                )
            ScreenType.Transition ->
                ScreenRouteDecision(
                    ScreenRouteAction.RETRY_UNSTABLE, result.screenType, confidence, safeFallback,
                    requiresContentCorroboration = false, reason = "transition_unstable"
                )
            ScreenType.Unknown ->
                ScreenRouteDecision(
                    ScreenRouteAction.RETRY_UNKNOWN, result.screenType, confidence, safeFallback,
                    requiresContentCorroboration = false, reason = "unknown_screen_fail_closed"
                )
        }
    }
}
