package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The OCR callback supplies independent evidence; the original suffix is never a fallback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ActionResourceTextRecoveryTest {
    private fun block(text: String, rect: Rect) = MLKitOcrProvider.RecognizedBlock(text, rect)
    private fun scene() = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.WHITE)
        for (y in 124 until 136) for (x in 73 until 82) setPixel(x, y, Color.rgb(120, 190, 130))
        for (y in 253 until 267) for (x in 70 until 79) setPixel(x, y, Color.rgb(120, 190, 130))
        for (y in 253 until 267) for (x in 100 until 109) setPixel(x, y, Color.BLACK)
        for (y in 253 until 267) for (x in 114 until 123) setPixel(x, y, Color.BLACK)
    }
    private fun layout(labelled: Boolean = true) = MLKitOcrProvider.Layout(
        listOfNotNull(block("EVOLVE", Rect(15, 250, 60, 270)),
            block("POWER UP", Rect(15, 210, 60, 230)),
            block("CANDY", Rect(50, 150, 160, 165)).takeIf { labelled }),
        listOf(block("123", Rect(100, 120, 120, 140)), block("3000", Rect(100, 210, 145, 230)),
            block("O50", Rect(70, 250, 124, 270))))

    @Test fun verifiedGlyphAndAgreeingPixelReadRecoverCostWithoutChangingPowerUp() = runBlocking {
        val bitmap = scene()
        var calls = 0
        try {
            val recovered = ActionResourceTextRecovery.recover(bitmap, layout(), null) { crop ->
                calls++
                assertTrue(crop.width < 40)
                MLKitOcrProvider.Layout(emptyList(), listOf(block("50", Rect(2, 2, 25, 16))))
            }
            val context = ExtractionContext(actionResources = ActionResourceMatcher.observe(bitmap, recovered))
            assertEquals(1, calls)
            assertEquals(50, evolveEvidence(recovered, recovered.lines, 200, context).cost.read.value)
            assertEquals(3000, powerUpEvidence(recovered, recovered.lines, 200, context).cost.read.value)
        } finally { bitmap.recycle() }
    }

    @Test fun disagreeingOrAbsentPixelReadNeverUsesSuffixAsCost() = runBlocking {
        val bitmap = scene()
        try {
            for (read in listOf("20", "", "O50")) {
                val original = layout()
                val recovered = ActionResourceTextRecovery.recover(bitmap, original, null) {
                    MLKitOcrProvider.Layout(emptyList(), listOf(block(read, Rect(2, 2, 25, 16))))
                }
                assertEquals(original, recovered)
            }
        } finally { bitmap.recycle() }
    }

    @Test fun unlabelledResourceAndNeutralCoveredAmountDoNotTriggerRecovery() = runBlocking {
        for (covered in listOf(false, true)) {
            val bitmap = scene()
            try {
                if (covered) coverTrailingDigit(bitmap)
                val original = layout(labelled = covered)
                val recovered = ActionResourceTextRecovery.recover(bitmap, original, null) {
                    fail("Untrusted resource must not request a numeric re-read")
                    MLKitOcrProvider.Layout(emptyList(), emptyList())
                }
                assertEquals(original, recovered)
            } finally { bitmap.recycle() }
        }
    }

    private fun coverTrailingDigit(bitmap: Bitmap) {
        for (y in 253 until 267) for (x in 123 until 133) bitmap.setPixel(x, y, Color.GRAY)
    }

    @Test fun numericReadWithAdditionalUncertainInkRemainsBlocking() = runBlocking {
        val bitmap = scene()
        try {
            val original = layout()
            val recovered = ActionResourceTextRecovery.recover(bitmap, original, null) {
                MLKitOcrProvider.Layout(emptyList(), listOf(block("50", Rect(2, 2, 25, 16)),
                    block("O", Rect(27, 2, 35, 16))))
            }
            assertEquals(original, recovered)
        } finally { bitmap.recycle() }
    }

    @Test fun multipleMergedCandidatesRemainBlocking() = runBlocking {
        val bitmap = scene()
        try {
            val original = layout().let { it.copy(elements = it.elements + block("O25", Rect(80, 250, 145, 270))) }
            val recovered = ActionResourceTextRecovery.recover(bitmap, original, null) {
                fail("Ambiguous action row must not request recovery")
                MLKitOcrProvider.Layout(emptyList(), emptyList())
            }
            assertEquals(original, recovered)
        } finally { bitmap.recycle() }
    }
}
