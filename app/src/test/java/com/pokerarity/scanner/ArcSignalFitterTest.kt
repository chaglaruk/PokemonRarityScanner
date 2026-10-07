package com.pokerarity.scanner

import com.pokerarity.scanner.util.ocr.ArcLevelEvidence
import com.pokerarity.scanner.util.ocr.ArcLevelInterpreter
import com.pokerarity.scanner.util.ocr.ArcSignalFitter
import com.pokerarity.scanner.util.ocr.ArcSignalObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Phase 3C arc fitter semantics on independently constructed synthetic scenes. The
 * brightness bands reproduce the measured game facts (thin grey unfilled line ~150,
 * bright white fill and marker ~255, dark background ~70) WITHOUT mirroring fitter
 * internals: expectations are parameter neighborhoods, not implementation echoes.
 *
 * Band geometry mirrors the production arc crop on a 1080x2340 frame: band origin
 * (108, 339), band 864x442, ring center (542, 682), radius 434 (in-frame coordinates).
 */
@Suppress("MagicNumber")
class ArcSignalFitterTest {

    private val bandWidth = 864
    private val bandHeight = 442
    private val ringCX = 434.0   // band coordinates (542 - 108)
    private val ringCY = 343.0   // band coordinates (682 - 339)
    private val ringR = 434.0

    private val background = 70f
    private val lineBrightness = 150f
    private val fillBrightness = 255f

    /**
     * Paints the synthetic brightness band; angles in degrees (0 = right, 180 = left),
     * fill runs from 176 degrees down to each marker angle, marker dot on top.
     */
    @Suppress("LongParameterList")  // documented synthetic fixture builder
    private fun scene(
        markerAngles: List<Double>,
        lineBrightness: Float = this.lineBrightness,
        fillBrightness: Float = this.fillBrightness,
        background: Float = this.background,
        offCurveBlob: Pair<Double, Double>? = null,
        occlusionAngles: ClosedFloatingPointRange<Double>? = null,
        extraFillRun: ClosedFloatingPointRange<Double>? = null,
        scale: Double = 1.0,
        translate: Pair<Double, Double> = 0.0 to 0.0
    ): FloatArray {
        val width = (bandWidth * scale).roundToInt()
        val height = (bandHeight * scale).roundToInt()
        val cx = ringCX * scale + translate.first
        val cy = ringCY * scale + translate.second
        val radius = ringR * scale
        val band = FloatArray(width * height) { background }
        val saturation = FloatArray(width * height) { 10f }
        fun paint(x: Double, y: Double, value: Float, radiusPx: Double) {
            val x0 = (x - radiusPx).roundToInt().coerceIn(0, width - 1)
            val x1 = (x + radiusPx).roundToInt().coerceIn(0, width - 1)
            val y0 = (y - radiusPx).roundToInt().coerceIn(0, height - 1)
            val y1 = (y + radiusPx).roundToInt().coerceIn(0, height - 1)
            for (yy in y0..y1) for (xx in x0..x1) {
                band[yy * width + xx] = value
                saturation[yy * width + xx] = 10f
            }
        }
        var angle = 2.0
        while (angle <= 176.0) {
            val rad = Math.toRadians(angle)
            val x = cx + radius * cos(rad)
            val y = cy - radius * sin(rad)
            val marker = markerAngles.any { abs(angle - it) <= 1.0 }
            val filled = markerAngles.any { angle > it }
            val occluded = occlusionAngles?.let { angle in it } == true
            when {
                marker -> paint(x, y, fillBrightness, 5.0 * scale)
                filled && !occluded -> paint(x, y, fillBrightness, 3.0 * scale)
                !occluded -> paint(x, y, lineBrightness, 1.8 * scale)
            }
            angle += 0.25
        }
        extraFillRun?.let { run ->
            var a = run.start
            while (a <= run.endInclusive) {
                val rad = Math.toRadians(a)
                for (d in -3..3) {
                    paint(cx + (radius + d) * cos(rad), cy - (radius + d) * sin(rad), fillBrightness, 1.0)
                }
                a += 0.25
            }
        }
        offCurveBlob?.let { (bx, by) -> paint(bx, by, 255f, 24.0) }
        return band
    }

    private fun observe(scene: FloatArray, trusted: Boolean = true, degraded: Boolean = false): ArcSignalObservation {
        val saturation = FloatArray(scene.size) { 10f }
        return ArcSignalFitter.observeBand(
            bandWidth = bandWidth, bandHeight = bandHeight,
            brightness = scene, saturation = saturation,
            geometryTrusted = trusted, geometryDegraded = degraded)
    }

