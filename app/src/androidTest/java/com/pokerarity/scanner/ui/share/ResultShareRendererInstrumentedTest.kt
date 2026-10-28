package com.pokerarity.scanner.ui.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.data.model.ScanReviewCodes
import com.pokerarity.scanner.data.model.pokemonFromScanExtras
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultShareRendererInstrumentedTest {
    @Test
    fun renderedShareCardIncludesVisibleConfidencePanelAndLegacyCardDoesNot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val token = UUID.randomUUID().toString()
        val confidenceName = "confidence-$token.png"
        val legacyName = "legacy-$token.png"
        val confidenceFile = File(context.cacheDir, "shared/$confidenceName")
        val legacyFile = File(context.cacheDir, "shared/$legacyName")
        val confidenceSupport = ScanDecisionSupport(
            eventConfidenceCode = "",
            eventConfidenceLabel = "",
            eventConfidenceDetail = "",
            scanConfidenceScore = 72,
            scanConfidenceLabel = ScanReviewCodes.REVIEW,
            scanConfidenceDetail = "${ScanReviewCodes.DETAILS_PREFIX}${ScanReviewCodes.CP_PRESENT},${ScanReviewCodes.HP_MISSING}"
        )
        val confidencePokemon = pokemonFromScanExtras(
            name = "Pikachu",
            cp = 500,
            hp = 51,
            score = 42,
            tier = "RARE",
            isShiny = false,
            isLucky = false,
            hasCostume = false,
            hasSpecialForm = false,
            isShadow = false,
            dateText = null,
            decisionSupport = confidenceSupport
        )

        try {
            val confidenceUri = ResultShareRenderer.renderPokemonCardToImageUri(
                context,
                confidencePokemon,
                confidenceName
            )
            val legacyUri = ResultShareRenderer.renderPokemonCardToImageUri(
                context,
                confidencePokemon.copy(decisionSupport = null),
                legacyName
            )
            assertNotNull("confidence share card should render with app resources", confidenceUri)
            assertNotNull("legacy share card should still render", legacyUri)

            val confidenceBitmap = decode(context.contentResolver.openInputStream(checkNotNull(confidenceUri)))
            val legacyBitmap = decode(context.contentResolver.openInputStream(checkNotNull(legacyUri)))
            try {
                assertEquals(1200, confidenceBitmap.width)
                assertEquals(1600, confidenceBitmap.height)
                assertEquals(confidenceBitmap.width, legacyBitmap.width)
                assertEquals(confidenceBitmap.height, legacyBitmap.height)

                val panelDifferencePixels = countDifferentPixels(
                    confidenceBitmap,
                    legacyBitmap,
                    left = 536,
                    top = 748,
                    right = 1104,
                    bottom = 864
                )
                assertTrue(
                    "review confidence should change the caught-date row into a visible confidence panel",
                    panelDifferencePixels > 10_000
                )
                assertTrue(
                    "the panel should rasterize readable label text",
                    countBrightPixels(confidenceBitmap, left = 564, top = 785, right = 1076, bottom = 830) > 80
                )
            } finally {
                confidenceBitmap.recycle()
                legacyBitmap.recycle()
            }
        } finally {
            confidenceFile.delete()
            legacyFile.delete()
            assertFalse("synthetic confidence image should be removed", confidenceFile.exists())
            assertFalse("synthetic legacy image should be removed", legacyFile.exists())
        }
    }

    private fun decode(stream: java.io.InputStream?): Bitmap {
        assertNotNull("share image stream should be readable", stream)
        return checkNotNull(stream).use { checkNotNull(BitmapFactory.decodeStream(it)) }
    }

    private fun countDifferentPixels(first: Bitmap, second: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                if (first.getPixel(x, y) != second.getPixel(x, y)) count++
            }
        }
        return count
    }

    private fun countBrightPixels(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) > 180 && Color.green(pixel) > 180 && Color.blue(pixel) > 180) count++
            }
        }
        return count
    }
}
