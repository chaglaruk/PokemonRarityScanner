package com.pokerarity.scanner.util.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

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

    @Test
    fun dateOcrNoiseNormalizesToIsoDateWhereSafe() {
        val result = FieldCandidateNormalizer.normalizeDate(
            raw = "Nov 5, 2O17",
            currentDate = iso("2026-06-26")
        )

        assertEquals("2017-11-05", result.normalizedText)
        assertEquals("2017-11-05", result.parsedValue)
        assertEquals("found", result.status)
        assertEquals("date_found", result.reason)
    }

    @Test
    fun futureDateBecomesMissingNotFakeValue() {
        val result = FieldCandidateNormalizer.normalizeDate(
            raw = "2026-12-01",
            currentDate = iso("2026-06-26")
        )

        assertNull(result.parsedValue)
        assertEquals("missing", result.status)
        assertEquals("date_rejected_future", result.reason)
    }

    @Test
    fun impossibleDateBecomesMissingNotRolledDate() {
        val result = FieldCandidateNormalizer.normalizeDate(
            raw = "2020-02-30",
            currentDate = iso("2026-06-26")
        )

        assertNull(result.parsedValue)
        assertEquals("missing", result.status)
    }

    @Test
    fun dateNormalizerSupportsCommonPokemonGoFormats() {
        val today = iso("2026-06-26")

        assertEquals("2017-11-05", FieldCandidateNormalizer.normalizeDate("2017-11-05", today).parsedValue)
        assertEquals("2017-11-05", FieldCandidateNormalizer.normalizeDate("2017/11/05", today).parsedValue)
        assertEquals("2017-11-13", FieldCandidateNormalizer.normalizeDate("13/11/2017", today).parsedValue)
        assertEquals("2017-11-13", FieldCandidateNormalizer.normalizeDate("11/13/2017", today).parsedValue)
        assertEquals("2017-11-05", FieldCandidateNormalizer.normalizeDate("5 Nov 2017", today).parsedValue)
    }

    private fun iso(value: String) =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)!!
}
