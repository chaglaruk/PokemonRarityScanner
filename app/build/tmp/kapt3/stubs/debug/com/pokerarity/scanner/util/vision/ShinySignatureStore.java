package com.pokerarity.scanner.util.vision;

import android.content.Context;
import org.json.JSONObject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000p\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0007\n\u0002\b\r\n\u0002\u0010$\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u0014\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\n\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0003BCDB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u001a\u001a\u00020\b2\u0006\u0010\u001b\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001cH\u0002J\u0010\u0010\u001e\u001a\u00020\u001f2\u0006\u0010 \u001a\u00020!H\u0002J\u0018\u0010\"\u001a\u00020\u00192\u0006\u0010 \u001a\u00020!2\b\u0010#\u001a\u0004\u0018\u00010\u0004J\u0018\u0010$\u001a\u00020\b2\u0006\u0010%\u001a\u00020\u001c2\u0006\u0010&\u001a\u00020\bH\u0002J\u0017\u0010\'\u001a\u0004\u0018\u00010\b2\u0006\u0010%\u001a\u00020\u001cH\u0002\u00a2\u0006\u0002\u0010(J\u0018\u0010)\u001a\u00020\b2\u0006\u0010\u001b\u001a\u00020\b2\u0006\u0010\u001d\u001a\u00020\bH\u0002J>\u0010*\u001a\u000e\u0012\u0004\u0012\u00020\u0019\u0012\u0004\u0012\u00020\b0+2\u0006\u0010,\u001a\u00020-2\u0006\u0010.\u001a\u00020\u001c2\b\u0010/\u001a\u0004\u0018\u00010\u001c2\b\u0010#\u001a\u0004\u0018\u00010\u00042\u0006\u00100\u001a\u00020\u0019J,\u0010*\u001a\u000e\u0012\u0004\u0012\u00020\u0019\u0012\u0004\u0012\u00020\b0+2\u0006\u0010,\u001a\u00020-2\u0006\u0010.\u001a\u00020\u001c2\b\u0010#\u001a\u0004\u0018\u00010\u0004J\u0012\u00101\u001a\u00020\u001c2\b\u00102\u001a\u0004\u0018\u00010\u0001H\u0002J\u0012\u00103\u001a\u00020\u001c2\b\u00104\u001a\u0004\u0018\u000105H\u0002J \u00106\u001a\u0002072\u0006\u0010,\u001a\u00020-2\u0006\u0010.\u001a\u00020\u001c2\u0006\u00108\u001a\u000209H\u0002J:\u0010:\u001a\u0014\u0012\u0004\u0012\u000207\u0012\u0004\u0012\u000207\u0012\u0004\u0012\u00020\u00040;2\u0006\u0010<\u001a\u0002072\u0006\u0010=\u001a\u0002072\u0006\u0010>\u001a\u0002072\u0006\u0010?\u001a\u000207H\u0002J\u0014\u0010@\u001a\u00020\u0006*\u00020\u00062\u0006\u0010A\u001a\u00020\u0006H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0015\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00170\u0016X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0018\u001a\u00020\u0019X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006E"}, d2 = {"Lcom/pokerarity/scanner/util/vision/ShinySignatureStore;", "", "()V", "ASSET_PATH", "", "EDGE_BINS", "", "SHINY_COLOR_GAP", "", "SHINY_COLOR_GAP_SOFT", "SHINY_COLOR_GAP_STRICT", "SHINY_MARGIN", "SHINY_MAX_COLOR", "SHINY_MAX_COLOR_STRICT", "SHINY_MAX_TOTAL_RELATIVE", "SHINY_MAX_TOTAL_RELATIVE_STRICT", "SHINY_REF_HUE_GAP_SOFT", "SHINY_RELATIVE_MARGIN", "SHINY_RELATIVE_MARGIN_SOFT", "SHINY_RELATIVE_MARGIN_STRICT", "SHINY_SCORE_THRESHOLD", "bySpecies", "", "Lcom/pokerarity/scanner/util/vision/ShinySignatureStore$ShinyEntry;", "loaded", "", "colorDistance", "a", "", "b", "ensureLoaded", "", "context", "Landroid/content/Context;", "hasSpecies", "species", "histogramMassNearHue", "hist", "hue", "hueCentroid", "([F)Ljava/lang/Float;", "hueDistance", "matchSignature", "Lkotlin/Pair;", "signature", "Lcom/pokerarity/scanner/util/vision/SpriteSignature$Signature;", "colorHist", "altColorHist", "strictMode", "readColor", "colorObj", "readEdge", "edgeArr", "Lorg/json/JSONArray;", "scoreWithBreakdown", "Lcom/pokerarity/scanner/util/vision/ShinySignatureStore$ScoreParts;", "ref", "Lcom/pokerarity/scanner/util/vision/ShinySignatureStore$SignatureRef;", "selectBestScores", "Lkotlin/Triple;", "normalA", "shinyA", "normalB", "shinyB", "floorMod", "mod", "ScoreParts", "ShinyEntry", "SignatureRef", "app_debug"})
public final class ShinySignatureStore {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ASSET_PATH = "data/shiny_signatures.json";
    private static final float SHINY_SCORE_THRESHOLD = 0.38F;
    private static final float SHINY_MARGIN = 0.01F;
    private static final float SHINY_RELATIVE_MARGIN = 0.04F;
    private static final float SHINY_RELATIVE_MARGIN_SOFT = 0.01F;
    private static final float SHINY_COLOR_GAP = 0.08F;
    private static final float SHINY_COLOR_GAP_SOFT = 0.015F;
    private static final float SHINY_MAX_COLOR = 0.7F;
    private static final float SHINY_MAX_TOTAL_RELATIVE = 0.52F;
    private static final float SHINY_RELATIVE_MARGIN_STRICT = 0.08F;
    private static final float SHINY_COLOR_GAP_STRICT = 0.12F;
    private static final float SHINY_MAX_COLOR_STRICT = 0.68F;
    private static final float SHINY_MAX_TOTAL_RELATIVE_STRICT = 0.5F;
    private static final float SHINY_REF_HUE_GAP_SOFT = 45.0F;
    private static final int EDGE_BINS = 8;
    private static boolean loaded = false;
    @org.jetbrains.annotations.NotNull()
    private static java.util.Map<java.lang.String, com.pokerarity.scanner.util.vision.ShinySignatureStore.ShinyEntry> bySpecies;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.util.vision.ShinySignatureStore INSTANCE = null;
    
