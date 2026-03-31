package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry
import com.pokerarity.scanner.data.model.ReleaseWindow
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
        if (entry == null) return null
        val appearances = entry.historicalEvents
        if (appearances.isEmpty()) {
            if (entry.eventLabel == null && entry.variantLabel == null) return null
            return ResolvedHistoricalEvent(
                variantLabel = entry.variantLabel,
                eventLabel = entry.eventLabel,
                releaseWindow = ReleaseWindow(
                    firstSeen = entry.eventStart,
                    lastSeen = entry.eventEnd
                )
            )
        }

        val matched = if (caughtDate != null) {
            appearances.lastOrNull { appearance ->
                val start = parseDate(appearance.startDate)?.time
                val end = parseDate(appearance.endDate ?: appearance.startDate)?.time
                val time = caughtDate.time
                start != null && end != null && time in start..end
            }
        } else {
            null
        }

        if (caughtDate != null && matched == null) {
            return null
        }

        val resolvedAppearance = matched ?: appearances.maxByOrNull { it.endDate ?: it.startDate ?: "" }

        return ResolvedHistoricalEvent(
            variantLabel = entry.variantLabel,
            eventLabel = resolvedAppearance?.eventLabel ?: entry.eventLabel,
            releaseWindow = ReleaseWindow(
                firstSeen = resolvedAppearance?.startDate ?: entry.eventStart,
                lastSeen = resolvedAppearance?.endDate ?: entry.eventEnd
            )
        )
    }

    private fun parseDate(value: String?): Date? =
        runCatching {
            value?.let { java.text.SimpleDateFormat("yyyy-MM-dd").parse(it) }
        }.getOrNull()
}
