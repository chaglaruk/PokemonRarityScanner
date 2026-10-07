package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Phase 3C forward-model arc fitter (plan §7.4) — pixel/curve observation stage.
 *
 * Independently designed; the historical degenerate arc detector inside
 * ImagePreprocessor is NOT a starting point and must never be called from production
 * recognition. Measured signal (development 1080x2340 corpus, see the Phase 3C
 * measurement report): the CP arc is a thin ring segment; its traversed portion is
 * bright white fill (measured ~255) ending at a bright marker, while the remaining
 * portion is a dim grey line (measured ~141-152) — NOT a uniform white fill. The
 * fitter therefore:
 *
 *  1. fits the ring inside the geometry-derived arc band by maximizing the angular
 *     coverage of the thin whitish curve mask (no fixed center/radius screen ratios —
 *     the search space derives from the band rect);
 *  2. profiles brightness along the fitted ring, detects the fill boundary and the
 *     marker, and returns a continuous normalized parameter t (0 = arc start at the
 *     lower-left terminus, 1 = arc end at the lower-right terminus) with fit metadata;
 *  3. fails closed: weak geometry, low coverage, no fill transition, band-clipped or
 *     occluded boundaries, or ambiguous runs yield typed Unknown/Unsupported — never a
 *     forced scalar.
 *
 * Level interpretation is intentionally OUT of scope here: the position→level mapping
 * is not established by the development evidence, so production consumes
 * [ArcSignalObservation] only diagnostically. See [ArcLevelInterpreter].
 */
// The bounded pixel-search structure (grid/refine loops, multi-state observation
// pipeline, geometry parameters) is inherent to a forward-model fitter; its complexity
// findings are acknowledged here rather than scattered across the algorithm.
@Suppress(
    "MagicNumber",
    "TooManyFunctions",
    "LongParameterList",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LoopWithTooManyJumpStatements",
    "ReturnCount",
    "MaxLineLength"
)
internal object ArcSignalFitter {

    // Bounded thresholds measured on the development corpus (fill ~255, unfilled line
    // ~141-152, local background 77-85); centralized here, never per-candidate tuned.
    private const val LINE_PRESENCE_BRIGHTNESS = 120.0
    private const val FILL_BRIGHTNESS = 235.0
    private const val MARKER_ANNULUS_FILL = 250.0
    private const val MIN_CURVE_BRIGHTNESS = 140.0
    private const val MAX_CURVE_SATURATION = 70.0
    private const val LINE_LOCAL_CONTRAST = 20.0
    private const val BACKGROUND_OFFSET_PX = 16
    private const val FIT_ANNULUS_PX = 4
    private const val COARSE_ANNULUS_PX = 8
    private const val PROFILE_ANNULUS_PX = 7
    private const val MIN_RING_COVERAGE = 0.60
    private const val MIN_BAND_PX = 80
    private const val MIN_FILL_RUN_DEGREES = 2.0
    private const val ANGULAR_GAP_TOLERANCE_DEGREES = 3.0
    private const val MIN_ARC_RUN_DEGREES = 60.0
    private const val MARKER_TOLERANCE = 0.03
    private const val PROFILE_STEP_DEGREES = 0.5
    private const val PROFILE_STEPS = 360
    private const val RING_COARSE_STEP_PX = 8.0
    private const val COARSE_SEARCH_MIN = 0.30
    private const val COARSE_SEARCH_MAX = 0.60
    private const val CENTER_X_SEARCH_MIN = 0.35
    private const val CENTER_X_SEARCH_MAX = 0.65
    // The ring center sits near the arc band's lower edge (the arc opens downward), so
    // the vertical search must reach the band bottom, unlike the horizontal bounds.
    private const val CENTER_Y_SEARCH_MIN = 0.20
    private const val CENTER_Y_SEARCH_MAX = 1.0

    /**
     * Minimum in-band angles for a credible coverage score. Without this floor a
     * degenerate circle whose in-band extent is a handful of angles would score a
     * perfect coverage and beat the true ring during the search.
     */
    private const val MIN_OBSERVABLE_ANGLES = 60

