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

        if (!dateBackedEvent.isNullOrBlank()) {
            val caughtText = caughtDate?.let { fullDateFormatter.format(it) }
            val detail = listOfNotNull(
                cleanVariant?.let { "Costume: $it." },
                caughtText?.let { "Caught on $it." },
                eventWindow?.let { window ->
                    val windowText = if (eventWindowDays != null) "$window, $eventWindowDays" else window
                    "That catch date fits the $windowText release window."
                },
            ).joinToString(" ").takeIf { it.isNotBlank() }
            reasons += encodeExplanationItem(
                title = "Event Pokemon: $dateBackedEvent",
                detail = detail ?: "Its catch date fits this event release."
            )
        } else if (isCostumeLike) {
            reasons += encodeExplanationItem(
                title = cleanVariant?.let { "Costume Pokemon: $it" } ?: "Costume Pokemon",
                detail = if (cleanVariant != null) {
                    "This costume makes it more collectible than the regular version."
                } else {
                    "A costume was detected, but the exact event needs a matching catch date before I name it."
                }
            )
        }

        if (isShiny) {
            reasons += encodeExplanationItem(
                title = "Shiny Pokemon",
                detail = when {
                    isCostumeLike -> "It is shiny and costumed, which is a stronger collector combo."
                    hasLocationCard -> "It is shiny and has a special background, so it stands out more."
                    else -> "Shiny Pokemon are harder to find than the regular version."
                }
            )
        }

        if (hasLocationCard) {
            reasons += encodeExplanationItem(
                title = "Special background",
                detail = "The background/location card is an extra collectible detail."
            )
        }

        if (hasSpecialForm && !isCostumeLike) {
            reasons += encodeExplanationItem(
                title = cleanVariant?.let { "Special form: $it" } ?: "Special form",
                detail = "This is not the regular form, so collectors may value it more."
            )
        }

        if (caughtDate != null && (ageScore ?: 0) > 0) {
            reasons += encodeExplanationItem(
                title = "Older catch",
                detail = "Caught on ${fullDateFormatter.format(caughtDate)}; older catches can be more interesting to collectors."
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
