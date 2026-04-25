package com.pokerarity.scanner.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared ISO-8601 date parsing utility.
 *
 * Creates a new [SimpleDateFormat] per call to guarantee thread safety —
 * `SimpleDateFormat` is NOT thread-safe and must never be shared across threads.
 */
object DateParseUtils {

    /**
     * Parses an ISO-8601 date string (yyyy-MM-dd) into a [Date].
     * Returns `null` if [value] is null, blank, or unparseable.
     */
    fun parseIsoDate(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)
        }.getOrNull()
    }
}
