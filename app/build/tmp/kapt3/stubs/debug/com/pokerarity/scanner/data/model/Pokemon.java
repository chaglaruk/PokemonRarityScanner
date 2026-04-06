package com.pokerarity.scanner.data.model;

import com.pokerarity.scanner.data.local.db.ScanHistoryEntity;
import com.pokerarity.scanner.ui.theme.PokemonType;
import com.pokerarity.scanner.ui.theme.RarityColor;
import com.pokerarity.scanner.ui.theme.TypeColors;
import java.text.SimpleDateFormat;
import java.util.Locale;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000X\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u001a\n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0002\b\u001d\b\u0086\b\u0018\u00002\u00020\u0001B\u00d1\u0001\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0003\u0012\b\u0010\t\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\n\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\r\u0012\u000e\b\u0002\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f\u0012\b\b\u0002\u0010\u0010\u001a\u00020\u0011\u0012\u0006\u0010\u0012\u001a\u00020\u0003\u0012\u0006\u0010\u0013\u001a\u00020\u0014\u0012\b\b\u0002\u0010\u0015\u001a\u00020\u0007\u0012\u0006\u0010\u0016\u001a\u00020\u0007\u0012\u0006\u0010\u0017\u001a\u00020\u0007\u0012\u0006\u0010\u0018\u001a\u00020\u0007\u0012\f\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f\u0012\f\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u001b0\u000f\u0012\n\b\u0002\u0010\u001c\u001a\u0004\u0018\u00010\u001d\u0012\n\b\u0002\u0010\u001e\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\u0002\u0010\u001fJ\t\u0010G\u001a\u00020\u0003H\u00c6\u0003J\t\u0010H\u001a\u00020\u0011H\u00c6\u0003J\t\u0010I\u001a\u00020\u0003H\u00c6\u0003J\t\u0010J\u001a\u00020\u0014H\u00c6\u0003J\t\u0010K\u001a\u00020\u0007H\u00c6\u0003J\t\u0010L\u001a\u00020\u0007H\u00c6\u0003J\t\u0010M\u001a\u00020\u0007H\u00c6\u0003J\t\u0010N\u001a\u00020\u0007H\u00c6\u0003J\u000f\u0010O\u001a\b\u0012\u0004\u0012\u00020\u00070\u000fH\u00c6\u0003J\u000f\u0010P\u001a\b\u0012\u0004\u0012\u00020\u001b0\u000fH\u00c6\u0003J\u000b\u0010Q\u001a\u0004\u0018\u00010\u001dH\u00c6\u0003J\t\u0010R\u001a\u00020\u0005H\u00c6\u0003J\u000b\u0010S\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\t\u0010T\u001a\u00020\u0007H\u00c6\u0003J\t\u0010U\u001a\u00020\u0003H\u00c6\u0003J\u0010\u0010V\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010,J\u0010\u0010W\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010,J\u000b\u0010X\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010Y\u001a\u0004\u0018\u00010\rH\u00c6\u0003J\u000f\u0010Z\u001a\b\u0012\u0004\u0012\u00020\u00070\u000fH\u00c6\u0003J\u00f4\u0001\u0010[\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u00032\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\r2\u000e\b\u0002\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f2\b\b\u0002\u0010\u0010\u001a\u00020\u00112\b\b\u0002\u0010\u0012\u001a\u00020\u00032\b\b\u0002\u0010\u0013\u001a\u00020\u00142\b\b\u0002\u0010\u0015\u001a\u00020\u00072\b\b\u0002\u0010\u0016\u001a\u00020\u00072\b\b\u0002\u0010\u0017\u001a\u00020\u00072\b\b\u0002\u0010\u0018\u001a\u00020\u00072\u000e\b\u0002\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f2\u000e\b\u0002\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u001b0\u000f2\n\b\u0002\u0010\u001c\u001a\u0004\u0018\u00010\u001d2\n\b\u0002\u0010\u001e\u001a\u0004\u0018\u00010\u0007H\u00c6\u0001\u00a2\u0006\u0002\u0010\\J\u0013\u0010]\u001a\u00020\u00112\b\u0010^\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010_\u001a\u00020\u0003H\u00d6\u0001J\t\u0010`\u001a\u00020\u0007H\u00d6\u0001R\u0017\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u001b0\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010!R\u0011\u0010\u0018\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\"\u0010#R\u0011\u0010\b\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b$\u0010%R\u0013\u0010\u001c\u001a\u0004\u0018\u00010\u001d\u00a2\u0006\b\n\u0000\u001a\u0004\b&\u0010\'R\u0011\u0010\u0017\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b(\u0010#R\u0011\u0010\u0010\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b)\u0010*R\u0015\u0010\t\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010-\u001a\u0004\b+\u0010,R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b.\u0010%R\u0015\u0010\n\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010-\u001a\u0004\b/\u0010,R\u0017\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b0\u0010!R\u0013\u0010\f\u001a\u0004\u0018\u00010\r\u00a2\u0006\b\n\u0000\u001a\u0004\b1\u00102R\u0013\u0010\u000b\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b3\u0010#R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b4\u0010#R\u0011\u0010\u0013\u001a\u00020\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b5\u00106R\u0017\u00107\u001a\u0002088F\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0006\u001a\u0004\b9\u0010:R\u0011\u0010\u0012\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b;\u0010%R\u0011\u0010\u0015\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b<\u0010#R\u0011\u0010=\u001a\u00020\u00078F\u00a2\u0006\u0006\u001a\u0004\b>\u0010#R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b?\u0010:R\u0017\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b@\u0010!R\u0013\u0010\u001e\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\bA\u0010#R\u0011\u0010\u0016\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\bB\u0010#R\u0011\u0010C\u001a\u00020D8F\u00a2\u0006\u0006\u001a\u0004\bE\u0010F\u0082\u0002\u000b\n\u0005\b\u00a1\u001e0\u0001\n\u0002\b!\u00a8\u0006a"}, d2 = {"Lcom/pokerarity/scanner/data/model/Pokemon;", "", "id", "", "sourceId", "", "name", "", "cp", "hp", "iv", "ivText", "ivSolveMode", "Lcom/pokerarity/scanner/data/model/IvSolveMode;", "ivSignalsUsed", "", "hasArcSignal", "", "rarityScore", "rarity", "Lcom/pokerarity/scanner/data/model/Rarity;", "rarityTierCode", "type", "displayDate", "caughtDate", "tags", "analysis", "Lcom/pokerarity/scanner/data/model/RarityAnalysisItem;", "decisionSupport", "Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;", "telemetryUploadId", "(IJLjava/lang/String;ILjava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;Lcom/pokerarity/scanner/data/model/IvSolveMode;Ljava/util/List;ZILcom/pokerarity/scanner/data/model/Rarity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Ljava/util/List;Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;Ljava/lang/String;)V", "getAnalysis", "()Ljava/util/List;", "getCaughtDate", "()Ljava/lang/String;", "getCp", "()I", "getDecisionSupport", "()Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;", "getDisplayDate", "getHasArcSignal", "()Z", "getHp", "()Ljava/lang/Integer;", "Ljava/lang/Integer;", "getId", "getIv", "getIvSignalsUsed", "getIvSolveMode", "()Lcom/pokerarity/scanner/data/model/IvSolveMode;", "getIvText", "getName", "getRarity", "()Lcom/pokerarity/scanner/data/model/Rarity;", "rarityColor", "Landroidx/compose/ui/graphics/Color;", "getRarityColor-0d7_KjU", "()J", "getRarityScore", "getRarityTierCode", "rarityTierLabel", "getRarityTierLabel", "getSourceId", "getTags", "getTelemetryUploadId", "getType", "typeColors", "Lcom/pokerarity/scanner/ui/theme/TypeColors;", "getTypeColors", "()Lcom/pokerarity/scanner/ui/theme/TypeColors;", "component1", "component10", "component11", "component12", "component13", "component14", "component15", "component16", "component17", "component18", "component19", "component2", "component20", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "(IJLjava/lang/String;ILjava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;Lcom/pokerarity/scanner/data/model/IvSolveMode;Ljava/util/List;ZILcom/pokerarity/scanner/data/model/Rarity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Ljava/util/List;Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;Ljava/lang/String;)Lcom/pokerarity/scanner/data/model/Pokemon;", "equals", "other", "hashCode", "toString", "app_debug"})
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
    private final boolean hasArcSignal = false;
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
    java.util.List<java.lang.String> ivSignalsUsed, boolean hasArcSignal, int rarityScore, @org.jetbrains.annotations.NotNull()
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
    
    public final boolean getHasArcSignal() {
        return false;
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
    
    public final boolean component10() {
        return false;
    }
    
    public final int component11() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.Rarity component12() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component13() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component14() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component15() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component16() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> component17() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pokerarity.scanner.data.model.RarityAnalysisItem> component18() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pokerarity.scanner.data.model.ScanDecisionSupport component19() {
        return null;
    }
    
    public final long component2() {
        return 0L;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component20() {
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
    java.util.List<java.lang.String> ivSignalsUsed, boolean hasArcSignal, int rarityScore, @org.jetbrains.annotations.NotNull()
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