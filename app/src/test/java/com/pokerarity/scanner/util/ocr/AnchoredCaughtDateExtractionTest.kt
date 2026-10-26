package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import com.pokerarity.scanner.util.DateParseUtils
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnchoredCaughtDateExtractionTest {
    private val dateBadge = Rect(770, 350, 1000, 430)

    @Test
    fun futureDateInProductionCaughtDateRegionIsRejected() {
        val result = parseCaughtDateFromOcrLines(
            lines = listOf(line("2026-11-05", top = 700)),
            imageHeight = 1000,
            dateBadgeRect = dateBadge,
            currentDate = date("2026-10-26")
        )

        assertNull(result)
    }

    @Test
    fun validPastDateInProductionCaughtDateRegionIsAcceptedAndOtherRegionsAreIgnored() {
        val result = parseCaughtDateFromOcrLines(
            lines = listOf(
                line("2024-05-19", top = 120),
                line("2017-11-05", top = 700)
            ),
            imageHeight = 1000,
            dateBadgeRect = dateBadge,
            currentDate = date("2026-10-26")
        )

        assertEquals("2017-11-05", DateParseUtils.formatDate(result!!, DateParseUtils.ISO_DATE_FORMATTER))
    }

    @Test
    fun preLaunchDateInProductionCaughtDateRegionIsRejected() {
        val result = parseCaughtDateFromOcrLines(
            lines = listOf(line("2016-07-05", top = 700)),
            imageHeight = 1000,
            dateBadgeRect = dateBadge,
            currentDate = date("2026-10-26")
        )

        assertNull(result)
    }

    private fun line(text: String, top: Int) =
        MLKitOcrProvider.RecognizedBlock(text, Rect(10, top, 300, top + 30))

    private fun date(value: String) = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)!!
}
