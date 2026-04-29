package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.ReleaseWindow
import com.pokerarity.scanner.data.model.encodeExplanationItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RarityExplanationFormatter {

    private val fullDateFormatter
        get() = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val monthDateFormatter
        get() = SimpleDateFormat("MMM yyyy", Locale.getDefault())

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
        val scoreDetail = formatScoreDetail(totalScore, baseScore, variantScore, ageScore, collectorScore)

        if (!dateBackedEvent.isNullOrBlank()) {
            val caughtText = caughtDate?.let { fullDateFormatter.format(it) }
            val detail = listOfNotNull(
                cleanVariant?.let { "Costume: $it" },
                caughtText?.let { "Caught on $it" },
                eventWindow?.let { window ->
                    if (eventWindowDays != null) "$window ($eventWindowDays)" else window
                },
                scoreDetail
            ).joinToString(". ").takeIf { it.isNotBlank() }
            reasons += encodeExplanationItem(
                title = "Event Pokemon: $dateBackedEvent",
                detail = detail
            )
        } else if (isCostumeLike) {
            reasons += encodeExplanationItem(
                title = cleanVariant?.let { "Costume Pokemon: $it" } ?: "Costume Pokemon",
                detail = scoreDetail ?: "Costume detected; exact event needs a matching catch date"
            )
        }

        if (isShiny) {
            reasons += encodeExplanationItem(
                title = "Shiny Pokemon",
                detail = when {
                    isCostumeLike -> "Shiny + costume is a stronger collector signal"
                    hasLocationCard -> "Shiny + special background is a stronger collector signal"
                    else -> "Shiny signal adds variant rarity"
                }
            )
        }

        if (hasLocationCard) {
            reasons += encodeExplanationItem(
                title = "Special background",
                detail = "Location/background card detected"
            )
        }

        if (hasSpecialForm && !isCostumeLike) {
            reasons += encodeExplanationItem(
                title = cleanVariant?.let { "Special form: $it" } ?: "Special form",
                detail = scoreDetail
            )
        }

        return reasons.distinct()
    }

    fun buildAgeReason(caughtDate: Date, ageLabel: String?): String {
        val title = "Caught on ${fullDateFormatter.format(caughtDate)}"
        val detail = ageLabel?.takeIf { it.isNotBlank() } ?: monthDateFormatter.format(caughtDate)
        return encodeExplanationItem(title, detail)
    }

    fun formatReleaseWindow(window: ReleaseWindow?): String? {
        val firstSeen = window?.firstSeen?.let(::parseIsoDate)
        val lastSeen = window?.lastSeen?.let(::parseIsoDate)
        return when {
            firstSeen != null && lastSeen != null -> "First seen ${fullDateFormatter.format(firstSeen)}, last seen ${fullDateFormatter.format(lastSeen)}"
            firstSeen != null -> "First seen ${fullDateFormatter.format(firstSeen)}"
            lastSeen != null -> "Last seen ${fullDateFormatter.format(lastSeen)}"
            else -> null
        }
    }

    private fun formatCompactReleaseWindow(window: ReleaseWindow?): String? {
        val firstSeen = window?.firstSeen?.let(::parseIsoDate)
        val lastSeen = window?.lastSeen?.let(::parseIsoDate)
        return when {
            firstSeen != null && lastSeen != null -> {
                val sameYear = SimpleDateFormat("yyyy", Locale.US).format(firstSeen) ==
                    SimpleDateFormat("yyyy", Locale.US).format(lastSeen)
                val sameMonth = sameYear &&
                    SimpleDateFormat("MMM", Locale.US).format(firstSeen) ==
                    SimpleDateFormat("MMM", Locale.US).format(lastSeen)
                when {
                    sameMonth -> "${SimpleDateFormat("MMM d", Locale.getDefault()).format(firstSeen)}-${SimpleDateFormat("d, yyyy", Locale.getDefault()).format(lastSeen)}"
                    sameYear -> "${SimpleDateFormat("MMM d", Locale.getDefault()).format(firstSeen)} - ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(lastSeen)}"
                    else -> "${fullDateFormatter.format(firstSeen)} - ${fullDateFormatter.format(lastSeen)}"
                }
            }
            firstSeen != null -> fullDateFormatter.format(firstSeen)
            lastSeen != null -> fullDateFormatter.format(lastSeen)
            else -> null
        }
    }

    private fun formatWindowDuration(window: ReleaseWindow?): String? {
        val firstSeen = window?.firstSeen?.let(::parseIsoDate)
        val lastSeen = window?.lastSeen?.let(::parseIsoDate)
        if (firstSeen == null || lastSeen == null) return null
        val days = (((lastSeen.time - firstSeen.time) / 86_400_000L) + 1L).coerceAtLeast(1L)
        return if (days == 1L) "1-day event window" else "$days-day event window"
    }

    private fun formatScoreDetail(
        totalScore: Int?,
        baseScore: Int?,
        variantScore: Int?,
        ageScore: Int?,
        collectorScore: Int?
    ): String? {
        if (totalScore == null) return null
        val parts = listOfNotNull(
            baseScore?.takeIf { it > 0 }?.let { "+$it base" },
            variantScore?.takeIf { it > 0 }?.let { "+$it variant" },
            ageScore?.takeIf { it > 0 }?.let { "+$it age" },
            collectorScore?.takeIf { it > 0 }?.let { "+$it collector" }
        )
        return if (parts.isEmpty()) {
            "Score: $totalScore"
        } else {
            "Score: $totalScore (${parts.joinToString(", ")})"
        }
    }

    private fun isCaughtDateInsideWindow(caughtDate: Date, window: ReleaseWindow?): Boolean {
        val firstSeen = window?.firstSeen?.let(::parseIsoDate) ?: return false
        val lastSeen = window.lastSeen?.let(::parseIsoDate) ?: return false
        return caughtDate.time in firstSeen.time..lastSeen.time
    }

    private fun parseIsoDate(value: String): Date? {
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)
        }.getOrNull()
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
