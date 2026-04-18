package com.pokerarity.scanner.data.model;

import com.pokerarity.scanner.data.local.db.ScanHistoryEntity;
import com.pokerarity.scanner.ui.theme.PokemonType;
import com.pokerarity.scanner.ui.theme.RarityColor;
import com.pokerarity.scanner.ui.theme.TypeColors;
import java.text.SimpleDateFormat;
import java.util.Locale;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000Z\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0002\b\r\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\u001a>\u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\t2\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00010\t2\f\u0010\f\u001a\b\u0012\u0004\u0012\u00020\r0\t2\f\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00010\t2\u0006\u0010\u000f\u001a\u00020\r\u001a\u001e\u0010\u0010\u001a\u00020\u00012\f\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00010\t2\u0006\u0010\u000f\u001a\u00020\rH\u0002\u001a\u001c\u0010\u0011\u001a\u0010\u0012\u0004\u0012\u00020\u0001\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u00122\u0006\u0010\u0013\u001a\u00020\u0001\u001a\u001a\u0010\u0014\u001a\u00020\u00012\u0006\u0010\u0015\u001a\u00020\u00012\n\b\u0002\u0010\u0016\u001a\u0004\u0018\u00010\u0001\u001a$\u0010\u0017\u001a\u0004\u0018\u00010\u00012\u0006\u0010\u0015\u001a\u00020\u00012\b\u0010\u0016\u001a\u0004\u0018\u00010\u00012\u0006\u0010\u0018\u001a\u00020\u0019H\u0002\u001a\u0010\u0010\u001a\u001a\u00020\u00012\u0006\u0010\u001b\u001a\u00020\u0001H\u0002\u001a\u0010\u0010\u001c\u001a\u00020\u00012\u0006\u0010\u001d\u001a\u00020\u0001H\u0002\u001a\u0012\u0010\u001e\u001a\u0004\u0018\u00010\u00012\b\u0010\u001f\u001a\u0004\u0018\u00010\u0001\u001a\u00f3\u0001\u0010 \u001a\u00020!2\u0006\u0010\u001d\u001a\u00020\u00012\u0006\u0010\"\u001a\u00020\r2\b\u0010#\u001a\u0004\u0018\u00010\r2\n\b\u0002\u0010\u001f\u001a\u0004\u0018\u00010\u00012\n\b\u0002\u0010$\u001a\u0004\u0018\u00010%2\u000e\b\u0002\u0010&\u001a\b\u0012\u0004\u0012\u00020\u00010\t2\n\b\u0002\u0010\'\u001a\u0004\u0018\u00010\r2\n\b\u0002\u0010(\u001a\u0004\u0018\u00010)2\n\b\u0002\u0010*\u001a\u0004\u0018\u00010)2\b\b\u0002\u0010+\u001a\u00020\u00192\n\b\u0002\u0010,\u001a\u0004\u0018\u00010\u00012\u0006\u0010-\u001a\u00020\r2\u0006\u0010.\u001a\u00020\u00012\u0006\u0010/\u001a\u00020\u00192\u0006\u00100\u001a\u00020\u00192\u0006\u00101\u001a\u00020\u00192\u0006\u00102\u001a\u00020\u00192\u0006\u00103\u001a\u00020\u00192\b\u00104\u001a\u0004\u0018\u00010\u00012\u0010\b\u0002\u00105\u001a\n\u0012\u0004\u0012\u00020\n\u0018\u00010\t2\n\b\u0002\u00106\u001a\u0004\u0018\u0001072\n\b\u0002\u00108\u001a\u0004\u0018\u00010\u0001\u00a2\u0006\u0002\u00109\u001a\n\u0010:\u001a\u00020!*\u00020;\"\u000e\u0010\u0000\u001a\u00020\u0001X\u0082T\u00a2\u0006\u0002\n\u0000\"\u0014\u0010\u0002\u001a\u00020\u00038BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0004\u0010\u0005\"\u0014\u0010\u0006\u001a\u00020\u00038BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0007\u0010\u0005\u00a8\u0006<"}, d2 = {"EXPLANATION_DETAIL_SEPARATOR", "", "displayDateFormatter", "Ljava/text/SimpleDateFormat;", "getDisplayDateFormatter", "()Ljava/text/SimpleDateFormat;", "shortDateFormatter", "getShortDateFormatter", "buildAnalysisItems", "", "Lcom/pokerarity/scanner/data/model/RarityAnalysisItem;", "breakdownKeys", "breakdownValues", "", "explanations", "fallbackScore", "buildNarrativeExplanation", "decodeExplanationItem", "Lkotlin/Pair;", "value", "encodeExplanationItem", "title", "detail", "explanationToPhrase", "isTurkish", "", "formatRarityTierLabel", "code", "inferTypeFromSpecies", "name", "normalizeIvText", "ivText", "pokemonFromScanExtras", "Lcom/pokerarity/scanner/data/model/Pokemon;", "cp", "hp", "ivSolveMode", "Lcom/pokerarity/scanner/data/model/IvSolveMode;", "ivSignalsUsed", "ivCandidateCount", "ivLevelMin", "", "ivLevelMax", "hasArcSignal", "pvpSummary", "score", "tier", "isShiny", "isLucky", "hasCostume", "hasSpecialForm", "isShadow", "dateText", "analysisOverride", "decisionSupport", "Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;", "telemetryUploadId", "(Ljava/lang/String;ILjava/lang/Integer;Ljava/lang/String;Lcom/pokerarity/scanner/data/model/IvSolveMode;Ljava/util/List;Ljava/lang/Integer;Ljava/lang/Float;Ljava/lang/Float;ZLjava/lang/String;ILjava/lang/String;ZZZZZLjava/lang/String;Ljava/util/List;Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;Ljava/lang/String;)Lcom/pokerarity/scanner/data/model/Pokemon;", "toUiPokemon", "Lcom/pokerarity/scanner/data/local/db/ScanHistoryEntity;", "PokeRarityScanner-v1.8.2_debug"})
public final class PokemonKt {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String EXPLANATION_DETAIL_SEPARATOR = "||";
    
