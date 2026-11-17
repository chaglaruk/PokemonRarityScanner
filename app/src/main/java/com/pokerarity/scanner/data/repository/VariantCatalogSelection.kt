package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.ReleaseWindow
import com.pokerarity.scanner.data.model.VariantCatalogEntry
import java.util.Locale

private val formLabelNormalizationRegex = Regex("[^a-z0-9]")

internal data class VariantExplanationSelection(
    val entry: VariantCatalogEntry? = null,
    val allowExactMetadata: Boolean = false,
    val allowDerivedMetadata: Boolean = false,
    val hasVerifiedForm: Boolean = false,
    val isFormScopedVariant: Boolean = false
) {
    val allowFormScopedMetadata: Boolean
        get() = hasVerifiedForm || !isFormScopedVariant
}

internal object VariantCatalogSelection {
    private const val EXACT_SPECIES_METADATA_CONFIDENCE = 0.50f
    private const val DERIVED_SPECIES_METADATA_CONFIDENCE = 0.35f

    fun selectForExplanation(
        finalSpecies: String,
        fullMatch: FullVariantMatch?,
        bySprite: Map<String, VariantCatalogEntry>,
        trustedFormLabel: String? = null,
        authoritativeBySprite: Map<String, AuthoritativeVariantEntry> = emptyMap()
    ): VariantExplanationSelection {
        return fullMatch?.let { match ->
            val entry = match.finalSpriteKey?.let(bySprite::get)
            if (entry == null || !entry.species.equals(finalSpecies, ignoreCase = true)) {
                null
            } else {
                val isFormScopedVariant = match.resolvedVariantClass.equals("form", ignoreCase = true) ||
                    entry.variantClass.equals("form", ignoreCase = true)
                val normalizedTrustedForm = normalizeFormLabel(trustedFormLabel)
                val authoritativeEntry = authoritativeBySprite[entry.spriteKey]
                val hasVerifiedForm = isFormScopedVariant && normalizedTrustedForm.isNotEmpty() &&
                    listOfNotNull(
                        entry.variantLabel,
                        entry.gameMasterFormName,
                        authoritativeEntry?.variantLabel,
                        authoritativeEntry?.gameMasterFormName
                    ).any { normalizeFormLabel(it) == normalizedTrustedForm }
                VariantExplanationSelection(
                    entry = entry,
                    allowExactMetadata = match.explanationMode == "exact_authoritative" &&
                        match.variantConfidence >= EXACT_SPECIES_METADATA_CONFIDENCE,
                    allowDerivedMetadata = match.explanationMode in setOf(
                        "exact_authoritative",
                        "derived_authoritative"
                    ) && match.variantConfidence >= DERIVED_SPECIES_METADATA_CONFIDENCE,
                    hasVerifiedForm = hasVerifiedForm,
                    isFormScopedVariant = isFormScopedVariant
                )
            }
        } ?: VariantExplanationSelection()
    }

    private fun normalizeFormLabel(label: String?): String =
        label.orEmpty().lowercase(Locale.ROOT).replace(formLabelNormalizationRegex, "")
}

internal fun VariantExplanationSelection.variantLabelOrNull(): String? =
    if ((allowExactMetadata || allowDerivedMetadata) && allowFormScopedMetadata) entry?.variantLabel else null

internal fun VariantExplanationSelection.primaryEventLabelOrNull(): String? =
    if (allowExactMetadata || allowDerivedMetadata) entry?.primaryEventLabel else null

internal fun VariantExplanationSelection.eventTagsOrEmpty(): List<String> =
    if (allowExactMetadata || allowDerivedMetadata) entry?.eventTags.orEmpty() else emptyList()

internal fun VariantExplanationSelection.releaseWindowOrNull(): ReleaseWindow? =
    if ((allowExactMetadata || allowDerivedMetadata) && allowFormScopedMetadata) entry?.releaseWindow else null
