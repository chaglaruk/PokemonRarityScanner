package com.pokerarity.scanner.data.model;

/**
 * Complete rarity assessment for a scanned Pokemon.
 *
 * @param totalScore Overall rarity score
 * @param tier Human-readable category derived from totalScore
 * @param breakdown Points awarded per high-level axis
 * @param explanation Human-readable reasons for the score
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010$\n\u0000\n\u0002\u0010 \n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0002\b!\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B\u008d\u0001\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\n\u0012\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\u0007\u0012\u0012\u0010\f\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00030\r\u0012\f\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f\u0012\u000e\b\u0002\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00110\u000f\u0012\b\b\u0002\u0010\u0012\u001a\u00020\u0013\u0012\n\b\u0002\u0010\u0014\u001a\u0004\u0018\u00010\u0015\u00a2\u0006\u0002\u0010\u0016J\t\u0010*\u001a\u00020\u0003H\u00c6\u0003J\t\u0010+\u001a\u00020\u0013H\u00c6\u0003J\u000b\u0010,\u001a\u0004\u0018\u00010\u0015H\u00c6\u0003J\t\u0010-\u001a\u00020\u0005H\u00c6\u0003J\u000b\u0010.\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010/\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u00100\u001a\u0004\u0018\u00010\nH\u00c6\u0003J\u000b\u00101\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u0015\u00102\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00030\rH\u00c6\u0003J\u000f\u00103\u001a\b\u0012\u0004\u0012\u00020\u00070\u000fH\u00c6\u0003J\u000f\u00104\u001a\b\u0012\u0004\u0012\u00020\u00110\u000fH\u00c6\u0003J\u0099\u0001\u00105\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\n2\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\u00072\u0014\b\u0002\u0010\f\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00030\r2\u000e\b\u0002\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f2\u000e\b\u0002\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00110\u000f2\b\b\u0002\u0010\u0012\u001a\u00020\u00132\n\b\u0002\u0010\u0014\u001a\u0004\u0018\u00010\u0015H\u00c6\u0001J\u0013\u00106\u001a\u0002072\b\u00108\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u00109\u001a\u00020\u0003H\u00d6\u0001J\t\u0010:\u001a\u00020\u0007H\u00d6\u0001R\u0017\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00110\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u001d\u0010\f\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00030\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u0011\u0010\u0012\u001a\u00020\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u001cR\u0013\u0010\u0014\u001a\u0004\u0018\u00010\u0015\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001eR\u0017\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00070\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010\u0018R\u0013\u0010\b\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010!R\u0013\u0010\t\u001a\u0004\u0018\u00010\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\"\u0010#R\u0013\u0010\u000b\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b$\u0010!R\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b%\u0010!R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b&\u0010\'R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b(\u0010)\u00a8\u0006;"}, d2 = {"Lcom/pokerarity/scanner/data/model/RarityScore;", "", "totalScore", "", "tier", "Lcom/pokerarity/scanner/data/model/RarityTier;", "recognitionSummary", "", "ivEstimate", "ivSolve", "Lcom/pokerarity/scanner/data/model/IvSolveDetails;", "pvpSummary", "breakdown", "", "explanation", "", "axes", "Lcom/pokerarity/scanner/data/model/RarityAxisScore;", "confidence", "", "decisionSupport", "Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;", "(ILcom/pokerarity/scanner/data/model/RarityTier;Ljava/lang/String;Ljava/lang/String;Lcom/pokerarity/scanner/data/model/IvSolveDetails;Ljava/lang/String;Ljava/util/Map;Ljava/util/List;Ljava/util/List;FLcom/pokerarity/scanner/data/model/ScanDecisionSupport;)V", "getAxes", "()Ljava/util/List;", "getBreakdown", "()Ljava/util/Map;", "getConfidence", "()F", "getDecisionSupport", "()Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;", "getExplanation", "getIvEstimate", "()Ljava/lang/String;", "getIvSolve", "()Lcom/pokerarity/scanner/data/model/IvSolveDetails;", "getPvpSummary", "getRecognitionSummary", "getTier", "()Lcom/pokerarity/scanner/data/model/RarityTier;", "getTotalScore", "()I", "component1", "component10", "component11", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "equals", "", "other", "hashCode", "toString", "PokeRarityScanner-v1.8.2_debug"})
public final class RarityScore {
    private final int totalScore = 0;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.data.model.RarityTier tier = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String recognitionSummary = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String ivEstimate = null;
    @org.jetbrains.annotations.Nullable()
    private final com.pokerarity.scanner.data.model.IvSolveDetails ivSolve = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String pvpSummary = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.Map<java.lang.String, java.lang.Integer> breakdown = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.String> explanation = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.pokerarity.scanner.data.model.RarityAxisScore> axes = null;
    private final float confidence = 0.0F;
    @org.jetbrains.annotations.Nullable()
    private final com.pokerarity.scanner.data.model.ScanDecisionSupport decisionSupport = null;
    
    public RarityScore(int totalScore, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.RarityTier tier, @org.jetbrains.annotations.Nullable()
    java.lang.String recognitionSummary, @org.jetbrains.annotations.Nullable()
    java.lang.String ivEstimate, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.data.model.IvSolveDetails ivSolve, @org.jetbrains.annotations.Nullable()
    java.lang.String pvpSummary, @org.jetbrains.annotations.NotNull()
    java.util.Map<java.lang.String, java.lang.Integer> breakdown, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> explanation, @org.jetbrains.annotations.NotNull()
    java.util.List<com.pokerarity.scanner.data.model.RarityAxisScore> axes, float confidence, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.data.model.ScanDecisionSupport decisionSupport) {
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
    public final java.lang.String getRecognitionSummary() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getIvEstimate() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pokerarity.scanner.data.model.IvSolveDetails getIvSolve() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getPvpSummary() {
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
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pokerarity.scanner.data.model.RarityAxisScore> getAxes() {
        return null;
    }
    
    public final float getConfidence() {
        return 0.0F;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pokerarity.scanner.data.model.ScanDecisionSupport getDecisionSupport() {
        return null;
    }
    
    public final int component1() {
        return 0;
    }
    
    public final float component10() {
        return 0.0F;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pokerarity.scanner.data.model.ScanDecisionSupport component11() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.RarityTier component2() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component3() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component4() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pokerarity.scanner.data.model.IvSolveDetails component5() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component6() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.Map<java.lang.String, java.lang.Integer> component7() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> component8() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pokerarity.scanner.data.model.RarityAxisScore> component9() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.RarityScore copy(int totalScore, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.RarityTier tier, @org.jetbrains.annotations.Nullable()
    java.lang.String recognitionSummary, @org.jetbrains.annotations.Nullable()
    java.lang.String ivEstimate, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.data.model.IvSolveDetails ivSolve, @org.jetbrains.annotations.Nullable()
    java.lang.String pvpSummary, @org.jetbrains.annotations.NotNull()
    java.util.Map<java.lang.String, java.lang.Integer> breakdown, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> explanation, @org.jetbrains.annotations.NotNull()
    java.util.List<com.pokerarity.scanner.data.model.RarityAxisScore> axes, float confidence, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.data.model.ScanDecisionSupport decisionSupport) {
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