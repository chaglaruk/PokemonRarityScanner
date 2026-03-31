package com.pokerarity.scanner.data.model;

/**
 * Rarity tier classification for a Pokemon.
 * Tiers are ordered from least rare to most rare.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0010\b\u0086\u0081\u0002\u0018\u0000 \u00142\b\u0012\u0004\u0012\u00020\u00000\u0001:\u0001\u0014B\u001f\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0007R\u0011\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\tR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fj\u0002\b\rj\u0002\b\u000ej\u0002\b\u000fj\u0002\b\u0010j\u0002\b\u0011j\u0002\b\u0012j\u0002\b\u0013\u00a8\u0006\u0015"}, d2 = {"Lcom/pokerarity/scanner/data/model/RarityTier;", "", "label", "", "minScore", "", "color", "(Ljava/lang/String;ILjava/lang/String;ILjava/lang/String;)V", "getColor", "()Ljava/lang/String;", "getLabel", "getMinScore", "()I", "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHICAL", "GOD_TIER", "Companion", "app_debug"})
public enum RarityTier {
    /*public static final*/ COMMON /* = new COMMON(null, 0, null) */,
    /*public static final*/ UNCOMMON /* = new UNCOMMON(null, 0, null) */,
    /*public static final*/ RARE /* = new RARE(null, 0, null) */,
    /*public static final*/ EPIC /* = new EPIC(null, 0, null) */,
    /*public static final*/ LEGENDARY /* = new LEGENDARY(null, 0, null) */,
    /*public static final*/ MYTHICAL /* = new MYTHICAL(null, 0, null) */,
    /*public static final*/ GOD_TIER /* = new GOD_TIER(null, 0, null) */;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String label = null;
    private final int minScore = 0;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String color = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.data.model.RarityTier.Companion Companion = null;
    
    RarityTier(java.lang.String label, int minScore, java.lang.String color) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getLabel() {
        return null;
    }
    
    public final int getMinScore() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getColor() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public static kotlin.enums.EnumEntries<com.pokerarity.scanner.data.model.RarityTier> getEntries() {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/pokerarity/scanner/data/model/RarityTier$Companion;", "", "()V", "fromScore", "Lcom/pokerarity/scanner/data/model/RarityTier;", "score", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.model.RarityTier fromScore(int score) {
            return null;
        }
    }
}