package com.pokerarity.scanner.util.vision;

import android.content.Context;
import android.graphics.Bitmap;
import org.json.JSONObject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000^\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0005\n\u0002\u0010\b\n\u0000\n\u0002\u0010$\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0002$%B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u0016H\u0002J\u0018\u0010\u0017\u001a\u00020\u00122\u0006\u0010\u0015\u001a\u00020\u00162\b\u0010\u0018\u001a\u0004\u0018\u00010\u0004J,\u0010\u0019\u001a\u000e\u0012\u0004\u0012\u00020\u0012\u0012\u0004\u0012\u00020\u00060\u001a2\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u001b\u001a\u00020\u001c2\b\u0010\u0018\u001a\u0004\u0018\u00010\u0004J$\u0010\u001d\u001a\u000e\u0012\u0004\u0012\u00020\u0012\u0012\u0004\u0012\u00020\u00060\u001a2\u0006\u0010\u001e\u001a\u00020\u001f2\b\u0010\u0018\u001a\u0004\u0018\u00010\u0004J\u001a\u0010 \u001a\u0004\u0018\u00010!2\u0006\u0010\u001e\u001a\u00020\u001f2\b\u0010\u0018\u001a\u0004\u0018\u00010\u0004J&\u0010\"\u001a\u00020!2\u0006\u0010\u001e\u001a\u00020\u001f2\u0006\u0010\u0018\u001a\u00020\u00042\f\u0010#\u001a\b\u0012\u0004\u0012\u00020\u00100\u000fH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082T\u00a2\u0006\u0002\n\u0000R \u0010\r\u001a\u0014\u0012\u0004\u0012\u00020\u0004\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00100\u000f0\u000eX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006&"}, d2 = {"Lcom/pokerarity/scanner/util/vision/CostumeSignatureStore;", "", "()V", "ASSET_PATH", "", "COSTUME_MARGIN", "", "COSTUME_SCORE_THRESHOLD", "COSTUME_SOFT_MARGIN", "DENSE_VARIANT_MARGIN_THRESHOLD", "DENSE_VARIANT_SCORE_THRESHOLD", "EDGE_BINS", "", "bySpecies", "", "", "Lcom/pokerarity/scanner/util/vision/CostumeSignatureStore$CostumeSignature;", "loaded", "", "ensureLoaded", "", "context", "Landroid/content/Context;", "hasSpecies", "species", "match", "Lkotlin/Pair;", "bitmap", "Landroid/graphics/Bitmap;", "matchSignature", "signature", "Lcom/pokerarity/scanner/util/vision/SpriteSignature$Signature;", "matchSignatureDetails", "Lcom/pokerarity/scanner/util/vision/CostumeSignatureStore$MatchDetails;", "matchSignatureInternal", "candidates", "CostumeSignature", "MatchDetails", "app_debug"})
public final class CostumeSignatureStore {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ASSET_PATH = "data/costume_signatures.json";
    private static final float COSTUME_SCORE_THRESHOLD = 0.3F;
    private static final float COSTUME_MARGIN = 0.02F;
    private static final float COSTUME_SOFT_MARGIN = 0.01F;
    private static final float DENSE_VARIANT_SCORE_THRESHOLD = 0.3F;
    private static final float DENSE_VARIANT_MARGIN_THRESHOLD = 0.04F;
    private static final int EDGE_BINS = 8;
    private static boolean loaded = false;
    @org.jetbrains.annotations.NotNull()
    private static java.util.Map<java.lang.String, ? extends java.util.List<com.pokerarity.scanner.util.vision.CostumeSignatureStore.CostumeSignature>> bySpecies;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.util.vision.CostumeSignatureStore INSTANCE = null;
    
    private CostumeSignatureStore() {
        super();
    }
    
