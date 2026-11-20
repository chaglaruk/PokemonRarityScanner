package com.pokerarity.scanner.ui.result

import com.pokerarity.scanner.data.model.RecognitionTruth
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultActivityShinyTruthTest {

    @Test
    fun explicitTruthValuesArePreserved() {
        assertEquals(RecognitionTruth.TRUE, parseShinyTruth("TRUE", legacyIsShiny = false))
        assertEquals(RecognitionTruth.FALSE, parseShinyTruth("false", legacyIsShiny = true))
        assertEquals(RecognitionTruth.UNKNOWN, parseShinyTruth("UNKNOWN", legacyIsShiny = true))
    }

    @Test
    fun legacyFalseStaysUnknownAndLegacyTrueStaysTrue() {
        assertEquals(RecognitionTruth.UNKNOWN, parseShinyTruth(null, legacyIsShiny = false))
        assertEquals(RecognitionTruth.TRUE, parseShinyTruth(null, legacyIsShiny = true))
        assertEquals(RecognitionTruth.UNKNOWN, parseShinyTruth("unrecognized", legacyIsShiny = false))
    }
}
