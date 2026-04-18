package com.pokerarity.scanner.util.vision;

import android.content.Context;
import android.graphics.Bitmap;
import com.pokerarity.scanner.data.model.VisualFeatures;
import com.pokerarity.scanner.data.repository.RarityManifestLoader;
import java.io.InputStreamReader;

/**
 * Detects visual features (Shiny, Shadow, Lucky, Costume) from a Pokemon GO screenshot
 * using native Android color analysis. No OpenCV dependency required.
 *
 * All analysis runs on a 360p downscaled bitmap for performance.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000l\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010 \n\u0002\b\f\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0014\n\u0002\b\t\n\u0002\u0010\b\n\u0002\b\t\n\u0002\u0010\u0006\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0007\u0018\u0000 H2\u00020\u0001:\u0003GHIB\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004JE\u0010\r\u001a\u00020\u000e2\u0012\u0010\u000f\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u00102\u0018\u0010\u0013\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u00100\u00142\b\u0010\u0015\u001a\u0004\u0018\u00010\u0007H\u0000\u00a2\u0006\u0002\b\u0016J\u008f\u0001\u0010\u0017\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u00102\u0006\u0010\u0018\u001a\u00020\u000e2\u0012\u0010\u0019\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u00102\u0012\u0010\u001a\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u00102\u0012\u0010\u001b\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u00102\u0012\u0010\u001c\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u00102\b\u0010\u0015\u001a\u0004\u0018\u00010\u00072\u0012\u0010\u001d\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u0010H\u0000\u00a2\u0006\u0002\b\u001eJ\u0010\u0010\u001f\u001a\u00020\u00072\u0006\u0010 \u001a\u00020!H\u0002J&\u0010\"\u001a\u00020#2\u0006\u0010 \u001a\u00020!2\n\b\u0002\u0010\u0015\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010$\u001a\u0004\u0018\u00010\u0007J$\u0010%\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u00102\u0006\u0010 \u001a\u00020!2\b\u0010\u0015\u001a\u0004\u0018\u00010\u0007J\u0018\u0010&\u001a\u00020\u00122\u0006\u0010\'\u001a\u00020(2\u0006\u0010)\u001a\u00020\u0012H\u0002J\u0018\u0010*\u001a\u00020\u00122\u0006\u0010+\u001a\u00020\u00122\u0006\u0010,\u001a\u00020\u0012H\u0002J\u001a\u0010-\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u00102\u0006\u0010 \u001a\u00020!J\u001a\u0010.\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u00102\u0006\u0010 \u001a\u00020!J\u001a\u0010/\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u00102\u0006\u0010 \u001a\u00020!J$\u00100\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u00102\u0006\u00101\u001a\u0002022\b\u0010\u0015\u001a\u0004\u0018\u00010\u0007J&\u00103\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u00102\u0006\u00104\u001a\u00020(2\b\u0010\u0015\u001a\u0004\u0018\u00010\u0007H\u0002J6\u00105\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00120\u00102\u0006\u00106\u001a\u00020\u00122\u0006\u00107\u001a\u00020\u00122\u0006\u00108\u001a\u00020\u00122\b\u0010\u0015\u001a\u0004\u0018\u00010\u0007H\u0002J\u0014\u00109\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\b0\u0006H\u0002J\u0014\u0010:\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\b0\u0006H\u0002J$\u0010;\u001a\u00020<2\f\u0010=\u001a\b\u0012\u0004\u0012\u0002020\u00142\f\u0010>\u001a\b\u0012\u0004\u0012\u0002020\u0014H\u0002J\u0016\u0010?\u001a\u00020\u00122\f\u0010@\u001a\b\u0012\u0004\u0012\u0002020\u0014H\u0002J!\u0010A\u001a\u00020\u00112\b\u0010B\u001a\u0004\u0018\u00010C2\b\u0010\u0015\u001a\u0004\u0018\u00010\u0007H\u0000\u00a2\u0006\u0002\bDJ\u0014\u0010E\u001a\u000202*\u0002022\u0006\u0010F\u001a\u000202H\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\'\u0010\u0005\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\b0\u00068BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u000b\u0010\f\u001a\u0004\b\t\u0010\n\u00a8\u0006J"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VisualFeatureDetector;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "pokemonColors", "", "", "Lcom/pokerarity/scanner/util/vision/VisualFeatureDetector$ColorReference;", "getPokemonColors", "()Ljava/util/Map;", "pokemonColors$delegate", "Lkotlin/Lazy;", "chooseBestShinySignatureResult", "Lcom/pokerarity/scanner/util/vision/VisualFeatureDetector$SignatureConsensus;", "primaryResult", "Lkotlin/Pair;", "", "", "extraResults", "", "pokemonName", "chooseBestShinySignatureResult$PokeRarityScanner_v1_8_2_debug", "chooseShinyResult", "signatureConsensus", "maskedColorResult", "rawColorResult", "hueResult", "histHueResult", "costumeResult", "chooseShinyResult$PokeRarityScanner_v1_8_2_debug", "computeHeadPHash", "bitmap", "Landroid/graphics/Bitmap;", "detect", "Lcom/pokerarity/scanner/data/model/VisualFeatures;", "sizeTag", "hasCostume", "histogramMassNearHue", "hist", "", "hue", "hueDistance", "h1", "h2", "isLocationCard", "isLucky", "isShadow", "isShinyByColor", "dominantColor", "", "isShinyByHistogramHue", "colorHist", "isShinyByObservedHue", "observedHue", "observedSat", "observedVal", "loadGeneratedPokemonColors", "loadPokemonColors", "rgbDistance", "", "a", "b", "rgbHue", "rgb", "shouldUseCostumeHeuristic", "signatureDetails", "Lcom/pokerarity/scanner/util/vision/CostumeSignatureStore$MatchDetails;", "shouldUseCostumeHeuristic$PokeRarityScanner_v1_8_2_debug", "floorMod", "mod", "ColorReference", "Companion", "SignatureConsensus", "PokeRarityScanner-v1.8.2_debug"})
public final class VisualFeatureDetector {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String GENERATED_COLORS_PATH = "data/pokemon_colors_generated.json";
    private static final float MIN_COSTUME_CONFIDENCE = 0.2F;
    private static final float BORDERLINE_COSTUME_CONFIDENCE = 0.24F;
    private static final float MIN_HEURISTIC_ONLY_COSTUME_CONFIDENCE = 0.65F;
    
    /**
     * Shadow Pokemon: purple aura around sprite
     */
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.ranges.IntRange SHADOW_HUE_RANGE = null;
    private static final float SHADOW_MIN_SATURATION = 0.4F;
    private static final float SHADOW_MIN_VALUE = 0.3F;
    private static final float SHADOW_THRESHOLD = 0.05F;
    
    /**
     * Lucky Pokemon: golden/yellow background
     */
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.ranges.IntRange LUCKY_HUE_RANGE = null;
    private static final float LUCKY_MIN_SATURATION = 0.6F;
    private static final float LUCKY_MIN_VALUE = 0.7F;
    private static final float LUCKY_THRESHOLD = 0.15F;
    
    /**
     * RGB Euclidean distance threshold for shiny match
     */
    private static final double SHINY_COLOR_DIST_THRESHOLD = 50.0;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy pokemonColors$delegate = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.util.vision.VisualFeatureDetector.Companion Companion = null;
    
    public VisualFeatureDetector(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    private final java.util.Map<java.lang.String, com.pokerarity.scanner.util.vision.VisualFeatureDetector.ColorReference> getPokemonColors() {
        return null;
    }
    
    /**
     * Run all detections and return combined results with a confidence score.
     */
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.VisualFeatures detect(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.Nullable()
    java.lang.String pokemonName, @org.jetbrains.annotations.Nullable()
    java.lang.String sizeTag) {
        return null;
    }
    
    /**
     * Detect special background location cards (e.g. GO Fest, City backgrounds).
     * These have distinct non-standard colors in the background region.
     */
    @org.jetbrains.annotations.NotNull()
    public final kotlin.Pair<java.lang.Boolean, java.lang.Float> isLocationCard(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap) {
        return null;
    }
    
    /**
     * Detect if the Pokemon is shiny by comparing the extracted dominant RGB
     * against the known reference values using Euclidean Distance.
     *
     * @return Pair(isShiny, confidence)
     */
    @org.jetbrains.annotations.NotNull()
    public final kotlin.Pair<java.lang.Boolean, java.lang.Float> isShinyByColor(int dominantColor, @org.jetbrains.annotations.Nullable()
    java.lang.String pokemonName) {
        return null;
    }
    
    /**
     * Detect shadow Pokemon by looking for a purple aura around the sprite.
     * Shadow Pokemon have a distinctive purple haze (HSV: H=260-280) around
     * the border of their sprite.
     *
     * @return Pair(isShadow, confidence)
     */
    @org.jetbrains.annotations.NotNull()
    public final kotlin.Pair<java.lang.Boolean, java.lang.Float> isShadow(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap) {
        return null;
    }
    
    /**
     * Detect lucky Pokemon by looking for a golden/yellow background.
     * Lucky Pokemon have a distinctive golden sparkle background
     * (HSV: H=45-65, S=60-100%, V=70-100%).
     *
     * @return Pair(isLucky, confidence)
     */
    @org.jetbrains.annotations.NotNull()
    public final kotlin.Pair<java.lang.Boolean, java.lang.Float> isLucky(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap) {
        return null;
    }
    
    /**
     * Costume detection based on color deviation in the head region.
     * Ash hat (Red/White), Party hats, etc. usually deviate from species normal color.
     */
    @org.jetbrains.annotations.NotNull()
    public final kotlin.Pair<java.lang.Boolean, java.lang.Float> hasCostume(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.Nullable()
    java.lang.String pokemonName) {
        return null;
    }
    
    /**
     * Calculate the shortest distance between two hues on the color wheel (0-360).
     */
    private final java.lang.String computeHeadPHash(android.graphics.Bitmap bitmap) {
        return null;
    }
    
    private final float hueDistance(float h1, float h2) {
        return 0.0F;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlin.Pair<java.lang.Boolean, java.lang.Float> chooseShinyResult$PokeRarityScanner_v1_8_2_debug(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.util.vision.VisualFeatureDetector.SignatureConsensus signatureConsensus, @org.jetbrains.annotations.NotNull()
    kotlin.Pair<java.lang.Boolean, java.lang.Float> maskedColorResult, @org.jetbrains.annotations.NotNull()
    kotlin.Pair<java.lang.Boolean, java.lang.Float> rawColorResult, @org.jetbrains.annotations.NotNull()
    kotlin.Pair<java.lang.Boolean, java.lang.Float> hueResult, @org.jetbrains.annotations.NotNull()
    kotlin.Pair<java.lang.Boolean, java.lang.Float> histHueResult, @org.jetbrains.annotations.Nullable()
    java.lang.String pokemonName, @org.jetbrains.annotations.NotNull()
    kotlin.Pair<java.lang.Boolean, java.lang.Float> costumeResult) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.vision.VisualFeatureDetector.SignatureConsensus chooseBestShinySignatureResult$PokeRarityScanner_v1_8_2_debug(@org.jetbrains.annotations.NotNull()
    kotlin.Pair<java.lang.Boolean, java.lang.Float> primaryResult, @org.jetbrains.annotations.NotNull()
    java.util.List<kotlin.Pair<java.lang.Boolean, java.lang.Float>> extraResults, @org.jetbrains.annotations.Nullable()
    java.lang.String pokemonName) {
        return null;
    }
    
    public final boolean shouldUseCostumeHeuristic$PokeRarityScanner_v1_8_2_debug(@org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.util.vision.CostumeSignatureStore.MatchDetails signatureDetails, @org.jetbrains.annotations.Nullable()
    java.lang.String pokemonName) {
        return false;
    }
    
    private final kotlin.Pair<java.lang.Boolean, java.lang.Float> isShinyByObservedHue(float observedHue, float observedSat, float observedVal, java.lang.String pokemonName) {
        return null;
    }
    
    private final kotlin.Pair<java.lang.Boolean, java.lang.Float> isShinyByHistogramHue(float[] colorHist, java.lang.String pokemonName) {
        return null;
    }
    
    private final float rgbHue(java.util.List<java.lang.Integer> rgb) {
        return 0.0F;
    }
    
    private final double rgbDistance(java.util.List<java.lang.Integer> a, java.util.List<java.lang.Integer> b) {
        return 0.0;
    }
    
    private final float histogramMassNearHue(float[] hist, float hue) {
        return 0.0F;
    }
    
    private final int floorMod(int $this$floorMod, int mod) {
        return 0;
    }
    
    /**
     * Load pokemon colors from rarity_manifest.json.
     */
    private final java.util.Map<java.lang.String, com.pokerarity.scanner.util.vision.VisualFeatureDetector.ColorReference> loadPokemonColors() {
        return null;
    }
    
    private final java.util.Map<java.lang.String, com.pokerarity.scanner.util.vision.VisualFeatureDetector.ColorReference> loadGeneratedPokemonColors() {
        return null;
    }
    
    /**
     * Reference color data for normal vs shiny forms.
     */
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0010\b\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B!\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u0012\f\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\u0002\u0010\u0006J\u000f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00c6\u0003J\u000f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00c6\u0003J)\u0010\f\u001a\u00020\u00002\u000e\b\u0002\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\u000e\b\u0002\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00c6\u0001J\u0013\u0010\r\u001a\u00020\u000e2\b\u0010\u000f\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0010\u001a\u00020\u0004H\u00d6\u0001J\t\u0010\u0011\u001a\u00020\u0012H\u00d6\u0001R\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0017\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\b\u00a8\u0006\u0013"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VisualFeatureDetector$ColorReference;", "", "normal", "", "", "shiny", "(Ljava/util/List;Ljava/util/List;)V", "getNormal", "()Ljava/util/List;", "getShiny", "component1", "component2", "copy", "equals", "", "other", "hashCode", "toString", "", "PokeRarityScanner-v1.8.2_debug"})
    public static final class ColorReference {
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<java.lang.Integer> normal = null;
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<java.lang.Integer> shiny = null;
        
        public ColorReference(@org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Integer> normal, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Integer> shiny) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.Integer> getNormal() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.Integer> getShiny() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.Integer> component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.Integer> component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.VisualFeatureDetector.ColorReference copy(@org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Integer> normal, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Integer> shiny) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0010\u0006\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0014"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VisualFeatureDetector$Companion;", "", "()V", "BORDERLINE_COSTUME_CONFIDENCE", "", "GENERATED_COLORS_PATH", "", "LUCKY_HUE_RANGE", "Lkotlin/ranges/IntRange;", "LUCKY_MIN_SATURATION", "LUCKY_MIN_VALUE", "LUCKY_THRESHOLD", "MIN_COSTUME_CONFIDENCE", "MIN_HEURISTIC_ONLY_COSTUME_CONFIDENCE", "SHADOW_HUE_RANGE", "SHADOW_MIN_SATURATION", "SHADOW_MIN_VALUE", "SHADOW_THRESHOLD", "SHINY_COLOR_DIST_THRESHOLD", "", "PokeRarityScanner-v1.8.2_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\b\n\u0002\b\u0010\n\u0002\u0010\u000e\n\u0000\b\u0080\b\u0018\u00002\u00020\u0001B)\u0012\u0012\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0004\u00a2\u0006\u0002\u0010\tJ\u0015\u0010\u0010\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0003H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0007H\u00c6\u0003J\t\u0010\u0012\u001a\u00020\u0004H\u00c6\u0003J3\u0010\u0013\u001a\u00020\u00002\u0014\b\u0002\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u0004H\u00c6\u0001J\u0013\u0010\u0014\u001a\u00020\u00042\b\u0010\u0015\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0016\u001a\u00020\u0007H\u00d6\u0001J\t\u0010\u0017\u001a\u00020\u0018H\u00d6\u0001R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\b\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u001d\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0019"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VisualFeatureDetector$SignatureConsensus;", "", "result", "Lkotlin/Pair;", "", "", "matchedCount", "", "primaryMatched", "(Lkotlin/Pair;IZ)V", "getMatchedCount", "()I", "getPrimaryMatched", "()Z", "getResult", "()Lkotlin/Pair;", "component1", "component2", "component3", "copy", "equals", "other", "hashCode", "toString", "", "PokeRarityScanner-v1.8.2_debug"})
    public static final class SignatureConsensus {
        @org.jetbrains.annotations.NotNull()
        private final kotlin.Pair<java.lang.Boolean, java.lang.Float> result = null;
        private final int matchedCount = 0;
        private final boolean primaryMatched = false;
        
        public SignatureConsensus(@org.jetbrains.annotations.NotNull()
        kotlin.Pair<java.lang.Boolean, java.lang.Float> result, int matchedCount, boolean primaryMatched) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final kotlin.Pair<java.lang.Boolean, java.lang.Float> getResult() {
            return null;
        }
        
        public final int getMatchedCount() {
            return 0;
        }
        
        public final boolean getPrimaryMatched() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final kotlin.Pair<java.lang.Boolean, java.lang.Float> component1() {
            return null;
        }
        
        public final int component2() {
            return 0;
        }
        
        public final boolean component3() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.VisualFeatureDetector.SignatureConsensus copy(@org.jetbrains.annotations.NotNull()
        kotlin.Pair<java.lang.Boolean, java.lang.Float> result, int matchedCount, boolean primaryMatched) {
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