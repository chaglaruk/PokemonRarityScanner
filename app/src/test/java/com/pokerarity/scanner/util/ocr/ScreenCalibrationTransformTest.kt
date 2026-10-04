package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase 2B: normalized persisted geometry transforms explicitly and fails closed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenCalibrationTransformTest {

    private val corpusBar = Rect(226, 882, 674, 894)

    @Test
    fun normalizedRoundTripIsStableAtRecognitionScale() {
        val normalized = requireNotNull(NormalizedRect.fromRect(corpusBar, 900, 1950))

        val restored = requireNotNull(normalized.toRect(900, 1950))

        assertEquals(corpusBar, restored)
    }

    @Test
    fun transformUsesTheExplicitRecognitionFrameNotTheSourceFrame() {
        val normalized = requireNotNull(NormalizedRect.fromRect(corpusBar, 900, 1950))

        val atOtherScale = requireNotNull(normalized.toRect(600, 1300))

        assertEquals((normalized.left * 600).roundToInt(), atOtherScale.left)
        assertEquals((normalized.top * 1300).roundToInt(), atOtherScale.top)
        assertTrue(atOtherScale.right <= 600 && atOtherScale.bottom <= 1300)
    }

    @Test
    fun fromRectRejectsOutOfBoundsOrEmptyRects() {
        assertNull(NormalizedRect.fromRect(Rect(0, 0, 0, 0), 900, 1950))
        assertNull(NormalizedRect.fromRect(Rect(-5, 10, 100, 20), 900, 1950))
        assertNull(NormalizedRect.fromRect(Rect(10, 10, 950, 20), 900, 1950))
        assertNull(NormalizedRect.fromRect(corpusBar, 0, 1950))
    }

    @Test
    fun toRectRefusesDegenerateOrOutOfBoundsNormalizedRects() {
        assertNull(
            "width below the structural minimum must fail closed",
            NormalizedRect(0.2f, 0.4f, 0.2001f, 0.5f).toRect(900, 1950)
        )
        assertNull(NormalizedRect(0.2f, 0.4f, 0.3f, 0.4001f).toRect(900, 1950))
        assertNull(NormalizedRect(-0.1f, 0.4f, 0.3f, 0.5f).toRect(900, 1950))
        assertNull(NormalizedRect(0.2f, 0.4f, 1.3f, 0.5f).toRect(900, 1950))
    }

    @Test
    fun thinBarRectsRemainValid() {
        // The measured HP bar is 12px tall at 1950 (≈0.0062 normalized) and must survive.
        val normalized = requireNotNull(NormalizedRect.fromRect(corpusBar, 900, 1950))
        assertTrue(
            normalized.isValid(CalibrationTolerances.MIN_NORMALIZED_WIDTH, CalibrationTolerances.MIN_NORMALIZED_HEIGHT)
        )
        assertNotNull(normalized.toRect(900, 1950))
    }

    @Test
    fun recordValidityFailsClosedOnBrokenGeometry() {
        val valid = requireNotNull(
            ScreenCalibrationRecord(
                signature = requireNotNull(DisplayGeometrySignature.from(1080, 2340, 450, 900, 1950)),
                hpBar = requireNotNull(NormalizedRect.fromRect(corpusBar, 900, 1950)),
                detailCard = NormalizedRect(0f, 0.334f, 1f, 0.7f),
                nameBand = NormalizedRect(0.12f, 0.362f, 0.88f, 0.452f),
                createdAtMs = 100L,
                lastValidatedAtMs = 100L
            )
        )
        assertTrue(valid.isStructurallyValid())

        assertFalse("revision must be positive", valid.copy(schemaRevision = 0).isStructurallyValid())
        assertFalse("timestamps must be non-negative", valid.copy(createdAtMs = -1L).isStructurallyValid())
        assertFalse(
            "inverted rect must be invalid",
            valid.copy(hpBar = NormalizedRect(0.9f, 0.4f, 0.8f, 0.5f)).isStructurallyValid()
        )
        assertFalse(
            "name band below the structural minimum height must be invalid",
            valid.copy(nameBand = NormalizedRect(0.12f, 0.362f, 0.88f, 0.364f)).isStructurallyValid()
        )
    }
}
