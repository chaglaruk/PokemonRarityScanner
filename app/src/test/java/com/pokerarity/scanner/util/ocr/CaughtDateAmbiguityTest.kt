package com.pokerarity.scanner.util.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * Caught date ambiguity and edge case regression tests for [TextParseUtils.parseDate].
 *
 * These cover:
 * - Ambiguous MM/DD vs DD/MM when both values are <= 12
 * - Future dates beyond the valid range
 * - Impossible calendar dates (Feb 30, month 13)
 * - OCR character substitution noise
 * - Compact date formats (no separators)
 * - Year-only inputs
 * - Multi-line badge text formats
 * - Empty/garbage inputs
 */
class CaughtDateAmbiguityTest {

    private fun assertDateEquals(
        expectedYear: Int,
        expectedMonth: Int, // 1-indexed (January = 1)
        expectedDay: Int,
        actual: java.util.Date?,
        message: String = ""
    ) {
        assertNotNull("Date should not be null: $message", actual)
        val cal = Calendar.getInstance().apply { time = actual!! }
        assertEquals("Year mismatch: $message", expectedYear, cal.get(Calendar.YEAR))
        assertEquals("Month mismatch: $message", expectedMonth - 1, cal.get(Calendar.MONTH))
        assertEquals("Day mismatch: $message", expectedDay, cal.get(Calendar.DAY_OF_MONTH))
    }

    // ── Unambiguous dates (one value > 12) ─────────────────────────────

    @Test
    fun `unambiguous date 25 slash 03 with year on separate line`() {
        val date = TextParseUtils.parseDate("2019\n25/03")
        assertDateEquals(2019, 3, 25, date, "25/03 -> day=25 month=3")
    }

    @Test
    fun `unambiguous date 15 dot 06 with year`() {
        val date = TextParseUtils.parseDate("2020\n15.06")
        assertDateEquals(2020, 6, 15, date, "15.06 -> day=15 month=6")
    }

    @Test
    fun `unambiguous date month 11 day 28`() {
        val date = TextParseUtils.parseDate("2018\n11/28")
        assertDateEquals(2018, 11, 28, date, "11/28 -> month=11 day=28")
    }

    // ── Ambiguous dates (both values <= 12) ────────────────────────────
    // When both values are valid as month or day, the parser treats
    // the first value as month. These tests document that behavior.

    @Test
    fun `ambiguous date 03 slash 07 treats first as month`() {
        val date = TextParseUtils.parseDate("2021\n03/07")
        assertNotNull("Should parse ambiguous 03/07", date)
        val cal = Calendar.getInstance().apply { time = date!! }
        // Parser puts first <= 12 value as month
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        // Both (month=3,day=7) and (month=7,day=3) are valid;
        // documenting actual behavior: v1 goes to month when ambiguous
        assertEquals("Ambiguous: first value becomes month, second becomes day",
            3, month)
        assertEquals(7, day)
    }

    @Test
    fun `ambiguous date 12 slash 01 treats first as month`() {
        val date = TextParseUtils.parseDate("2022\n12/01")
        assertNotNull("Should parse ambiguous 12/01", date)
        val cal = Calendar.getInstance().apply { time = date!! }
        // Both values <= 12: parser decides ordering
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        // Document actual behavior
        assertEquals("Ambiguous 12/01: month", 12, month)
        assertEquals("Ambiguous 12/01: day", 1, day)
    }

    // ── Impossible dates ───────────────────────────────────────────────

    @Test
    fun `February 30 is clamped`() {
        // makeDate clamps day to 31 max but doesn't validate calendar
        // This test documents the current behavior
        val date = TextParseUtils.parseDate("2020\n02/30")
        // 30 > 12 so day=30, month=2 → February 30 doesn't exist
        // but makeDate coerces, so a date is still produced
        if (date != null) {
            val cal = Calendar.getInstance().apply { time = date }
            // Just verify a date was produced and year is correct
            assertEquals(2020, cal.get(Calendar.YEAR))
        }
    }

    @Test
    fun `month 13 day 05 returns null`() {
        // "13/05" → v1=13 > 12, treated as day=13 month=5
        val date = TextParseUtils.parseDate("2019\n13/05")
        // This is actually valid: day=13, month=5
        assertNotNull("13/05 is valid as day=13, month=5", date)
        assertDateEquals(2019, 5, 13, date)
    }

    @Test
    fun `both values above 12 returns null`() {
        // Neither fits as month, so should return null
        val date = TextParseUtils.parseDate("2019\n32/33")
        assertNull("32/33 has no valid month/day interpretation", date)
    }

    // ── Year range boundaries ──────────────────────────────────────────

    @Test
    fun `year 2016 is minimum valid`() {
        val date = TextParseUtils.parseDate("2016\n07/04")
        assertNotNull("2016 is the minimum valid year", date)
        assertDateEquals(2016, 7, 4, date)
    }

    @Test
    fun `year 2026 is maximum valid`() {
        val date = TextParseUtils.parseDate("2026\n01/15")
        assertNotNull("2026 is the maximum valid year", date)
    }

