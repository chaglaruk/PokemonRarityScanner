package com.pokerarity.scanner

import com.pokerarity.scanner.util.ocr.AppraisalBarReader
import com.pokerarity.scanner.util.ocr.AppraisalIvEvidence
import com.pokerarity.scanner.util.ocr.AppraisalIvInterpreter
import com.pokerarity.scanner.util.ocr.AppraisalStat
import com.pokerarity.scanner.util.ocr.AppraisalStatObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3D appraisal-bar reader semantics on independently constructed synthetic
 * bands. Fixtures paint the measured-game-fact structure (white panel, grey track,
 * saturated colored fill, dark text, white icons, divider marks) WITHOUT mirroring
 * reader internals; expectations are endpoint neighborhoods, not implementation echoes.
 *
 * These synthetic controls prove mechanics. Real Samsung pixel replay is separate;
 * production IV interpretation remains disabled without independent IV labels.
 */
@Suppress("MagicNumber")
class AppraisalBarReaderTest {

    private val bandWidth = 500
    private val bandHeight = 40

    // Synthetic palette (construction-defined, not measured from real frames).
    private val panel = 255f to 5f        // white panel: bright, no chroma
    private val track = 190f to 15f       // grey track: mid-bright, low chroma
    private val fill = 200f to 180f       // colored fill: strong chroma
    private val text = 60f to 30f         // dark text: dim
    private val icon = 255f to 5f         // white icon: bright, no chroma

    /**
     * Builds one stat band: panel background; a track from trackStartFrac to
     * trackEndFrac; colored fill from trackStartFrac to fillEndFrac; optional divider
     * marks, text block and white icon.
     */
    @Suppress("LongParameterList")  // documented synthetic fixture builder
    private fun band(
        trackStartFrac: Double = 0.10,
        trackEndFrac: Double = 0.90,
        fillEndFrac: Double? = null,
        dividers: List<Double> = emptyList(),
        textColumns: IntRange? = null,
        iconColumns: IntRange? = null,
        fillHueShift: Float = 0f
    ): Pair<FloatArray, FloatArray> {
        val brightness = FloatArray(bandWidth * bandHeight) { panel.first }
        val saturation = FloatArray(bandWidth * bandHeight) { panel.second }
        fun paint(x0: Int, x1: Int, value: Pair<Float, Float>) {
            for (x in x0.coerceIn(0, bandWidth - 1)..x1.coerceIn(0, bandWidth - 1)) {
                for (y in 0 until bandHeight) {
                    brightness[y * bandWidth + x] = value.first
                    saturation[y * bandWidth + x] = (value.second + fillHueShift).coerceAtMost(255f)
                }
            }
        }
        val trackLeft = (trackStartFrac * bandWidth).toInt()
        val trackRight = (trackEndFrac * bandWidth).toInt()
        paint(trackLeft, trackRight, track)
        if (fillEndFrac != null) {
            paint(trackLeft, (fillEndFrac * bandWidth).toInt(), fill)
            for (divider in dividers) {
                paint((divider * bandWidth).toInt(), (divider * bandWidth).toInt() + 1, track)
            }
        }
        textColumns?.let { paint(it.first, it.last, text) }
        iconColumns?.let { paint(it.first, it.last, icon) }
        return brightness to saturation
    }

    private fun read(
        scene: Pair<FloatArray, FloatArray>,
        trusted: Boolean = true
    ): AppraisalStatObservation = AppraisalBarReader.observeStatBand(
        bandWidth, bandHeight, scene.first, scene.second, trusted)

    private fun endpointOf(observation: AppraisalStatObservation): Double? =
        (observation.endpoint as? AppraisalStatObservation.EndpointState.Interval)
            ?.let { (it.min + it.max) / 2 }

    // -- geometry gates ------------------------------------------------------------------

    @Test
    fun untrustedGeometryYieldsUnsupported() {
        val result = read(band(fillEndFrac = 0.5), trusted = false)
        assertEquals(
            AppraisalStatObservation.EndpointState.Unsupported("appraisal_band_malformed"),
            result.endpoint)
    }

    @Test
    fun malformedBandFailsClosed() {
        val result = AppraisalBarReader.observeStatBand(
            10, 10, FloatArray(100), FloatArray(100), geometryTrusted = true)
        assertTrue(result.endpoint is AppraisalStatObservation.EndpointState.Unsupported)
        assertTrue(result.reasonCodes.contains("appraisal_band_malformed"))
    }

