package com.pokerarity.scanner.data.model;

import com.pokerarity.scanner.data.local.db.ScanHistoryEntity;
import com.pokerarity.scanner.ui.theme.PokemonType;
import com.pokerarity.scanner.ui.theme.RarityColor;
import com.pokerarity.scanner.ui.theme.TypeColors;
import java.text.SimpleDateFormat;
import java.util.Locale;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000L\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010 \n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0012\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0013\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B\u0089\u0001\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0003\u0012\b\u0010\t\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\n\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\u0007\u0012\u0006\u0010\f\u001a\u00020\u0003\u0012\u0006\u0010\r\u001a\u00020\u000e\u0012\u0006\u0010\u000f\u001a\u00020\u0007\u0012\u0006\u0010\u0010\u001a\u00020\u0007\u0012\u0006\u0010\u0011\u001a\u00020\u0007\u0012\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00070\u0013\u0012\f\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00150\u0013\u00a2\u0006\u0002\u0010\u0016J\t\u00103\u001a\u00020\u0003H\u00c6\u0003J\t\u00104\u001a\u00020\u0007H\u00c6\u0003J\t\u00105\u001a\u00020\u0007H\u00c6\u0003J\t\u00106\u001a\u00020\u0007H\u00c6\u0003J\u000f\u00107\u001a\b\u0012\u0004\u0012\u00020\u00070\u0013H\u00c6\u0003J\u000f\u00108\u001a\b\u0012\u0004\u0012\u00020\u00150\u0013H\u00c6\u0003J\t\u00109\u001a\u00020\u0005H\u00c6\u0003J\t\u0010:\u001a\u00020\u0007H\u00c6\u0003J\t\u0010;\u001a\u00020\u0003H\u00c6\u0003J\u0010\u0010<\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010\u001fJ\u0010\u0010=\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010\u001fJ\u000b\u0010>\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\t\u0010?\u001a\u00020\u0003H\u00c6\u0003J\t\u0010@\u001a\u00020\u000eH\u00c6\u0003J\u00ac\u0001\u0010A\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u00032\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\u00072\b\b\u0002\u0010\f\u001a\u00020\u00032\b\b\u0002\u0010\r\u001a\u00020\u000e2\b\b\u0002\u0010\u000f\u001a\u00020\u00072\b\b\u0002\u0010\u0010\u001a\u00020\u00072\b\b\u0002\u0010\u0011\u001a\u00020\u00072\u000e\b\u0002\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00070\u00132\u000e\b\u0002\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00150\u0013H\u00c6\u0001\u00a2\u0006\u0002\u0010BJ\u0013\u0010C\u001a\u00020D2\b\u0010E\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010F\u001a\u00020\u0003H\u00d6\u0001J\t\u0010G\u001a\u00020\u0007H\u00d6\u0001R\u0017\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00150\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u0011\u0010\u0011\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u0011\u0010\b\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u001cR\u0011\u0010\u0010\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001aR\u0015\u0010\t\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010 \u001a\u0004\b\u001e\u0010\u001fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b!\u0010\u001cR\u0015\u0010\n\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010 \u001a\u0004\b\"\u0010\u001fR\u0013\u0010\u000b\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b#\u0010\u001aR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b$\u0010\u001aR\u0011\u0010\r\u001a\u00020\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b%\u0010&R\u0017\u0010\'\u001a\u00020(8F\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0006\u001a\u0004\b)\u0010*R\u0011\u0010\f\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b+\u0010\u001cR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b,\u0010*R\u0017\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00070\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b-\u0010\u0018R\u0011\u0010\u000f\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b.\u0010\u001aR\u0011\u0010/\u001a\u0002008F\u00a2\u0006\u0006\u001a\u0004\b1\u00102\u0082\u0002\u000b\n\u0005\b\u00a1\u001e0\u0001\n\u0002\b!\u00a8\u0006H"}, d2 = {"Lcom/pokerarity/scanner/data/model/Pokemon;", "", "id", "", "sourceId", "", "name", "", "cp", "hp", "iv", "ivText", "rarityScore", "rarity", "Lcom/pokerarity/scanner/data/model/Rarity;", "type", "displayDate", "caughtDate", "tags", "", "analysis", "Lcom/pokerarity/scanner/data/model/RarityAnalysisItem;", "(IJLjava/lang/String;ILjava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;ILcom/pokerarity/scanner/data/model/Rarity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "getAnalysis", "()Ljava/util/List;", "getCaughtDate", "()Ljava/lang/String;", "getCp", "()I", "getDisplayDate", "getHp", "()Ljava/lang/Integer;", "Ljava/lang/Integer;", "getId", "getIv", "getIvText", "getName", "getRarity", "()Lcom/pokerarity/scanner/data/model/Rarity;", "rarityColor", "Landroidx/compose/ui/graphics/Color;", "getRarityColor-0d7_KjU", "()J", "getRarityScore", "getSourceId", "getTags", "getType", "typeColors", "Lcom/pokerarity/scanner/ui/theme/TypeColors;", "getTypeColors", "()Lcom/pokerarity/scanner/ui/theme/TypeColors;", "component1", "component10", "component11", "component12", "component13", "component14", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "(IJLjava/lang/String;ILjava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;ILcom/pokerarity/scanner/data/model/Rarity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Ljava/util/List;)Lcom/pokerarity/scanner/data/model/Pokemon;", "equals", "", "other", "hashCode", "toString", "app_debug"})
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
    private final int rarityScore = 0;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.data.model.Rarity rarity = null;
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
    
    public Pokemon(int id, long sourceId, @org.jetbrains.annotations.NotNull()
    java.lang.String name, int cp, @org.jetbrains.annotations.Nullable()
    java.lang.Integer hp, @org.jetbrains.annotations.Nullable()
    java.lang.Integer iv, @org.jetbrains.annotations.Nullable()
    java.lang.String ivText, int rarityScore, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.Rarity rarity, @org.jetbrains.annotations.NotNull()
    java.lang.String type, @org.jetbrains.annotations.NotNull()
    java.lang.String displayDate, @org.jetbrains.annotations.NotNull()
    java.lang.String caughtDate, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> tags, @org.jetbrains.annotations.NotNull()
    java.util.List<com.pokerarity.scanner.data.model.RarityAnalysisItem> analysis) {
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
    
    public final int getRarityScore() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.Rarity getRarity() {
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
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.ui.theme.TypeColors getTypeColors() {
        return null;
    }
    
    public final int component1() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component10() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component11() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component12() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> component13() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pokerarity.scanner.data.model.RarityAnalysisItem> component14() {
        return null;
    }
    
    public final long component2() {
        return 0L;
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
    
    public final int component8() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.Rarity component9() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.Pokemon copy(int id, long sourceId, @org.jetbrains.annotations.NotNull()
    java.lang.String name, int cp, @org.jetbrains.annotations.Nullable()
    java.lang.Integer hp, @org.jetbrains.annotations.Nullable()
    java.lang.Integer iv, @org.jetbrains.annotations.Nullable()
    java.lang.String ivText, int rarityScore, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.Rarity rarity, @org.jetbrains.annotations.NotNull()
    java.lang.String type, @org.jetbrains.annotations.NotNull()
    java.lang.String displayDate, @org.jetbrains.annotations.NotNull()
    java.lang.String caughtDate, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> tags, @org.jetbrains.annotations.NotNull()
    java.util.List<com.pokerarity.scanner.data.model.RarityAnalysisItem> analysis) {
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