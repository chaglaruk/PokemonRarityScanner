package com.pokerarity.scanner

import com.pokerarity.scanner.util.ocr.TextParseUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * JVM unit tests for TextParseUtils — no Android device required.
 * Mirrors existing androidTest cases + additional edge cases.
 */
class TextParserLogicTest {

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // --- CP parsing ---

    @Test
    fun parseCpPrefersExplicitAnchor() {
        assertEquals(1278, TextParseUtils.parseCP("0 CP1278 1"))
    }

    @Test
    fun parseCpFromCleanDigits() {
        assertEquals(2705, TextParseUtils.parseCP("2705"))
    }

    @Test
    fun parseCpIgnoresDateLikeNumbers() {
        assertNull(TextParseUtils.parseCP("2024"))
    }

    @Test
    fun parseCpFromNoisyText() {
        assertEquals(1500, TextParseUtils.parseCP("CP 1500"))
    }

    @Test
    fun parseCpReturnsNullForBlank() {
        assertNull(TextParseUtils.parseCP(""))
    }

    // --- HP parsing ---

    @Test
    fun parseHpRejectsImpossiblePairs() {
        assertNull(TextParseUtils.parseHPPair("43 /743 HP"))
        assertNull(TextParseUtils.parseHPPair("93/793 HP"))
        assertEquals(82 to 82, TextParseUtils.parseHPPair("82/82HP"))
    }

    @Test
    fun parseHpNormalPair() {
        assertEquals(120 to 150, TextParseUtils.parseHPPair("120/150 HP"))
    }

    @Test
    fun parseHpReturnsNullForBlank() {
        assertNull(TextParseUtils.parseHPPair(""))
    }

    // --- Date parsing ---

    @Test
    fun parseDateSupportsCompactBadgeTokens() {
        assertEquals("2018-11-08", fmt.format(TextParseUtils.parseDate("2018 1108")!!))
        assertEquals("2018-08-30", fmt.format(TextParseUtils.parseDate("2018 3008")!!))
        assertEquals("2018-10-21", fmt.format(TextParseUtils.parseDate("2018 21110")!!))
    }

    @Test
    fun parseDateWithSlash() {
        assertEquals("2023-03-22", fmt.format(TextParseUtils.parseDate("22/03 2023")!!))
    }

    @Test
    fun parseDateReturnsNullWithNoYear() {
        assertNull(TextParseUtils.parseDate("hello world"))
    }

    @Test
    fun parseDateReturnsNullForBlank() {
        assertNull(TextParseUtils.parseDate(""))
    }
    @Test
    fun parseDateRecoversFromOcrTypos() {
        assertEquals("2023-03-22", fmt.format(TextParseUtils.parseDate("ZZ/O3 20Z3")!!))
        assertEquals("2021-11-25", fmt.format(TextParseUtils.parseDate("2S/lI 2O21")!!))
        assertEquals("2020-08-08", fmt.format(TextParseUtils.parseDate("0B/OB/2020")!!))
    }
}
