// Amaç: Nadirlik açıklamalarını ve olay tarihlerini biçimlendirmek.
package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.ReleaseWindow
import com.pokerarity.scanner.data.model.encodeExplanationItem
import com.pokerarity.scanner.util.DateParseUtils
import java.util.Date

object RarityExplanationFormatter {

    fun buildVariantReasons(
        species: String,
        variantClass: String?,
        isShiny: Boolean,
        isCostumeLike: Boolean,
        variantLabel: String?,
        primaryEventLabel: String?,
        eventTags: List<String>,
        releaseWindow: ReleaseWindow?,
    ): List<String> {
        val reasons = mutableListOf<String>()
        val eventName = sanitizeDisplayEventLabel(primaryEventLabel)
            ?: eventTags.asSequence().mapNotNull(::sanitizeDisplayEventLabel).firstOrNull()
        val releaseDetail = formatReleaseWindow(releaseWindow)
        val resolvedVariantLabel = sanitizeDisplayVariantLabel(variantLabel)

        when {
            isShiny && isCostumeLike -> {
                reasons += encodeExplanationItem(
                    title = resolvedVariantLabel?.let { "Costume: $it" } ?: "Shiny costume variant",
                    detail = eventName?.let { "Released through $it" }
                        ?: resolvedVariantLabel?.let { "Special shiny $species variant" }
                        ?: "$species costume-specific shiny variant"
                )
            }
            isCostumeLike -> {
                reasons += encodeExplanationItem(
                    title = resolvedVariantLabel?.let { "Costume: $it" } ?: "Costume variant",
                    detail = eventName?.let { "Released through $it" }
                        ?: resolvedVariantLabel?.let { "$species costume release" }
                        ?: "$species costume release"
                )
            }
            isShiny -> {
                reasons += encodeExplanationItem(
                    title = "Shiny variant",
                    detail = "$species shiny release"
                )
            }
            variantClass == "form" -> {
                reasons += encodeExplanationItem(
                    title = resolvedVariantLabel?.let { "Form: $it" } ?: "Special form",
                    detail = eventName?.let { "Released through $it" } ?: "$species alternate form"
                )
            }
        }

        if (!eventName.isNullOrBlank()) {
            reasons += encodeExplanationItem(
                title = "Event: $eventName",
                detail = releaseDetail ?: "Event metadata matched"
            )
        } else if (releaseDetail != null) {
            reasons += encodeExplanationItem(
                title = "Release window",
                detail = releaseDetail
            )
        }

        return reasons
    }

    fun buildValueReasons(
        isShiny: Boolean,
        isCostumeLike: Boolean,
        hasLocationCard: Boolean,
        hasSpecialForm: Boolean,
        variantLabel: String?,
        eventLabel: String?,
        releaseWindow: ReleaseWindow?,
        caughtDate: Date?,
        totalScore: Int? = null,
        baseScore: Int? = null,
        variantScore: Int? = null,
        ageScore: Int? = null,
        collectorScore: Int? = null,
    ): List<String> {
        val reasons = mutableListOf<String>()
        val cleanEvent = sanitizeDisplayEventLabel(eventLabel)
        val cleanVariant = sanitizeDisplayVariantLabel(variantLabel)
        val dateBackedEvent = cleanEvent?.takeIf {
            releaseWindow != null && (caughtDate == null || isCaughtDateInsideWindow(caughtDate, releaseWindow))
        }
        val eventWindow = formatCompactReleaseWindow(releaseWindow)
        val eventWindowDays = formatWindowDuration(releaseWindow)

        if (!dateBackedEvent.isNullOrBlank()) {
            val caughtText = caughtDate?.let { DateParseUtils.formatDate(it, DateParseUtils.getSystemMmmDdYyyy()) }
            val detail = listOfNotNull(
                cleanVariant?.let { "Costume: $it." },
                caughtText?.let { "Caught $it." },
                eventWindow?.let { window ->
                    val windowText = if (eventWindowDays != null) "$window, $eventWindowDays" else window
                    "This matches the $windowText event window."
                },
            ).joinToString(" ").takeIf { it.isNotBlank() }
            reasons += encodeExplanationItem(
                title = "Caught during $dateBackedEvent",
                detail = detail ?: "The catch date matches this event release."
            )
        } else if (isCostumeLike) {
            reasons += encodeExplanationItem(
                title = cleanVariant?.let { "Costume: $it" } ?: "Costume Pokemon",
                detail = if (cleanVariant != null) {
                    "Limited costumes are more collectible than regular spawns."
                } else {
                    "Event name is hidden until the catch date confirms the exact release."
                }
            )
        }

        if (isShiny) {
            reasons += encodeExplanationItem(
                title = "Shiny",
                detail = when {
                    isCostumeLike -> "Shiny plus costume is a stronger collector combination."
                    hasLocationCard -> "Shiny plus special background is harder to replace."
                    else -> "Shiny versions are harder to find than regular ones."
                }
            )
        }

        if (hasLocationCard) {
            reasons += encodeExplanationItem(
                title = "Special background",
                detail = "Background cards are limited event souvenirs."
            )
        }

        if (hasSpecialForm && !isCostumeLike) {
            reasons += encodeExplanationItem(
                title = cleanVariant?.let { "Special form: $it" } ?: "Special form",
                detail = "Alternate forms are more collectible than the regular form."
            )
        }

        if (caughtDate != null && (ageScore ?: 0) > 0) {
            reasons += encodeExplanationItem(
                title = "Older catch",
                detail = "Caught ${DateParseUtils.formatDate(caughtDate, DateParseUtils.getSystemMmmDdYyyy())}; older Pokemon matter more for collectors and trades."
            )
        }

        if (!isShiny && !isCostumeLike && !hasLocationCard && !hasSpecialForm && (baseScore ?: 0) >= 8) {
            reasons += encodeExplanationItem(
                title = "Species rarity",
                detail = "This species is less common than everyday spawns."
            )
        }

        return reasons.distinct().take(4)
    }