    private fun parameterOf(observation: ArcSignalObservation): Double? =
        when (val p = observation.parameter) {
            is ArcSignalObservation.ParameterState.Interval -> (p.min + p.max) / 2
            is ArcSignalObservation.ParameterState.Alternatives -> p.values.average()
            else -> null
        }

    // -- geometry gates -------------------------------------------------------------------

    @Test
    fun untrustedGeometryCannotProduceArcAuthority() {
        val result = observe(scene(markerAngles = listOf(90.0)), trusted = false)
        assertEquals(ArcSignalObservation.ParameterState.Unsupported("arc_geometry_untrusted"), result.parameter)
        assertTrue(result.reasonCodes.contains("arc_geometry_untrusted"))
    }

    @Test
    fun malformedBandFailsClosed() {
        val result = ArcSignalFitter.observeBand(
            bandWidth = 40, bandHeight = 40,
            brightness = FloatArray(40 * 40), saturation = FloatArray(40 * 40),
            geometryTrusted = true, geometryDegraded = false)
        assertTrue(result.parameter is ArcSignalObservation.ParameterState.Unsupported)
        assertTrue(result.reasonCodes.contains("arc_geometry_malformed"))
    }

    @Test
    fun degradedGeometryIsFlaggedInProvenance() {
        val result = observe(scene(markerAngles = listOf(90.0)), degraded = true)
        assertTrue(result.reasonCodes.contains("arc_geometry_degraded"))
    }

    // -- parameter observation -------------------------------------------------------------

    @Test
    fun cleanMarkerYieldsNeighborhoodInterval() {
        // NOTE: the production arc band clips the ring top (angles ~52..128 degrees fall
        // outside the band), so observable markers live near the arc ends.
        val result = observe(scene(markerAngles = listOf(140.0)))
        val parameter = parameterOf(result)
        // Marker at 140 degrees: t ~= (176-140)/174 ~= 0.21.
        assertTrue("state=${result.parameter} parameter=$parameter",
            parameter != null && parameter in 0.15..0.27)
        assertTrue(result.parameter is ArcSignalObservation.ParameterState.Interval)
        assertTrue(result.ringCoverage > 0.6f)
    }

    @Test
    fun displacedMarkerMovesTheParameterMonotonically() {
        // All three markers sit in the band-observable left segment; a marker whose
        // fill crosses the band-clipped ring top is correctly Unknown (see the report).
        val low = parameterOf(observe(scene(markerAngles = listOf(155.0))))
        val mid = parameterOf(observe(scene(markerAngles = listOf(145.0))))
        val high = parameterOf(observe(scene(markerAngles = listOf(138.0))))
        assertTrue("low=$low mid=$mid high=$high",
            low != null && mid != null && high != null && low < mid && mid < high)
    }

    @Test
    fun twoSeparatedFillRunsYieldAlternativesNotAnArbitraryWinner() {
        // Main fill 176->150 plus an independent bright run 90->60 separated by unfilled
        // line: two plausible boundaries, no arbitrary winner.
        val result = observe(scene(markerAngles = listOf(150.0), extraFillRun = 20.0..40.0))
        val alternatives = result.parameter as? ArcSignalObservation.ParameterState.Alternatives
        assertTrue("state=${result.parameter}", alternatives != null)
        assertEquals(2, alternatives!!.values.size)
    }

    @Test
    fun brightOffCurveBlobIsIgnored() {
        val without = parameterOf(observe(scene(markerAngles = listOf(140.0))))
        val withBlob = parameterOf(observe(scene(markerAngles = listOf(140.0), offCurveBlob = 250.0 to 150.0)))
        assertTrue("without=$without withBlob=$withBlob",
            without != null && withBlob != null && abs(without - withBlob) < 0.05)
    }

    @Test
    fun whiteLineWithoutFillTransitionIsNotAnEndpoint() {
        // A uniformly bright-ish line that never reaches the fill threshold: no boundary
        // may be invented from it.
        val result = observe(scene(markerAngles = emptyList(), lineBrightness = 210f))
        assertTrue("state=${result.parameter}", result.parameter is ArcSignalObservation.ParameterState.Unknown)
        assertTrue(result.reasonCodes.contains("no_fill_transition"))
    }

    @Test
    fun occludedBoundaryIsUnknownInsteadOfWrong() {
        // The fill boundary is immediately followed by occlusion (sprite crossing the
        // arc): the boundary is not credible.
        val result = observe(scene(markerAngles = listOf(150.0), occlusionAngles = 100.0..149.0))
        assertTrue("state=${result.parameter}", result.parameter is ArcSignalObservation.ParameterState.Unknown)
        assertTrue(result.reasonCodes.contains("fill_boundary_occluded"))
    }

