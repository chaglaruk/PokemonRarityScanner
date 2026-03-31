package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.GlobalRarityLegacyEntry
import com.pokerarity.scanner.data.model.ReleaseWindow

data class GlobalLegacyExplanationResolved(
    val eventLabel: String?,
    val releaseWindow: ReleaseWindow?
)

internal object GlobalLegacyExplanationFallback {
    fun resolve(entry: GlobalRarityLegacyEntry?): GlobalLegacyExplanationResolved {
        if (entry == null) {
            return GlobalLegacyExplanationResolved(
                eventLabel = null,
                releaseWindow = null
            )
        }

        val historicalEventLabel = entry.lastKnownEvent ?: entry.eventLabel
        val historicalWindow = when {
            entry.firstSeen != null || entry.lastSeen != null ->
                ReleaseWindow(firstSeen = entry.firstSeen, lastSeen = entry.lastSeen)
            entry.eventStart != null || entry.eventEnd != null ->
                ReleaseWindow(firstSeen = entry.eventStart, lastSeen = entry.eventEnd)
            else -> null
        }
        if (historicalEventLabel != null || historicalWindow != null) {
            return GlobalLegacyExplanationResolved(
                eventLabel = historicalEventLabel,
                releaseWindow = historicalWindow
            )
        }

        val activeWindow = when {
            entry.activeEventStart != null || entry.activeEventEnd != null ->
                ReleaseWindow(firstSeen = entry.activeEventStart, lastSeen = entry.activeEventEnd)
            else -> null
        }
        return GlobalLegacyExplanationResolved(
            eventLabel = entry.activeEventLabel,
            releaseWindow = activeWindow
        )
    }
}
