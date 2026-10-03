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

    @Test
    fun decisiveRepetitiveGridClassifiesAsStorageListDespiteDetailLikePanels() {
        // Storage list rows also produce cp/header and detail-panel evidence; a measured
        // repetitive grid must win before the detail branch can mislabel a list screen.
        val bitmap = storageListBitmap(1080, 2400)

        val result = classifier.classify(bitmap)

        assertEquals(result.toString(), ScreenType.StorageList, result.screenType)
        assertTrue(result.anchors.any { it.name == ScreenAnchorName.StorageGrid })
        // A stable storage list is a terminal non-detail route, never a safe-to-proceed screen.
        assertTrue(result.anchors.first { it.name == ScreenAnchorName.StorageGrid }.confidence >= 0.90f)
    }

    @Test
    fun weakGridRepetitionDoesNotOverturnADetailScreen() {
        // Partially repetitive texture (below the decisive bar) keeps the detail route;
        // content corroboration after OCR remains the authority for such stress frames.
        val bitmap = detailWithWeakGridBitmap(1080, 2400)

        val result = classifier.classify(bitmap)

        assertEquals(result.toString(), ScreenType.PokemonDetail, result.screenType)
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

        private val darkBackground = Color.rgb(28, 30, 34)
        private val brightNeutral = Color.rgb(222, 224, 228)

        private fun storageCellRects(): List<Pair<Float, Float>> {
            val cells = mutableListOf<Pair<Float, Float>>()
            for (row in 0 until 4) {
                for (column in 0 until 3) {
                    cells += Pair(0.08f + column * 0.29f, 0.24f + row * 0.15f)
                }
            }
            return cells
        }

        fun storageListBitmap(width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(darkBackground)
            // Bright list-row panels that also trip the generic detail-panel heuristic
            // (drawn first: the measured grid cells overwrite them where they overlap).
            fillRect(bitmap, 0.00f, 0.60f, 1.00f, 0.94f, Color.rgb(208, 210, 214))
            fillRect(bitmap, 0.36f, 0.060f, 0.64f, 0.083f, brightNeutral)
            // The 12 measured grid cells, each bright across its full measured area
            // with a dark stripe inside so per-cell contrast stays high.
            for ((leftF, topF) in storageCellRects()) {
                val left = (leftF * width).toInt()
                val top = (topF * height).toInt()
                val right = left + (width * 0.22f).toInt()
                val bottom = top + (height * 0.10f).toInt()
                fillRect(bitmap,
                    left.toFloat() / width, top.toFloat() / height,
                    right.toFloat() / width, bottom.toFloat() / height,
                    brightNeutral)
                fillRect(bitmap,
                    left.toFloat() / width, (bottom - (height * 0.025f)) / height,
                    right.toFloat() / width, bottom.toFloat() / height,
                    darkBackground)
            }
            return bitmap
        }

        fun detailWithWeakGridBitmap(width: Int, height: Int): Bitmap {
            val bitmap = pokemonDetailBitmap(width, height)
            // Sparse grid-like brightness: only 6 of 12 measured cells fire (0.5 evidence),
            // below both the decisive bar and the ordinary storage threshold.
            for ((index, cell) in storageCellRects().withIndex()) {
                if (index >= 6) break
                val (leftF, topF) = cell
                val left = (leftF * width).toInt()
                val top = (topF * height).toInt()
                fillRect(bitmap,
                    left.toFloat() / width, top.toFloat() / height,
                    (left + width * 0.20f) / width, (top + height * 0.075f) / height,
                    brightNeutral)
            }
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