    @Test
    fun noVisibleMarkerYieldsUnknown() {
        val result = observe(scene(markerAngles = emptyList()))
        assertTrue(result.parameter is ArcSignalObservation.ParameterState.Unknown)
        assertTrue(result.reasonCodes.contains("no_fill_transition"))
    }

    @Test
    fun scaledScenePreservesTheNormalizedParameter() {
        val full = parameterOf(observe(scene(markerAngles = listOf(140.0))))
        val halfScene = scene(markerAngles = listOf(140.0), scale = 0.5)
        val half = ArcSignalFitter.observeBand(
            bandWidth = bandWidth / 2, bandHeight = bandHeight / 2,
            brightness = halfScene, saturation = FloatArray(halfScene.size) { 10f },
            geometryTrusted = true, geometryDegraded = false)
        val scaledParameter = parameterOf(half)
        assertTrue("full=$full scaled=$scaledParameter",
            full != null && scaledParameter != null && abs(full - scaledParameter) < 0.06)
    }

    @Test
    fun translatedScenePreservesTheFit() {
        val base = parameterOf(observe(scene(markerAngles = listOf(140.0))))
        val shifted = ArcSignalFitter.observeBand(
            bandWidth = bandWidth, bandHeight = bandHeight,
            brightness = scene(markerAngles = listOf(140.0), translate = 30.0 to -20.0),
            saturation = FloatArray(bandWidth * bandHeight) { 10f },
            geometryTrusted = true, geometryDegraded = false)
        val shiftedParameter = parameterOf(shifted)
        assertTrue("base=$base shifted=$shiftedParameter",
            base != null && shiftedParameter != null && abs(base - shiftedParameter) < 0.05)
    }

    // -- privacy / bounded evidence ---------------------------------------------------------

    @Test
    fun observationCarriesBoundedCodesOnly() {
        val result = observe(scene(markerAngles = listOf(90.0)))
        val bounded = Regex("""^[a-z0-9_.]{1,48}$""")
        result.reasonCodes.forEach { assertTrue(bounded.matches(it)) }
        val serialized = result.toString()
        assertFalse(serialized.contains('/'))
        assertFalse(serialized.contains('\\'))
    }

    // -- level interpretation seam -----------------------------------------------------------

    @Test
    fun productionLevelInterpretationStaysUnknownWithoutMapper() {
        val observation = observe(scene(markerAngles = listOf(140.0)))
        val evidence = ArcLevelInterpreter.interpret(observation, mapper = null)
        assertTrue(evidence is ArcLevelEvidence.Unknown)
        assertTrue(evidence.reasonCodes.contains("arc_level_mapping_unestablished"))
    }

    @Test
    fun mapperSeamProducesRangeWhenSuppliedByTests() {
        val observation = observe(scene(markerAngles = listOf(140.0)))
        val evidence = ArcLevelInterpreter.interpret(observation) { parameter ->
            1.0 + parameter.start * 50.0..1.0 + parameter.endInclusive * 50.0
        }
        assertTrue("evidence=$evidence", evidence is ArcLevelEvidence.Range)
        val range = evidence as ArcLevelEvidence.Range
        assertTrue("min=${range.minLevel} max=${range.maxLevel}",
            range.minLevel in 9.0..14.0 && range.maxLevel in 9.0..14.0)
    }

    @Test
    fun unsupportedObservationMapsToUnsupportedEvidence() {
        val evidence = ArcLevelInterpreter.interpret(
            observe(scene(markerAngles = listOf(140.0)), trusted = false), mapper = null)
        assertTrue(evidence is ArcLevelEvidence.Unsupported)
    }

    @Test
    fun lowCoverageNoiseIsUnknownNotAuthoritative() {
        // Incoherent noise (no ring structure): the fit must fail low and the observation
        // must not fabricate a parameter.
        val noisy = FloatArray(bandWidth * bandHeight) { index -> if (index % 3 == 0) 220f else 60f }
        val result = ArcSignalFitter.observeBand(
            bandWidth = bandWidth, bandHeight = bandHeight,
            brightness = noisy, saturation = FloatArray(noisy.size) { 10f },
            geometryTrusted = true, geometryDegraded = false)
        assertTrue("state=${result.parameter}", result.parameter is ArcSignalObservation.ParameterState.Unknown)
    }
}
