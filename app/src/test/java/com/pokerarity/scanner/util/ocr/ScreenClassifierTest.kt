package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenClassifierTest {

    private val classifier = ScreenClassifier()

    @Test
    fun classifiesObviousDetailScreen() {
        val bitmap = pokemonDetailBitmap(1080, 2400)

        val result = classifier.classify(bitmap)

        assertEquals(result.toString(), ScreenType.PokemonDetail, result.screenType)
        assertTrue(result.confidence >= 0.70f)
        assertTrue(result.anchors.any { it.name == ScreenAnchorName.DetailCard })
        assertFalse(result.safeFallback)
    }

    @Test
    fun returnsUnknownForBlankBitmap() {
        val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)

        val result = classifier.classify(bitmap)

        assertEquals(result.toString(), ScreenType.Unknown, result.screenType)
        assertTrue(result.confidence < 0.30f)
        assertTrue(result.safeFallback)
    }

    @Test
    fun keepsAmbiguousInputLowConfidence() {
        val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(138, 148, 155))

        val result = classifier.classify(bitmap)

        assertEquals(result.toString(), ScreenType.Unknown, result.screenType)
        assertTrue(result.confidence < 0.50f)
        assertTrue(result.safeFallback)
    }

    companion object {
        fun pokemonDetailBitmap(width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(65, 150, 190))

            fillRect(bitmap, 0.00f, 0.42f, 1.00f, 0.90f, Color.WHITE)
            fillRect(bitmap, 0.36f, 0.060f, 0.64f, 0.083f, Color.WHITE)
            fillRect(bitmap, 0.26f, 0.383f, 0.74f, 0.407f, Color.WHITE)
            fillRect(bitmap, 0.32f, 0.458f, 0.68f, 0.480f, Color.rgb(78, 84, 90))
            fillRect(bitmap, 0.28f, 0.640f, 0.78f, 0.662f, Color.rgb(78, 84, 90))

            return bitmap
        }

        private fun fillRect(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float, color: Int) {
            val x0 = (bitmap.width * left).toInt().coerceIn(0, bitmap.width)
            val y0 = (bitmap.height * top).toInt().coerceIn(0, bitmap.height)
            val x1 = (bitmap.width * right).toInt().coerceIn(x0, bitmap.width)
            val y1 = (bitmap.height * bottom).toInt().coerceIn(y0, bitmap.height)
            if (x1 <= x0 || y1 <= y0) return
            val row = IntArray(x1 - x0) { color }
            for (y in y0 until y1) {
                bitmap.setPixels(row, 0, row.size, x0, y, row.size, 1)
            }
        }
    }
}