    private ShinySignatureStore() {
        super();
    }
    
    public final boolean hasSpecies(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.Nullable()
    java.lang.String species) {
        return false;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlin.Pair<java.lang.Boolean, java.lang.Float> matchSignature(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.util.vision.SpriteSignature.Signature signature, @org.jetbrains.annotations.NotNull()
    float[] colorHist, @org.jetbrains.annotations.Nullable()
    java.lang.String species) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlin.Pair<java.lang.Boolean, java.lang.Float> matchSignature(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.util.vision.SpriteSignature.Signature signature, @org.jetbrains.annotations.NotNull()
    float[] colorHist, @org.jetbrains.annotations.Nullable()
    float[] altColorHist, @org.jetbrains.annotations.Nullable()
    java.lang.String species, boolean strictMode) {
        return null;
    }
    
    private final void ensureLoaded(android.content.Context context) {
    }
    
    private final float[] readEdge(org.json.JSONArray edgeArr) {
        return null;
    }
    
    private final float[] readColor(java.lang.Object colorObj) {
        return null;
    }
    
    private final float colorDistance(float[] a, float[] b) {
        return 0.0F;
    }
    
    private final com.pokerarity.scanner.util.vision.ShinySignatureStore.ScoreParts scoreWithBreakdown(com.pokerarity.scanner.util.vision.SpriteSignature.Signature signature, float[] colorHist, com.pokerarity.scanner.util.vision.ShinySignatureStore.SignatureRef ref) {
        return null;
    }
    
    private final java.lang.Float hueCentroid(float[] hist) {
        return null;
    }
    
    private final float hueDistance(float a, float b) {
        return 0.0F;
    }
    
    private final float histogramMassNearHue(float[] hist, float hue) {
        return 0.0F;
    }
    
    private final int floorMod(int $this$floorMod, int mod) {
        return 0;
    }
    
    private final kotlin.Triple<com.pokerarity.scanner.util.vision.ShinySignatureStore.ScoreParts, com.pokerarity.scanner.util.vision.ShinySignatureStore.ScoreParts, java.lang.String> selectBestScores(com.pokerarity.scanner.util.vision.ShinySignatureStore.ScoreParts normalA, com.pokerarity.scanner.util.vision.ShinySignatureStore.ScoreParts shinyA, com.pokerarity.scanner.util.vision.ShinySignatureStore.ScoreParts normalB, com.pokerarity.scanner.util.vision.ShinySignatureStore.ScoreParts shinyB) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0012\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B-\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0003\u0012\u0006\u0010\u0007\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\bJ\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0012\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0013\u001a\u00020\u0003H\u00c6\u0003J;\u0010\u0014\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u00032\b\b\u0002\u0010\u0007\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\u0015\u001a\u00020\u00162\b\u0010\u0017\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0018\u001a\u00020\u0019H\u00d6\u0001J\t\u0010\u001a\u001a\u00020\u001bH\u00d6\u0001R\u0011\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\nR\u0011\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\nR\u0011\u0010\u0007\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\nR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\n\u00a8\u0006\u001c"}, d2 = {"Lcom/pokerarity/scanner/util/vision/ShinySignatureStore$ScoreParts;", "", "total", "", "color", "dh", "ah", "ed", "(FFFFF)V", "getAh", "()F", "getColor", "getDh", "getEd", "getTotal", "component1", "component2", "component3", "component4", "component5", "copy", "equals", "", "other", "hashCode", "", "toString", "", "app_debug"})
    public static final class ScoreParts {
        private final float total = 0.0F;
        private final float color = 0.0F;
        private final float dh = 0.0F;
        private final float ah = 0.0F;
        private final float ed = 0.0F;
        
        public ScoreParts(float total, float color, float dh, float ah, float ed) {
            super();
        }
        
        public final float getTotal() {
            return 0.0F;
        }
        
        public final float getColor() {
            return 0.0F;
        }
        
        public final float getDh() {
            return 0.0F;
        }
        
        public final float getAh() {
            return 0.0F;
        }
        
        public final float getEd() {
            return 0.0F;
        }
        
        public final float component1() {
            return 0.0F;
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
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.ShinySignatureStore.ScoreParts copy(float total, float color, float dh, float ah, float ed) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u000e\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B%\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\u0006\u0010\u0007\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\bJ\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0006H\u00c6\u0003J\t\u0010\u0012\u001a\u00020\u0006H\u00c6\u0003J1\u0010\u0013\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u0010\u0007\u001a\u00020\u0006H\u00c6\u0001J\u0013\u0010\u0014\u001a\u00020\u00152\b\u0010\u0016\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0017\u001a\u00020\u0018H\u00d6\u0001J\t\u0010\u0019\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0007\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\n\u00a8\u0006\u001a"}, d2 = {"Lcom/pokerarity/scanner/util/vision/ShinySignatureStore$ShinyEntry;", "", "species", "", "key", "normal", "Lcom/pokerarity/scanner/util/vision/ShinySignatureStore$SignatureRef;", "shiny", "(Ljava/lang/String;Ljava/lang/String;Lcom/pokerarity/scanner/util/vision/ShinySignatureStore$SignatureRef;Lcom/pokerarity/scanner/util/vision/ShinySignatureStore$SignatureRef;)V", "getKey", "()Ljava/lang/String;", "getNormal", "()Lcom/pokerarity/scanner/util/vision/ShinySignatureStore$SignatureRef;", "getShiny", "getSpecies", "component1", "component2", "component3", "component4", "copy", "equals", "", "other", "hashCode", "", "toString", "app_debug"})
    public static final class ShinyEntry {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String species = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String key = null;
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.util.vision.ShinySignatureStore.SignatureRef normal = null;
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.util.vision.ShinySignatureStore.SignatureRef shiny = null;
        
        public ShinyEntry(@org.jetbrains.annotations.NotNull()
        java.lang.String species, @org.jetbrains.annotations.NotNull()
        java.lang.String key, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.util.vision.ShinySignatureStore.SignatureRef normal, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.util.vision.ShinySignatureStore.SignatureRef shiny) {
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
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.ShinySignatureStore.SignatureRef getNormal() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.ShinySignatureStore.SignatureRef getShiny() {
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
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.ShinySignatureStore.SignatureRef component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.ShinySignatureStore.SignatureRef component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.ShinySignatureStore.ShinyEntry copy(@org.jetbrains.annotations.NotNull()
        java.lang.String species, @org.jetbrains.annotations.NotNull()
        java.lang.String key, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.util.vision.ShinySignatureStore.SignatureRef normal, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.util.vision.ShinySignatureStore.SignatureRef shiny) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0014\n\u0002\b\u000e\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B%\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\u0006\u0010\u0007\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\bJ\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0006H\u00c6\u0003J\t\u0010\u0012\u001a\u00020\u0006H\u00c6\u0003J1\u0010\u0013\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u0010\u0007\u001a\u00020\u0006H\u00c6\u0001J\u0013\u0010\u0014\u001a\u00020\u00152\b\u0010\u0016\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0017\u001a\u00020\u0018H\u00d6\u0001J\t\u0010\u0019\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0007\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\nR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\f\u00a8\u0006\u001a"}, d2 = {"Lcom/pokerarity/scanner/util/vision/ShinySignatureStore$SignatureRef;", "", "aHash", "", "dHash", "edge", "", "color", "(Ljava/lang/String;Ljava/lang/String;[F[F)V", "getAHash", "()Ljava/lang/String;", "getColor", "()[F", "getDHash", "getEdge", "component1", "component2", "component3", "component4", "copy", "equals", "", "other", "hashCode", "", "toString", "app_debug"})
    public static final class SignatureRef {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String aHash = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String dHash = null;
        @org.jetbrains.annotations.NotNull()
        private final float[] edge = null;
        @org.jetbrains.annotations.NotNull()
        private final float[] color = null;
        
        public SignatureRef(@org.jetbrains.annotations.NotNull()
        java.lang.String aHash, @org.jetbrains.annotations.NotNull()
        java.lang.String dHash, @org.jetbrains.annotations.NotNull()
        float[] edge, @org.jetbrains.annotations.NotNull()
        float[] color) {
            super();
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
        public final float[] getColor() {
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
        
        @org.jetbrains.annotations.NotNull()
        public final float[] component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final float[] component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.ShinySignatureStore.SignatureRef copy(@org.jetbrains.annotations.NotNull()
        java.lang.String aHash, @org.jetbrains.annotations.NotNull()
        java.lang.String dHash, @org.jetbrains.annotations.NotNull()
        float[] edge, @org.jetbrains.annotations.NotNull()
        float[] color) {
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