    /** Production entry: extracts the arc band from the current recognition frame. */
    fun observe(bitmap: Bitmap, geometry: ScreenGeometry?): ArcSignalObservation {
        val crop = geometry?.crop(ScreenField.Arc)
        val rect = crop?.rect
        val malformed = rect == null || rect.width() < MIN_BAND_PX || rect.height() < MIN_BAND_PX ||
            rect.left < 0 || rect.top < 0 || rect.right > bitmap.width || rect.bottom > bitmap.height
        return when {
            crop == null -> gated("arc_geometry_unavailable", null)
            crop.provenance != CropProvenance.AnchorDerived -> gated("arc_geometry_untrusted", null)
            malformed -> gated("arc_geometry_malformed", null)
            else -> {
                val band = bandPixels(bitmap, requireNotNull(rect))
                observeBand(
                    bandWidth = rect.width(),
                    bandHeight = rect.height(),
                    brightness = band.first,
                    saturation = band.second,
                    geometryTrusted = true,
                    geometryDegraded = geometry.fallbackReasons.isNotEmpty()
                )
            }
        }
    }

    /**
     * Pure core (no Android types): fits the ring and observes the fill boundary inside
     * the brightness band of the geometry-derived arc crop. [geometryTrusted] is false
     * for legacy/fallback provenance, which never produces arc authority. The band
     * origin is irrelevant to the fit (coordinates are band-local), so it is not part
     * of the pure contract.
     */
    fun observeBand(
        bandWidth: Int,
        bandHeight: Int,
        brightness: FloatArray,
        saturation: FloatArray,
        geometryTrusted: Boolean,
        geometryDegraded: Boolean
    ): ArcSignalObservation = when {
        !geometryTrusted -> gated("arc_geometry_untrusted", null)
        bandWidth < MIN_BAND_PX || bandHeight < MIN_BAND_PX ||
            brightness.size < bandWidth * bandHeight ||
            saturation.size != brightness.size -> gated("arc_geometry_malformed", null)
        else -> {
            val provenance = if (geometryDegraded) "arc_geometry_degraded" else "arc_geometry_anchor_derived"
            val band = BandPixels(brightness, saturation, bandWidth, bandHeight)
            when (val ring = fitRing(band, bandWidth, bandHeight)) {
                null -> gated("arc_ring_not_found", provenance)
                else -> {
                    val lowCoverage = ring.coverage < MIN_RING_COVERAGE
                    when {
                        lowCoverage -> ArcSignalObservation(
                            parameter = ArcSignalObservation.ParameterState.Unknown("arc_ring_coverage_low"),
                            ringCoverage = ring.coverage,
                            fillFraction = null,
                            reasonCodes = listOf("arc_ring_coverage_low", provenance)
                        )
                        else -> observeSignal(band, ring, provenance)
                    }
                }
            }
        }
    }

    private fun gated(reason: String, provenance: String?): ArcSignalObservation =
        ArcSignalObservation(
            parameter = ArcSignalObservation.ParameterState.Unsupported(reason),
            ringCoverage = 0f,
            fillFraction = null,
            reasonCodes = listOfNotNull(reason, provenance)
        )


