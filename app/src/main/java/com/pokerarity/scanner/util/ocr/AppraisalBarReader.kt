package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

private const val FILL_MIN_CHROMA = 90.0
private const val TRACK_MIN_BRIGHTNESS = 120.0
private const val TRACK_MAX_BRIGHTNESS = 240.0
private const val TRACK_MAX_CHROMA = 70.0
private const val RGB_MASK = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

/**
 * Phase 3D geometric appraisal-bar reader (plan §7.5) — stage A pixel observation.
 *
 * Reads each stat bar band inside the anchor-derived `ScreenField.Appraisal*` crops by
 * crop-local statistics, never absolute screen coordinates:
 *
 *  1. per-column fill profile: a column is FILL when its band rows contain colored
 *     (high-chroma) pixels — the colored stat fill is chromatic while panel text,
 *     white icons and the grey track are low-chroma;
 *  2. per-column track profile: a column is TRACK when its rows contain the bar
 *     structure (fill OR the grey track: mid brightness, low chroma);
 *  3. the track is the longest contiguous column run with track presence; interior
 *     divider gaps of the fill are tolerated via the divider tolerance so divider
 *     marks never become endpoints;
 *  4. the endpoint is the last fill column of the run, normalized to the measured
 *     track extent, with a divider/antialiasing uncertainty interval.
 *
 * MEASUREMENT STATUS: geometry and colors are checked against 24 human-checked
 * Samsung appraisal frames at 900/native width. IV labels and a calibrated fill-to-IV
 * mapping remain absent. This reader is DIAGNOSTIC_ONLY: the production recognition
 * path does not consume its endpoints, and IV interpretation remains disabled.
 * The uncertainty interval is provisional; it is not calibrated IV truth.
 */
@Suppress("MagicNumber")
internal object AppraisalBarReader {

    private const val MIN_BAND_PX = 40
    private const val MIN_BAND_HEIGHT = 4
    // The grey track must be distinguishable from the bright white panel background:
    // a brightness ceiling keeps panel columns out of the track run.
    private const val TRACK_ROW_MIN_COUNT = 2
    private const val TRACK_MIN_COLUMNS = 12
    private const val FILL_COLUMN_MIN_ROWS = 2
    private const val DIVIDER_TOLERANCE_COLUMNS = 3
    private const val ENDPOINT_UNCERTAINTY_FRACTION = 0.02

    /** Production entry: measures the three anchor-derived stat bands of one frame. */
    fun observe(bitmap: Bitmap, geometry: ScreenGeometry?): AppraisalBarObservation {
        val stats = AppraisalStat.entries.associateWith { stat ->
            observeStat(bitmap, geometry, stat)
        }
        return AppraisalBarObservation(readings = stats, reasonCodes = listOf("appraisal_observed"))
    }

    private fun observeStat(
        bitmap: Bitmap,
        geometry: ScreenGeometry?,
        stat: AppraisalStat
    ): AppraisalStatObservation {
        val crop = geometry?.crop(stat.field)
        val rect = crop?.rect
        return when {
            crop == null || crop.provenance != CropProvenance.AnchorDerived || rect == null ->
                unsupported("appraisal_geometry_unavailable")
            rect.width() < MIN_BAND_PX || rect.height() < MIN_BAND_HEIGHT ||
                rect.left < 0 || rect.top < 0 ||
                rect.right > bitmap.width || rect.bottom > bitmap.height ->
                unsupported("appraisal_geometry_malformed")
            else -> {
                val pixels = bandPixels(bitmap, rect)
                observeStatBand(
                    bandWidth = rect.width(),
                    bandHeight = rect.height(),
                    brightness = pixels.first,
                    saturation = pixels.second,
                    geometryTrusted = true
                )
            }
        }
    }

    private fun unsupported(reason: String): AppraisalStatObservation =
        AppraisalStatObservation(
            endpoint = AppraisalStatObservation.EndpointState.Unsupported(reason),
            fillFraction = null,
            reasonCodes = listOf(reason)
        )

    /**
     * Pure core (no Android types): reads ONE stat bar band. [geometryTrusted] is false
     * for missing/legacy-fallback crops, which never produce appraisal evidence.
     */
    fun observeStatBand(
        bandWidth: Int,
        bandHeight: Int,
        brightness: FloatArray,
        saturation: FloatArray,
        geometryTrusted: Boolean
    ): AppraisalStatObservation = when {
        !geometryTrusted || bandWidth < MIN_BAND_PX || bandHeight < MIN_BAND_HEIGHT ||
            brightness.size.toLong() < bandWidth.toLong() * bandHeight || saturation.size != brightness.size ->
            unsupported("appraisal_band_malformed")
        else -> {
            val band = BandPixels(brightness, saturation, bandWidth, bandHeight)
            val fillProfile = columnFillProfile(band)
            val track = longestTrackRun(columnTrackProfile(band), fillProfile)
            val endpoint = track?.let { fillEndpoint(fillProfile, it) }
            when {
                track == null -> unknown("bar_track_not_found", null)
                track.second - track.first + 1 < TRACK_MIN_COLUMNS -> unknown("bar_track_too_short", null)
                endpoint == null -> unknown("no_fill_transition", 0f)
                else -> endpointObservation(endpoint, track)
            }
        }
    }

