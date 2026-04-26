package com.pokerarity.scanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DateParseUtilsTest {

    private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @Test
    fun parseIsoDateReturnsNullForNullOrBlankValues() {
        listOf(null, "", "   ").forEach { value ->
            assertNull(DateParseUtils.parseIsoDate(value))
        }
    }

    @Test
    fun parseIsoDateParsesValidDateOnlyValue() {
        val parsed = DateParseUtils.parseIsoDate("2026-02-28")

        assertNotNull(parsed)
        assertEquals("2026-02-28", formatter.format(parsed))
    }

    @Test
    fun parseIsoDateRejectsInvalidCalendarDates() {
        listOf("2026-02-30", "2026-13-01", "2026-00-10", "2026-04-31").forEach { value ->
            assertNull(value, DateParseUtils.parseIsoDate(value))
        }
    }

    @Test
    fun parseIsoDateRejectsUnsupportedFormats() {
        listOf(
            "not-a-date",
            "2026-2-3",
            "2026-02-28T00:00:00Z",
            "2026-02-28 extra",
            " 2026-02-28"
        ).forEach { value ->
            assertNull(value, DateParseUtils.parseIsoDate(value))
        }
    }
}