    private fun bandPixels(bitmap: Bitmap, rect: android.graphics.Rect): Pair<FloatArray, FloatArray> {
        val pixels = IntArray(rect.width() * rect.height())
        bitmap.getPixels(pixels, 0, rect.width(), rect.left, rect.top, rect.width(), rect.height())
        val brightness = FloatArray(pixels.size)
        val saturation = FloatArray(pixels.size)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            val maxValue = max(red, max(green, blue))
            val minValue = min(red, min(green, blue))
            brightness[index] = (maxValue + minValue) / 2.0f
            saturation[index] = (maxValue - minValue).toFloat()
        }
        return brightness to saturation
    }

    private data class BandPixels(
        val brightness: FloatArray,
        val saturation: FloatArray,
        val width: Int,
        val height: Int
    ) {
        fun at(x: Int, y: Int): Float =
            if (x in 0 until width && y in 0 until height) brightness[y * width + x] else 0f

        /** Thin whitish curve pixel: bright enough and low-saturation (grey/white). */
        fun isCurvePixel(x: Int, y: Int): Boolean {
            if (x !in 0 until width || y !in 0 until height) return false
            val index = y * width + x
            return brightness[index] >= MIN_CURVE_BRIGHTNESS && saturation[index] <= MAX_CURVE_SATURATION
        }
    }

    private data class RingFit(val centerX: Double, val centerY: Double, val radius: Double, val coverage: Float)

    private data class RingProfile(
        val ringMax: Double,
        val contrast: Double,
        val annulusFillCount: Int,
        val observable: Boolean
    ) {
        val hasLine: Boolean get() = observable && ringMax >= LINE_PRESENCE_BRIGHTNESS && contrast > LINE_LOCAL_CONTRAST
        val isFill: Boolean get() = observable && ringMax >= FILL_BRIGHTNESS
        val isMarker: Boolean get() = observable && annulusFillCount >= 2 * PROFILE_ANNULUS_PX - 1
    }

    /**
     * Bounded coarse-to-fine search of the ring (center + radius) inside the band,
     * maximizing the angular coverage of the thin whitish curve mask. The search space
     * derives from the band rect; no absolute screen ratios are used. Angles whose base
     * ring point leaves the band are unobservable and are excluded from the coverage
     * denominator (the production arc band can clip the ring top; fitting uses the
     * visible segments only). The minimum observable-angle floor prevents degenerate
     * circles whose tiny in-band extent would otherwise score a perfect coverage, and
     * the coarse pass uses a wide annulus contiguous with its grid step so the true
     * ring cannot slip between grid points; the tight-annulus refine then recovers
     * pixel precision.
     */
    private fun fitRing(band: BandPixels, width: Int, height: Int): RingFit? {
        val angles = DoubleArray(161) { index -> 10.0 + index }
        fun coverage(cx: Double, cy: Double, radius: Double, annulusPx: Int): Float {
            var hits = 0
            var observable = 0
            for (angle in angles) {
                val rad = Math.toRadians(angle)
                val dirX = cos(rad)
                val dirY = -sin(rad)
                if ((cx + radius * dirX).roundToInt() !in 0 until width ||
                    (cy + radius * dirY).roundToInt() !in 0 until height
                ) continue
                observable++
                var curveHit = false
                for (d in -annulusPx..annulusPx) {
                    if (band.isCurvePixel((cx + (radius + d) * dirX).roundToInt(), (cy + (radius + d) * dirY).roundToInt())) {
                        curveHit = true
                        break
                    }
                }
                if (curveHit) hits++
            }
            if (observable < MIN_OBSERVABLE_ANGLES) return 0f
            return hits.toFloat() / observable
        }

        var bestCoverage = -1f
        var best = RingFit(0.0, 0.0, 0.0, 0f)
        var radius = width * COARSE_SEARCH_MIN
        while (radius <= width * COARSE_SEARCH_MAX) {
            var cx = width * CENTER_X_SEARCH_MIN
            while (cx <= width * CENTER_X_SEARCH_MAX) {
                var cy = height * CENTER_Y_SEARCH_MIN
                while (cy <= height * CENTER_Y_SEARCH_MAX) {
                    val score = coverage(cx, cy, radius, COARSE_ANNULUS_PX)
                    if (score > bestCoverage) {
                        bestCoverage = score
                        best = RingFit(cx, cy, radius, score)
                    }
                    cy += RING_COARSE_STEP_PX
                }
                cx += RING_COARSE_STEP_PX
            }
            radius += RING_COARSE_STEP_PX
        }
        if (bestCoverage < 0) return null
        var cx = best.centerX
        var cy = best.centerY
        var r = best.radius
        for (step in intArrayOf(8, 4, 2, 1)) {
            var improved = true
            while (improved) {
                improved = false
                for (dr in intArrayOf(-step, 0, step)) {
                    for (dx in intArrayOf(-step, 0, step)) {
                        for (dy in intArrayOf(-step, 0, step)) {
                            val score = coverage(cx + dx, cy + dy, r + dr, FIT_ANNULUS_PX)
                            if (score > bestCoverage) {
                                bestCoverage = score
                                cx += dx; cy += dy; r += dr
                                improved = true
                            }
                        }
                    }
                }
            }
        }
        return RingFit(cx, cy, r, bestCoverage)
    }

    private fun scanProfiles(band: BandPixels, ring: RingFit): List<RingProfile> {
        val profiles = mutableListOf<RingProfile>()
        for (index in 0 until PROFILE_STEPS) {
            val angle = index * PROFILE_STEP_DEGREES
            if (angle > 180.0) break
            val rad = Math.toRadians(angle)
            val baseX = (ring.centerX + ring.radius * cos(rad)).roundToInt()
            val baseY = (ring.centerY - ring.radius * sin(rad)).roundToInt()
            if (baseX !in 0 until band.width || baseY !in 0 until band.height) {
                profiles += RingProfile(0.0, 0.0, 0, false)
                continue
            }
            var ringMax = 0.0
            var annulusFillCount = 0
            for (d in -PROFILE_ANNULUS_PX..PROFILE_ANNULUS_PX) {
                val value = band.at(
                    (ring.centerX + (ring.radius + d) * cos(rad)).roundToInt(),
                    (ring.centerY - (ring.radius + d) * sin(rad)).roundToInt()
                ).toDouble()
                ringMax = max(ringMax, value)
                if (value >= MARKER_ANNULUS_FILL) annulusFillCount++
            }
            var bgMin = 255.0
            for (d in intArrayOf(-BACKGROUND_OFFSET_PX, BACKGROUND_OFFSET_PX)) {
                bgMin = min(bgMin, band.at(
                    (ring.centerX + (ring.radius + d) * cos(rad)).roundToInt(),
                    (ring.centerY - (ring.radius + d) * sin(rad)).roundToInt()
                ).toDouble())
            }
            profiles += RingProfile(ringMax, ringMax - bgMin, annulusFillCount, true)
        }
        return profiles
    }

    /**
     * Fill-boundary and marker observation along the fitted ring. The fill boundary is
     * scanned from the arc start (lower-left) with an angular gap tolerance for sprite
     * overlap; a boundary is credible only while the unfilled line remains visible
     * after it (a boundary where the line disappears is band clipping or occlusion).
     */
    private fun observeSignal(
        band: BandPixels,
        ring: RingFit,
        provenanceCode: String
    ): ArcSignalObservation {
        val profiles = scanProfiles(band, ring)
        val arcStart = scanExtent(profiles, fromIndex = profiles.lastIndex, direction = -1)
        val arcEnd = scanExtent(profiles, fromIndex = 0, direction = +1)
        val spanDegrees = if (arcStart == null || arcEnd == null) {
            0.0
        } else {
            (arcStart - arcEnd) * PROFILE_STEP_DEGREES
        }
        if (arcStart == null || arcEnd == null || spanDegrees < MIN_ARC_RUN_DEGREES) {
            return ArcSignalObservation(
                parameter = ArcSignalObservation.ParameterState.Unknown("arc_line_not_found"),
                ringCoverage = ring.coverage,
                fillFraction = null,
                reasonCodes = listOf("arc_line_not_found", provenanceCode)
            )
        }
        val (runs, boundaryOccluded) = significantFillRuns(profiles, arcStart, arcEnd)
        if (runs.isEmpty()) {
            return ArcSignalObservation(
                parameter = ArcSignalObservation.ParameterState.Unknown("no_fill_transition"),
                ringCoverage = ring.coverage,
                fillFraction = 0f,
                reasonCodes = listOf("no_fill_transition", provenanceCode)
            )
        }
        val fillFraction = runs.sumOf { (it[0] - it[1]) * PROFILE_STEP_DEGREES } / spanDegrees
        if (boundaryOccluded) {
            return ArcSignalObservation(
                parameter = ArcSignalObservation.ParameterState.Unknown("fill_boundary_occluded"),
                ringCoverage = ring.coverage,
                fillFraction = fillFraction.toFloat(),
                reasonCodes = listOf("fill_boundary_occluded", provenanceCode)
            )
        }
        return buildObservation(runs, profiles, ring.coverage, fillFraction, provenanceCode)
    }

    /**
     * Fill runs scanning from the arc start with the angular gap tolerance, plus
     * whether any closed run's trailing gap had NO visible line — that boundary is a
     * band-clip or occlusion edge, not a credible level boundary.
     */
    private fun significantFillRuns(
        profiles: List<RingProfile>,
        arcStart: Int,
        arcEnd: Int
    ): Pair<List<IntArray>, Boolean> {
        val fillRuns = mutableListOf<IntArray>()
        var boundaryOccluded = false
        var index = arcStart
        var runStart: Int? = null
        var gap = 0
        fun closeRun(lastFillIndex: Int, gapStartIndex: Int, gapCount: Int) {
            fillRuns += intArrayOf(runStart!!, lastFillIndex)
            // The closing gap itself: if no line is visible anywhere in it, the run
            // ended at an occlusion/band edge rather than at a real fill boundary.
            val gapIndices = gapStartIndex until min(profiles.size, gapStartIndex + gapCount)
            if (gapIndices.none { profiles[it].hasLine }) boundaryOccluded = true
        }
        while (index >= arcEnd) {
            val profile = profiles[index]
            when {
                !profile.observable -> {
                    if (runStart != null) {
                        closeRun((index + 1 + gap).coerceAtMost(runStart), index, gap + 1)
                        runStart = null
                    }
                    gap++
                }
                profile.isFill -> {
                    if (runStart == null) runStart = index
                    gap = 0
                }
                runStart != null -> {
                    gap++
                    if (gap * PROFILE_STEP_DEGREES > ANGULAR_GAP_TOLERANCE_DEGREES) {
                        closeRun(index + gap - 1, index, gap)
                        runStart = null
                    }
                }
            }
            index--
        }
        if (runStart != null) fillRuns += intArrayOf(runStart, arcEnd)
        return Pair(
            fillRuns.filter { (it[0] - it[1]) * PROFILE_STEP_DEGREES >= MIN_FILL_RUN_DEGREES },
            boundaryOccluded
        )
    }

    private fun buildObservation(
        runs: List<IntArray>,
        profiles: List<RingProfile>,
        coverage: Float,
        fillFraction: Double,
        provenanceCode: String
    ): ArcSignalObservation {
        // Geometric normalization: t = (180 - angle) / 180 relative to the FITTED ring
        // (t = 0 at the left terminus height, t = 1 at the right terminus height). This
        // is translation- and scale-invariant; the drawn arc's small terminus offset is
        // left to a future mapping calibration, never guessed here.
        fun parameterOf(index: Int): Double = (PROFILE_STEPS - index) / PROFILE_STEPS.toDouble()
        val markerBoundaries = runs.map { run ->
            // The marker sits at the trailing edge of the fill run; when the annulus-wide
            // dot is visible the midpoint of its extent refines the position.
            var cursor = run[1]
            while (cursor < profiles.size && cursor <= run[0] && profiles[cursor].isMarker) cursor++
            val markerIndex = if (cursor > run[1]) (run[1] + cursor - 1) / 2 else run[1]
            parameterOf(markerIndex)
        }.sorted()
        val parameter = if (markerBoundaries.size == 1) {
            val center = markerBoundaries.single()
            ArcSignalObservation.ParameterState.Interval(
                min = (center - MARKER_TOLERANCE).coerceIn(0.0, 1.0),
                max = (center + MARKER_TOLERANCE).coerceIn(0.0, 1.0)
            )
        } else {
            ArcSignalObservation.ParameterState.Alternatives(
                values = markerBoundaries,
                tolerance = MARKER_TOLERANCE
            )
        }
        return ArcSignalObservation(
            parameter = parameter,
            ringCoverage = coverage,
            fillFraction = fillFraction.coerceIn(0.0, 1.0).toFloat(),
            reasonCodes = listOf("arc_observed", provenanceCode)
        )
    }

    /**
     * First index with line support scanning in [direction]; null when none. The scan
     * tolerates short unobservable/occluded gaps but stops at long ones so the extent
     * never crosses the band edge or a wide occlusion.
     */
    private fun scanExtent(profiles: List<RingProfile>, fromIndex: Int, direction: Int): Int? {
        var index = fromIndex
        var gap = 0
        var found: Int? = null
        while (index in profiles.indices) {
            val profile = profiles[index]
            when {
                !profile.observable -> {
                    gap++
                    if (found != null && gap * PROFILE_STEP_DEGREES > ANGULAR_GAP_TOLERANCE_DEGREES) return found
                }
                profile.hasLine -> {
                    if (found == null) found = index
                    gap = 0
                }
                else -> {
                    gap++
                    if (found != null && gap * PROFILE_STEP_DEGREES > ANGULAR_GAP_TOLERANCE_DEGREES) return found
                }
            }
            index += direction
        }
        return found
    }
}

