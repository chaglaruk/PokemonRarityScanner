package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Constructed layout controls complement privately replayed human-checked Samsung frames. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppraisalPanelLocatorTest {
    private fun scene(width: Int = 600, shift: Int = 0, count: Int = 3, full: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(width, width * 13 / 6, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(40, 90, 130))
        fun paint(rect: Rect, color: Int) {
            val scale = width / 600.0
            for (y in (rect.top * scale).toInt() until (rect.bottom * scale).toInt()) {
                for (x in (rect.left * scale).toInt() until (rect.right * scale).toInt()) bitmap.setPixel(x, y, color)
            }
        }
        paint(Rect(18, 430, 582, 780), Color.WHITE)
        paint(Rect(45 + shift, 800, 300 + shift, 1100), Color.WHITE)
        for (index in 0 until count) {
            val y = 860 + index * 75
            paint(Rect(75 + shift, y, 267 + shift, y + 12), Color.rgb(226, 226, 224))
            paint(Rect(75 + shift, y, (if (full) 267 else 210) + shift, y + 12),
                if (full) Color.rgb(242, 80, 110) else Color.rgb(242, 166, 74))
            for (divider in listOf(139, 203)) paint(Rect(divider + shift, y, divider + shift + 4, y + 12), Color.WHITE)
        }
        return bitmap
    }

    @Test fun resolutionAndCardPlacementDoNotChangeMeasuredRouting() {
        for (width in listOf(600, 900, 1080)) for (shift in listOf(0, 230)) {
            assertMeasuredRouting(width, shift)
        }
    }

    private fun assertMeasuredRouting(width: Int, shift: Int) {
        val bitmap = scene(width, shift)
        try {
            val layout = requireNotNull(AppraisalPanelLocator.locate(bitmap))
            assertEquals(3, layout.tracks.size)
            val classification = ScreenClassifier().classify(bitmap)
            assertEquals(ScreenType.Appraisal, classification.screenType)
            val geometry = ScreenGeometryBuilder().build(bitmap, classification)
            AppraisalStat.entries.forEach { stat ->
                assertEquals(CropProvenance.AnchorDerived, geometry.crop(stat.field)?.provenance)
                assertTrue(layout.panel.contains(requireNotNull(geometry.crop(stat.field)?.rect)))
            }
        } finally { bitmap.recycle() }
    }

    @Test fun missingOrCoveredTrackDoesNotSupplyThreeBarGeometry() {
        for (covered in listOf(false, true)) {
            val bitmap = scene(count = if (covered) 3 else 2)
            try {
                if (covered) coverThirdTrack(bitmap)
                assertNull(AppraisalPanelLocator.locate(bitmap))
                val observation = AppraisalBarReader.observe(bitmap, ScreenGeometryBuilder().build(bitmap))
                assertTrue(observation.readings.values.all {
                    it.endpoint is AppraisalStatObservation.EndpointState.Unsupported
                })
            } finally { bitmap.recycle() }
        }
    }

    private fun coverThirdTrack(bitmap: Bitmap) {
        for (y in 1000 until 1030) for (x in 65 until 275) bitmap.setPixel(x, y, Color.BLUE)
    }

    @Test fun fullRedBarsAndWhiteDividersStillCannotEstablishAnExactIv() {
        val bitmap = scene(full = true)
        try {
            val observation = AppraisalBarReader.observe(bitmap, ScreenGeometryBuilder().build(bitmap))
            observation.readings.values.forEach { reading ->
                assertTrue(requireNotNull(reading.fillFraction) > 0.98f)
                assertTrue(reading.endpoint is AppraisalStatObservation.EndpointState.Interval)
                assertTrue(AppraisalIvInterpreter.interpretStat(reading, null) is AppraisalIvEvidence.Unknown)
            }
        } finally { bitmap.recycle() }
    }
}
