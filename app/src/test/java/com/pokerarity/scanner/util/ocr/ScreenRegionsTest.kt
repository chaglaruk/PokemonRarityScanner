package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ScreenRegionsTest {

    @Test
    fun getRectForRegion_returnsStaticNameRegionInsideBitmapBounds() {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        try {
            val rect = ScreenRegions.getRectForRegion(bitmap, ScreenRegions.REGION_NAME)

            val expectedTop = (2340 * 0.375f).toInt()
            val expectedHeight = (2340 * 0.07f).toInt()
            assertEquals(expectedTop, rect.top)
            assertEquals(expectedTop + expectedHeight, rect.bottom)
            assertTrue(rect.left >= 0)
            assertTrue(rect.top >= 0)
            assertTrue(rect.right <= bitmap.width)
            assertTrue(rect.bottom <= bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun getRectForRegion_doesNotShiftHpRegionWhenBrightBandLooksLikeFalseAnchor() {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).apply {
                drawColor(Color.BLACK)
                val paint = Paint().apply { color = Color.rgb(180, 180, 180) }
                drawRect(
                    0f,
                    bitmap.height * 0.62f,
                    bitmap.width.toFloat(),
                    bitmap.height * 0.66f,
                    paint
                )
            }

            val rect = ScreenRegions.getRectForRegion(bitmap, ScreenRegions.REGION_HP)

            assertEquals((bitmap.height * 0.456f).toInt(), rect.top)
            assertEquals((bitmap.height * (0.456f + 0.055f)).toInt(), rect.bottom)
        } finally {
            bitmap.recycle()
        }
    }
}
