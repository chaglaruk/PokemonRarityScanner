package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityResult
import com.pokerarity.scanner.data.model.RarityResultTier
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.VisualFeatures
import java.util.Locale

internal object RarityResultMapper {
    fun build(
        pokemon: PokemonData,
        features: VisualFeatures,
        score: RarityScore,
        scanConfidence: Float
    ): RarityResult {
        val warnings = buildWarnings(pokemon, features, scanConfidence)
        val speciesKnown = isKnownSpecies(pokemon.realName ?: pokemon.name)
        val coreEvidenceCount = listOf(
            (pokemon.cp ?: 0) > 0,
            (pokemon.hp ?: 0) > 0 || (pokemon.maxHp ?: 0) > 0,
            pokemon.caughtDate != null
        ).count { it }

        val tier = when {
            !speciesKnown -> RarityResultTier.UNKNOWN
            coreEvidenceCount == 0 -> RarityResultTier.INSUFFICIENT_DATA
            scanConfidence < 0.45f && score.tier.minScore >= 40 -> RarityResultTier.INSUFFICIENT_DATA
            else -> RarityResultTier.fromScoreTier(score.tier)
        }

        val confidence = when (tier) {
            RarityResultTier.UNKNOWN -> minOf(scanConfidence, 0.20f)
            RarityResultTier.INSUFFICIENT_DATA -> minOf(scanConfidence, 0.40f)
            else -> scanConfidence
        }.coerceIn(0f, 1f)

        val reasons = buildReasons(score, features, tier)

        return RarityResult(
            tier = tier,
            confidence = confidence,
            reasons = reasons,
            warnings = warnings
        )
    }

    private fun buildWarnings(
        pokemon: PokemonData,
        features: VisualFeatures,
        scanConfidence: Float
    ): List<String> = buildList {
        if (!isKnownSpecies(pokemon.realName ?: pokemon.name)) add("unknown_species")
        if ((pokemon.cp ?: 0) <= 0) add("missing_cp")
        if ((pokemon.hp ?: 0) <= 0 && (pokemon.maxHp ?: 0) <= 0) add("missing_hp")
        if (pokemon.caughtDate == null) add("missing_caught_date")
        if (scanConfidence < 0.60f) add("low_scan_confidence")
        if (hasVariantSignal(features) && features.confidence < 0.60f) add("low_variant_confidence")
    }

    private fun buildReasons(
        score: RarityScore,
        features: VisualFeatures,
        tier: RarityResultTier
    ): List<String> = buildList {
        when (tier) {
            RarityResultTier.UNKNOWN -> add("Species was not identified confidently enough for rarity classification")
            RarityResultTier.INSUFFICIENT_DATA -> add("Scan is missing core evidence needed for a reliable rarity classification")
            else -> add("Calculated rarity tier: ${tier.name.lowercase(Locale.US)}")
        }
        if (features.isShiny) add("Shiny visual signal")
        if (features.isLucky) add("Lucky visual signal")
        if (features.isShadow) add("Shadow visual signal")
        if (features.isPurified) add("Purified visual signal")
        if (features.hasCostume) add("Costume or event visual signal")
        if (features.hasSpecialForm) add("Special form visual signal")
        if (features.hasLocationCard) add("Location card visual signal")
        if (features.isXXL) add("XXL size signal")
        if (features.isXXS) add("XXS size signal")
        addAll(score.explanation.filter { it.isNotBlank() }.take(4))
    }.distinct()

    private fun isKnownSpecies(value: String?): Boolean =
        !value.isNullOrBlank() && !value.equals("Unknown", ignoreCase = true)

    private fun hasVariantSignal(features: VisualFeatures): Boolean =
        features.isShiny ||
            features.isShadow ||
            features.isPurified ||
            features.isLucky ||
            features.hasSpecialForm ||
            features.hasCostume ||
            features.hasLocationCard ||
            features.isXXS ||
            features.isXXL
}
