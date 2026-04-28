package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.ReleaseWindow
import com.pokerarity.scanner.util.DateParseUtils
import java.util.Date

data class ResolvedExplanationMetadata(
    val variantLabel: String?,
    val eventLabel: String?,
    val releaseWindow: ReleaseWindow?
)

internal object VariantExplanationMetadata {
    fun resolve(
        selection: VariantExplanationSelection,
        fullMatch: FullVariantMatch?,
        authoritativeBySprite: Map<String, AuthoritativeVariantEntry>,
        caughtDate: Date? = null
    ): ResolvedExplanationMetadata {
        if (!selection.allowDerivedMetadata && !selection.allowExactMetadata) {
            return ResolvedExplanationMetadata(
                variantLabel = null,
                eventLabel = null,
                releaseWindow = null
            )
        }

        val selectionAuthoritative = selection.entry?.spriteKey?.let(authoritativeBySprite::get)
        val matchedAuthoritative = fullMatch?.finalSpriteKey?.let(authoritativeBySprite::get)
        val selectionHistorical =
            AuthoritativeHistoricalEventResolver.resolve(selectionAuthoritative, caughtDate)
        val matchedHistorical =
            AuthoritativeHistoricalEventResolver.resolve(matchedAuthoritative, caughtDate)
        val exactEventWindow = when {
            fullMatch != null && caughtDate == null ->
                if (fullMatch.resolvedEventWindow == null) {
                    matchedAuthoritative?.topLevelReleaseWindowIfUnambiguous()
                } else {
                    null
                }
            fullMatch != null -> fullMatch.resolvedEventWindow
                ?: matchedHistorical?.releaseWindow
                ?: matchedAuthoritative?.topLevelReleaseWindowIfUnambiguous()
            else -> selectionHistorical?.releaseWindow
                ?: selection.releaseWindowOrNull()
                ?: selectionAuthoritative?.topLevelReleaseWindowIfUnambiguous()
        }
        val canExposeExactEventMetadata =
            (selection.allowExactMetadata || (fullMatch != null && selection.allowDerivedMetadata)) &&
                canExposeEventWindow(caughtDate, exactEventWindow)
        val variantLabel = when {
            fullMatch?.explanationMode == "generic_variant" -> selection.variantLabelOrNull()
            fullMatch != null -> matchedAuthoritative?.variantLabel ?: selection.variantLabelOrNull()
            selection.allowExactMetadata || selection.allowDerivedMetadata ->
                selectionAuthoritative?.variantLabel ?: selection.variantLabelOrNull()
            else -> null
        }

        return ResolvedExplanationMetadata(
            variantLabel = variantLabel,
            eventLabel = if (canExposeExactEventMetadata) {
                when {
                    fullMatch != null && caughtDate != null && fullMatch.resolvedEventWindow != null ->
                        fullMatch.resolvedEventLabel ?: matchedHistorical?.eventLabel
                    fullMatch != null ->
                        matchedHistorical?.eventLabel ?: matchedAuthoritative?.eventLabel
                    else ->
                        selectionHistorical?.eventLabel ?: selectionAuthoritative?.eventLabel ?: selection.primaryEventLabelOrNull()
                }
            } else {
                null
            },
            releaseWindow = if (canExposeExactEventMetadata) {
                exactEventWindow
            } else {
                null
            }
        )
    }

    private fun canExposeEventWindow(caughtDate: Date?, window: ReleaseWindow?): Boolean {
        if (window?.firstSeen.isNullOrBlank() || window?.lastSeen.isNullOrBlank()) return false
        if (caughtDate == null) return true
        val start = parseDate(window!!.firstSeen) ?: return false
        val end = parseDate(window.lastSeen) ?: return false
        return caughtDate.time in start.time..end.time
    }

    private fun AuthoritativeVariantEntry.topLevelReleaseWindowIfUnambiguous(): ReleaseWindow? {
        if (historicalEvents.isNotEmpty()) return null
        if (eventStart.isNullOrBlank() || eventEnd.isNullOrBlank()) return null
        return ReleaseWindow(firstSeen = eventStart, lastSeen = eventEnd)
    }

    private fun parseDate(value: String?): Date? = DateParseUtils.parseIsoDate(value)
}