    public final boolean hasSpecies(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.Nullable()
    java.lang.String species) {
        return false;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlin.Pair<java.lang.Boolean, java.lang.Float> match(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.Nullable()
    java.lang.String species) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlin.Pair<java.lang.Boolean, java.lang.Float> matchSignature(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.util.vision.SpriteSignature.Signature signature, @org.jetbrains.annotations.Nullable()
    java.lang.String species) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pokerarity.scanner.util.vision.CostumeSignatureStore.MatchDetails matchSignatureDetails(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.util.vision.SpriteSignature.Signature signature, @org.jetbrains.annotations.Nullable()
    java.lang.String species) {
        return null;
    }
    
    private final void ensureLoaded(android.content.Context context) {
    }
    
    private final com.pokerarity.scanner.util.vision.CostumeSignatureStore.MatchDetails matchSignatureInternal(com.pokerarity.scanner.util.vision.SpriteSignature.Signature signature, java.lang.String species, java.util.List<com.pokerarity.scanner.util.vision.CostumeSignatureStore.CostumeSignature> candidates) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u0014\n\u0002\b\u0013\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B5\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\u0006\u0010\u0007\u001a\u00020\u0003\u0012\u0006\u0010\b\u001a\u00020\u0003\u0012\u0006\u0010\t\u001a\u00020\n\u00a2\u0006\u0002\u0010\u000bJ\t\u0010\u0014\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0015\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0016\u001a\u00020\u0006H\u00c6\u0003J\t\u0010\u0017\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0018\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0019\u001a\u00020\nH\u00c6\u0003JE\u0010\u001a\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u0010\u0007\u001a\u00020\u00032\b\b\u0002\u0010\b\u001a\u00020\u00032\b\b\u0002\u0010\t\u001a\u00020\nH\u00c6\u0001J\u0013\u0010\u001b\u001a\u00020\u00062\b\u0010\u001c\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u001d\u001a\u00020\u001eH\u00d6\u0001J\t\u0010\u001f\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0007\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\b\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\rR\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0011R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\rR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\r\u00a8\u0006 "}, d2 = {"Lcom/pokerarity/scanner/util/vision/CostumeSignatureStore$CostumeSignature;", "", "species", "", "key", "isCostume", "", "aHash", "dHash", "edge", "", "(Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;Ljava/lang/String;[F)V", "getAHash", "()Ljava/lang/String;", "getDHash", "getEdge", "()[F", "()Z", "getKey", "getSpecies", "component1", "component2", "component3", "component4", "component5", "component6", "copy", "equals", "other", "hashCode", "", "toString", "app_debug"})
    public static final class CostumeSignature {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String species = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String key = null;
        private final boolean isCostume = false;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String aHash = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String dHash = null;
        @org.jetbrains.annotations.NotNull()
        private final float[] edge = null;
        
        public CostumeSignature(@org.jetbrains.annotations.NotNull()
        java.lang.String species, @org.jetbrains.annotations.NotNull()
        java.lang.String key, boolean isCostume, @org.jetbrains.annotations.NotNull()
        java.lang.String aHash, @org.jetbrains.annotations.NotNull()
        java.lang.String dHash, @org.jetbrains.annotations.NotNull()
        float[] edge) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getSpecies() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getKey() {
            return null;
        }
        
        public final boolean isCostume() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getAHash() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getDHash() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final float[] getEdge() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        public final boolean component3() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component5() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final float[] component6() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.CostumeSignatureStore.CostumeSignature copy(@org.jetbrains.annotations.NotNull()
        java.lang.String species, @org.jetbrains.annotations.NotNull()
        java.lang.String key, boolean isCostume, @org.jetbrains.annotations.NotNull()
        java.lang.String aHash, @org.jetbrains.annotations.NotNull()
        java.lang.String dHash, @org.jetbrains.annotations.NotNull()
        float[] edge) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0004\n\u0002\u0010\b\n\u0002\b\u0018\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B=\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\u0006\u0010\u0007\u001a\u00020\u0005\u0012\u0006\u0010\b\u001a\u00020\u0005\u0012\u0006\u0010\t\u001a\u00020\n\u0012\u0006\u0010\u000b\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\fJ\t\u0010\u0017\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0018\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0019\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001a\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001b\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001c\u001a\u00020\nH\u00c6\u0003J\t\u0010\u001d\u001a\u00020\u0003H\u00c6\u0003JO\u0010\u001e\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00052\b\b\u0002\u0010\u0007\u001a\u00020\u00052\b\b\u0002\u0010\b\u001a\u00020\u00052\b\b\u0002\u0010\t\u001a\u00020\n2\b\b\u0002\u0010\u000b\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\u001f\u001a\u00020\u00032\b\u0010 \u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010!\u001a\u00020\nH\u00d6\u0001J\t\u0010\"\u001a\u00020#H\u00d6\u0001R\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0007\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u000eR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u000eR\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u000b\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0014R\u0011\u0010\b\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u000e\u00a8\u0006$"}, d2 = {"Lcom/pokerarity/scanner/util/vision/CostumeSignatureStore$MatchDetails;", "", "matched", "", "confidence", "", "bestCostume", "bestNormal", "scoreGap", "costumeCandidateCount", "", "denseVariantSpecies", "(ZFFFFIZ)V", "getBestCostume", "()F", "getBestNormal", "getConfidence", "getCostumeCandidateCount", "()I", "getDenseVariantSpecies", "()Z", "getMatched", "getScoreGap", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "copy", "equals", "other", "hashCode", "toString", "", "app_debug"})
    public static final class MatchDetails {
        private final boolean matched = false;
        private final float confidence = 0.0F;
        private final float bestCostume = 0.0F;
        private final float bestNormal = 0.0F;
        private final float scoreGap = 0.0F;
        private final int costumeCandidateCount = 0;
        private final boolean denseVariantSpecies = false;
        
        public MatchDetails(boolean matched, float confidence, float bestCostume, float bestNormal, float scoreGap, int costumeCandidateCount, boolean denseVariantSpecies) {
            super();
        }
        
        public final boolean getMatched() {
            return false;
        }
        
        public final float getConfidence() {
            return 0.0F;
        }
        
        public final float getBestCostume() {
            return 0.0F;
        }
        
        public final float getBestNormal() {
            return 0.0F;
        }
        
        public final float getScoreGap() {
            return 0.0F;
        }
        
        public final int getCostumeCandidateCount() {
            return 0;
        }
        
        public final boolean getDenseVariantSpecies() {
            return false;
        }
        
        public final boolean component1() {
            return false;
        }
        
        public final float component2() {
            return 0.0F;
        }
        
        public final float component3() {
            return 0.0F;
        }
        
        public final float component4() {
            return 0.0F;
        }
        
        public final float component5() {
            return 0.0F;
        }
        
        public final int component6() {
            return 0;
        }
        
        public final boolean component7() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.CostumeSignatureStore.MatchDetails copy(boolean matched, float confidence, float bestCostume, float bestNormal, float scoreGap, int costumeCandidateCount, boolean denseVariantSpecies) {
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