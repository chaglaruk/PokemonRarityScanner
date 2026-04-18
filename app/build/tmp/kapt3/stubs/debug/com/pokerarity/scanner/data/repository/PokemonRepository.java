package com.pokerarity.scanner.data.repository;

import com.pokerarity.scanner.data.local.db.AppDatabase;
import com.pokerarity.scanner.data.local.db.EventPokemonEntity;
import com.pokerarity.scanner.data.local.db.PokemonEntity;
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity;
import com.pokerarity.scanner.data.model.LiveEventContext;
import com.pokerarity.scanner.data.model.PokemonData;
import com.pokerarity.scanner.data.model.RarityScore;
import com.pokerarity.scanner.data.model.VisualFeatures;
import kotlinx.coroutines.flow.Flow;
import java.util.Date;

/**
 * Central repository for Pokemon data, events, and scan history.
 * Serves as the single source of truth between the database and the UI/service layers.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0088\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0019\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0016\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eH\u0086@\u00a2\u0006\u0002\u0010\u000fJ\u0012\u0010\u0010\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00130\u00120\u0011J\u0012\u0010\u0014\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00150\u00120\u0011J \u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00192\b\u0010\u001a\u001a\u0004\u0018\u00010\u001bH\u0086@\u00a2\u0006\u0002\u0010\u001cJ\u0016\u0010\u001d\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u0019H\u0086@\u00a2\u0006\u0002\u0010\u001eJ\u0018\u0010\u001f\u001a\u0004\u0018\u00010\u00132\u0006\u0010 \u001a\u00020\u0019H\u0086@\u00a2\u0006\u0002\u0010\u001eJ\u001a\u0010!\u001a\u0004\u0018\u00010\u00192\b\u0010\"\u001a\u0004\u0018\u00010\u0019H\u0086@\u00a2\u0006\u0002\u0010\u001eJ\u001c\u0010#\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00150\u00120\u00112\b\b\u0002\u0010$\u001a\u00020\u0017J\u001c\u0010%\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00150\u00120\u00112\b\b\u0002\u0010&\u001a\u00020\u0017J\u0018\u0010\'\u001a\u0004\u0018\u00010\u00152\u0006\u0010\r\u001a\u00020\u000eH\u0086@\u00a2\u0006\u0002\u0010\u000fJ\u000e\u0010(\u001a\u00020\u0017H\u0086@\u00a2\u0006\u0002\u0010)J\u001c\u0010*\u001a\u00020\f2\f\u0010+\u001a\b\u0012\u0004\u0012\u00020\u00130\u0012H\u0086@\u00a2\u0006\u0002\u0010,J\u0016\u0010-\u001a\u00020\u000e2\u0006\u0010+\u001a\u00020\u0013H\u0086@\u00a2\u0006\u0002\u0010.J\u0016\u0010/\u001a\u00020\u000e2\u0006\u00100\u001a\u00020\u0015H\u0086@\u00a2\u0006\u0002\u00101J\u0012\u00102\u001a\u0004\u0018\u00010\u00192\u0006\u00103\u001a\u00020\u0019H\u0002J\u001e\u00104\u001a\u00020\u00172\u0006\u0010+\u001a\u0002052\u0006\u00106\u001a\u000207H\u0086@\u00a2\u0006\u0002\u00108J \u00109\u001a\u0004\u0018\u00010:2\u0006\u0010+\u001a\u0002052\u0006\u00106\u001a\u000207H\u0086@\u00a2\u0006\u0002\u00108J&\u0010;\u001a\u00020\u000e2\u0006\u0010<\u001a\u0002052\u0006\u00106\u001a\u0002072\u0006\u0010=\u001a\u00020>H\u0086@\u00a2\u0006\u0002\u0010?J6\u0010@\u001a\u00020\u00172\u0006\u0010A\u001a\u00020B2\b\u0010C\u001a\u0004\u0018\u00010\u00192\b\u0010D\u001a\u0004\u0018\u00010\u00192\b\u0010E\u001a\u0004\u0018\u00010\u00192\u0006\u0010F\u001a\u00020GH\u0002J\u001c\u0010H\u001a\b\u0012\u0004\u0012\u00020\u00130\u00122\u0006\u0010I\u001a\u00020\u0019H\u0086@\u00a2\u0006\u0002\u0010\u001eR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006J"}, d2 = {"Lcom/pokerarity/scanner/data/repository/PokemonRepository;", "", "database", "Lcom/pokerarity/scanner/data/local/db/AppDatabase;", "(Lcom/pokerarity/scanner/data/local/db/AppDatabase;)V", "eventDao", "Lcom/pokerarity/scanner/data/local/db/EventDao;", "pokemonDao", "Lcom/pokerarity/scanner/data/local/db/PokemonDao;", "scanHistoryDao", "Lcom/pokerarity/scanner/data/local/db/ScanHistoryDao;", "deleteScan", "", "id", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAllPokemon", "Lkotlinx/coroutines/flow/Flow;", "", "Lcom/pokerarity/scanner/data/local/db/PokemonEntity;", "getAllScans", "Lcom/pokerarity/scanner/data/local/db/ScanHistoryEntity;", "getEventWeight", "", "pokemonName", "", "caughtDate", "Ljava/util/Date;", "(Ljava/lang/String;Ljava/util/Date;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getPokemonBaseRarity", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getPokemonByName", "name", "getPokemonFromCandy", "candyName", "getRareScans", "minScore", "getRecentScans", "limit", "getScanById", "getScanCount", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "insertAllPokemon", "pokemon", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "insertPokemon", "(Lcom/pokerarity/scanner/data/local/db/PokemonEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "insertScanHistory", "entity", "(Lcom/pokerarity/scanner/data/local/db/ScanHistoryEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "parseNameWithFuzzy", "input", "resolveEventBonus", "Lcom/pokerarity/scanner/data/model/PokemonData;", "features", "Lcom/pokerarity/scanner/data/model/VisualFeatures;", "(Lcom/pokerarity/scanner/data/model/PokemonData;Lcom/pokerarity/scanner/data/model/VisualFeatures;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "resolveLiveEventContext", "Lcom/pokerarity/scanner/data/model/LiveEventContext;", "saveScan", "pokemonData", "rarityScore", "Lcom/pokerarity/scanner/data/model/RarityScore;", "(Lcom/pokerarity/scanner/data/model/PokemonData;Lcom/pokerarity/scanner/data/model/VisualFeatures;Lcom/pokerarity/scanner/data/model/RarityScore;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "scoreEventEntryMatch", "entry", "Lcom/pokerarity/scanner/data/local/db/EventPokemonEntity;", "eventLabel", "spriteKey", "variantToken", "prefersShiny", "", "searchPokemon", "query", "PokeRarityScanner-v1.8.2_debug"})
public final class PokemonRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.data.local.db.AppDatabase database = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.data.local.db.PokemonDao pokemonDao = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.data.local.db.EventDao eventDao = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.data.local.db.ScanHistoryDao scanHistoryDao = null;
    
    public PokemonRepository(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.local.db.AppDatabase database) {
        super();
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getPokemonByName(@org.jetbrains.annotations.NotNull()
    java.lang.String name, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.pokerarity.scanner.data.local.db.PokemonEntity> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object searchPokemon(@org.jetbrains.annotations.NotNull()
    java.lang.String query, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.pokerarity.scanner.data.local.db.PokemonEntity>> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<java.util.List<com.pokerarity.scanner.data.local.db.PokemonEntity>> getAllPokemon() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object insertPokemon(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.local.db.PokemonEntity pokemon, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object insertAllPokemon(@org.jetbrains.annotations.NotNull()
    java.util.List<com.pokerarity.scanner.data.local.db.PokemonEntity> pokemon, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Get the actual Pokemon species from candy name.
     * Candy names are based on the base form, not the current evolution.
     * Example: Raichu -> "Pikachu Candy", Dragonite -> "Dratini Candy"
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getPokemonFromCandy(@org.jetbrains.annotations.Nullable()
    java.lang.String candyName, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.String> $completion) {
        return null;
    }
    
    /**
     * Simple name parser for candy matching (reused from TextParser logic)
     */
    private final java.lang.String parseNameWithFuzzy(java.lang.String input) {
        return null;
    }
    
    /**
     * Get the base rarity for a Pokemon species (0-25 scale).
     * Priority: 1) rarity_manifest.json  2) Database  3) Default (5)
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getPokemonBaseRarity(@org.jetbrains.annotations.NotNull()
    java.lang.String pokemonName, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion) {
        return null;
    }
    
    /**
     * Get the event rarity weight for a Pokemon caught on a specific date.
     * Returns 0 if no event was active.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getEventWeight(@org.jetbrains.annotations.NotNull()
    java.lang.String pokemonName, @org.jetbrains.annotations.Nullable()
    java.util.Date caughtDate, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object resolveEventBonus(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.PokemonData pokemon, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.VisualFeatures features, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object resolveLiveEventContext(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.PokemonData pokemon, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.VisualFeatures features, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.pokerarity.scanner.data.model.LiveEventContext> $completion) {
        return null;
    }
    
    private final int scoreEventEntryMatch(com.pokerarity.scanner.data.local.db.EventPokemonEntity entry, java.lang.String eventLabel, java.lang.String spriteKey, java.lang.String variantToken, boolean prefersShiny) {
        return 0;
    }
    
    /**
     * Save a complete scan result to the database.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object saveScan(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.PokemonData pokemonData, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.VisualFeatures features, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.RarityScore rarityScore, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object insertScanHistory(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.local.db.ScanHistoryEntity entity, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<java.util.List<com.pokerarity.scanner.data.local.db.ScanHistoryEntity>> getAllScans() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<java.util.List<com.pokerarity.scanner.data.local.db.ScanHistoryEntity>> getRecentScans(int limit) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getScanById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.pokerarity.scanner.data.local.db.ScanHistoryEntity> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object deleteScan(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<java.util.List<com.pokerarity.scanner.data.local.db.ScanHistoryEntity>> getRareScans(int minScore) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getScanCount(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion) {
        return null;
    }
}