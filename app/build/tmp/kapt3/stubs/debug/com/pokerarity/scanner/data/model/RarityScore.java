package com.pokerarity.scanner.data.model;

/**
 * Complete rarity assessment for a scanned Pokemon.
 *
 * @param totalScore Overall rarity score (0-100)
 * @param tier Human-readable category derived from totalScore
 * @param breakdown Points awarded per category (Base, Shiny, Costume, Form, Age, Event)
 * @param explanation Human-readable reasons for the score
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010$\n\u0000\n\u0002\u0010 \n\u0002\b\u0012\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001BC\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u0012\u0012\u0010\b\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00030\t\u0012\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00070\u000b\u00a2\u0006\u0002\u0010\fJ\t\u0010\u0017\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0018\u001a\u00020\u0005H\u00c6\u0003J\u000b\u0010\u0019\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u0015\u0010\u001a\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00030\tH\u00c6\u0003J\u000f\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u00070\u000bH\u00c6\u0003JO\u0010\u001c\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u00072\u0014\b\u0002\u0010\b\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00030\t2\u000e\b\u0002\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00070\u000bH\u00c6\u0001J\u0013\u0010\u001d\u001a\u00020\u001e2\b\u0010\u001f\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010 \u001a\u00020\u0003H\u00d6\u0001J\t\u0010!\u001a\u00020\u0007H\u00d6\u0001R\u001d\u0010\b\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00030\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0017\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00070\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016\u00a8\u0006\""}, d2 = {"Lcom/pokerarity/scanner/data/model/RarityScore;", "", "totalScore", "", "tier", "Lcom/pokerarity/scanner/data/model/RarityTier;", "ivEstimate", "", "breakdown", "", "explanation", "", "(ILcom/pokerarity/scanner/data/model/RarityTier;Ljava/lang/String;Ljava/util/Map;Ljava/util/List;)V", "getBreakdown", "()Ljava/util/Map;", "getExplanation", "()Ljava/util/List;", "getIvEstimate", "()Ljava/lang/String;", "getTier", "()Lcom/pokerarity/scanner/data/model/RarityTier;", "getTotalScore", "()I", "component1", "component2", "component3", "component4", "component5", "copy", "equals", "", "other", "hashCode", "toString", "app_debug"})
public final class RarityScore {
    private final int totalScore = 0;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.data.model.RarityTier tier = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String ivEstimate = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.Map<java.lang.String, java.lang.Integer> breakdown = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.String> explanation = null;
    
    public RarityScore(int totalScore, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.RarityTier tier, @org.jetbrains.annotations.Nullable()
    java.lang.String ivEstimate, @org.jetbrains.annotations.NotNull()
    java.util.Map<java.lang.String, java.lang.Integer> breakdown, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> explanation) {
        super();
    }
    
    public final int getTotalScore() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.RarityTier getTier() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getIvEstimate() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.Map<java.lang.String, java.lang.Integer> getBreakdown() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> getExplanation() {
        return null;
    }
    
    public final int component1() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.RarityTier component2() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component3() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.Map<java.lang.String, java.lang.Integer> component4() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> component5() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.RarityScore copy(int totalScore, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.RarityTier tier, @org.jetbrains.annotations.Nullable()
    java.lang.String ivEstimate, @org.jetbrains.annotations.NotNull()
    java.util.Map<java.lang.String, java.lang.Integer> breakdown, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> explanation) {
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