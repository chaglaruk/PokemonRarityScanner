package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry
import com.pokerarity.scanner.data.model.ReleaseWindow
import com.pokerarity.scanner.util.DateParseUtils
import java.util.Date

data class ResolvedHistoricalEvent(
    val variantLabel: String? = null,
    val eventLabel: String? = null,
    val releaseWindow: ReleaseWindow? = null
)

internal object AuthoritativeHistoricalEventResolver {

    fun resolve(
        entry: AuthoritativeVariantEntry?,
        caughtDate: Date?
    ): ResolvedHistoricalEvent? {
        if (entry == null || caughtDate == null) return null
        val appearances = entry.historicalEvents
        if (appearances.isEmpty()) {
            if (entry.eventLabel == null && entry.variantLabel == null) return null
            val topLevelStart = parseDate(entry.eventStart)?.time
            val topLevelEnd = parseDate(entry.eventEnd ?: entry.eventStart)?.time
            if (topLevelStart != null && topLevelEnd != null) {
                val time = caughtDate.time
                if (time !in topLevelStart..topLevelEnd) return null
            } else if (topLevelStart == null && topLevelEnd == null) {
                return null
            }
            return ResolvedHistoricalEvent(
                variantLabel = entry.variantLabel,
                eventLabel = entry.eventLabel,
                releaseWindow = ReleaseWindow(
                    firstSeen = entry.eventStart,
                    lastSeen = entry.eventEnd
                )
            )
        }

        val matched = appearances.lastOrNull { appearance ->
            val start = parseDate(appearance.startDate)?.time
            val end = parseDate(appearance.endDate ?: appearance.startDate)?.time
            val time = caughtDate.time
            start != null && end != null && time in start..end
        }

        if (matched == null) {
            return null
        }

        return ResolvedHistoricalEvent(
            variantLabel = entry.variantLabel,
            eventLabel = matched.eventLabel,
            releaseWindow = ReleaseWindow(
                firstSeen = matched.startDate,
                lastSeen = matched.endDate ?: matched.startDate
            )
        )
    }

    private fun parseDate(value: String?): Date? = DateParseUtils.parseIsoDate(value)
}
