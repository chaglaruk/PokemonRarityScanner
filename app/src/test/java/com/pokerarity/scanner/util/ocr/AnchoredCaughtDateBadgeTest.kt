package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import com.pokerarity.scanner.util.DateParseUtils
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnchoredCaughtDateBadgeTest {
    private val badge = Rect(770, 350, 1000, 430)

    @Test
    fun splitYearAndMonthDayRowsInsideBadgeAreParsedTogetherInScreenOrder() {
        val result = parseCaughtDateFromOcrLines(
            lines = listOf(
                line("21/11", Rect(830, 390, 930, 414)),
                line("2021", Rect(830, 360, 920, 384)),
                line("2024-05-19", Rect(20, 100, 180, 125))
            ),
            imageHeight = 1000,
            dateBadgeRect = badge
        )

        assertEquals("2021-11-21", format(result))
    }

    @Test
    fun conflictingLowerScreenDateKeepsTheResultAmbiguous() {
        val result = parseCaughtDateFromOcrLines(
            lines = listOf(
                line("2021", Rect(830, 360, 920, 384)),
                line("21/11", Rect(830, 390, 930, 414)),
                line("2020-03-04", Rect(120, 700, 320, 730))
            ),
            imageHeight = 1000,
            dateBadgeRect = badge
        )

        assertNull(result)
    }

    @Test
    fun conflictingDatesInsideBadgeKeepTheResultAmbiguous() {
        val result = parseCaughtDateFromOcrLines(
            lines = listOf(
                line("2021-11-21", Rect(800, 360, 930, 384)),
                line("2020-03-04", Rect(800, 390, 930, 414))
            ),
            imageHeight = 1000,
            dateBadgeRect = badge
        )

        assertNull(result)
    }

    @Test
    fun conflictingDatesOnOneBadgeLineAreNotResolvedByTheFirstParserMatch() {
        val result = parseCaughtDateFromOcrLines(
            lines = listOf(line("2021-11-21 2020-03-04", Rect(800, 360, 980, 384))),
            imageHeight = 1000,
            dateBadgeRect = badge
        )

        assertNull(result)
    }

    @Test
    fun futureBadgeDateIsRejected() {
        val result = parseCaughtDateFromOcrLines(
            lines = listOf(line("2026-12-01", Rect(800, 360, 930, 384))),
            imageHeight = 1000,
            dateBadgeRect = badge,
            currentDate = DateParseUtils.parseIsoDate("2026-06-26")!!
        )

        assertNull(result)
    }

    @Test
    fun lowerScreenDateFallbackRemainsAvailableWhenBadgeHasNoDate() {
        val result = parseCaughtDateFromOcrLines(
            lines = listOf(line("2017-11-05", Rect(120, 700, 320, 730))),
            imageHeight = 1000,
            dateBadgeRect = badge
        )

        assertEquals("2017-11-05", format(result))
    }

    @Test
    fun conflictingDatesOnOneLowerScreenLineRemainAmbiguous() {
        val result = parseCaughtDateFromOcrLines(
            lines = listOf(line("2021-11-21 2020-03-04", Rect(120, 700, 500, 730))),
            imageHeight = 1000,
            dateBadgeRect = badge
        )

        assertNull(result)
    }

    private fun line(text: String, bounds: Rect) = MLKitOcrProvider.RecognizedBlock(text, bounds)

    private fun format(date: Date?): String? =
        date?.let { DateParseUtils.formatDate(it, DateParseUtils.ISO_DATE_FORMATTER) }
}