    // -- fill endpoint observation ---------------------------------------------------------

    @Test
    fun clearPartialFillYieldsEndpointNeighborhood() {
        val result = read(band(fillEndFrac = 0.5))
        val endpoint = endpointOf(result)
        assertTrue("endpoint=$endpoint", endpoint != null && endpoint in 0.44..0.56)
        assertTrue(result.endpoint is AppraisalStatObservation.EndpointState.Interval)
    }

    @Test
    fun nearEmptyFillYieldsNearZeroEndpoint() {
        val result = read(band(fillEndFrac = 0.13))
        val endpoint = endpointOf(result)
        assertTrue("endpoint=$endpoint", endpoint != null && endpoint in 0.0..0.08)
    }

    @Test
    fun fullBarYieldsNearOneEndpoint() {
        val result = read(band(fillEndFrac = 0.90))
        val endpoint = endpointOf(result)
        assertTrue("endpoint=$endpoint", endpoint != null && endpoint in 0.92..1.0)
    }

    @Test
    fun emptyTrackYieldsUnknownNotZeroFillGuess() {
        // A visible track with NO fill at all: no fill transition exists, so the reader
        // must not fabricate an endpoint from the track alone.
        val result = read(band(fillEndFrac = null))
        assertTrue("state=${result.endpoint}", result.endpoint is AppraisalStatObservation.EndpointState.Unknown)
        assertTrue(result.reasonCodes.contains("no_fill_transition"))
    }

    @Test
    fun noTrackStructureYieldsUnknown() {
        // A plain panel with no bar structure at all: no track run exists.
        val (bright, sat) = band(fillEndFrac = null, trackStartFrac = 0.5, trackEndFrac = 0.5001)
        val result = read(bright to sat)
        assertTrue(result.endpoint is AppraisalStatObservation.EndpointState.Unknown)
        assertTrue(result.reasonCodes.contains("bar_track_too_short"))
    }

    @Test
    fun dividerMarksDoNotBecomeEndpoints() {
        // Fill to 0.55 with divider marks at 0.30/0.42 inside the fill: the endpoint
        // stays at the true fill boundary, not at a divider.
        val result = read(band(fillEndFrac = 0.55, dividers = listOf(0.30, 0.42)))
        val endpoint = endpointOf(result)
        assertTrue("endpoint=$endpoint", endpoint != null && endpoint in 0.49..0.61)
    }

    @Test
    fun textAndIconPixelsDoNotBecomeFillEndpoints() {
        val without = endpointOf(read(band(fillEndFrac = 0.4)))
        val withNoise = endpointOf(read(
            band(fillEndFrac = 0.4, textColumns = 40..55, iconColumns = 430..455)))
        assertTrue("without=$without withNoise=$withNoise",
            without != null && withNoise != null && kotlin.math.abs(without - withNoise) < 0.05)
    }

    @Test
    fun hueVariationWithinRangeDoesNotChangeTheEndpoint() {
        val base = endpointOf(read(band(fillEndFrac = 0.45)))
        val shifted = endpointOf(read(band(fillEndFrac = 0.45, fillHueShift = 40f)))
        assertTrue("base=$base shifted=$shifted",
            base != null && shifted != null && kotlin.math.abs(base - shifted) < 0.05)
    }

    // -- invariance -------------------------------------------------------------------------

    @Test
    fun scaledBandPreservesTheNormalizedEndpoint() {
        val width2 = bandWidth * 2
        val height2 = bandHeight * 2
        val (bright, sat) = band(fillEndFrac = 0.5)
        val scaledBright = FloatArray(width2 * height2)
        val scaledSat = FloatArray(width2 * height2)
        for (y in 0 until height2) {
            for (x in 0 until width2) {
                val src = (y / 2) * bandWidth + (x / 2)
                scaledBright[y * width2 + x] = bright[src]
                scaledSat[y * width2 + x] = sat[src]
            }
        }
        val full = endpointOf(read(bright to sat))
        val scaled = endpointOf(AppraisalBarReader.observeStatBand(
            width2, height2, scaledBright, scaledSat, geometryTrusted = true))
        assertTrue("full=$full scaled=$scaled",
            full != null && scaled != null && kotlin.math.abs(full - scaled) < 0.06)
    }