    private fun unknown(reason: String, fillFraction: Float?): AppraisalStatObservation =
        AppraisalStatObservation(
            endpoint = AppraisalStatObservation.EndpointState.Unknown(reason),
            fillFraction = fillFraction,
            reasonCodes = listOf(reason)
        )

    /**
     * The last fill column of the run starting at the track start, tolerating interior
     * divider gaps so divider marks never become endpoints; null when no fill at all.
     */
    private fun fillEndpoint(fillProfile: IntArray, track: Pair<Int, Int>): Int? {
        val (trackLeft, trackRight) = track
        var lastFill = -1
        var gap = 0
        var cursor = trackLeft
        while (cursor <= trackRight) {
            if (fillProfile[cursor] >= FILL_COLUMN_MIN_ROWS) {
                lastFill = cursor
                gap = 0
            } else {
                gap++
                if (gap > maxOf(DIVIDER_TOLERANCE_COLUMNS, fillProfile.size / 50)) break
            }
            cursor++
        }
        return if (lastFill >= trackLeft) lastFill else null
    }

    private fun endpointObservation(lastFill: Int, track: Pair<Int, Int>): AppraisalStatObservation {
        val (trackLeft, trackRight) = track
        val trackSpan = (trackRight - trackLeft).coerceAtLeast(1)
        val endpoint = (lastFill - trackLeft).toDouble() / trackSpan
        val fillFraction = (lastFill - trackLeft + 1).toDouble() / (trackSpan + 1)
        val uncertainty = ENDPOINT_UNCERTAINTY_FRACTION.coerceAtLeast(1.0 / trackSpan)
        return AppraisalStatObservation(
            endpoint = AppraisalStatObservation.EndpointState.Interval(
                min = (endpoint - uncertainty).coerceIn(0.0, 1.0),
                max = (endpoint + uncertainty).coerceIn(0.0, 1.0)
            ),
            fillFraction = fillFraction.toFloat(),
            reasonCodes = listOf("bar_observed")
        )
    }

    private fun columnFillProfile(band: BandPixels): IntArray =
        IntArray(band.width) { x -> band.fillRows(x) }

    private fun columnTrackProfile(band: BandPixels): IntArray =
        IntArray(band.width) { x -> band.trackRows(x) }

    /**
     * The bar track is the longest contiguous column run where the track (grey bar or
     * fill) is present; isolated bright non-track columns (text/icons) are excluded by
     * the per-column count threshold.
     */
    private fun longestTrackRun(
        trackProfile: IntArray,
        fillProfile: IntArray
    ): Pair<Int, Int>? {
        var bestLeft = -1
        var bestRight = -1
        var bestLength = 0
        var runLeft = -1
        var runLength = 0
        var lastTracked = -1
        var gap = 0
        for (x in trackProfile.indices) {
            val tracked = trackProfile[x] >= TRACK_ROW_MIN_COUNT || fillProfile[x] >= FILL_COLUMN_MIN_ROWS
            if (tracked) {
                if (runLeft < 0) runLeft = x
                lastTracked = x
                runLength = x - runLeft + 1
                gap = 0
            } else if (runLeft >= 0 && ++gap > maxOf(DIVIDER_TOLERANCE_COLUMNS, trackProfile.size / 50)) {
                if (runLength > bestLength) {
                    bestLength = runLength
                    bestLeft = runLeft
                    bestRight = lastTracked
                }
                runLeft = -1
                runLength = 0
                gap = 0
            }
        }
        if (runLeft >= 0 && runLength > bestLength) {
            bestLeft = runLeft
            bestRight = lastTracked
        }
        return if (bestLeft < 0) null else bestLeft to bestRight
    }
}

private fun bandPixels(bitmap: Bitmap, rect: android.graphics.Rect): Pair<FloatArray, FloatArray> {
    val pixels = IntArray(rect.width() * rect.height())
    bitmap.getPixels(pixels, 0, rect.width(), rect.left, rect.top, rect.width(), rect.height())
    val brightness = FloatArray(pixels.size)
    val saturation = FloatArray(pixels.size)
    for (index in pixels.indices) {
        val pixel = pixels[index]
        val red = (pixel shr RED_SHIFT) and RGB_MASK
        val green = (pixel shr GREEN_SHIFT) and RGB_MASK
        val blue = pixel and RGB_MASK
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
    fun fillRows(x: Int): Int {
        if (x !in 0 until width) return 0
        var count = 0
        for (y in 0 until height) {
            if (saturation[y * width + x] >= FILL_MIN_CHROMA) count++
        }
        return count
    }

    fun trackRows(x: Int): Int {
        if (x !in 0 until width) return 0
        var count = 0
        for (y in 0 until height) {
            val index = y * width + x
            val isTrack = brightness[index] in TRACK_MIN_BRIGHTNESS..TRACK_MAX_BRIGHTNESS &&
                saturation[index] <= TRACK_MAX_CHROMA
            if (isTrack) count++
        }
        return count
    }
}
