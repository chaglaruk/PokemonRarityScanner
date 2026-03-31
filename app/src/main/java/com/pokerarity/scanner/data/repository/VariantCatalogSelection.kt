package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.ReleaseWindow
import com.pokerarity.scanner.data.model.VariantCatalogEntry

internal data class VariantExplanationSelection(
    val entry: VariantCatalogEntry? = null,
    val allowExactMetadata: Boolean = false,
    val allowDerivedMetadata: Boolean = false
)

internal object VariantCatalogSelection {
    private const val EXACT_SPECIES_METADATA_CONFIDENCE = 0.50f
    private const val DERIVED_SPECIES_METADATA_CONFIDENCE = 0.40f

    fun selectForExplanation(
        finalSpecies: String,
        fullMatch: FullVariantMatch?,
        bySprite: Map<String, VariantCatalogEntry>
    ): VariantExplanationSelection {
        if (fullMatch == null) return VariantExplanationSelection()

        val fullVariantEntry = fullMatch.finalSpriteKey?.let(bySprite::get)
            ?: return VariantExplanationSelection()
        if (!fullVariantEntry.species.equals(finalSpecies, ignoreCase = true)) {
            return VariantExplanationSelection()
        }

        return VariantExplanationSelection(
            entry = fullVariantEntry,
            allowExactMetadata = fullMatch.explanationMode == "exact_authoritative" &&
                fullMatch.variantConfidence >= EXACT_SPECIES_METADATA_CONFIDENCE,
            allowDerivedMetadata = fullMatch.explanationMode in setOf("exact_authoritative", "derived_authoritative", "generic_variant") &&
                fullMatch.variantConfidence >= DERIVED_SPECIES_METADATA_CONFIDENCE
        )
    }
}

internal fun VariantExplanationSelection.variantLabelOrNull(): String? =
    if (allowExactMetadata || allowDerivedMetadata) entry?.variantLabel else null

internal fun VariantExplanationSelection.primaryEventLabelOrNull(): String? =
    if (allowExactMetadata || allowDerivedMetadata) entry?.primaryEventLabel else null

internal fun VariantExplanationSelection.eventTagsOrEmpty(): List<String> =
    if (allowExactMetadata || allowDerivedMetadata) entry?.eventTags.orEmpty() else emptyList()

internal fun VariantExplanationSelection.releaseWindowOrNull(): ReleaseWindow? =
    if (allowExactMetadata || allowDerivedMetadata) entry?.releaseWindow else null
