package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HealthBarLocatorTest {
    @Test fun followsTheHealthBarAcrossScrollPositions() {
        for (top in listOf(50, 500, 1050)) {
            val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            bitmap.setPixels(IntArray(540 * 14) { Color.rgb(90, 230, 170) }, 0, 540, 270, top, 540, 14)
            val result = HealthBarLocator.locate(bitmap)
            assertNotNull(result)
            assertTrue(result!!.top in top..top + 2)
            bitmap.recycle()
        }
    }

    @Test fun largeGreenButtonsAndBackgroundsAreNotHealthBars() {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixels(IntArray(950 * 150) { Color.rgb(90, 230, 170) }, 0, 950, 50, 900, 950, 150)
        assertNull(HealthBarLocator.locate(bitmap))
        bitmap.eraseColor(Color.rgb(90, 230, 170))
        assertNull(HealthBarLocator.locate(bitmap))
        bitmap.recycle()
    }
}