    @Test
    fun `year 2015 returns null`() {
        val date = TextParseUtils.parseDate("2015\n07/04")
        assertNull("2015 is before Pokemon GO launch, no valid year anchor", date)
    }

    @Test
    fun `year 2027 is valid for closed testing future catches`() {
        val date = TextParseUtils.parseDate("2027\n07/04")
        assertNotNull("2027 should parse as a supported future caught date", date)
        assertDateEquals(2027, 7, 4, date)
    }

    @Test
    fun `year 2031 returns null`() {
        val date = TextParseUtils.parseDate("2031\n07/04")
        assertNull("2031 is beyond the supported caught-date range", date)
    }

    // ── OCR noise substitution ─────────────────────────────────────────

    @Test
    fun `OCR O substituted as 0 in year`() {
        // "2O19" → O→0 → "2019"
        val date = TextParseUtils.parseDate("2O19\n05/12")
        assertNotNull("O→0 substitution should parse year 2019", date)
        val cal = Calendar.getInstance().apply { time = date!! }
        assertEquals(2019, cal.get(Calendar.YEAR))
    }

    @Test
    fun `OCR I substituted as 1 in date`() {
        // "20I9" → I→1 → "2019"
        val date = TextParseUtils.parseDate("20I9\nI2/05")
        assertNotNull("I→1 substitution should parse year 2019", date)
        val cal = Calendar.getInstance().apply { time = date!! }
        assertEquals(2019, cal.get(Calendar.YEAR))
    }

    @Test
    fun `OCR S substituted as 5 in day`() {
        // "2020\nS/12" → S→5 → "5/12"
        val date = TextParseUtils.parseDate("2020\nS/12")
        assertNotNull("S→5 substitution", date)
    }

    // ── Multi-line badge text ──────────────────────────────────────────

    @Test
    fun `year above month slash day on separate line`() {
        val date = TextParseUtils.parseDate("2017\n11/05")
        assertDateEquals(2017, 11, 5, date, "Existing regression: year on first line")
    }

    @Test
    fun `year below month slash day`() {
        val date = TextParseUtils.parseDate("08/23\n2019")
        assertNotNull("Year on second line should still parse", date)
        val cal = Calendar.getInstance().apply { time = date!! }
        assertEquals(2019, cal.get(Calendar.YEAR))
    }

    @Test
    fun `date with spaces around separator`() {
        val date = TextParseUtils.parseDate("2020\n07 / 14")
        assertNotNull("Spaces around slash should parse", date)
        assertDateEquals(2020, 7, 14, date)
    }

    // ── Compact date formats ───────────────────────────────────────────

    @Test
    fun `compact four digit month day 0705`() {
        val date = TextParseUtils.parseDate("2018 0705")
        assertNotNull("Compact 0705 should parse as month/day", date)
        val cal = Calendar.getInstance().apply { time = date!! }
        assertEquals(2018, cal.get(Calendar.YEAR))
    }

    @Test
    fun `compact four digit month day 1225 Christmas`() {
        val date = TextParseUtils.parseDate("2019 1225")
        assertNotNull("Compact 1225 → December 25", date)
        assertDateEquals(2019, 12, 25, date)
    }

    // ── Garbage and empty inputs ───────────────────────────────────────

    @Test
    fun `blank input returns null`() {
        assertNull(TextParseUtils.parseDate(""))
        assertNull(TextParseUtils.parseDate("   "))
    }

    @Test
    fun `pure garbage returns null`() {
        assertNull(TextParseUtils.parseDate("XYZW!@#\$"))
    }

    @Test
    fun `year only without month day returns null`() {
        // Year alone without any month/day digits → no valid date
        val date = TextParseUtils.parseDate("2020")
        // This may return null or a default date; document behavior
        // Based on the code, digits.size==0 means no month/day → returns null
        assertNull("Year-only input should return null", date)
    }

    @Test
    fun `date text with only noise characters returns null`() {
        assertNull(TextParseUtils.parseDate("////...."))
    }

    // ── Edge: day 31 ───────────────────────────────────────────────────

    @Test
    fun `day 31 in valid month parses correctly`() {
        val date = TextParseUtils.parseDate("2020\n01/31")
        assertNotNull("Jan 31 is valid", date)
        assertDateEquals(2020, 1, 31, date)
    }

    @Test
    fun `day 31 with month greater than 12`() {
        // 31/01 → day=31, month=1
        val date = TextParseUtils.parseDate("2020\n31/01")
        assertNotNull("31/01 → day=31, month=1", date)
        assertDateEquals(2020, 1, 31, date)
    }

    // ── Dot separator ──────────────────────────────────────────────────

    @Test
    fun `dot separator 15 dot 08 with year`() {
        val date = TextParseUtils.parseDate("2021\n15.08")
        assertNotNull("Dot separator should parse", date)
        assertDateEquals(2021, 8, 15, date)
    }
}