    fun buildAgeReason(caughtDate: Date, ageLabel: String?): String {
        val title = "Caught on ${DateParseUtils.formatDate(caughtDate, DateParseUtils.getSystemMmmDdYyyy())}"
        val detail = ageLabel?.takeIf { it.isNotBlank() } ?: DateParseUtils.formatDate(caughtDate, DateParseUtils.getSystemMmmYyyy())
        return encodeExplanationItem(title, detail)
    }

    fun formatReleaseWindow(window: ReleaseWindow?): String? {
        val firstSeen = window?.firstSeen?.let(DateParseUtils::parseIsoDate)
        val lastSeen = window?.lastSeen?.let(DateParseUtils::parseIsoDate)
        return when {
            firstSeen != null && lastSeen != null -> "First seen ${DateParseUtils.formatDate(firstSeen, DateParseUtils.getSystemMmmDdYyyy())}, last seen ${DateParseUtils.formatDate(lastSeen, DateParseUtils.getSystemMmmDdYyyy())}"
            firstSeen != null -> "First seen ${DateParseUtils.formatDate(firstSeen, DateParseUtils.getSystemMmmDdYyyy())}"
            lastSeen != null -> "Last seen ${DateParseUtils.formatDate(lastSeen, DateParseUtils.getSystemMmmDdYyyy())}"
            else -> null
        }
    }

    private fun formatCompactReleaseWindow(window: ReleaseWindow?): String? {
        val firstSeen = window?.firstSeen?.let(DateParseUtils::parseIsoDate)
        val lastSeen = window?.lastSeen?.let(DateParseUtils::parseIsoDate)
        return when {
            firstSeen != null && lastSeen != null -> {
                val sameYear = DateParseUtils.formatDate(firstSeen, DateParseUtils.YYYY_FORMATTER) ==
                    DateParseUtils.formatDate(lastSeen, DateParseUtils.YYYY_FORMATTER)
                val sameMonth = sameYear &&
                    DateParseUtils.formatDate(firstSeen, DateParseUtils.MMM_FORMATTER) ==
                    DateParseUtils.formatDate(lastSeen, DateParseUtils.MMM_FORMATTER)
                when {
                    sameMonth -> "${DateParseUtils.formatDate(firstSeen, DateParseUtils.getSystemMmmD())}-${DateParseUtils.formatDate(lastSeen, DateParseUtils.getSystemDYyyy())}"
                    sameYear -> "${DateParseUtils.formatDate(firstSeen, DateParseUtils.getSystemMmmD())} - ${DateParseUtils.formatDate(lastSeen, DateParseUtils.getSystemMmmDdYyyy())}"
                    else -> "${DateParseUtils.formatDate(firstSeen, DateParseUtils.getSystemMmmDdYyyy())} - ${DateParseUtils.formatDate(lastSeen, DateParseUtils.getSystemMmmDdYyyy())}"
                }
            }
            firstSeen != null -> DateParseUtils.formatDate(firstSeen, DateParseUtils.getSystemMmmDdYyyy())
            lastSeen != null -> DateParseUtils.formatDate(lastSeen, DateParseUtils.getSystemMmmDdYyyy())
            else -> null
        }
    }

    private fun formatWindowDuration(window: ReleaseWindow?): String? {
        val firstSeen = window?.firstSeen?.let(DateParseUtils::parseIsoDate)
        val lastSeen = window?.lastSeen?.let(DateParseUtils::parseIsoDate)
        if (firstSeen == null || lastSeen == null) return null
        val days = (((lastSeen.time - firstSeen.time) / 86_400_000L) + 1L).coerceAtLeast(1L)
        return if (days == 1L) "1-day event window" else "$days-day event window"
    }

    private fun isCaughtDateInsideWindow(caughtDate: Date, window: ReleaseWindow?): Boolean {
        val firstSeen = window?.firstSeen?.let(DateParseUtils::parseIsoDate) ?: return false
        val lastSeen = window.lastSeen?.let(DateParseUtils::parseIsoDate) ?: return false
        return caughtDate.time in firstSeen.time..lastSeen.time
    }

    private fun sanitizeDisplayEventLabel(label: String?): String? {
        val value = label?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (Regex("""^G\d+$""", RegexOption.IGNORE_CASE).matches(value)) return null
        if (Regex("""^P(ikachu|ichu|raichu)\s+Flying\s+\d+$""", RegexOption.IGNORE_CASE).matches(value)) return null
        if (Regex("""costume release$""", RegexOption.IGNORE_CASE).containsMatchIn(value)) return null
        return value
    }

    private fun sanitizeDisplayVariantLabel(label: String?): String? {
        val value = label?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (Regex("""^G\d+\s+costume$""", RegexOption.IGNORE_CASE).matches(value)) return null
        if (Regex("""^Flying\s+\d+\s+costume$""", RegexOption.IGNORE_CASE).matches(value)) return null
        if (Regex("""^Pikachu\s+Flying\s+\d+$""", RegexOption.IGNORE_CASE).matches(value)) return null
        return value
    }
}
