package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry
import com.pokerarity.scanner.data.model.HistoricalEventAppearance
import com.pokerarity.scanner.data.model.MasterPokedexEntry

internal object EventMetadataEvidence {

    fun sanitize(entry: AuthoritativeVariantEntry): AuthoritativeVariantEntry {
        val historicalEvents = sanitizeHistoricalEvents(runCatching { entry.historicalEvents }.getOrNull().orEmpty())
        val keepTopLevelEvent = shouldKeepTopLevelEvent(
            variantClass = runCatching { entry.variantClass }.getOrDefault("base"),
            isCostumeLike = runCatching { entry.isCostumeLike }.getOrDefault(false),
            eventStart = entry.eventStart,
            eventEnd = entry.eventEnd,
            historicalEvents = historicalEvents
        )

        return entry.copy(
            eventLabel = entry.eventLabel.takeIf { keepTopLevelEvent },
            eventStart = entry.eventStart.takeIf { keepTopLevelEvent && !it.isNullOrBlank() },
            eventEnd = entry.eventEnd.takeIf { keepTopLevelEvent && !it.isNullOrBlank() },
            historicalEvents = historicalEvents,
            aliases = runCatching { entry.aliases }.getOrNull().orEmpty(),
            sourceIds = runCatching { entry.sourceIds }.getOrNull().orEmpty()
        )
    }

    fun sanitize(entry: MasterPokedexEntry): MasterPokedexEntry {
        val historicalEvents = sanitizeHistoricalEvents(runCatching { entry.historicalEvents }.getOrNull().orEmpty())
        val keepTopLevelEvent = shouldKeepTopLevelEvent(
            variantClass = runCatching { entry.variantClass }.getOrDefault("base"),
            isCostumeLike = runCatching { entry.isCostumeLike }.getOrDefault(false),
            eventStart = entry.eventStart,
            eventEnd = entry.eventEnd,
            historicalEvents = historicalEvents
        )

        return entry.copy(
            eventLabel = entry.eventLabel.takeIf { keepTopLevelEvent },
            eventStart = entry.eventStart.takeIf { keepTopLevelEvent && !it.isNullOrBlank() },
            eventEnd = entry.eventEnd.takeIf { keepTopLevelEvent && !it.isNullOrBlank() },
            historicalEvents = historicalEvents,
            aliases = runCatching { entry.aliases }.getOrNull().orEmpty(),
            signature = runCatching { entry.signature }.getOrNull()
        )
    }

    private fun sanitizeHistoricalEvents(events: List<HistoricalEventAppearance>): List<HistoricalEventAppearance> {
        return events.filter { appearance ->
            !appearance.eventLabel.isNullOrBlank() &&
                !appearance.startDate.isNullOrBlank() &&
                !appearance.endDate.isNullOrBlank()
        }
    }

    private fun shouldKeepTopLevelEvent(
        variantClass: String,
        isCostumeLike: Boolean,
        eventStart: String?,
        eventEnd: String?,
        historicalEvents: List<HistoricalEventAppearance>
    ): Boolean {
        if (variantClass.equals("base", ignoreCase = true) && !isCostumeLike) {
            return false
        }
        if (historicalEvents.isNotEmpty()) {
            return true
        }
        return !eventStart.isNullOrBlank() && !eventEnd.isNullOrBlank()
    }
}