    private static final java.text.SimpleDateFormat getDisplayDateFormatter() {
        return null;
    }
    
    private static final java.text.SimpleDateFormat getShortDateFormatter() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.data.model.Pokemon toUiPokemon(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.local.db.ScanHistoryEntity $this$toUiPokemon) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.data.model.Pokemon pokemonFromScanExtras(@org.jetbrains.annotations.NotNull()
    java.lang.String name, int cp, @org.jetbrains.annotations.Nullable()
    java.lang.Integer hp, @org.jetbrains.annotations.Nullable()
    java.lang.String ivText, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.data.model.IvSolveMode ivSolveMode, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> ivSignalsUsed, @org.jetbrains.annotations.Nullable()
    java.lang.Integer ivCandidateCount, @org.jetbrains.annotations.Nullable()
    java.lang.Float ivLevelMin, @org.jetbrains.annotations.Nullable()
    java.lang.Float ivLevelMax, boolean hasArcSignal, @org.jetbrains.annotations.Nullable()
    java.lang.String pvpSummary, int score, @org.jetbrains.annotations.NotNull()
    java.lang.String tier, boolean isShiny, boolean isLucky, boolean hasCostume, boolean hasSpecialForm, boolean isShadow, @org.jetbrains.annotations.Nullable()
    java.lang.String dateText, @org.jetbrains.annotations.Nullable()
    java.util.List<com.pokerarity.scanner.data.model.RarityAnalysisItem> analysisOverride, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.data.model.ScanDecisionSupport decisionSupport, @org.jetbrains.annotations.Nullable()
    java.lang.String telemetryUploadId) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public static final java.lang.String normalizeIvText(@org.jetbrains.annotations.Nullable()
    java.lang.String ivText) {
        return null;
    }
    
    private static final java.lang.String formatRarityTierLabel(java.lang.String code) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public static final java.util.List<com.pokerarity.scanner.data.model.RarityAnalysisItem> buildAnalysisItems(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> breakdownKeys, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Integer> breakdownValues, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> explanations, int fallbackScore) {
        return null;
    }
    
    private static final java.lang.String buildNarrativeExplanation(java.util.List<java.lang.String> explanations, int fallbackScore) {
        return null;
    }
    
    private static final java.lang.String explanationToPhrase(java.lang.String title, java.lang.String detail, boolean isTurkish) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String encodeExplanationItem(@org.jetbrains.annotations.NotNull()
    java.lang.String title, @org.jetbrains.annotations.Nullable()
    java.lang.String detail) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public static final kotlin.Pair<java.lang.String, java.lang.String> decodeExplanationItem(@org.jetbrains.annotations.NotNull()
    java.lang.String value) {
        return null;
    }
    
    private static final java.lang.String inferTypeFromSpecies(java.lang.String name) {
        return null;
    }
}