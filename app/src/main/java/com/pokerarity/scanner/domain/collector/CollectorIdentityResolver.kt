package com.pokerarity.scanner.domain.collector

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VariantCatalogEntry
import com.pokerarity.scanner.data.model.VariantIdentityKey
import com.pokerarity.scanner.data.model.VisualFeatures

/**
 * Resolves collector identity only when scan and catalog metadata agree.
 * Background scans fail closed because the current scan model has no typed
 * background label that can distinguish collector backgrounds safely.
 */
class CollectorIdentityResolver(
    private val catalogBySpriteKey: Map<String, VariantCatalogEntry>
) {
    fun resolve(
        pokemon: PokemonData,
        features: VisualFeatures?
    ): VariantIdentityKey? {
        features ?: return null
        if (features.hasLocationCard) return null

        val species = (pokemon.realName ?: pokemon.name)
            ?.trim()
            ?.takeUnless { it.isBlank() || it.equals("Unknown", ignoreCase = true) }
            ?: return null
        val match = pokemon.fullVariantMatch ?: return null
        if (!match.finalSpecies.equals(species, ignoreCase = true)) return null
        if (match.speciesConfidence < EXACT_SPECIES_CONFIDENCE ||
            match.variantConfidence < MIN_VARIANT_CONFIDENCE
        ) return null

        val spriteKey = match.finalSpriteKey?.takeIf { it.isNotBlank() } ?: return null
        val entry = catalogBySpriteKey[spriteKey] ?: return null
        if (entry.dex <= 0 || entry.formId.isBlank()) return null
        if (!entry.species.equals(species, ignoreCase = true)) return null

        val variantClass = entry.variantClass.lowercase()
        if (variantClass !in SUPPORTED_VARIANT_CLASSES) return null
        if (!match.resolvedVariantClass.equals(variantClass, ignoreCase = true)) return null
        if (entry.isShiny != match.resolvedShiny || entry.isShiny != features.isShiny) return null

        val isCostume = variantClass == "costume"
        if (entry.isCostumeLike != isCostume) return null
        if (match.resolvedCostume != isCostume || features.hasCostume != isCostume) return null

        val isForm = variantClass == "form"
        if (match.resolvedForm != isForm || features.hasSpecialForm != isForm) return null

        return VariantIdentityKey(
            dex = entry.dex,
            formId = entry.formId,
            variantId = entry.variantId,
            isShiny = features.isShiny,
            isShadow = features.isShadow,
            isPurified = features.isPurified,
            isLucky = features.isLucky,
            isCostume = features.hasCostume,
            backgroundType = null,
            backgroundLabel = null
        )
    }

    private companion object {
        const val EXACT_SPECIES_CONFIDENCE = 0.9f
        const val MIN_VARIANT_CONFIDENCE = 0.5f
        val SUPPORTED_VARIANT_CLASSES = setOf("base", "costume", "form")
    }
}
