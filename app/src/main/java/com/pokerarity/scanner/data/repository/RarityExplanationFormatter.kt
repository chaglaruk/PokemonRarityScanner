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

    private fun parseIsoDate(value: String): Date? {
        return runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value) }.getOrNull()
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
        return value
    }
}
