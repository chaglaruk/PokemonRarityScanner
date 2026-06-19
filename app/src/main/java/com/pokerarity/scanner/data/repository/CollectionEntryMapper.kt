package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.local.db.CollectionEntryEntity
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.model.VariantIdentityKey
import java.util.Date

/**
 * Pure mapper for creating CollectionEntryEntity instances from scan results.
 */
object CollectionEntryMapper {

    fun toEntity(
        scanHistoryId: Long?,
        dex: Int,
        formId: String?,
        variantId: String?,
        pokemonData: PokemonData,
        features: VisualFeatures,
        rarityScore: RarityScore,
        backgroundType: String?,
        backgroundLabel: String?,
        createdAt: Date = Date()
    ): CollectionEntryEntity {
        
        val identityKey = VariantIdentityKey(
            dex = dex,
            formId = formId,
            variantId = variantId,
            isShiny = features.isShiny,
            isShadow = features.isShadow,
            isPurified = features.isPurified,
            isLucky = features.isLucky,
            isCostume = features.hasCostume,
            backgroundType = backgroundType,
            backgroundLabel = backgroundLabel
        ).asStringKey()

        // Fallback for species name if name isn't directly available
        val resolvedSpeciesName = pokemonData.name 
            ?: pokemonData.fullVariantMatch?.finalSpecies 
            ?: "Unknown"

        val resolvedCostumeLabel = if (features.hasCostume) {
            pokemonData.fullVariantMatch?.resolvedEventLabel
        } else {
            null
        }

        return CollectionEntryEntity(
            scanHistoryId = scanHistoryId,
            dex = dex,
            speciesName = resolvedSpeciesName,
            formId = formId,
            variantId = variantId,
            variantIdentityKey = identityKey,
            isShiny = features.isShiny,
            isShadow = features.isShadow,
            isPurified = features.isPurified,
            isLucky = features.isLucky,
            isCostume = features.hasCostume,
            isXXL = features.isXXL,
            isXXS = features.isXXS,
            costumeLabel = resolvedCostumeLabel,
            backgroundType = backgroundType,
            backgroundLabel = backgroundLabel,
            eventLabel = pokemonData.fullVariantMatch?.resolvedEventLabel,
            caughtDate = pokemonData.caughtDate,
            ivExact = rarityScore.ivSolve?.ivExact,
            ivMin = rarityScore.ivSolve?.ivMin,
            ivMax = rarityScore.ivSolve?.ivMax,
            rarityScore = rarityScore.totalScore,
            rarityTierCode = rarityScore.tier.name,
            createdAt = createdAt
        )
    }
}