/**
 * Stage C seam: position→level interpretation. Production Phase 3C passes NO mapper —
 * the development evidence does not establish a trustworthy position→level mapping, so
 * the level authority stays [ArcLevelEvidence.Unknown] while the measured parameter is
 * carried as bounded metadata. Tests and a future evidence-backed slice may supply a
 * mapper to produce Range/Alternatives.
 */
internal object ArcLevelInterpreter {

    fun interface ArcLevelMapper {
        /** Maps a normalized parameter interval to a legal level window, or null if untrusted. */
        fun levelWindow(parameter: ClosedFloatingPointRange<Double>): ClosedFloatingPointRange<Double>?
    }

    fun interpret(
        observation: ArcSignalObservation?,
        mapper: ArcLevelMapper?
    ): ArcLevelEvidence = when {
        observation == null -> ArcLevelEvidence.Unsupported(listOf("arc_observation_absent"))
        observation.parameter is ArcSignalObservation.ParameterState.Unsupported ->
            ArcLevelEvidence.Unsupported(observation.reasonCodes + observation.parameter.reasonCode)
        observation.parameter is ArcSignalObservation.ParameterState.Unknown ->
            ArcLevelEvidence.Unknown(observation, observation.reasonCodes + observation.parameter.reasonCode)
        mapper == null -> ArcLevelEvidence.Unknown(
            observation,
            observation.reasonCodes + "arc_level_mapping_unestablished"
        )
        else -> interpretMapped(observation, mapper)
    }

    private fun interpretMapped(
        observation: ArcSignalObservation,
        mapper: ArcLevelMapper
    ): ArcLevelEvidence {
        val ranges = when (val parameter = observation.parameter) {
            is ArcSignalObservation.ParameterState.Interval ->
                listOfNotNull(mapper.levelWindow(parameter.min..parameter.max))
            is ArcSignalObservation.ParameterState.Alternatives ->
                parameter.values.mapNotNull { value ->
                    val window = (value - parameter.tolerance).coerceIn(0.0, 1.0)..
                        (value + parameter.tolerance).coerceIn(0.0, 1.0)
                    mapper.levelWindow(window)
                }
            else -> emptyList()
        }
        return when {
            ranges.isEmpty() -> ArcLevelEvidence.Unknown(
                observation, observation.reasonCodes + "arc_mapping_refused_window")
            ranges.size == 1 -> ArcLevelEvidence.Range(
                ranges.single().start, ranges.single().endInclusive,
                observation.reasonCodes + "arc_mapping_applied")
            else -> ArcLevelEvidence.Alternatives(ranges, observation.reasonCodes + "arc_mapping_ambiguous")
        }
    }
}
