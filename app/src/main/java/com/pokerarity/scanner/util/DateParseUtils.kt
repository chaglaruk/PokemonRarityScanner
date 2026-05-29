// Amaç: Proje genelinde kullanılacak thread-safe tarih ayrıştırma ve biçimlendirme yardımcı sınıfı.
package com.pokerarity.scanner.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Date
import java.util.Locale

/**
 * Shared ISO-8601 and dynamic date parsing and formatting utility.
 *
 * Uses thread-safe [DateTimeFormatter] and [LocalDate] under the hood,
 * providing optimal performance without the garbage collection overhead of `SimpleDateFormat`.
 */
object DateParseUtils {
    private val ISO_DATE_ONLY_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

    val ISO_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.US)
        .withResolverStyle(ResolverStyle.STRICT)
    val ISO_DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
    val FILE_DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
    val MMM_DD_YYYY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)
    val MMM_YYYY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
    val YYYY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy", Locale.US)
    val MMM_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)
    val MMM_D_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    val D_YYYY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d, yyyy", Locale.US)

    // Locale-dependent formatters
    fun getSystemMmmDdYyyy(): DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
    fun getSystemMmmYyyy(): DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
    fun getSystemMmmD(): DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    fun getSystemDYyyy(): DateTimeFormatter = DateTimeFormatter.ofPattern("d, yyyy", Locale.getDefault())

    fun Date.toLocalDate(): LocalDate {
        return Instant.ofEpochMilli(this.time).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    fun Date.toLocalDateTime(): LocalDateTime {
        return Instant.ofEpochMilli(this.time).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }

    fun LocalDate.toDate(): Date {
        return Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())
    }

    fun LocalDateTime.toDate(): Date {
        return Date.from(this.atZone(ZoneId.systemDefault()).toInstant())
    }

    /**
     * Parses a strict ISO-8601 date-only string (`yyyy-MM-dd`) into a [Date].
     * Returns `null` if [value] is null, blank, malformed, or not a real calendar date.
     */
    fun parseIsoDate(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        if (!ISO_DATE_ONLY_PATTERN.matches(value)) return null
        return runCatching {
            LocalDate.parse(value, ISO_DATE_FORMATTER).toDate()
        }.getOrNull()
    }

    /**
     * Formats a [Date] utilizing a specified [DateTimeFormatter] in the system's timezone.
     */
    fun formatDate(date: Date, formatter: DateTimeFormatter): String {
        return formatter.format(Instant.ofEpochMilli(date.time).atZone(ZoneId.systemDefault()))
    }
}
