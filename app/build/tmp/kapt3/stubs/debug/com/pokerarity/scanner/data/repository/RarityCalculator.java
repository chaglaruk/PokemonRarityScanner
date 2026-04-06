package com.pokerarity.scanner.data.repository;

import com.pokerarity.scanner.data.model.PokemonData;
import com.pokerarity.scanner.data.model.RarityAxisScore;
import com.pokerarity.scanner.data.model.RarityScore;
import com.pokerarity.scanner.data.model.RarityTier;
import com.pokerarity.scanner.data.model.ReleaseWindow;
import com.pokerarity.scanner.data.model.ScanDecisionSupport;
import com.pokerarity.scanner.data.model.GlobalRarityLegacyEntry;
import com.pokerarity.scanner.data.model.IvSolveDetails;
import com.pokerarity.scanner.data.model.IvSolveMode;
import com.pokerarity.scanner.data.model.VariantCatalogEntry;
import com.pokerarity.scanner.data.model.VisualFeatures;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Calculates rarity scores for Pokemon based on multiple weighted factors.
 *
 * Score breakdown (0â€“100):
 *  â€¢ Base Species Rarity : 0-25  (from rarity_manifest.json)
 *  â€¢ Shiny Bonus         : 0-20  (based on shiny detection)
 *  â€¢ Costume Bonus       : 0-15  (based on costume rarity tier)
 *  â€¢ Form Bonus          : 0-10  (shadow / lucky / purified)
 *  â€¢ Age Bonus           : 0-30  (days since capture)
 *
 * Total is capped at 100.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u00d2\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0006\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010!\n\u0002\b\t\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\r\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0002\b\n\u0018\u00002\u00020\u0001:\u0005wxyz{B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0018\u0010(\u001a\u00020)2\u0006\u0010*\u001a\u00020+2\u0006\u0010,\u001a\u00020-H\u0002JB\u0010.\u001a\u00020/2\u0006\u0010*\u001a\u00020+2\b\u00100\u001a\u0004\u0018\u00010\u00072\b\u00101\u001a\u0004\u0018\u0001022\b\u00103\u001a\u0004\u0018\u00010\u00072\b\u00104\u001a\u0004\u0018\u0001022\b\u00105\u001a\u0004\u0018\u000106H\u0002J$\u00107\u001a\u0004\u0018\u00010\u00072\u0006\u0010*\u001a\u00020+2\b\u00105\u001a\u0004\u0018\u0001062\u0006\u00108\u001a\u000209H\u0002J*\u0010:\u001a\u00020;2\u0006\u0010*\u001a\u00020+2\u0006\u0010,\u001a\u00020-2\b\b\u0002\u0010<\u001a\u00020 2\b\b\u0002\u0010=\u001a\u00020 J \u0010>\u001a\u00020 2\b\u0010?\u001a\u0004\u0018\u00010@2\f\u0010A\u001a\b\u0012\u0004\u0012\u00020\u00070BH\u0002J>\u0010C\u001a\u00020 2\u0006\u0010D\u001a\u00020 2\u0006\u0010E\u001a\u00020 2\u0006\u0010F\u001a\u00020 2\u0006\u0010G\u001a\u00020 2\u0006\u0010H\u001a\u00020 2\u0006\u0010I\u001a\u00020 2\u0006\u0010J\u001a\u00020\u001aJ\u0018\u0010K\u001a\u00020L2\u0006\u0010*\u001a\u00020+2\u0006\u0010,\u001a\u00020-H\u0002J(\u0010M\u001a\u00020;2\u0006\u0010*\u001a\u00020+2\u0006\u0010,\u001a\u00020-2\u0006\u0010<\u001a\u00020 2\u0006\u0010=\u001a\u00020 H\u0002J\u0010\u0010N\u001a\u00020O2\u0006\u0010P\u001a\u00020 H\u0002J\u0012\u0010Q\u001a\u00020\u00072\b\u0010R\u001a\u0004\u0018\u00010@H\u0002J\u0012\u0010S\u001a\u0002092\b\u0010T\u001a\u0004\u0018\u00010\u0007H\u0002J\u001a\u0010U\u001a\u0002092\u0006\u0010V\u001a\u00020\u00072\b\u0010W\u001a\u0004\u0018\u00010\u0007H\u0002J\u0014\u0010X\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00120\u0006H\u0002J$\u0010Y\u001a\u0004\u0018\u00010\u001c2\u0006\u0010*\u001a\u00020+2\u0006\u0010Z\u001a\u00020\u00072\b\u0010[\u001a\u0004\u0018\u00010%H\u0002J\u0010\u0010\\\u001a\u00020]2\u0006\u0010*\u001a\u00020+H\u0002J\u0018\u0010^\u001a\u0002092\u0006\u0010_\u001a\u00020\u00072\u0006\u0010`\u001a\u00020\u0007H\u0002J\u001e\u0010a\u001a\b\u0012\u0004\u0012\u00020b0\b2\u0006\u0010*\u001a\u00020+2\b\b\u0002\u0010c\u001a\u00020 J\u001e\u0010d\u001a\b\u0012\u0004\u0012\u00020b0\b2\u0006\u0010*\u001a\u00020+2\b\b\u0002\u0010c\u001a\u00020 J7\u0010e\u001a\u00020f2\u0006\u0010*\u001a\u00020+2\u0006\u0010g\u001a\u00020\u00122\u0006\u0010h\u001a\u00020 2\u0006\u0010i\u001a\u00020L2\b\u0010j\u001a\u0004\u0018\u00010 H\u0002\u00a2\u0006\u0002\u0010kJ\u0018\u0010l\u001a\u00020\u001a2\u0006\u0010*\u001a\u00020+2\u0006\u0010g\u001a\u00020\u0012H\u0002J!\u0010m\u001a\u0004\u0018\u00010\u001a2\b\u0010n\u001a\u0004\u0018\u00010\u001a2\u0006\u0010o\u001a\u00020\u001aH\u0002\u00a2\u0006\u0002\u0010pJ\u0016\u0010q\u001a\u00020r2\u0006\u0010*\u001a\u00020+2\u0006\u0010V\u001a\u00020\u0007J1\u0010s\u001a\u0004\u0018\u00010 2\u0006\u0010*\u001a\u00020+2\u000e\b\u0002\u0010t\u001a\b\u0012\u0004\u0012\u00020 0\b2\n\b\u0002\u0010,\u001a\u0004\u0018\u00010-\u00a2\u0006\u0002\u0010uJ\u000e\u0010v\u001a\u0004\u0018\u000102*\u00020\u001cH\u0002R-\u0010\u0005\u001a\u0014\u0012\u0004\u0012\u00020\u0007\u0012\n\u0012\b\u0012\u0004\u0012\u00020\t0\b0\u00068BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\f\u0010\r\u001a\u0004\b\n\u0010\u000bR\'\u0010\u000e\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\t0\u00068BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0010\u0010\r\u001a\u0004\b\u000f\u0010\u000bR\'\u0010\u0011\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00120\u00068BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0014\u0010\r\u001a\u0004\b\u0013\u0010\u000bR-\u0010\u0015\u001a\u0014\u0012\u0004\u0012\u00020\u0007\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00160\b0\u00068BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0018\u0010\r\u001a\u0004\b\u0017\u0010\u000bR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0019\u001a\u000e\u0012\u0004\u0012\u00020\u001a\u0012\u0004\u0012\u00020\u001a0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\'\u0010\u001b\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u001c0\u00068BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u001e\u0010\r\u001a\u0004\b\u001d\u0010\u000bR \u0010\u001f\u001a\u0014\u0012\u0004\u0012\u00020 \u0012\n\u0012\b\u0012\u0004\u0012\u00020\u001a0!0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\"\u001a\u00020#X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\'\u0010$\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020%0\u00068BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\'\u0010\r\u001a\u0004\b&\u0010\u000b\u00a8\u0006|"}, d2 = {"Lcom/pokerarity/scanner/data/repository/RarityCalculator;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "authoritativeVariantBySpecies", "", "", "", "Lcom/pokerarity/scanner/data/model/AuthoritativeVariantEntry;", "getAuthoritativeVariantBySpecies", "()Ljava/util/Map;", "authoritativeVariantBySpecies$delegate", "Lkotlin/Lazy;", "authoritativeVariantBySprite", "getAuthoritativeVariantBySprite", "authoritativeVariantBySprite$delegate", "baseStats", "Lcom/pokerarity/scanner/data/repository/RarityCalculator$BaseStats;", "getBaseStats", "baseStats$delegate", "bulbapediaEventArchiveBySpecies", "Lcom/pokerarity/scanner/data/model/BulbapediaEventArchiveEntry;", "getBulbapediaEventArchiveBySpecies", "bulbapediaEventArchiveBySpecies$delegate", "cpmMap", "", "globalLegacyBySprite", "Lcom/pokerarity/scanner/data/model/GlobalRarityLegacyEntry;", "getGlobalLegacyBySprite", "globalLegacyBySprite$delegate", "stardustToLevel", "", "Lkotlin/ranges/ClosedFloatingPointRange;", "supportDateFormatter", "Ljava/text/SimpleDateFormat;", "variantCatalogBySprite", "Lcom/pokerarity/scanner/data/model/VariantCatalogEntry;", "getVariantCatalogBySprite", "variantCatalogBySprite$delegate", "analyzeIV", "Lcom/pokerarity/scanner/data/repository/RarityCalculator$IVResult;", "pokemon", "Lcom/pokerarity/scanner/data/model/PokemonData;", "features", "Lcom/pokerarity/scanner/data/model/VisualFeatures;", "buildDecisionSupport", "Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;", "rawEventLabel", "rawReleaseWindow", "Lcom/pokerarity/scanner/data/model/ReleaseWindow;", "sanitizedEventLabel", "sanitizedReleaseWindow", "ivDetails", "Lcom/pokerarity/scanner/data/model/IvSolveDetails;", "buildIvSupportNote", "isTurkish", "", "calculate", "Lcom/pokerarity/scanner/data/model/RarityScore;", "baseRarity", "eventWeight", "calculateAgeBonus", "caughtDate", "Ljava/util/Date;", "explanation", "", "calculateCP", "baseAtk", "baseDef", "baseSta", "ivAtk", "ivDef", "ivSta", "level", "calculateRarityConfidence", "", "calculateRulesBased", "determineRarityTier", "Lcom/pokerarity/scanner/data/model/RarityTier;", "score", "formatDateSimple", "date", "hasReliableHpOcr", "rawOcrText", "isRareGender", "species", "gender", "loadBaseStats", "lookupGlobalLegacyEntry", "speciesName", "variantEntry", "lookupVariantCatalogEntry", "Lcom/pokerarity/scanner/data/repository/VariantExplanationSelection;", "matchPlaceholder", "cp", "pattern", "rankSpeciesByObservedProfile", "Lcom/pokerarity/scanner/data/repository/RarityCalculator$SpeciesProfileCandidate;", "limit", "rankSpeciesByPhysicalProfile", "runIVSearch", "Lcom/pokerarity/scanner/data/repository/RarityCalculator$IVSearchResult;", "stats", "hp", "arc", "stardust", "(Lcom/pokerarity/scanner/data/model/PokemonData;Lcom/pokerarity/scanner/data/repository/RarityCalculator$BaseStats;IFLjava/lang/Integer;)Lcom/pokerarity/scanner/data/repository/RarityCalculator$IVSearchResult;", "scorePhysicalProfile", "scoreRelativeMetric", "observed", "expected", "(Ljava/lang/Double;D)Ljava/lang/Double;", "scoreSpeciesFit", "Lcom/pokerarity/scanner/data/repository/RarityCalculator$SpeciesFit;", "validateAndFixCP", "allOcrCPs", "(Lcom/pokerarity/scanner/data/model/PokemonData;Ljava/util/List;Lcom/pokerarity/scanner/data/model/VisualFeatures;)Ljava/lang/Integer;", "toReleaseWindow", "BaseStats", "IVResult", "IVSearchResult", "SpeciesFit", "SpeciesProfileCandidate", "app_debug"})
public final class RarityCalculator {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final java.text.SimpleDateFormat supportDateFormatter = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy baseStats$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy variantCatalogBySprite$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy authoritativeVariantBySprite$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy authoritativeVariantBySpecies$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy globalLegacyBySprite$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy bulbapediaEventArchiveBySpecies$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.Map<java.lang.Double, java.lang.Double> cpmMap = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.Map<java.lang.Integer, kotlin.ranges.ClosedFloatingPointRange<java.lang.Double>> stardustToLevel = null;
    
    public RarityCalculator(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    private final java.util.Map<java.lang.String, com.pokerarity.scanner.data.repository.RarityCalculator.BaseStats> getBaseStats() {
        return null;
    }
    
    private final java.util.Map<java.lang.String, com.pokerarity.scanner.data.model.VariantCatalogEntry> getVariantCatalogBySprite() {
        return null;
    }
    
    private final java.util.Map<java.lang.String, com.pokerarity.scanner.data.model.AuthoritativeVariantEntry> getAuthoritativeVariantBySprite() {
        return null;
    }
    
    private final java.util.Map<java.lang.String, java.util.List<com.pokerarity.scanner.data.model.AuthoritativeVariantEntry>> getAuthoritativeVariantBySpecies() {
        return null;
    }
    
    private final java.util.Map<java.lang.String, com.pokerarity.scanner.data.model.GlobalRarityLegacyEntry> getGlobalLegacyBySprite() {
        return null;
    }
    
    private final java.util.Map<java.lang.String, java.util.List<com.pokerarity.scanner.data.model.BulbapediaEventArchiveEntry>> getBulbapediaEventArchiveBySpecies() {
        return null;
    }
    
    private final java.util.Map<java.lang.String, com.pokerarity.scanner.data.repository.RarityCalculator.BaseStats> loadBaseStats() {
        return null;
    }
    
    /**
     * Resmi Pokemon GO CP FormÃ¼lÃ¼
     */
    public final int calculateCP(int baseAtk, int baseDef, int baseSta, int ivAtk, int ivDef, int ivSta, double level) {
        return 0;
    }
    
    /**
     * Matematiksel Fallback ve CP Tamamlama (CP tamamen null olsa bile)
     * OCR'dan gelen tÃ¼m olasÄ± CP adaylarÄ±nÄ± (OCR'Ä±n gÃ¼rÃ¼ltÃ¼lÃ¼ okuduÄŸu her ÅŸey) dikkate alÄ±r.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer validateAndFixCP(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.PokemonData pokemon, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Integer> allOcrCPs, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.data.model.VisualFeatures features) {
        return null;
    }
    
    private final boolean hasReliableHpOcr(java.lang.String rawOcrText) {
        return false;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.repository.RarityCalculator.SpeciesFit scoreSpeciesFit(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.PokemonData pokemon, @org.jetbrains.annotations.NotNull()
    java.lang.String species) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pokerarity.scanner.data.repository.RarityCalculator.SpeciesProfileCandidate> rankSpeciesByObservedProfile(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.PokemonData pokemon, int limit) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pokerarity.scanner.data.repository.RarityCalculator.SpeciesProfileCandidate> rankSpeciesByPhysicalProfile(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.PokemonData pokemon, int limit) {
        return null;
    }
    
    private final double scorePhysicalProfile(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.data.repository.RarityCalculator.BaseStats stats) {
        return 0.0;
    }
    
    private final java.lang.Double scoreRelativeMetric(java.lang.Double observed, double expected) {
        return null;
    }
    
    private final boolean matchPlaceholder(java.lang.String cp, java.lang.String pattern) {
        return false;
    }
    
    /**
     * Primary entry point. Calculates a comprehensive rarity score.
     */
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.RarityScore calculate(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.PokemonData pokemon, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.VisualFeatures features, int baseRarity, int eventWeight) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.model.RarityScore calculateRulesBased(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.data.model.VisualFeatures features, int baseRarity, int eventWeight) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.model.ScanDecisionSupport buildDecisionSupport(com.pokerarity.scanner.data.model.PokemonData pokemon, java.lang.String rawEventLabel, com.pokerarity.scanner.data.model.ReleaseWindow rawReleaseWindow, java.lang.String sanitizedEventLabel, com.pokerarity.scanner.data.model.ReleaseWindow sanitizedReleaseWindow, com.pokerarity.scanner.data.model.IvSolveDetails ivDetails) {
        return null;
    }
    
    private final java.lang.String buildIvSupportNote(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.data.model.IvSolveDetails ivDetails, boolean isTurkish) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.repository.VariantExplanationSelection lookupVariantCatalogEntry(com.pokerarity.scanner.data.model.PokemonData pokemon) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.model.GlobalRarityLegacyEntry lookupGlobalLegacyEntry(com.pokerarity.scanner.data.model.PokemonData pokemon, java.lang.String speciesName, com.pokerarity.scanner.data.model.VariantCatalogEntry variantEntry) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.model.ReleaseWindow toReleaseWindow(com.pokerarity.scanner.data.model.GlobalRarityLegacyEntry $this$toReleaseWindow) {
        return null;
    }
    
    private final float calculateRarityConfidence(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.data.model.VisualFeatures features) {
        return 0.0F;
    }
    
    private final com.pokerarity.scanner.data.repository.RarityCalculator.IVResult analyzeIV(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.data.model.VisualFeatures features) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.repository.RarityCalculator.IVSearchResult runIVSearch(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.data.repository.RarityCalculator.BaseStats stats, int hp, float arc, java.lang.Integer stardust) {
        return null;
    }
    
    private final boolean isRareGender(java.lang.String species, java.lang.String gender) {
        return false;
    }
    
    private final int calculateAgeBonus(java.util.Date caughtDate, java.util.List<java.lang.String> explanation) {
        return 0;
    }
    
    private final com.pokerarity.scanner.data.model.RarityTier determineRarityTier(int score) {
        return null;
    }
    
    private final java.lang.String formatDateSimple(java.util.Date date) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u0006\n\u0002\b\u0010\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B-\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\tJ\t\u0010\u0011\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0012\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0013\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0014\u001a\u00020\u0007H\u00c6\u0003J\t\u0010\u0015\u001a\u00020\u0007H\u00c6\u0003J;\u0010\u0016\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u0007H\u00c6\u0001J\u0013\u0010\u0017\u001a\u00020\u00182\b\u0010\u0019\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u001a\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\u001b\u001a\u00020\u001cH\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\u000bR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u000bR\u0011\u0010\b\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u000e\u00a8\u0006\u001d"}, d2 = {"Lcom/pokerarity/scanner/data/repository/RarityCalculator$BaseStats;", "", "atk", "", "def", "sta", "heightM", "", "weightKg", "(IIIDD)V", "getAtk", "()I", "getDef", "getHeightM", "()D", "getSta", "getWeightKg", "component1", "component2", "component3", "component4", "component5", "copy", "equals", "", "other", "hashCode", "toString", "", "app_debug"})
    public static final class BaseStats {
        private final int atk = 0;
        private final int def = 0;
        private final int sta = 0;
        private final double heightM = 0.0;
        private final double weightKg = 0.0;
        
        public BaseStats(int atk, int def, int sta, double heightM, double weightKg) {
            super();
        }
        
        public final int getAtk() {
            return 0;
        }
        
        public final int getDef() {
            return 0;
        }
        
        public final int getSta() {
            return 0;
        }
        
        public final double getHeightM() {
            return 0.0;
        }
        
        public final double getWeightKg() {
            return 0.0;
        }
        
        public final int component1() {
            return 0;
        }
        
        public final int component2() {
            return 0;
        }
        
        public final int component3() {
            return 0;
        }
        
        public final double component4() {
            return 0.0;
        }
        
        public final double component5() {
            return 0.0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityCalculator.BaseStats copy(int atk, int def, int sta, double heightM, double weightKg) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u000e\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B+\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\b\u00a2\u0006\u0002\u0010\tJ\t\u0010\u0011\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\u0012\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\u000b\u0010\u0013\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\u000b\u0010\u0014\u001a\u0004\u0018\u00010\bH\u00c6\u0003J7\u0010\u0015\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\bH\u00c6\u0001J\u0013\u0010\u0016\u001a\u00020\u00172\b\u0010\u0018\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0019\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\u001a\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0013\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\rR\u0013\u0010\u0007\u001a\u0004\u0018\u00010\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u001b"}, d2 = {"Lcom/pokerarity/scanner/data/repository/RarityCalculator$IVResult;", "", "bonusPoints", "", "rangeText", "", "explanation", "solveDetails", "Lcom/pokerarity/scanner/data/model/IvSolveDetails;", "(ILjava/lang/String;Ljava/lang/String;Lcom/pokerarity/scanner/data/model/IvSolveDetails;)V", "getBonusPoints", "()I", "getExplanation", "()Ljava/lang/String;", "getRangeText", "getSolveDetails", "()Lcom/pokerarity/scanner/data/model/IvSolveDetails;", "component1", "component2", "component3", "component4", "copy", "equals", "", "other", "hashCode", "toString", "app_debug"})
    public static final class IVResult {
        private final int bonusPoints = 0;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String rangeText = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String explanation = null;
        @org.jetbrains.annotations.Nullable()
        private final com.pokerarity.scanner.data.model.IvSolveDetails solveDetails = null;
        
        public IVResult(int bonusPoints, @org.jetbrains.annotations.Nullable()
        java.lang.String rangeText, @org.jetbrains.annotations.Nullable()
        java.lang.String explanation, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.data.model.IvSolveDetails solveDetails) {
            super();
        }
        
        public final int getBonusPoints() {
            return 0;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getRangeText() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getExplanation() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final com.pokerarity.scanner.data.model.IvSolveDetails getSolveDetails() {
            return null;
        }
        
        public final int component1() {
            return 0;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component3() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final com.pokerarity.scanner.data.model.IvSolveDetails component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityCalculator.IVResult copy(int bonusPoints, @org.jetbrains.annotations.Nullable()
        java.lang.String rangeText, @org.jetbrains.annotations.Nullable()
        java.lang.String explanation, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.data.model.IvSolveDetails solveDetails) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0010\b\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0082\b\u0018\u00002\u00020\u0001B\u0013\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\u0002\u0010\u0005J\u000f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00c6\u0003J\u0019\u0010\t\u001a\u00020\u00002\u000e\b\u0002\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00c6\u0001J\u0013\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\r\u001a\u00020\u0004H\u00d6\u0001J\t\u0010\u000e\u001a\u00020\u000fH\u00d6\u0001R\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\u0010"}, d2 = {"Lcom/pokerarity/scanner/data/repository/RarityCalculator$IVSearchResult;", "", "ivSums", "", "", "(Ljava/util/List;)V", "getIvSums", "()Ljava/util/List;", "component1", "copy", "equals", "", "other", "hashCode", "toString", "", "app_debug"})
    static final class IVSearchResult {
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<java.lang.Integer> ivSums = null;
        
        public IVSearchResult(@org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Integer> ivSums) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.Integer> getIvSums() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.Integer> component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityCalculator.IVSearchResult copy(@org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Integer> ivSums) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0017\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B7\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0007\u0012\u0006\u0010\t\u001a\u00020\u0005\u0012\b\b\u0002\u0010\n\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u000bJ\t\u0010\u0015\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0016\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0017\u001a\u00020\u0007H\u00c6\u0003J\t\u0010\u0018\u001a\u00020\u0007H\u00c6\u0003J\t\u0010\u0019\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001a\u001a\u00020\u0005H\u00c6\u0003JE\u0010\u001b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u00072\b\b\u0002\u0010\t\u001a\u00020\u00052\b\b\u0002\u0010\n\u001a\u00020\u0005H\u00c6\u0001J\u0013\u0010\u001c\u001a\u00020\u00072\b\u0010\u001d\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u001e\u001a\u00020\u001fH\u00d6\u0001J\t\u0010 \u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\b\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\rR\u0011\u0010\t\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0010R\u0011\u0010\n\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0010R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014\u00a8\u0006!"}, d2 = {"Lcom/pokerarity/scanner/data/repository/RarityCalculator$SpeciesFit;", "", "species", "", "score", "", "hpPossible", "", "cpPossible", "minArcDiff", "sizeScore", "(Ljava/lang/String;DZZDD)V", "getCpPossible", "()Z", "getHpPossible", "getMinArcDiff", "()D", "getScore", "getSizeScore", "getSpecies", "()Ljava/lang/String;", "component1", "component2", "component3", "component4", "component5", "component6", "copy", "equals", "other", "hashCode", "", "toString", "app_debug"})
    public static final class SpeciesFit {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String species = null;
        private final double score = 0.0;
        private final boolean hpPossible = false;
        private final boolean cpPossible = false;
        private final double minArcDiff = 0.0;
        private final double sizeScore = 0.0;
        
        public SpeciesFit(@org.jetbrains.annotations.NotNull()
        java.lang.String species, double score, boolean hpPossible, boolean cpPossible, double minArcDiff, double sizeScore) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getSpecies() {
            return null;
        }
        
        public final double getScore() {
            return 0.0;
        }
        
        public final boolean getHpPossible() {
            return false;
        }
        
        public final boolean getCpPossible() {
            return false;
        }
        
        public final double getMinArcDiff() {
            return 0.0;
        }
        
        public final double getSizeScore() {
            return 0.0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        public final double component2() {
            return 0.0;
        }
        
        public final boolean component3() {
            return false;
        }
        
        public final boolean component4() {
            return false;
        }
        
        public final double component5() {
            return 0.0;
        }
        
        public final double component6() {
            return 0.0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityCalculator.SpeciesFit copy(@org.jetbrains.annotations.NotNull()
        java.lang.String species, double score, boolean hpPossible, boolean cpPossible, double minArcDiff, double sizeScore) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0006\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\f\u001a\u00020\u0005H\u00c6\u0003J\u001d\u0010\r\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0005H\u00c6\u0001J\u0013\u0010\u000e\u001a\u00020\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0011\u001a\u00020\u0012H\u00d6\u0001J\t\u0010\u0013\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0014"}, d2 = {"Lcom/pokerarity/scanner/data/repository/RarityCalculator$SpeciesProfileCandidate;", "", "species", "", "score", "", "(Ljava/lang/String;D)V", "getScore", "()D", "getSpecies", "()Ljava/lang/String;", "component1", "component2", "copy", "equals", "", "other", "hashCode", "", "toString", "app_debug"})
    public static final class SpeciesProfileCandidate {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String species = null;
        private final double score = 0.0;
        
        public SpeciesProfileCandidate(@org.jetbrains.annotations.NotNull()
        java.lang.String species, double score) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getSpecies() {
            return null;
        }
        
        public final double getScore() {
            return 0.0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        public final double component2() {
            return 0.0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityCalculator.SpeciesProfileCandidate copy(@org.jetbrains.annotations.NotNull()
        java.lang.String species, double score) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
}