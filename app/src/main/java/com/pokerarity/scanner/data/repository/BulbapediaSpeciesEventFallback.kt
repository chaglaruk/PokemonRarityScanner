package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.BulbapediaEventArchiveEntry
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.ReleaseWindow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object BulbapediaSpeciesEventFallback {
    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun resolve(
        finalSpecies: String,
        caughtDate: Date?,
        costumeLike: Boolean,
        fullMatch: FullVariantMatch?,
        bySpecies: Map<String, List<BulbapediaEventArchiveEntry>>,
    ): ResolvedExplanationMetadata? {
        if (caughtDate == null || !costumeLike) return null
        val entries = bySpecies[finalSpecies].orEmpty()
        if (entries.isEmpty()) return null

        val tokenHints = buildTokenHints(fullMatch)
        val matched = entries.mapNotNull { entry ->
            val appearance = entry.appearances.firstOrNull { appearance ->
                val start = appearance.startDate?.let(::parseDate) ?: return@firstOrNull false
                val end = (appearance.endDate ?: appearance.startDate)?.let(::parseDate) ?: start
                caughtDate.time in start.time..end.time
            } ?: return@mapNotNull null

            val spanDays = computeSpanDays(appearance.startDate, appearance.endDate)
            val tokenScore = when {
                entry.normalizedToken.isNullOrBlank() -> 0
                tokenHints.contains(normalizeToken(entry.normalizedToken)) -> 100
                else -> 0
            }
            Triple(entry, appearance, tokenScore to spanDays)
        }

        if (matched.isEmpty()) return null

        val best = matched.maxWithOrNull(
            compareBy<Triple<BulbapediaEventArchiveEntry, com.pokerarity.scanner.data.model.HistoricalEventAppearance, Pair<Int, Long>>>(
                { it.third.first },
                { -it.third.second.toInt() }
            )
        ) ?: return null

        val distinctEvents = matched.mapNotNull { it.second.eventLabel }.distinct()
        if (best.third.first == 0 && distinctEvents.size > 1) {
            return null
        }

        val variantLabel = best.first.formLabel
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it.contains("costume", ignoreCase = true)) it else "$it costume" }

        return ResolvedExplanationMetadata(
            variantLabel = variantLabel,
            eventLabel = best.second.eventLabel,
            releaseWindow = ReleaseWindow(
                firstSeen = best.second.startDate,
                lastSeen = best.second.endDate ?: best.second.startDate
            )
        )
    }

    private fun computeSpanDays(startRaw: String?, endRaw: String?): Long {
        val start = startRaw?.let(::parseDate) ?: return Long.MAX_VALUE
        val end = (endRaw ?: startRaw)?.let(::parseDate) ?: start
        return ((end.time - start.time) / 86_400_000L).coerceAtLeast(0L)
    }

    private fun buildTokenHints(fullMatch: FullVariantMatch?): Set<String> {
        val values = mutableSetOf<String>()
        val spriteKey = fullMatch?.finalSpriteKey.orEmpty()
        if (spriteKey.isNotBlank()) {
            values += normalizeToken(spriteKey.substringAfter('_').substringAfter('_'))
        }
        values += normalizeToken(fullMatch?.resolvedEventLabel)
        return values.filter { it.isNotBlank() }.toSet()
    }

    private fun normalizeToken(value: String?): String {
        val normalized = value
            .orEmpty()
            .uppercase(Locale.US)
            .replace("WORLD CHAMPIONSHIPS", "WCS")
            .replace("â€™", "")
            .replace("'", "")
            .replace("-", "_")
            .replace(" ", "_")
            .replace("__", "_")
            .trim('_')

        return when {
            normalized.startsWith("FFLYING_") || normalized.startsWith("FLYING_") -> "FLY"
            normalized.startsWith("FFWCS_") -> normalized.removePrefix("FF")
            normalized.startsWith("FWCS_") -> normalized.removePrefix("F")
            normalized.startsWith("CSPRING_") -> normalized.removePrefix("C")
            normalized.startsWith("CFALL_") -> normalized.removePrefix("C")
            normalized.startsWith("CSUMMER_") -> normalized.removePrefix("C")
            normalized.startsWith("CNOVEMBER_") -> normalized.removePrefix("C")
            normalized.startsWith("FWINTER_") -> normalized.removePrefix("F")
            normalized.startsWith("FFASHION_") -> normalized.removePrefix("F")
            normalized.startsWith("FGOFEST_") -> normalized.removePrefix("F")
            normalized.startsWith("FCOSTUME_") -> normalized.removePrefix("F")
            normalized.startsWith("FJACKET") -> "JACKET"
            else -> normalized
        }
    }

    private fun parseDate(value: String): Date? = runCatching { isoDate.parse(value) }.getOrNull()
}
