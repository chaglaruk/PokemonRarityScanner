package com.pokerarity.scanner.util

import com.pokerarity.scanner.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanErrorUserMessageTest {

    @Test
    fun lowConfidenceFailureUsesLocalizedDetailScreenRecoveryResource() {
        assertEquals(
            R.string.scan_low_confidence_recovery,
            ScanError.LOW_CONFIDENCE_RESULT.userMessageResourceId
        )
        assertEquals(
            "Keep the Pokemon detail screen open; scroll until its name and HP/CP are visible, then retry.",
            ScanError.LOW_CONFIDENCE_RESULT.userMessage
        )
    }

    @Test
    fun unrelatedErrorsKeepTheirExistingMessages() {
        assertNull(ScanError.OCR_FAILED.userMessageResourceId)
        assertEquals(
            "Couldn't read text from the screenshot. Try a clearer angle.",
            ScanError.OCR_FAILED.userMessage
        )
    }
}
