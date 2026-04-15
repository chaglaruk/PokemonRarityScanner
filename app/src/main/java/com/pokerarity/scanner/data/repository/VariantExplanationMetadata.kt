package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.ReleaseWindow
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
        val exactEventWindow = when {
            fullMatch != null -> fullMatch.resolvedEventWindow ?: matchedAuthoritative?.let {
                ReleaseWindow(firstSeen = it.eventStart, lastSeen = it.eventEnd)
            }
            else -> selectionAuthoritative?.let {
                ReleaseWindow(firstSeen = it.eventStart, lastSeen = it.eventEnd)
            } ?: selection.releaseWindowOrNull()
        }
        val canExposeExactEventMetadata =
            selection.allowExactMetadata &&
                isCaughtDateInsideWindow(caughtDate, exactEventWindow)
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
                    fullMatch != null ->
                        fullMatch.resolvedEventLabel ?: matchedAuthoritative?.eventLabel ?: selection.primaryEventLabelOrNull()
                    else ->
                        selectionAuthoritative?.eventLabel ?: selection.primaryEventLabelOrNull()
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

    private fun isCaughtDateInsideWindow(caughtDate: Date?, window: ReleaseWindow?): Boolean {
        if (caughtDate == null) return false
        if (window?.firstSeen.isNullOrBlank() || window?.lastSeen.isNullOrBlank()) return false
        val start = parseDate(window!!.firstSeen) ?: return false
        val end = parseDate(window.lastSeen) ?: return false
        return caughtDate.time in start.time..end.time
    }

    private fun parseDate(value: String?): Date? = runCatching {
        value?.let { java.text.SimpleDateFormat("yyyy-MM-dd").parse(it) }
    }.getOrNull()
}
