package com.pokerarity.scanner.data.model;

import com.pokerarity.scanner.data.local.db.ScanHistoryEntity;
import com.pokerarity.scanner.ui.theme.PokemonType;
import com.pokerarity.scanner.ui.theme.RarityColor;
import com.pokerarity.scanner.ui.theme.TypeColors;
import java.text.SimpleDateFormat;
import java.util.Locale;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u00008\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\t\n\u0002\u0018\u0002\n\u0000\u001a>\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u00042\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00070\u00042\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\t0\u00042\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00070\u00042\u0006\u0010\u000b\u001a\u00020\t\u001a\u0010\u0010\f\u001a\u00020\u00072\u0006\u0010\r\u001a\u00020\u0007H\u0002\u001a\u0083\u0001\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\r\u001a\u00020\u00072\u0006\u0010\u0010\u001a\u00020\t2\b\u0010\u0011\u001a\u0004\u0018\u00010\t2\u0006\u0010\u0012\u001a\u00020\t2\u0006\u0010\u0013\u001a\u00020\u00072\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u00152\u0006\u0010\u0017\u001a\u00020\u00152\u0006\u0010\u0018\u001a\u00020\u00152\u0006\u0010\u0019\u001a\u00020\u00152\b\u0010\u001a\u001a\u0004\u0018\u00010\u00072\b\u0010\u001b\u001a\u0004\u0018\u00010\u00072\u0010\b\u0002\u0010\u001c\u001a\n\u0012\u0004\u0012\u00020\u0005\u0018\u00010\u0004\u00a2\u0006\u0002\u0010\u001d\u001a\n\u0010\u001e\u001a\u00020\u000f*\u00020\u001f\"\u000e\u0010\u0000\u001a\u00020\u0001X\u0082\u0004\u00a2\u0006\u0002\n\u0000\"\u000e\u0010\u0002\u001a\u00020\u0001X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006 "}, d2 = {"displayDateFormatter", "Ljava/text/SimpleDateFormat;", "shortDateFormatter", "buildAnalysisItems", "", "Lcom/pokerarity/scanner/data/model/RarityAnalysisItem;", "breakdownKeys", "", "breakdownValues", "", "explanations", "fallbackScore", "inferTypeFromSpecies", "name", "pokemonFromScanExtras", "Lcom/pokerarity/scanner/data/model/Pokemon;", "cp", "hp", "score", "tier", "isShiny", "", "isLucky", "hasCostume", "hasSpecialForm", "isShadow", "dateText", "ivText", "analysisOverride", "(Ljava/lang/String;ILjava/lang/Integer;ILjava/lang/String;ZZZZZLjava/lang/String;Ljava/lang/String;Ljava/util/List;)Lcom/pokerarity/scanner/data/model/Pokemon;", "toUiPokemon", "Lcom/pokerarity/scanner/data/local/db/ScanHistoryEntity;", "app_debug"})
public final class PokemonKt {
    @org.jetbrains.annotations.NotNull()
    private static final java.text.SimpleDateFormat displayDateFormatter = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.text.SimpleDateFormat shortDateFormatter = null;
    
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.data.model.Pokemon toUiPokemon(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.local.db.ScanHistoryEntity $this$toUiPokemon) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.data.model.Pokemon pokemonFromScanExtras(@org.jetbrains.annotations.NotNull()
    java.lang.String name, int cp, @org.jetbrains.annotations.Nullable()
    java.lang.Integer hp, int score, @org.jetbrains.annotations.NotNull()
    java.lang.String tier, boolean isShiny, boolean isLucky, boolean hasCostume, boolean hasSpecialForm, boolean isShadow, @org.jetbrains.annotations.Nullable()
    java.lang.String dateText, @org.jetbrains.annotations.Nullable()
    java.lang.String ivText, @org.jetbrains.annotations.Nullable()
    java.util.List<com.pokerarity.scanner.data.model.RarityAnalysisItem> analysisOverride) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public static final java.util.List<com.pokerarity.scanner.data.model.RarityAnalysisItem> buildAnalysisItems(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> breakdownKeys, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Integer> breakdownValues, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> explanations, int fallbackScore) {
        return null;
    }
    
    private static final java.lang.String inferTypeFromSpecies(java.lang.String name) {
        return null;
    }
}