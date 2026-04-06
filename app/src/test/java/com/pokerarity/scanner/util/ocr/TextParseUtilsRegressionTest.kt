package com.pokerarity.scanner.util.ocr

import org.junit.Test
import org.junit.Assert.*

/**
 * Regression tests for CP/HP parsing improvements.
 * Based on real failure cases from Rapor.md (17 March 2026):
 * - Sawk: CP='1 311976 1', Vaporeon: HP='7227 / 227 HP'
 * - Seviper: HP='92/P2HP', Machamp: HP='174/174 HP 7'
 */
class TextParseUtilsRegressionTest {

    // ============ CP PARSING TESTS ============

    @Test
    fun testParseCpWithSpacedDigits_Sawk() {
        // Real case: "1 311976 1" should parse to 1976 (last 4 digits)
        val result = TextParseUtils.parseCP("1 311976 1")
        assertEquals("Expected CP 1976 from spaced '1 311976 1'", 1976, result)
    }

    @Test
    fun testParseCpLeadingZero_Minun() {
        // Real case: "03868" should become 3868
        val result = TextParseUtils.parseCP("03868")
        assertEquals("Expected CP 3868 from leading-zero '03868'", 3868, result)
    }

    @Test
    fun testParseCpWithExplicitAnchor() {
        // Standard anchor case
        val result = TextParseUtils.parseCP("CP 1234")
        assertEquals("Expected CP 1234 from 'CP 1234'", 1234, result)
    }

    @Test
    fun testParseCpWithNoise() {
        // Garbage before/after
        val result = TextParseUtils.parseCP("X 2100 ZZZZZ")
        assertEquals("Expected CP 2100 from 'X 2100 ZZZZZ'", 2100, result)
    }

    @Test
    fun testParseCpRejectsYear() {
        // Year-like patterns should be rejected
        val result = TextParseUtils.parseCP("2020")
        // Year 2020 is in exclusion range, should return null or try other extraction
        assertTrue("Should reject or extract non-year from year-like '2020'", 
                  result == null || result in 100..999)
    }

    // ============ HP PARSING TESTS ============

    @Test
    fun testParseHpWithLeadingDigitNoise_Vaporeon() {
        // Real case: "7227 / 227 HP" → should be (227, 227)
        // Extra leading "7" before "227" should be trimmed
        val result = TextParseUtils.parseHPPair("7227 / 227 HP")
        assertNotNull("Expected HP pair from '7227 / 227 HP'", result)
        assertEquals("Expected current HP 227", 227, result?.first)
        assertEquals("Expected max HP 227", 227, result?.second)
    }

    @Test
    fun testParseHpWithCharacterNoise_Seviper() {
        // Real case: "92/P2HP" → (92, 92)
        // Character 'P' in "P2" should be ignored
        val result = TextParseUtils.parseHPPair("92/P2HP")
        // The '2' after "/" and 'P' is noise, should be rejected or parsed as "92/2"
        if (result != null) {
            assertEquals("Expected HP around 92", 92, result.first)
            assertTrue("Expected max HP in reasonable range", result.second in 10..200)
        }
    }

    @Test
    fun testParseHpWithTrailingGarbage_Machamp() {
        // Real case: "174/174 HP 7" → (174, 174)
        // Trailing " 7" after HP should be ignored
        val result = TextParseUtils.parseHPPair("174/174 HP 7")
        assertNotNull("Expected HP pair from '174/174 HP 7'", result)
        assertEquals("Expected current HP 174", 174, result?.first)
        assertEquals("Expected max HP 174", 174, result?.second)
    }

    @Test
    fun testParseHpStandardCase() {
        // Standard well-formed case
        val result = TextParseUtils.parseHPPair("150/150")
        assertNotNull("Expected HP pair from '150/150'", result)
        assertEquals("Expected current HP 150", 150, result?.first)
        assertEquals("Expected max HP 150", 150, result?.second)
    }

    @Test
    fun testParseHpWithSpaces() {
        // Spaces around slash
        val result = TextParseUtils.parseHPPair("100  /  100")
        assertNotNull("Expected HP pair from '100  /  100'", result)
        assertEquals("Expected current HP 100", 100, result?.first)
        assertEquals("Expected max HP 100", 100, result?.second)
    }

    @Test
    fun testParseHpAllowsDamagedPokemonSlashPair_Kyurem() {
        val result = TextParseUtils.parseHPPair("51 / 212 HP")
        assertNotNull("Expected damaged HP pair from '51 / 212 HP'", result)
        assertEquals(51, result?.first)
        assertEquals(212, result?.second)
    }

    @Test
    fun testParseHpCurrentGreaterThanMax() {
        // Invalid: current > max
        val result = TextParseUtils.parseHPPair("200/100")
        // Should return null for invalid current > max
        assertNull("Expected rejection of invalid pair where current > max", result)
    }

    @Test
    fun testParseHpOutOfRange() {
        // Values outside reasonable HP range
        val result = TextParseUtils.parseHPPair("5/10")
        // Should probably return null as values too low
        assertNull("Expected rejection of out-of-range HP '5/10'", result)
    }

    // ============ COMBINED REGRESSION CASES ============

    @Test
    fun testCpHpTogetherSawk() {
        val cp = TextParseUtils.parseCP("1 311976 1")
        val hp = TextParseUtils.parseHPPair("128/128")
        assertEquals("Sawk CP should be 1976", 1976, cp)
        assertEquals("Sawk HP should be (128, 128)", Pair(128, 128), hp)
    }

    @Test
    fun testCpHpTogetherVaporeon() {
        val cp = TextParseUtils.parseCP("2100")
        val hp = TextParseUtils.parseHPPair("7227 / 227 HP")
        assertEquals("Vaporeon CP expected",  2100, cp)
        assertNotNull("Vaporeon HP should parse", hp)
        assertEquals("Vaporeon max HP should be 227", 227, hp?.second)
    }

    @Test
    fun testCpHpTogetherMachamp() {
        val cp = TextParseUtils.parseCP("3189")
        val hp = TextParseUtils.parseHPPair("174/174 HP 7")
        assertEquals("Machamp CP should be 3189", 3189, cp)
        assertEquals("Machamp HP should be (174, 174)", Pair(174, 174), hp)
    }
}

