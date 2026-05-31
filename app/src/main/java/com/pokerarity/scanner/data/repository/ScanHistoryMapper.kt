package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.VisualFeatures

/**
 * Pure mapper for converting in-memory scan results into database entities.
 * This allows safe unit testing of serialization/mapping logic without SQLCipher constraints.
 */
object ScanHistoryMapper {

    /**
     * Converts a completed scan into a persistable ScanHistoryEntity.
     *
     * Note on structured fields:
     * - `ocrConfidenceReasons` is explicitly EXCLUDED from persistence. It is transient debug
     *   telemetry logic and not needed for user scan history display.
     * - `variantDecisionTrace` is explicitly EXCLUDED from persistence. It is meant for
     *   telemetry and debug tracing, while users only care about the final rarity/flags.
     */
    fun toEntity(
        pokemonData: PokemonData,
        features: VisualFeatures,
        rarityScore: RarityScore
    ): ScanHistoryEntity {
        // Prevent accidental leaking of full local path strings if they ever appear in rawOcrText.
        // The telemetry layer already strips these, but history should be safe too.
        val safeOcrText = pokemonData.rawOcrText
            .split("\n")
            .filterNot { it.contains("C:/Users", ignoreCase = true) || it.contains("/tmp", ignoreCase = true) }
            .joinToString("\n")

        return ScanHistoryEntity(
            pokemonName = pokemonData.name,
            cp = pokemonData.cp,
            hp = pokemonData.hp,
            caughtDate = pokemonData.caughtDate,
            rawOcrText = safeOcrText,
            isShiny = features.isShiny,
            isShadow = features.isShadow,
            isLucky = features.isLucky,
            hasCostume = features.hasCostume,
            rarityScore = rarityScore.totalScore,
            rarityTier = rarityScore.tier.name
        )
    }
}
