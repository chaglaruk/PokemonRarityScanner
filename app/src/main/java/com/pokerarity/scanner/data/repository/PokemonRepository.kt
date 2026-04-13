package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.db.EventPokemonEntity
import com.pokerarity.scanner.data.local.db.PokemonEntity
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.LiveEventContext
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.VisualFeatures
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Central repository for Pokemon data, events, and scan history.
 * Serves as the single source of truth between the database and the UI/service layers.
 */
class PokemonRepository(private val database: AppDatabase) {

    private val pokemonDao = database.pokemonDao()
    private val eventDao = database.eventDao()
    private val scanHistoryDao = database.scanHistoryDao()

    // ──────────────────────────────────
    // Pokemon Lookup
    // ──────────────────────────────────

    suspend fun getPokemonByName(name: String): PokemonEntity? {
        return pokemonDao.getByName(name)
    }

    suspend fun searchPokemon(query: String): List<PokemonEntity> {
        return pokemonDao.searchByName(query)
    }

    fun getAllPokemon(): Flow<List<PokemonEntity>> {
        return pokemonDao.getAll()
    }

    suspend fun insertPokemon(pokemon: PokemonEntity): Long {
        return pokemonDao.insert(pokemon)
    }

    suspend fun insertAllPokemon(pokemon: List<PokemonEntity>) {
        pokemonDao.insertAll(pokemon)
    }

    // ──────────────────────────────────
    // Event Queries
    // ──────────────────────────────────

    /**
     * Get the actual Pokemon species from candy name.
     * Candy names are based on the base form, not the current evolution.
     * Example: Raichu -> "Pikachu Candy", Dragonite -> "Dratini Candy"
     */
    suspend fun getPokemonFromCandy(candyName: String?): String? {
        if (candyName == null) return null
        
        // Extract pokemon name from "X CANDY" format
        val pokemonPart = candyName.replace("CANDY", "").trim()
        if (pokemonPart.isEmpty()) return null
        
        // Clean OCR artifacts
        val cleaned = pokemonPart.replace(Regex("[^A-Z]"), "")
        if (cleaned.isEmpty()) return null
        
        // Try to find exact match in pokemon list first
        val exactMatch = getPokemonBaseRarity(cleaned)
        if (exactMatch > 5) { // If it's a known pokemon (not default 5 or 8)
            return cleaned
        }
        
        // Try fuzzy matching
        return parseNameWithFuzzy(cleaned)
    }
    
    /**
     * Simple name parser for candy matching (reused from TextParser logic)
     */
    private fun parseNameWithFuzzy(input: String): String? {
        // This would need access to pokemon names - for now return cleaned input
        return input.takeIf { it.isNotEmpty() }
    }

    /**
     * Get the base rarity for a Pokemon species (0-25 scale).
     * Priority: 1) rarity_manifest.json  2) Database  3) Default (5)
     */
    suspend fun getPokemonBaseRarity(pokemonName: String): Int {
        if (pokemonName.equals("Unknown", ignoreCase = true)) return 5
        
        // 1. Try manifest first (primary source of truth)
        val manifestRarity = RarityManifestLoader.getSpeciesRarity(pokemonName)
        if (manifestRarity != 5) { // 5 is the "not found" default from manifest
            return manifestRarity
        }
        
        // 2. Try database
        val pokemon = pokemonDao.getByName(pokemonName)
        if (pokemon != null) {
            return pokemon.baseRarity
        }
        
        // 3. Fallback default
        return 5
    }

    /**
     * Get the event rarity weight for a Pokemon caught on a specific date.
     * Returns 0 if no event was active.
     */
    suspend fun getEventWeight(pokemonName: String, caughtDate: Date?): Int {
        if (caughtDate == null) return 0

        val pokemon = pokemonDao.getByName(pokemonName) ?: return 0
        val events = eventDao.getEventsForPokemonOnDate(pokemon.id, caughtDate)

        return events.maxOfOrNull { it.rarityWeight } ?: 0
    }

    suspend fun resolveEventBonus(pokemon: PokemonData, features: VisualFeatures): Int {
        val baseName = (pokemon.realName ?: pokemon.name)?.trim().orEmpty()
        if (baseName.isBlank() || baseName.equals("Unknown", ignoreCase = true)) return 0

        val onDateEntries = eventDao.getEventPokemonForBaseNameOnDate(baseName, pokemon.caughtDate ?: Date())
        val allEntries = if (onDateEntries.isNotEmpty()) onDateEntries else eventDao.getEventPokemonForBaseName(baseName)
        if (allEntries.isEmpty()) return 0

        val spriteKey = pokemon.fullVariantMatch?.finalSpriteKey
        val variantToken = spriteKey
            ?.substringAfter('_', "")
            ?.substringAfter('_', "")
            ?.takeIf { it.isNotBlank() }
        val resolvedEventLabel = pokemon.fullVariantMatch?.resolvedEventLabel
        val prefersShiny = features.isShiny || pokemon.fullVariantMatch?.resolvedShiny == true

        val ranked = allEntries
            .map { entry ->
                entry to scoreEventEntryMatch(
                    entry = entry,
                    eventLabel = resolvedEventLabel,
                    spriteKey = spriteKey,
                    variantToken = variantToken,
                    prefersShiny = prefersShiny
                )
            }
            .sortedByDescending { it.second }

        return ranked.firstOrNull { it.second > 0 }?.first?.eventBonusScore ?: 0
    }

