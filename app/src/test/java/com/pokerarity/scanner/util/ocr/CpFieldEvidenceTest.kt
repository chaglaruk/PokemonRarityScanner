package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class CpFieldEvidenceTest {
    @Test
    fun readCrop_acceptsOneCompleteNumberWhenHeaderLabelWasObscured() {
        val result = CpFieldEvidence.readCrop(layout("2122"))

        assertEquals(FieldReadStatus.READ, result.status)
        assertEquals(2122, result.value)
        assertEquals("unlabeled_cp_header_numeric", result.reasonCode)
    }

    @Test
    fun readCrop_withholdsConflictingOrMalformedUnlabeledNumbers() {
        val conflicting = CpFieldEvidence.readCrop(layout("2122", "2212"))
        val malformed = CpFieldEvidence.readCrop(layout("2122/24"))

        assertEquals(FieldReadStatus.MISSING_NOT_VISIBLE, conflicting.status)
        assertEquals("unlabeled_cp_header_numeric_ambiguous", conflicting.reasonCode)
        assertEquals(FieldReadStatus.MISSING_NOT_VISIBLE, malformed.status)
        assertEquals("unlabeled_cp_header_numeric_unresolved", malformed.reasonCode)
    }

    @Test
    fun readCrop_acceptsOneNumberAfterAnOccludedHeaderFragment() {
        val result = CpFieldEvidence.readCrop(layout("P 2122"))

        assertEquals(FieldReadStatus.READ, result.status)
        assertEquals(2122, result.value)
        assertEquals("unlabeled_cp_header_numeric", result.reasonCode)
    }

    private fun layout(vararg lines: String) = MLKitOcrProvider.Layout(
        lines = lines.mapIndexed { index, text ->
            MLKitOcrProvider.RecognizedBlock(text, Rect(100, index * 30, 300, index * 30 + 20))
        },
        elements = emptyList()
    )
}