    @Test
    fun translatedBandPreservesTheNormalizedEndpoint() {
        val base = endpointOf(read(band(fillEndFrac = 0.5)))
        // Shift the whole scene right by 40 columns within a wider band.
        val wide = 600
        val shiftedBright = FloatArray(wide * bandHeight) { panel.first }
        val shiftedSat = FloatArray(wide * bandHeight) { panel.second }
        val (bright, sat) = band(fillEndFrac = 0.5)
        for (y in 0 until bandHeight) {
            for (x in 0 until bandWidth) {
                shiftedBright[y * wide + x + 40] = bright[y * bandWidth + x]
                shiftedSat[y * wide + x + 40] = sat[y * bandWidth + x]
            }
        }
        val shifted = endpointOf(AppraisalBarReader.observeStatBand(
            wide, bandHeight, shiftedBright, shiftedSat, true))
        assertTrue("base=$base shifted=$shifted",
            base != null && shifted != null && kotlin.math.abs(base - shifted) < 0.05)
    }

    // -- bounded evidence --------------------------------------------------------------------

    @Test
    fun observationCarriesBoundedCodesOnly() {
        val result = read(band(fillEndFrac = 0.5))
        val bounded = Regex("""^[a-z0-9_.]{1,48}$""")
        result.reasonCodes.forEach { assertTrue(bounded.matches(it)) }
        assertFalse(result.toString().contains('/'))
        assertFalse(result.toString().contains('\\'))
    }

    // -- IV interpretation seam (production mapper = null -> Unknown) -------------------------

    @Test
    fun productionInterpretationStaysUnknownWithoutMapper() {
        val observation = read(band(fillEndFrac = 0.5))
        val evidence = AppraisalIvInterpreter.interpretStat(observation, mapper = null)
        assertTrue(evidence is AppraisalIvEvidence.Unknown)
        assertTrue(evidence.reasonCodes.contains("appraisal_iv_mapping_unestablished"))
    }

    @Test
    fun mapperSeamProducesExactOnlyWhenGenuinelySingleValued() {
        val observation = read(band(fillEndFrac = 0.5))
        // A mapper that maps a wide endpoint interval to ONE legal value -> Exact.
        val evidence = AppraisalIvInterpreter.interpretStat(observation) {
            listOf(7.0..7.0)
        }
        assertTrue(evidence is AppraisalIvEvidence.Exact)
        assertEquals(7, (evidence as AppraisalIvEvidence.Exact).iv)
    }

    @Test
    fun boundaryStraddlingEndpointYieldsRangeNotRoundedExact() {
        val observation = read(band(fillEndFrac = 0.5))
        // A mapper whose window straddles the 7/8 quantization boundary: the honest
        // result is a RANGE 7..8, never a rounded exact value.
        val evidence = AppraisalIvInterpreter.interpretStat(observation) {
            listOf(6.8..8.2)
        }
        assertTrue("evidence=$evidence", evidence is AppraisalIvEvidence.Range)
        assertEquals(7, (evidence as AppraisalIvEvidence.Range).minIv)
        assertEquals(8, evidence.maxIv)
    }

    @Test
    fun mapperRefusingWindowYieldsUnknownNotGuessedValue() {
        val observation = read(band(fillEndFrac = 0.5))
        val evidence = AppraisalIvInterpreter.interpretStat(observation) { null }
        assertTrue(evidence is AppraisalIvEvidence.Unknown)
        assertTrue(evidence.reasonCodes.contains("appraisal_mapping_refused_window"))
    }

    @Test
    fun fractionalWindowDoesNotRoundIntoAnUnsupportedInteger() {
        val evidence = AppraisalIvInterpreter.interpretStat(read(band(fillEndFrac = 0.5))) {
            listOf(7.2..7.8)
        }
        assertTrue(evidence is AppraisalIvEvidence.Unknown)
    }

    @Test
    fun invalidAlternativeCannotBeDroppedToManufactureExactEvidence() {
        val evidence = AppraisalIvInterpreter.interpretStat(read(band(fillEndFrac = 0.5))) {
            listOf(7.0..7.0, Double.NaN..Double.NaN)
        }
        assertTrue(evidence is AppraisalIvEvidence.Unknown)
    }

    @Test
    fun unsupportedObservationMapsToUnsupportedEvidence() {
        val evidence = AppraisalIvInterpreter.interpretStat(
            read(band(fillEndFrac = 0.5), trusted = false), mapper = null)
        assertTrue(evidence is AppraisalIvEvidence.Unsupported)
    }
}
