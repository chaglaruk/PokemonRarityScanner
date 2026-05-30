package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.OcrConfidenceReasons
import com.pokerarity.scanner.data.model.OcrConfidenceReasonsBuilder
import com.pokerarity.scanner.data.model.OcrField
import com.pokerarity.scanner.data.model.OcrFieldConfidence
import com.pokerarity.scanner.data.model.OcrFieldStatus
import com.pokerarity.scanner.data.model.OcrSignalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Tests for [OcrConfidenceReasons], [OcrConfidenceReasonsBuilder], and
 * associated enums. These validate the structured OCR confidence model
 * before pipeline integration.
 */
class OcrConfidenceReasonsTest {

    // ── Empty / default state ──────────────────────────────────────────

    @Test
    fun `EMPTY has no fields or warnings`() {
        val empty = OcrConfidenceReasons.EMPTY
        assertTrue(empty.fields.isEmpty())
        assertTrue(empty.warnings.isEmpty())
        assertTrue(empty.allReasonCodes().isEmpty())
    }

    @Test
    fun `forField returns null for missing field`() {
        val reasons = OcrConfidenceReasons.EMPTY
        assertNull(reasons.forField(OcrField.CP))
        assertNull(reasons.forField(OcrField.HP))
        assertNull(reasons.forField(OcrField.CAUGHT_DATE))
    }

    // ── Builder: CP field ──────────────────────────────────────────────

    @Test
    fun `builder records CP parsed when value present`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCp(cp = 1500)
            .build()

