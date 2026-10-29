package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import com.pokerarity.scanner.util.DateParseUtils.toDate
import com.pokerarity.scanner.util.DateParseUtils.toLocalDate
import java.time.LocalDate
import java.util.Date

/** A failed OCR document cannot contribute a catch date from retained or partial lines. */
internal fun parseCaughtDateFromOcrLayout(
    layout: MLKitOcrProvider.Layout,
    imageHeight: Int,
    dateBadgeRect: Rect,
    currentDate: Date = Date()
): Date? = parseCaughtLocalDateFromOcrLayout(layout, imageHeight, dateBadgeRect, currentDate.toLocalDate())?.toDate()

/** Keep the calendar day before constructing the legacy epoch representation. */
internal fun parseCaughtLocalDateFromOcrLayout(
    layout: MLKitOcrProvider.Layout,
    imageHeight: Int,
    dateBadgeRect: Rect,
    currentDate: LocalDate = LocalDate.now()
): LocalDate? {
    if (layout.documentStatus != OcrDocumentStatus.SUCCESS) return null
    return parseCaughtLocalDateFromOcrLines(layout.lines, imageHeight, dateBadgeRect, currentDate)
}

/** Parses the catch badge as one spatial text group so split year/day-month rows stay together. */
internal fun parseCaughtDateFromOcrLines(
    lines: List<MLKitOcrProvider.RecognizedBlock>,
    imageHeight: Int,
    dateBadgeRect: Rect,
    currentDate: Date = Date()
): Date? = parseCaughtLocalDateFromOcrLines(lines, imageHeight, dateBadgeRect, currentDate.toLocalDate())?.toDate()

private fun parseCaughtLocalDateFromOcrLines(
    lines: List<MLKitOcrProvider.RecognizedBlock>,
    imageHeight: Int,
    dateBadgeRect: Rect,
    currentDate: LocalDate
): LocalDate? {
    if (imageHeight <= 0 || dateBadgeRect.isEmpty) return null

    val badgeLines = lines
        .filter { line -> line.bounds?.let { Rect.intersects(it, dateBadgeRect) } == true }
        .sortedWith(compareBy(
            { line -> line.bounds?.top ?: Int.MAX_VALUE },
            { line -> line.bounds?.left ?: Int.MAX_VALUE }
        ))
    val badgeText = badgeLines.joinToString("\n") { it.text }

    val badgeDates = (
        validatedCaughtDateCandidates(badgeText, currentDate) +
            badgeLines.flatMap { line -> validatedCaughtDateCandidates(line.text, currentDate) }
        ).distinct()
    val lowerScreenDates = lines
        .filter { line -> line.bounds?.top?.let { it > imageHeight / 2 } == true }
        .flatMap { line -> validatedCaughtDateCandidates(line.text, currentDate) }

    return if (badgeDates.size > 1) null else (badgeDates + lowerScreenDates).distinct().singleOrNull()
}

private val COMPLETE_CATCH_DATE_PATTERNS = listOf(
    Regex("""\b[0-9OILSZB]{4}\s*[-/.]\s*[0-9OILSZB]{1,2}\s*[-/.]\s*[0-9OILSZB]{1,2}\b""", RegexOption.IGNORE_CASE),
    Regex("""\b[0-9OILSZB]{1,2}\s*[-/.]\s*[0-9OILSZB]{1,2}\s*[-/.]\s*[0-9OILSZB]{4}\b""", RegexOption.IGNORE_CASE),
    Regex("""\b[0-9OILSZB]{4}\s+[0-9OILSZB]{1,2}\s*[/.]\s*[0-9OILSZB]{1,2}\b""", RegexOption.IGNORE_CASE),
    Regex("""\b[0-9OILSZB]{4}\s+[0-9OILSZB]{4,5}\b""", RegexOption.IGNORE_CASE),
    Regex(
        """\b(?:[A-Za-z]{3,9}\.?\s+[0-9OILSZB]{1,2}(?:ST|ND|RD|TH)?[,]?\s+[0-9OILSZB]{4}|""" +
            """[0-9OILSZB]{1,2}(?:ST|ND|RD|TH)?\s+[A-Za-z]{3,9}\.?[,]?\s+[0-9OILSZB]{4})\b""",
        RegexOption.IGNORE_CASE
    )
)

private fun completeCatchDateMatches(text: String): List<String> =
    COMPLETE_CATCH_DATE_PATTERNS.flatMap { pattern -> pattern.findAll(text).map { it.value }.toList() }

private fun validatedCaughtDateCandidates(text: String, currentDate: LocalDate): List<LocalDate> =
    (listOfNotNull(parseValidatedCatchLocalDate(text, currentDate)) +
        completeCatchDateMatches(text).mapNotNull { parseValidatedCatchLocalDate(it, currentDate) })
