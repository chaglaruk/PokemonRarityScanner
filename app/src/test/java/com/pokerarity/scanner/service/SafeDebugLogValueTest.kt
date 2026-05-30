// Purpose: Verify debug log helpers do not expose local filesystem paths.
package com.pokerarity.scanner.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SafeDebugLogValueTest {
    @Test
    fun localFileReferenceReportsPresenceWithoutPathDetails() {
        val value = SafeDebugLogValue.localFileReference("C:/Users/Player/AppData/Local/Temp/scan.png")

        assertEquals("present", value)
        assertFalse(value.contains("Users", ignoreCase = true))
        assertFalse(value.contains("scan.png", ignoreCase = true))
        assertFalse(value.contains("C:/", ignoreCase = true))
    }

    @Test
    fun localFileReferenceReportsAbsentForBlankPath() {
        assertEquals("absent", SafeDebugLogValue.localFileReference(null))
        assertEquals("absent", SafeDebugLogValue.localFileReference(""))
        assertEquals("absent", SafeDebugLogValue.localFileReference("   "))
    }
}
