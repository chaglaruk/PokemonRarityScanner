package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnchoredOcrLayoutStatusTest {
    @Test
    fun failedOrEmptyOcrCannotPublishRetainedSizeOrLuckyTags() {
        listOf(OcrDocumentStatus.FAILED, OcrDocumentStatus.EMPTY).forEach { status ->
            val result = sizeTagAndLuckyFromOcrLayout(layout(status))

            assertEquals("status=$status", null to false, result)
        }
    }

    @Test
    fun successfulOcrPublishesRecognizedSizeAndLuckyTags() {
        val result = sizeTagAndLuckyFromOcrLayout(layout(OcrDocumentStatus.SUCCESS))

        assertEquals("XL" to true, result)
    }

    private fun layout(status: OcrDocumentStatus) = MLKitOcrProvider.Layout(
        lines = listOf(
            MLKitOcrProvider.RecognizedBlock("XL", Rect(10, 10, 30, 30)),
            MLKitOcrProvider.RecognizedBlock("Lucky Pokémon", Rect(40, 10, 130, 30))
        ),
        elements = emptyList(),
        documentStatus = status
    )
}
