package com.pokerarity.scanner

import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.OcrDocumentStatus
import com.pokerarity.scanner.util.ocr.NameCropBounds
import com.pokerarity.scanner.util.ocr.boundedNameCropBounds
import com.pokerarity.scanner.util.ocr.mergeNameCropLayout
import com.pokerarity.scanner.util.ocr.translateNameCropBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameCropOcrRecoveryTest {

    @Test
    fun cropCoordinatesAreRestoredIntoTheSourceFrameWithoutLosingExistingLines() {
        val original = MLKitOcrProvider.Layout(
            lines = listOf(MLKitOcrProvider.RecognizedBlock("CP", null)),
            elements = emptyList()
        )
        val crop = MLKitOcrProvider.Layout(
            lines = listOf(MLKitOcrProvider.RecognizedBlock("title", null)),
            elements = listOf(MLKitOcrProvider.RecognizedBlock("pencil", null))
        )

        val merged = mergeNameCropLayout(original, crop, NameCropBounds(100, 700, 800, 900))

        assertEquals(2, merged.lines.size)
        assertEquals("CP", merged.lines.first().text)
        assertEquals("title", merged.lines.last().text)
        assertEquals("pencil", merged.elements.single().text)
    }

    @Test
    fun failedCropDoesNotAlterTheOriginalOcrDocument() {
        val original = MLKitOcrProvider.Layout(
            lines = listOf(MLKitOcrProvider.RecognizedBlock("CP", null)),
            elements = emptyList()
        )
        val failed = MLKitOcrProvider.Layout(emptyList(), emptyList(), OcrDocumentStatus.FAILED)

        assertSame(original, mergeNameCropLayout(original, failed, NameCropBounds(100, 700, 800, 900)))
    }

    @Test
    fun cropMustStayWithinTheBoundedTitleBand() {
        val accepted = boundedNameCropBounds(NameCropBounds(108, 704, 792, 880), 900, 1950)
        val oversized = boundedNameCropBounds(NameCropBounds(0, 0, 900, 1900), 900, 1950)
        val empty = boundedNameCropBounds(NameCropBounds(10, 10, 10, 40), 900, 1950)

        assertEquals(NameCropBounds(108, 704, 792, 880), accepted)
        assertNull(oversized)
        assertNull(empty)
        assertTrue((accepted!!.right - accepted.left) * (accepted.bottom - accepted.top) < 900 * 1950 / 8)
    }

    @Test
    fun croppedBlockCoordinatesTranslateIntoFullFrameSpace() {
        assertEquals(
            NameCropBounds(105, 712, 180, 738),
            translateNameCropBounds(NameCropBounds(5, 12, 80, 38), NameCropBounds(100, 700, 800, 900))
        )
    }
}
