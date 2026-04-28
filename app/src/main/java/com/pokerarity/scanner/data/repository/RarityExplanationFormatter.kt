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
    ): List<String> {
        val reasons = mutableListOf<String>()
        val cleanEvent = sanitizeDisplayEventLabel(eventLabel)
        val cleanVariant = sanitizeDisplayVariantLabel(variantLabel)
        val dateBackedEvent = cleanEvent?.takeIf {
            releaseWindow != null && (caughtDate == null || isCaughtDateInsideWindow(caughtDate, releaseWindow))
        }
        val eventWindow = formatCompactReleaseWindow(releaseWindow)

        if (!dateBackedEvent.isNullOrBlank()) {
            reasons += encodeExplanationItem(
                title = "Event Pokemon: $dateBackedEvent",
                detail = eventWindow
            )
        } else if (isCostumeLike) {
            reasons += encodeExplanationItem(
                title = cleanVariant?.let { "Costume Pokemon: $it" } ?: "Costume Pokemon",
                detail = null
            )
        }

        if (isShiny) {
            reasons += encodeExplanationItem(
                title = "Shiny Pokemon",
                detail = null
            )
        }

        if (hasLocationCard) {
            reasons += encodeExplanationItem(
                title = "Special background",
                detail = null
            )
        }

        if (hasSpecialForm && !isCostumeLike) {
            reasons += encodeExplanationItem(
                title = cleanVariant?.let { "Special form: $it" } ?: "Special form",
                detail = null
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
