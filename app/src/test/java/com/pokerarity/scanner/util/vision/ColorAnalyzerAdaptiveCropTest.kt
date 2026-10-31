package com.pokerarity.scanner.util.vision

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ColorAnalyzerAdaptiveCropTest {
    @Test
    fun adaptiveCropIncludesAccessoryAboveBodyButExcludesCpArea() {
        val frame = detailFrame()
        try {
            drawSpriteBody(frame)
            drawCrown(frame)
            drawCpMarker(frame)

            val crop = ColorAnalyzer.getSpriteRegionAdaptive(frame)

            assertTrue("crown top should be retained", crop.top <= CROWN_TOP)
            assertTrue("CP marker must remain outside the sprite crop", crop.top > CP_MARKER_BOTTOM)
            assertTrue("body should remain in the crop", crop.bottom > BODY_BOTTOM - 1)
        } finally {
            frame.recycle()
        }
    }

    @Test
    fun adaptiveCropDoesNotExpandUpwardWhenNoAccessoryIsPresent() {
        val frame = detailFrame()
        try {
            drawSpriteBody(frame)

            val crop = ColorAnalyzer.getSpriteRegionAdaptive(frame)

            assertTrue("plain sprite crop should begin near the body", crop.top in 130..BODY_TOP)
            assertTrue("plain sprite crop should exclude the CP area", crop.top > CP_MARKER_BOTTOM)
        } finally {
            frame.recycle()
        }
    }

    private fun detailFrame(): Bitmap = Bitmap.createBitmap(FRAME_WIDTH, FRAME_HEIGHT, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(BACKGROUND) }

    private fun drawSpriteBody(frame: Bitmap) {
        for (y in BODY_TOP until BODY_BOTTOM) {
            for (x in BODY_LEFT until BODY_RIGHT) frame.setPixel(x, y, BODY_COLOR)
        }
    }

    private fun drawCrown(frame: Bitmap) {
        for (y in CROWN_TOP until CROWN_BOTTOM) {
            for (x in CROWN_LEFT until CROWN_RIGHT) frame.setPixel(x, y, CROWN_COLOR)
        }
    }

    private fun drawCpMarker(frame: Bitmap) {
        for (y in CP_MARKER_TOP until CP_MARKER_BOTTOM) {
            for (x in CP_MARKER_LEFT until CP_MARKER_RIGHT) frame.setPixel(x, y, CP_MARKER_COLOR)
        }
    }

    private companion object {
        const val FRAME_WIDTH = 360
        const val FRAME_HEIGHT = 780
        const val BODY_LEFT = 140
        const val BODY_RIGHT = 220
        const val BODY_TOP = 160
        const val BODY_BOTTOM = 245
        const val CROWN_LEFT = 162
        const val CROWN_RIGHT = 198
        const val CROWN_TOP = 100
        const val CROWN_BOTTOM = 116
        const val CP_MARKER_LEFT = 170
        const val CP_MARKER_RIGHT = 190
        const val CP_MARKER_TOP = 45
        const val CP_MARKER_BOTTOM = 55
        val BACKGROUND = Color.rgb(45, 165, 145)
        val BODY_COLOR = Color.rgb(240, 60, 35)
        val CROWN_COLOR = Color.rgb(245, 205, 30)
        val CP_MARKER_COLOR = Color.rgb(45, 90, 245)
    }
}
