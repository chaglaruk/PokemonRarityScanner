package com.pokerarity.scanner.data.model;

import com.pokerarity.scanner.data.local.db.ScanHistoryEntity;
import com.pokerarity.scanner.ui.theme.PokemonType;
import com.pokerarity.scanner.ui.theme.RarityColor;
import com.pokerarity.scanner.ui.theme.TypeColors;
import java.text.SimpleDateFormat;
import java.util.Locale;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000b\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b \n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0002\b!\b\u0086\b\u0018\u00002\u00020\u0001B\u0083\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0003\u0012\b\u0010\t\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\r\u0012\u000e\b\u0002\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f\u0012\n\b\u0002\u0010\u0010\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u0011\u001a\u0004\u0018\u00010\u0012\u0012\n\b\u0002\u0010\u0013\u001a\u0004\u0018\u00010\u0012\u0012\b\b\u0002\u0010\u0014\u001a\u00020\u0015\u0012\n\b\u0002\u0010\u0016\u001a\u0004\u0018\u00010\u0007\u0012\u0006\u0010\u0017\u001a\u00020\u0003\u0012\u0006\u0010\u0018\u001a\u00020\u0019\u0012\b\b\u0002\u0010\u001a\u001a\u00020\u0007\u0012\u0006\u0010\u001b\u001a\u00020\u0007\u0012\u0006\u0010\u001c\u001a\u00020\u0007\u0012\u0006\u0010\u001d\u001a\u00020\u0007\u0012\f\u0010\u001e\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f\u0012\f\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020 0\u000f\u0012\n\b\u0002\u0010!\u001a\u0004\u0018\u00010\"\u0012\n\b\u0002\u0010#\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\u0002\u0010$J\t\u0010R\u001a\u00020\u0003H\u00c6\u0003J\u0010\u0010S\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u00101J\u0010\u0010T\u001a\u0004\u0018\u00010\u0012H\u00c6\u0003\u00a2\u0006\u0002\u00107J\u0010\u0010U\u001a\u0004\u0018\u00010\u0012H\u00c6\u0003\u00a2\u0006\u0002\u00107J\t\u0010V\u001a\u00020\u0015H\u00c6\u0003J\u000b\u0010W\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\t\u0010X\u001a\u00020\u0003H\u00c6\u0003J\t\u0010Y\u001a\u00020\u0019H\u00c6\u0003J\t\u0010Z\u001a\u00020\u0007H\u00c6\u0003J\t\u0010[\u001a\u00020\u0007H\u00c6\u0003J\t\u0010\\\u001a\u00020\u0007H\u00c6\u0003J\t\u0010]\u001a\u00020\u0005H\u00c6\u0003J\t\u0010^\u001a\u00020\u0007H\u00c6\u0003J\u000f\u0010_\u001a\b\u0012\u0004\u0012\u00020\u00070\u000fH\u00c6\u0003J\u000f\u0010`\u001a\b\u0012\u0004\u0012\u00020 0\u000fH\u00c6\u0003J\u000b\u0010a\u001a\u0004\u0018\u00010\"H\u00c6\u0003J\u000b\u0010b\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\t\u0010c\u001a\u00020\u0007H\u00c6\u0003J\t\u0010d\u001a\u00020\u0003H\u00c6\u0003J\u0010\u0010e\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u00101J\u0010\u0010f\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u00101J\u000b\u0010g\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010h\u001a\u0004\u0018\u00010\rH\u00c6\u0003J\u000f\u0010i\u001a\b\u0012\u0004\u0012\u00020\u00070\u000fH\u00c6\u0003J\u00a4\u0002\u0010j\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u00032\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\r2\u000e\b\u0002\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f2\n\b\u0002\u0010\u0010\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0011\u001a\u0004\u0018\u00010\u00122\n\b\u0002\u0010\u0013\u001a\u0004\u0018\u00010\u00122\b\b\u0002\u0010\u0014\u001a\u00020\u00152\n\b\u0002\u0010\u0016\u001a\u0004\u0018\u00010\u00072\b\b\u0002\u0010\u0017\u001a\u00020\u00032\b\b\u0002\u0010\u0018\u001a\u00020\u00192\b\b\u0002\u0010\u001a\u001a\u00020\u00072\b\b\u0002\u0010\u001b\u001a\u00020\u00072\b\b\u0002\u0010\u001c\u001a\u00020\u00072\b\b\u0002\u0010\u001d\u001a\u00020\u00072\u000e\b\u0002\u0010\u001e\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f2\u000e\b\u0002\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020 0\u000f2\n\b\u0002\u0010!\u001a\u0004\u0018\u00010\"2\n\b\u0002\u0010#\u001a\u0004\u0018\u00010\u0007H\u00c6\u0001\u00a2\u0006\u0002\u0010kJ\u0013\u0010l\u001a\u00020\u00152\b\u0010m\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010n\u001a\u00020\u0003H\u00d6\u0001J\t\u0010o\u001a\u00020\u0007H\u00d6\u0001R\u0017\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020 0\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b%\u0010&R\u0011\u0010\u001d\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\'\u0010(R\u0011\u0010\b\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b)\u0010*R\u0013\u0010!\u001a\u0004\u0018\u00010\"\u00a2\u0006\b\n\u0000\u001a\u0004\b+\u0010,R\u0011\u0010\u001c\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b-\u0010(R\u0011\u0010\u0014\u001a\u00020\u0015\u00a2\u0006\b\n\u0000\u001a\u0004\b.\u0010/R\u0015\u0010\t\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u00102\u001a\u0004\b0\u00101R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b3\u0010*R\u0015\u0010\n\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u00102\u001a\u0004\b4\u00101R\u0015\u0010\u0010\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u00102\u001a\u0004\b5\u00101R\u0015\u0010\u0013\u001a\u0004\u0018\u00010\u0012\u00a2\u0006\n\n\u0002\u00108\u001a\u0004\b6\u00107R\u0015\u0010\u0011\u001a\u0004\u0018\u00010\u0012\u00a2\u0006\n\n\u0002\u00108\u001a\u0004\b9\u00107R\u0017\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b:\u0010&R\u0013\u0010\f\u001a\u0004\u0018\u00010\r\u00a2\u0006\b\n\u0000\u001a\u0004\b;\u0010<R\u0013\u0010\u000b\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b=\u0010(R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b>\u0010(R\u0013\u0010\u0016\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b?\u0010(R\u0011\u0010\u0018\u001a\u00020\u0019\u00a2\u0006\b\n\u0000\u001a\u0004\b@\u0010AR\u0017\u0010B\u001a\u00020C8F\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0006\u001a\u0004\bD\u0010ER\u0011\u0010\u0017\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\bF\u0010*R\u0011\u0010\u001a\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\bG\u0010(R\u0011\u0010H\u001a\u00020\u00078F\u00a2\u0006\u0006\u001a\u0004\bI\u0010(R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\bJ\u0010ER\u0017\u0010\u001e\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\bK\u0010&R\u0013\u0010#\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\bL\u0010(R\u0011\u0010\u001b\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\bM\u0010(R\u0011\u0010N\u001a\u00020O8F\u00a2\u0006\u0006\u001a\u0004\bP\u0010Q\u0082\u0002\u000b\n\u0005\b\u00a1\u001e0\u0001\n\u0002\b!\u00a8\u0006p"}, d2 = {"Lcom/pokerarity/scanner/data/model/Pokemon;", "", "id", "", "sourceId", "", "name", "", "cp", "hp", "iv", "ivText", "ivSolveMode", "Lcom/pokerarity/scanner/data/model/IvSolveMode;", "ivSignalsUsed", "", "ivCandidateCount", "ivLevelMin", "", "ivLevelMax", "hasArcSignal", "", "pvpSummary", "rarityScore", "rarity", "Lcom/pokerarity/scanner/data/model/Rarity;", "rarityTierCode", "type", "displayDate", "caughtDate", "tags", "analysis", "Lcom/pokerarity/scanner/data/model/RarityAnalysisItem;", "decisionSupport", "Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;", "telemetryUploadId", "(IJLjava/lang/String;ILjava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;Lcom/pokerarity/scanner/data/model/IvSolveMode;Ljava/util/List;Ljava/lang/Integer;Ljava/lang/Float;Ljava/lang/Float;ZLjava/lang/String;ILcom/pokerarity/scanner/data/model/Rarity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Ljava/util/List;Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;Ljava/lang/String;)V", "getAnalysis", "()Ljava/util/List;", "getCaughtDate", "()Ljava/lang/String;", "getCp", "()I", "getDecisionSupport", "()Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;", "getDisplayDate", "getHasArcSignal", "()Z", "getHp", "()Ljava/lang/Integer;", "Ljava/lang/Integer;", "getId", "getIv", "getIvCandidateCount", "getIvLevelMax", "()Ljava/lang/Float;", "Ljava/lang/Float;", "getIvLevelMin", "getIvSignalsUsed", "getIvSolveMode", "()Lcom/pokerarity/scanner/data/model/IvSolveMode;", "getIvText", "getName", "getPvpSummary", "getRarity", "()Lcom/pokerarity/scanner/data/model/Rarity;", "rarityColor", "Landroidx/compose/ui/graphics/Color;", "getRarityColor-0d7_KjU", "()J", "getRarityScore", "getRarityTierCode", "rarityTierLabel", "getRarityTierLabel", "getSourceId", "getTags", "getTelemetryUploadId", "getType", "typeColors", "Lcom/pokerarity/scanner/ui/theme/TypeColors;", "getTypeColors", "()Lcom/pokerarity/scanner/ui/theme/TypeColors;", "component1", "component10", "component11", "component12", "component13", "component14", "component15", "component16", "component17", "component18", "component19", "component2", "component20", "component21", "component22", "component23", "component24", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "(IJLjava/lang/String;ILjava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;Lcom/pokerarity/scanner/data/model/IvSolveMode;Ljava/util/List;Ljava/lang/Integer;Ljava/lang/Float;Ljava/lang/Float;ZLjava/lang/String;ILcom/pokerarity/scanner/data/model/Rarity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Ljava/util/List;Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;Ljava/lang/String;)Lcom/pokerarity/scanner/data/model/Pokemon;", "equals", "other", "hashCode", "toString", "PokeRarityScanner-v1.8.2_debug"})
public final class Pokemon {
    private final int id = 0;
    private final long sourceId = 0L;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String name = null;
    private final int cp = 0;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer hp = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer iv = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String ivText = null;
    @org.jetbrains.annotations.Nullable()
    private final com.pokerarity.scanner.data.model.IvSolveMode ivSolveMode = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.String> ivSignalsUsed = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer ivCandidateCount = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Float ivLevelMin = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Float ivLevelMax = null;
    private final boolean hasArcSignal = false;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String pvpSummary = null;
    private final int rarityScore = 0;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.data.model.Rarity rarity = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String rarityTierCode = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String type = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String displayDate = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String caughtDate = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.String> tags = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.pokerarity.scanner.data.model.RarityAnalysisItem> analysis = null;
    @org.jetbrains.annotations.Nullable()
    private final com.pokerarity.scanner.data.model.ScanDecisionSupport decisionSupport = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String telemetryUploadId = null;
    
