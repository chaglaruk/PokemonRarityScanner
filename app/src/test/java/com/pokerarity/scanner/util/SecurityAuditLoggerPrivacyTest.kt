package com.pokerarity.scanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAuditLoggerPrivacyTest {
    @Test
    fun sanitizeAuditValueRemovesControlCharactersAndBoundsLength() {
        val raw = "secret-token\r\nlocal-path\t" + "x".repeat(200)

        val sanitized = SecurityAuditLogger.sanitizeAuditValue(raw)

        assertTrue(sanitized!!.length <= SecurityAuditLogger.MAX_LOG_VALUE_LENGTH)
        assertFalse(sanitized.contains('\r'))
        assertFalse(sanitized.contains('\n'))
        assertFalse(sanitized.contains('\t'))
    }

    @Test
    fun sanitizeAuditValueRejectsBlankValues() {
        assertNull(SecurityAuditLogger.sanitizeAuditValue(" \r\n\t "))
    }

    @Test
    fun presenceSummaryDoesNotIncludeSensitiveValue() {
        val summary = SecurityAuditLogger.presenceSummary(
            label = "UploadId",
            value = "private-upload-id-123"
        )

        assertEquals("UploadId present: true", summary)
        assertFalse(summary.contains("private-upload-id-123"))
    }
}
