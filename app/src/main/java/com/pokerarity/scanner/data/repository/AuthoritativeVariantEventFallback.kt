package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry
import com.pokerarity.scanner.data.model.ReleaseWindow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object AuthoritativeVariantEventFallback {
    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private data class MatchingAppearance(
        val variantLabel: String?,
        val eventLabel: String?,
        val start: String?,
        val end: String?
    )

    fun resolve(
        finalSpecies: String,
        caughtDate: Date?,
        costumeLike: Boolean,
        shiny: Boolean,
        bySpecies: Map<String, List<AuthoritativeVariantEntry>>
    ): ResolvedExplanationMetadata? {
        if (caughtDate == null || !costumeLike) return null
        val entries = bySpecies[finalSpecies].orEmpty()
            .filter { it.isCostumeLike }

        val best = entries
            .mapNotNull { entry ->
                resolveMatchingAppearance(entry, caughtDate)?.let { appearance ->
                    val start = appearance.start?.let(::parseDate) ?: return@let null
                    val end = appearance.end?.let(::parseDate) ?: start
                    val spanDays = ((end.time - start.time) / 86_400_000L).coerceAtLeast(0L)
                    Triple(entry, appearance, spanDays)
                }
            }
            .minByOrNull { it.third }
            ?: return null

        return ResolvedExplanationMetadata(
            variantLabel = best.second.variantLabel ?: best.first.variantLabel,
            eventLabel = best.second.eventLabel ?: best.first.eventLabel,
            releaseWindow = ReleaseWindow(
                firstSeen = best.second.start ?: best.first.eventStart,
                lastSeen = best.second.end ?: best.first.eventEnd
            )
        )
    }

    private fun resolveMatchingAppearance(
        entry: AuthoritativeVariantEntry,
        caughtDate: Date
    ): MatchingAppearance? {
        return buildAppearances(entry)
            .mapNotNull { appearance ->
                val start = appearance.start?.let(::parseDate) ?: return@mapNotNull null
                val end = appearance.end?.let(::parseDate) ?: start
                if (caughtDate.before(start) || caughtDate.after(end)) return@mapNotNull null
                val spanDays = ((end.time - start.time) / 86_400_000L).coerceAtLeast(0L)
                appearance to spanDays
            }
            .minByOrNull { it.second }
            ?.first
    }

    private fun buildAppearances(entry: AuthoritativeVariantEntry): List<MatchingAppearance> {
        val appearances = entry.historicalEvents.map {
            MatchingAppearance(
                variantLabel = entry.variantLabel,
                eventLabel = it.eventLabel,
                start = it.startDate,
                end = it.endDate
            )
        }.toMutableList()
        if (!entry.eventStart.isNullOrBlank() || !entry.eventEnd.isNullOrBlank()) {
            appearances += MatchingAppearance(
                variantLabel = entry.variantLabel,
                eventLabel = entry.eventLabel,
                start = entry.eventStart,
                end = entry.eventEnd
            )
        }
        return appearances
    }

    private fun parseDate(value: String): Date? = runCatching { isoDate.parse(value) }.getOrNull()
}