    public Pokemon(int id, long sourceId, @org.jetbrains.annotations.NotNull()
    java.lang.String name, int cp, @org.jetbrains.annotations.Nullable()
    java.lang.Integer hp, @org.jetbrains.annotations.Nullable()
    java.lang.Integer iv, @org.jetbrains.annotations.Nullable()
    java.lang.String ivText, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.data.model.IvSolveMode ivSolveMode, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> ivSignalsUsed, @org.jetbrains.annotations.Nullable()
    java.lang.Integer ivCandidateCount, @org.jetbrains.annotations.Nullable()
    java.lang.Float ivLevelMin, @org.jetbrains.annotations.Nullable()
    java.lang.Float ivLevelMax, boolean hasArcSignal, @org.jetbrains.annotations.Nullable()
    java.lang.String pvpSummary, int rarityScore, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.Rarity rarity, @org.jetbrains.annotations.NotNull()
    java.lang.String rarityTierCode, @org.jetbrains.annotations.NotNull()
    java.lang.String type, @org.jetbrains.annotations.NotNull()
    java.lang.String displayDate, @org.jetbrains.annotations.NotNull()
    java.lang.String caughtDate, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> tags, @org.jetbrains.annotations.NotNull()
    java.util.List<com.pokerarity.scanner.data.model.RarityAnalysisItem> analysis, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.data.model.ScanDecisionSupport decisionSupport, @org.jetbrains.annotations.Nullable()
    java.lang.String telemetryUploadId) {
        super();
    }
    
