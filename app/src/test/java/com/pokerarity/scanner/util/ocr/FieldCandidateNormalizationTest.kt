package com.pokerarity.scanner.util.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldCandidateNormalizationTest {

    @Test
    fun cpOcrNoiseNormalizesToDigitsWhereSafe() {
        val result = FieldCandidateNormalizer.normalizeCp(" C P 1 5O0 ")

        assertEquals("1500", result.normalizedText)
        assertEquals("1500", result.parsedValue)
        assertEquals("found", result.status)
        assertTrue(result.score > 0f)
    }

    @Test
    fun hpOcrNoiseNormalizesToSlashPairWhereSafe() {
        val result = FieldCandidateNormalizer.normalizeHp("HP 9O / 9O HP", cp = 800)

        assertEquals("90/90", result.normalizedText)
        assertEquals("90/90", result.parsedValue)
        assertEquals("found", result.status)
    }

    @Test
    fun stardustNumericTextUsesParserOnlyWhenValid() {
        val valid = FieldCandidateNormalizer.normalizeStardust("Power up 1,600") { text ->
            if (text.contains("1600")) 1600 else null
        }
        val invalid = FieldCandidateNormalizer.normalizeStardust("Power up 999") { null }

        assertEquals("1600", valid.parsedValue)
        assertEquals("found", valid.status)
        assertNull(invalid.parsedValue)
        assertEquals("missing", invalid.status)
    }

    @Test
    fun garbageInputBecomesMissingNotFakeValue() {
        val cp = FieldCandidateNormalizer.normalizeCp("CP OOO")
        val hp = FieldCandidateNormalizer.normalizeHp("HP ///")
        val stardust = FieldCandidateNormalizer.normalizeStardust("dust abc") { null }

        assertNull(cp.parsedValue)
        assertEquals("missing", cp.status)
        assertNull(hp.parsedValue)
        assertEquals("missing", hp.status)
        assertNull(stardust.parsedValue)
        assertEquals("missing", stardust.status)
    }
}
