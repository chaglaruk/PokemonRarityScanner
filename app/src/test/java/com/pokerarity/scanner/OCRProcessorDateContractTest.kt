package com.pokerarity.scanner

import com.pokerarity.scanner.util.ocr.FieldCandidateNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class OCRProcessorDateContractTest {

    @Test
    fun dateContractSupportsFastPathDateNoise() {
        val result = FieldCandidateNormalizer.normalizeDate(
            raw = "Nov 5, 2O17",
            currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-06-27")!!
        )

        assertEquals("found", result.status)
        assertEquals("2017-11-05", result.parsedValue)
        assertEquals("date_found", result.reason)
        assertNotNull(result.normalizedText)
    }
}