    public final int getId() {
        return 0;
    }
    
    public final long getSourceId() {
        return 0L;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getName() {
        return null;
    }
    
    public final int getCp() {
        return 0;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getHp() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getIv() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getIvText() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pokerarity.scanner.data.model.IvSolveMode getIvSolveMode() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> getIvSignalsUsed() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getIvCandidateCount() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float getIvLevelMin() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float getIvLevelMax() {
        return null;
    }
    
    public final boolean getHasArcSignal() {
        return false;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getPvpSummary() {
        return null;
    }
    
    public final int getRarityScore() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.Rarity getRarity() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getRarityTierCode() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getType() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getDisplayDate() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getCaughtDate() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> getTags() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pokerarity.scanner.data.model.RarityAnalysisItem> getAnalysis() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pokerarity.scanner.data.model.ScanDecisionSupport getDecisionSupport() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getTelemetryUploadId() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.ui.theme.TypeColors getTypeColors() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getRarityTierLabel() {
        return null;
    }
    
    public final int component1() {
        return 0;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component10() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float component11() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float component12() {
        return null;
    }
    
    public final boolean component13() {
        return false;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component14() {
        return null;
    }
    
    public final int component15() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.Rarity component16() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component17() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component18() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component19() {
        return null;
    }
    
    public final long component2() {
        return 0L;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component20() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> component21() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pokerarity.scanner.data.model.RarityAnalysisItem> component22() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pokerarity.scanner.data.model.ScanDecisionSupport component23() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component24() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component3() {
        return null;
    }
    
    public final int component4() {
        return 0;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component5() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component6() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component7() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pokerarity.scanner.data.model.IvSolveMode component8() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> component9() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.Pokemon copy(int id, long sourceId, @org.jetbrains.annotations.NotNull()
    java.lang.String name, int cp, @org.jetbrains.annotations.Nullable()
    java.lang.Integer hp, @org.jetbrains.annotations.Nullable()
    java.lang.Integer iv, @org.jetbrains.annotations.Nullable()
    java.lang.String ivText, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.data.model.IvSolveMode ivSolveMode, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> ivSignalsUsed, @org.jetbrains.annotations.Nullable()
    java.lang.Integer ivCandidateCount, @org.jetbrains.annotations.Nullable()
    java.lang.Float ivLevelMin, @org.jetbrains.annotations.Nullable()
    java.lang.Float ivLevelMax, boolean hasArcSignal, @org.jetbrains.annotations.Nullable()
    java.lang.String pvpSummary, int rarityScore, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.Rarity rarity, @org.jetbrains.annotations.NotNull()
    java.lang.String rarityTierCode, @org.jetbrains.annotations.NotNull()
    java.lang.String type, @org.jetbrains.annotations.NotNull()
    java.lang.String displayDate, @org.jetbrains.annotations.NotNull()
    java.lang.String caughtDate, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> tags, @org.jetbrains.annotations.NotNull()
    java.util.List<com.pokerarity.scanner.data.model.RarityAnalysisItem> analysis, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.data.model.ScanDecisionSupport decisionSupport, @org.jetbrains.annotations.Nullable()
    java.lang.String telemetryUploadId) {
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