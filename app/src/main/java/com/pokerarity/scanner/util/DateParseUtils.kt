package com.pokerarity.scanner.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared ISO-8601 date-only (`yyyy-MM-dd`) parsing utility.
 *
 * Creates a new [SimpleDateFormat] per call to guarantee thread safety —
 * `SimpleDateFormat` is NOT thread-safe and must never be shared across threads.
 * ISO-8601 datetime strings and other date formats are intentionally unsupported.
 */
object DateParseUtils {
    private val ISO_DATE_ONLY_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

    /**
     * Parses a strict ISO-8601 date-only string (`yyyy-MM-dd`) into a [Date].
     * Returns `null` if [value] is null, blank, malformed, or not a real calendar date.
     */
    fun parseIsoDate(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        if (!ISO_DATE_ONLY_PATTERN.matches(value)) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
            }.parse(value)
        }.getOrNull()
    }
}