        val cp = reasons.forField(OcrField.CP)
        assertNotNull(cp)
        assertEquals(OcrFieldStatus.PARSED, cp!!.status)
        assertEquals(OcrSignalSource.TOP_TEXT, cp.source)
        assertTrue(cp.reasonCodes.contains("cp_parsed"))
        assertTrue(reasons.isParsed(OcrField.CP))
        assertFalse(reasons.isMissing(OcrField.CP))
    }

    @Test
    fun `builder records CP missing when value null`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCp(cp = null)
            .build()

        val cp = reasons.forField(OcrField.CP)
        assertNotNull(cp)
        assertEquals(OcrFieldStatus.MISSING, cp!!.status)
        assertTrue(cp.reasonCodes.contains("cp_missing"))
        assertFalse(reasons.isParsed(OcrField.CP))
        assertTrue(reasons.isMissing(OcrField.CP))
    }

    @Test
    fun `builder records CP with math fallback source`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCp(cp = 2000, source = OcrSignalSource.MATH_FALLBACK, reasonCodes = listOf("cp_math_fallback"))
            .build()

        val cp = reasons.forField(OcrField.CP)
        assertNotNull(cp)
        assertEquals(OcrFieldStatus.PARSED, cp!!.status)
        assertEquals(OcrSignalSource.MATH_FALLBACK, cp.source)
        assertTrue(cp.reasonCodes.contains("cp_math_fallback"))
    }

    // ── Builder: HP field ──────────────────────────────────────────────

    @Test
    fun `builder records HP parsed when maxHp present`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withHp(hp = 100, maxHp = 100)
            .build()

        val hp = reasons.forField(OcrField.HP)
        assertNotNull(hp)
        assertEquals(OcrFieldStatus.PARSED, hp!!.status)
        assertTrue(hp.reasonCodes.contains("hp_pair_parsed"))
    }

    @Test
    fun `builder records HP low confidence when only current HP`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withHp(hp = 85, maxHp = null)
            .build()

        val hp = reasons.forField(OcrField.HP)
        assertNotNull(hp)
        assertEquals(OcrFieldStatus.LOW_CONFIDENCE, hp!!.status)
        assertTrue(hp.reasonCodes.contains("hp_current_only"))
    }

    @Test
    fun `builder records HP missing when both null`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withHp(hp = null, maxHp = null)
            .build()

        val hp = reasons.forField(OcrField.HP)
        assertNotNull(hp)
        assertEquals(OcrFieldStatus.MISSING, hp!!.status)
        assertTrue(hp.reasonCodes.contains("hp_missing"))
        assertTrue(reasons.isMissing(OcrField.HP))
    }

    // ── Builder: CAUGHT_DATE field ─────────────────────────────────────

    @Test
    fun `builder records date parsed when value present`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCaughtDate(caughtDate = Date())
            .build()

        val date = reasons.forField(OcrField.CAUGHT_DATE)
        assertNotNull(date)
        assertEquals(OcrFieldStatus.PARSED, date!!.status)
        assertTrue(date.reasonCodes.contains("date_parsed"))
        assertTrue(reasons.isParsed(OcrField.CAUGHT_DATE))
    }

    @Test
    fun `builder records date missing when null`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCaughtDate(caughtDate = null)
            .build()

        val date = reasons.forField(OcrField.CAUGHT_DATE)
        assertNotNull(date)
        assertEquals(OcrFieldStatus.MISSING, date!!.status)
        assertTrue(date.reasonCodes.contains("date_missing"))
        assertTrue(reasons.isMissing(OcrField.CAUGHT_DATE))
    }

    // ── Builder: combined fields ───────────────────────────────────────

    @Test
    fun `builder combines CP HP and date fields`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCp(cp = 1234)
            .withHp(hp = 90, maxHp = 90)
            .withCaughtDate(caughtDate = Date())
            .build()

        assertEquals(3, reasons.fields.size)
        assertTrue(reasons.isParsed(OcrField.CP))
        assertTrue(reasons.isParsed(OcrField.HP))
        assertTrue(reasons.isParsed(OcrField.CAUGHT_DATE))
    }

    @Test
    fun `builder with all missing produces all missing statuses`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCp(cp = null)
            .withHp(hp = null, maxHp = null)
            .withCaughtDate(caughtDate = null)
            .build()

        assertEquals(3, reasons.fields.size)
        assertTrue(reasons.isMissing(OcrField.CP))
        assertTrue(reasons.isMissing(OcrField.HP))
        assertTrue(reasons.isMissing(OcrField.CAUGHT_DATE))
    }

    // ── allReasonCodes ─────────────────────────────────────────────────

    @Test
    fun `allReasonCodes collects from all fields`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCp(cp = 100)
            .withHp(hp = null, maxHp = null)
            .withCaughtDate(caughtDate = Date())
            .build()

        val codes = reasons.allReasonCodes()
        assertTrue(codes.contains("cp_parsed"))
        assertTrue(codes.contains("hp_missing"))
        assertTrue(codes.contains("date_parsed"))
        assertEquals(3, codes.size)
    }

    // ── Warnings ───────────────────────────────────────────────────────

    @Test
    fun `builder records warnings`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCp(cp = 500)
            .addWarning("detail_pass_backfill")
            .build()

        assertEquals(1, reasons.warnings.size)
        assertEquals("detail_pass_backfill", reasons.warnings[0])
    }

    // ── Custom reason codes ────────────────────────────────────────────

    @Test
    fun `builder accepts custom reason codes`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCp(cp = 999, reasonCodes = listOf("cp_ocr_noisy", "cp_retry_improved"))
            .build()

        val cp = reasons.forField(OcrField.CP)
        assertNotNull(cp)
        assertEquals(2, cp!!.reasonCodes.size)
        assertTrue(cp.reasonCodes.contains("cp_ocr_noisy"))
        assertTrue(cp.reasonCodes.contains("cp_retry_improved"))
    }

    // ── Confidence values ──────────────────────────────────────────────

    @Test
    fun `builder stores confidence value`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCp(cp = 1500, confidence = 0.95f)
            .build()

        val cp = reasons.forField(OcrField.CP)
        assertNotNull(cp)
        assertEquals(0.95f, cp!!.confidence!!, 0.001f)
    }

    @Test
    fun `confidence is null when not provided`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCp(cp = 1500)
            .build()

        val cp = reasons.forField(OcrField.CP)
        assertNotNull(cp)
        assertNull(cp!!.confidence)
    }

    // ── Privacy: no raw OCR text or file paths ─────────────────────────

    @Test
    fun `reason codes do not contain file paths or raw OCR`() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCp(cp = 1500)
            .withHp(hp = 100, maxHp = 100)
            .withCaughtDate(caughtDate = Date())
            .addWarning("detail_pass_backfill")
            .build()

        val allCodes = reasons.allReasonCodes() + reasons.warnings
        for (code in allCodes) {
            assertFalse("Reason code should not contain path separators: $code",
                code.contains("/") || code.contains("\\"))
            assertFalse("Reason code should not contain 'C:' drive prefix: $code",
                code.contains("C:", ignoreCase = true))
            assertFalse("Reason code should not contain raw OCR markers: $code",
                code.contains("Name:") || code.contains("NameHC:"))
        }
    }

    // ── Enum coverage ──────────────────────────────────────────────────

    @Test
    fun `OcrField enum has expected values`() {
        val fields = OcrField.values()
        assertTrue(fields.contains(OcrField.CP))
        assertTrue(fields.contains(OcrField.HP))
        assertTrue(fields.contains(OcrField.CAUGHT_DATE))
        assertTrue(fields.contains(OcrField.SPECIES))
        assertTrue(fields.contains(OcrField.SIZE_TAG))
        assertTrue(fields.contains(OcrField.LUCKY))
    }

    @Test
    fun `OcrFieldStatus enum has expected values`() {
        val statuses = OcrFieldStatus.values()
        assertTrue(statuses.contains(OcrFieldStatus.PARSED))
        assertTrue(statuses.contains(OcrFieldStatus.MISSING))
        assertTrue(statuses.contains(OcrFieldStatus.LOW_CONFIDENCE))
        assertTrue(statuses.contains(OcrFieldStatus.CONFLICT))
    }

    @Test
    fun `OcrSignalSource enum has expected values`() {
        val sources = OcrSignalSource.values()
        assertTrue(sources.contains(OcrSignalSource.TOP_TEXT))
        assertTrue(sources.contains(OcrSignalSource.DETAIL_PASS))
        assertTrue(sources.contains(OcrSignalSource.CANDY))
        assertTrue(sources.contains(OcrSignalSource.VISUAL))
        assertTrue(sources.contains(OcrSignalSource.MATH_FALLBACK))
    }

    // ── Direct construction ────────────────────────────────────────────

    @Test
    fun `direct construction works with explicit field list`() {
        val reasons = OcrConfidenceReasons(
            fields = listOf(
                OcrFieldConfidence(
                    field = OcrField.CP,
                    status = OcrFieldStatus.CONFLICT,
                    source = OcrSignalSource.TOP_TEXT,
                    confidence = 0.3f,
                    reasonCodes = listOf("cp_multi_candidate_conflict")
                )
            ),
            warnings = listOf("multi_frame_cp_mismatch")
        )

        assertEquals(1, reasons.fields.size)
        assertEquals(OcrFieldStatus.CONFLICT, reasons.forField(OcrField.CP)?.status)
        assertEquals(1, reasons.warnings.size)
    }
}
