package com.pokerarity.scanner

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.util.ocr.AnchoredScreenText
import com.pokerarity.scanner.util.ocr.ExtractionContext
import com.pokerarity.scanner.util.ocr.FieldReadStatus
import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.TextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class AnchoredScreenTextCpNormalizationTest {
    private val parser = TextParser(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun anchoredCpDigitsRecoverCommonOcrConfusions() {
        listOf(
            "CP I278" to 1278,
            "C P 12B8" to 1288,
            "CP 1 5O0" to 1500
        ).forEach { (label, expected) ->
            val lines = detailLines().map {
                if (it.text.startsWith("CP ")) block(label, 430, 210, 650, 260) else it
            }

            val result = extract(lines)

            assertEquals(label, expected, result.cp)
            assertEquals(label, FieldReadStatus.READ, result.cpRead.status)
            assertFalse(label, result.numericConflict)
        }
    }

    @Test
    fun completeCpTokenNeverTruncatesOrAcceptsAnEmbeddedPrefix() {
        listOf("CP 12345", "CP 10O00", "CP 123456", "CP 1", "CP OI").forEach { label ->
            val result = extract(detailLines().map {
                if (it.text.startsWith("CP ")) block(label, 430, 210, 650, 260) else it
            })
            assertNull(label, result.cp)
            assertEquals(label, FieldReadStatus.VISIBLE_UNREADABLE, result.cpRead.status)
        }
    }

    @Test
    fun completeCpTokenRepairsEveryDigitWithoutDiscardingCharacters() {
        listOf(
            "CP 12 78" to 1278,
            "CP 12,78" to 1278,
            "CP I278" to 1278,
            "CP 1 5O0" to 1500
        ).forEach { (label, expected) ->
            val result = extract(detailLines().map {
                if (it.text.startsWith("CP ")) block(label, 430, 210, 650, 260) else it
            })
            assertEquals(label, expected, result.cp)
            assertEquals(label, FieldReadStatus.READ, result.cpRead.status)
        }
    }

    @Test
    fun cpNormalizationRejectsOutOfDomainAndMalformedTokensWithoutTruncating() {
        listOf(
            "CP OI",
            "CP 10O00",
            "CP 12B8X",
            "CP 1280/2000",
            "CP 1280.5",
            "CP 123-45",
            "CP 1280_5",
            "CP 123 - 45",
            "CP 123+45",
            "CP 123 + 45",
            "CP 123 – 45",
            "CP 123 — 45",
            "CP 1280 \u2044 2000",
            "CP 1280\u22152000",
            "CP 1280 \uFF0F 2000",
            "CP 1280 _ 5",
            "CP 1280 / 2000",
            "CP 1280 . 5"
        ).forEach { label ->
            val lines = detailLines().map {
                if (it.text.startsWith("CP ")) block(label, 430, 210, 650, 260) else it
            }

            val result = extract(lines)

            assertNull(label, result.cp)
            assertEquals(label, FieldReadStatus.VISIBLE_UNREADABLE, result.cpRead.status)
        }

        val mixed = extract(detailLines() + block("CP I278", 430, 290, 650, 340))
        assertNull(mixed.cp)
        assertEquals(FieldReadStatus.CONFLICT, mixed.cpRead.status)
        assertTrue(mixed.numericConflict)

        val validAndMalformed = extract(
            detailLines().map {
                if (it.text.startsWith("CP ")) block("CP 1280", 430, 210, 650, 260) else it
            } + block("CP 1280 \u2044 2000", 430, 290, 650, 340)
        )
        assertNull(validAndMalformed.cp)
        assertEquals(FieldReadStatus.CONFLICT, validAndMalformed.cpRead.status)
        assertTrue(validAndMalformed.numericConflict)
    }

    private fun extract(lines: List<MLKitOcrProvider.RecognizedBlock>) = AnchoredScreenText.extract(
        layout = MLKitOcrProvider.Layout(lines, emptyList()),
        parser = parser,
        width = 1080,
        height = 2340,
        context = ExtractionContext(bar = Rect(310, 745, 770, 758))
    )

    private fun detailLines() = listOf(
        block("CP 424", 430, 210, 650, 260),
        block("Eevee", 320, 660, 760, 725),
        block("80 / 80 HP", 400, 790, 680, 830),
        block("NORMAL", 435, 935, 645, 965),
        block("WEIGHT", 100, 985, 290, 1020),
        block("HEIGHT", 790, 985, 980, 1020),
        block("EEVEE CANDY", 490, 1140, 800, 1180),
        block("POWER UP", 130, 1370, 410, 1420)
    )

    private fun block(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        MLKitOcrProvider.RecognizedBlock(text, Rect(left, top, right, bottom))
}
