package com.pokerarity.scanner

import android.graphics.Rect
import com.pokerarity.scanner.util.ocr.AnchoredScreenText
import com.pokerarity.scanner.util.ocr.FieldRead
import com.pokerarity.scanner.util.ocr.SpeciesNameAcceptanceSource
import com.pokerarity.scanner.util.ocr.SpeciesNameDecision
import com.pokerarity.scanner.util.ocr.SpeciesNameDecisionDiagnostics
import com.pokerarity.scanner.util.ocr.NameCropOcrRecovery.shouldAttempt
import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.OcrDocumentStatus
import com.pokerarity.scanner.util.ocr.NameCropBounds
import com.pokerarity.scanner.util.ocr.NameCropOcrRecovery.boundedBounds
import com.pokerarity.scanner.util.ocr.NameCropOcrRecovery.merge
import com.pokerarity.scanner.util.ocr.NameCropOcrRecovery.translate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameCropOcrRecoveryTest {

    @Test
    fun confirmedRouteWithCpAndCandyCanRetryEvenWhenHpAndPowerUpUnreadable() {
        val withoutHp = titleFields(cp = true, hp = false, candy = true)
        assertTrue(shouldAttempt(withoutHp, routedDetail = true))
        assertTrue(!shouldAttempt(withoutHp, routedDetail = false))
    }

    @Test
    fun noDetailRouteOrInsufficientIndependentFieldsMustNotRetry() {
        assertTrue(!shouldAttempt(titleFields(cp = true, candy = false), routedDetail = true))
        assertTrue(!shouldAttempt(titleFields(cp = false, hp = false, candy = true), routedDetail = true))
        assertTrue(!shouldAttempt(titleFields(cp = true, candy = true, titleBand = false), routedDetail = true))
        assertTrue(!shouldAttempt(titleFields(cp = true, candy = true, numericConflict = true), routedDetail = true))
    }

    @Test
    fun legacyStrictEligibilityAndAlreadyRecognizedNameAreUnchanged() {
        val strict = titleFields(cp = true, hp = true, candy = true, types = true, powerUp = true,
            confirmedDetail = true)
        assertTrue(shouldAttempt(strict))
        val accepted = SpeciesNameDecision.Accepted(
            "Eevee", SpeciesNameAcceptanceSource.EXACT_CANONICAL,
            SpeciesNameDecisionDiagnostics(emptyList())
        )
        assertTrue(!shouldAttempt(strict.copy(name = accepted), routedDetail = true))
    }

    private fun titleFields(
        cp: Boolean = false,
        hp: Boolean = false,
        candy: Boolean = false,
        types: Boolean = false,
        powerUp: Boolean = false,
        confirmedDetail: Boolean = false,
        titleBand: Boolean = true,
        numericConflict: Boolean = false
    ): AnchoredScreenText.Fields = AnchoredScreenText.Fields(
        name = null,
        nameRaw = null,
        cp = 300.takeIf { cp },
        hp = (50 to 50).takeIf { hp },
        candy = "Eevee".takeIf { candy },
        powerUpCost = 200.takeIf { powerUp },
        types = setOf("normal").takeIf { types },
        detailScreen = confirmedDetail,
        hpRect = null,
        nameRect = Rect(100, 700, 800, 880).takeIf { titleBand },
        candyRect = null,
        costRect = null,
        cpRead = if (cp) FieldRead.read(300) else FieldRead.missing("not_visible"),
        hpRead = if (hp) FieldRead.read(50 to 50) else FieldRead.missing("not_visible"),
        candyRead = if (candy) FieldRead.read("Eevee") else FieldRead.missing("not_visible"),
        powerUpRead = if (powerUp) FieldRead.read(200) else FieldRead.missing("not_visible"),
        identityBlockingNumericConflict = numericConflict
    )

    @Test
    fun cropCoordinatesAreRestoredIntoTheSourceFrameWithoutLosingExistingLines() {
        val original = MLKitOcrProvider.Layout(
            lines = listOf(MLKitOcrProvider.RecognizedBlock("CP", null)),
            elements = emptyList()
        )
        val crop = MLKitOcrProvider.Layout(
            lines = listOf(MLKitOcrProvider.RecognizedBlock("title", null)),
            elements = listOf(MLKitOcrProvider.RecognizedBlock("pencil", null))
        )

        val merged = merge(original, crop, NameCropBounds(100, 700, 800, 900))

        assertEquals(2, merged.lines.size)
        assertEquals("CP", merged.lines.first().text)
        assertEquals("title", merged.lines.last().text)
        assertEquals("pencil", merged.elements.single().text)
    }

    @Test
    fun failedCropDoesNotAlterTheOriginalOcrDocument() {
        val original = MLKitOcrProvider.Layout(
            lines = listOf(MLKitOcrProvider.RecognizedBlock("CP", null)),
            elements = emptyList()
        )
        val failed = MLKitOcrProvider.Layout(emptyList(), emptyList(), OcrDocumentStatus.FAILED)

        assertSame(original, merge(original, failed, NameCropBounds(100, 700, 800, 900)))
    }

    @Test
    fun cropMustStayWithinTheBoundedTitleBand() {
        val accepted = boundedBounds(NameCropBounds(108, 704, 792, 880), 900, 1950)
        val oversized = boundedBounds(NameCropBounds(0, 0, 900, 1900), 900, 1950)
        val empty = boundedBounds(NameCropBounds(10, 10, 10, 40), 900, 1950)

        assertEquals(NameCropBounds(108, 704, 792, 880), accepted)
        assertNull(oversized)
        assertNull(empty)
        assertTrue((accepted!!.right - accepted.left) * (accepted.bottom - accepted.top) < 900 * 1950 / 8)
    }

    @Test
    fun croppedBlockCoordinatesTranslateIntoFullFrameSpace() {
        assertEquals(
            NameCropBounds(105, 712, 180, 738),
            translate(NameCropBounds(5, 12, 80, 38), NameCropBounds(100, 700, 800, 900))
        )
    }
}