    suspend fun resolveLiveEventContext(
        pokemon: PokemonData,
        features: VisualFeatures
    ): LiveEventContext? {
        val baseName = (pokemon.realName ?: pokemon.name)?.trim().orEmpty()
        if (baseName.isBlank() || baseName.equals("Unknown", ignoreCase = true)) return null

        val activeEntries = eventDao.getEventPokemonForBaseNameOnDate(baseName, Date())
        if (activeEntries.isEmpty()) return null

        val spriteKey = pokemon.fullVariantMatch?.finalSpriteKey
        val variantToken = spriteKey
            ?.substringAfter('_', "")
            ?.substringAfter('_', "")
            ?.takeIf { it.isNotBlank() }
        val resolvedEventLabel = pokemon.fullVariantMatch?.resolvedEventLabel
        val prefersShiny = features.isShiny || pokemon.fullVariantMatch?.resolvedShiny == true
        val winner = activeEntries
            .map { entry ->
                entry to scoreEventEntryMatch(
                    entry = entry,
                    eventLabel = resolvedEventLabel,
                    spriteKey = spriteKey,
                    variantToken = variantToken,
                    prefersShiny = prefersShiny
                )
            }
            .sortedByDescending { it.second }
            .firstOrNull { it.second > 0 }
            ?.first
            ?: return null

        return LiveEventContext(
            eventName = winner.eventName,
            eventBonusScore = winner.eventBonusScore,
            source = winner.source,
            boostedSpecies = winner.baseName
        )
    }

    private fun scoreEventEntryMatch(
        entry: EventPokemonEntity,
        eventLabel: String?,
        spriteKey: String?,
        variantToken: String?,
        prefersShiny: Boolean,
    ): Int {
        var score = 0
        if (!eventLabel.isNullOrBlank() && entry.eventName.equals(eventLabel, ignoreCase = true)) score += 120
        if (!spriteKey.isNullOrBlank() && entry.spriteKey.equals(spriteKey, ignoreCase = true)) score += 200
        if (!variantToken.isNullOrBlank() && entry.variantToken.equals(variantToken, ignoreCase = true)) score += 90
        if (prefersShiny && (entry.spriteKey?.contains("shiny", ignoreCase = true) == true || entry.variantToken?.contains("shiny", ignoreCase = true) == true)) {
            score += 20
        }
        if (entry.eventStart != null || entry.eventEnd != null) score += 10
        return score
    }

    // ──────────────────────────────────
    // Scan History
    // ──────────────────────────────────

    /**
     * Save a complete scan result to the database.
     */
    suspend fun saveScan(
        pokemonData: PokemonData,
        features: VisualFeatures,
        rarityScore: RarityScore
    ): Long {
        val entity = ScanHistoryEntity(
            pokemonName = pokemonData.name,
            cp = pokemonData.cp,
            hp = pokemonData.hp,
            caughtDate = pokemonData.caughtDate,
            rawOcrText = pokemonData.rawOcrText,
            isShiny = features.isShiny,
            isShadow = features.isShadow,
            isLucky = features.isLucky,
            hasCostume = features.hasCostume,
            rarityScore = rarityScore.totalScore,
            rarityTier = rarityScore.tier.name
        )
        return scanHistoryDao.insert(entity)
    }

    suspend fun insertScanHistory(entity: ScanHistoryEntity): Long {
        return scanHistoryDao.insert(entity)
    }

    fun getAllScans(): Flow<List<ScanHistoryEntity>> {
        return scanHistoryDao.getAll()
    }

    fun getRecentScans(limit: Int = 20): Flow<List<ScanHistoryEntity>> {
        return scanHistoryDao.getRecent(limit)
    }

    suspend fun getScanById(id: Long): ScanHistoryEntity? {
        return scanHistoryDao.getById(id)
    }

    suspend fun deleteScan(id: Long) {
        scanHistoryDao.deleteById(id)
    }

    fun getRareScans(minScore: Int = 60): Flow<List<ScanHistoryEntity>> {
        return scanHistoryDao.getByMinRarity(minScore)
    }

    suspend fun getScanCount(): Int {
        return scanHistoryDao.count()
    }
}
