package com.pokerarity.scanner.util.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 2B: the display signature is the compatibility identity of a calibration. */
class DisplayGeometrySignatureTest {

    private fun signature(
        sourceWidth: Int = 1080,
        sourceHeight: Int = 2340,
        densityDpi: Int = 450,
        recognitionWidth: Int = 900,
        recognitionHeight: Int = 1950
    ): DisplayGeometrySignature = requireNotNull(
        DisplayGeometrySignature.from(
            sourceWidth, sourceHeight, densityDpi, recognitionWidth, recognitionHeight
        )
    )

    @Test
    fun identicalConfigurationsAreCompatibleAndShareAStableKey() {
        val first = signature()
        val second = signature()
        assertTrue(first.isCompatibleWith(second))
        assertEquals(first.stableKey, second.stableKey)
    }

    @Test
    fun sourceResolutionChangeIsIncompatible() {
        assertFalseCompat(signature(), signature(sourceWidth = 1440, sourceHeight = 3120))
    }

    @Test
    fun orientationChangeIsIncompatible() {
        val landscape = requireNotNull(
            DisplayGeometrySignature.from(2340, 1080, 450, 900, 415)
        )
        assertFalseCompat(signature(), landscape)
        assertEquals(DisplayGeometrySignature.Orientation.LANDSCAPE, landscape.orientation)
        assertEquals(DisplayGeometrySignature.Orientation.PORTRAIT, signature().orientation)
    }

    @Test
    fun densitySignatureMismatchIsIncompatible() {
        assertFalseCompat(signature(), signature(densityDpi = 480))
    }

    @Test
    fun recognitionBitmapChangeIsIncompatible() {
        assertFalseCompat(signature(), signature(recognitionWidth = 810, recognitionHeight = 1755))
    }

    @Test
    fun differentSourceConfigsScaledToSameRecognitionWidthStayDistinct() {
        // Both 1080-wide and 1440-wide sources downscale to a 900-wide recognition bitmap;
        // they must never share a calibration identity.
        val s25Like = signature(recognitionWidth = 900, recognitionHeight = 1950)
        val s25UltraLike = signature(
            sourceWidth = 1440,
            sourceHeight = 3120,
            recognitionWidth = 900,
            recognitionHeight = 1950
        )
        assertFalseCompat(s25Like, s25UltraLike)
        assertNotEquals(s25Like.stableKey, s25UltraLike.stableKey)
    }

    @Test
    fun stableKeyIsDeterministicAndFileSafe() {
        val key = signature().stableKey
        assertEquals(signature().stableKey, key)
        assertEquals("1080x2340:P:450:900x1950", key)
        assertTrue(key.none { it in "\\/:*?\"<>|".filterNot { c -> c == ':' } })
    }

    @Test
    fun invalidDimensionsAreRejected() {
        assertNull(DisplayGeometrySignature.from(0, 2340, 450, 900, 1950))
        assertNull(DisplayGeometrySignature.from(1080, -1, 450, 900, 1950))
        assertNull(DisplayGeometrySignature.from(1080, 2340, 0, 900, 1950))
        assertNull(DisplayGeometrySignature.from(1080, 2340, 450, 0, 1950))
        assertNull(DisplayGeometrySignature.from(1080, 2340, 450, 900, 0))
    }

    private fun assertFalseCompat(left: DisplayGeometrySignature, right: DisplayGeometrySignature) {
        assertEquals(false, left.isCompatibleWith(right))
        assertNotEquals(left.stableKey, right.stableKey)
    }
}
