package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.ReleaseWindow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SanitizedVariantExplanation(
    val variantLabel: String?,
    val eventLabel: String?,
    val releaseWindow: ReleaseWindow?
)

object VariantExplanationSanity {
    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun sanitize(
        caughtDate: Date?,
        variantLabel: String?,
        eventLabel: String?,
        releaseWindow: ReleaseWindow?
    ): SanitizedVariantExplanation {
        val eventStart = releaseWindow?.firstSeen?.let(::parseIsoDate)
        val eventEnd = releaseWindow?.lastSeen?.let(::parseIsoDate)
        if (caughtDate != null && eventStart != null && caughtDate.before(eventStart)) {
            return SanitizedVariantExplanation(
                variantLabel = null,
                eventLabel = null,
                releaseWindow = null
            )
        }
        if (caughtDate != null && eventEnd != null && caughtDate.after(eventEnd)) {
            return SanitizedVariantExplanation(
                variantLabel = null,
                eventLabel = null,
                releaseWindow = null
            )
        }
        return SanitizedVariantExplanation(
            variantLabel = variantLabel,
            eventLabel = eventLabel,
            releaseWindow = releaseWindow
        )
    }

    private fun parseIsoDate(value: String): Date? = runCatching {
        isoDate.parse(value)
    }.getOrNull()
}
