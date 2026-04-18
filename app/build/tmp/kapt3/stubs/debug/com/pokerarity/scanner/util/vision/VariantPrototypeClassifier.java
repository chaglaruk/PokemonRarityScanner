package com.pokerarity.scanner.util.vision;

import android.content.Context;
import android.graphics.Bitmap;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000J\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u001e\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\u0014\n\u0002\b\t\u0018\u00002\u00020\u0001:\u0002\u001f B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J \u0010\u0005\u001a\u0004\u0018\u00010\u00062\u0006\u0010\u0007\u001a\u00020\b2\u000e\b\u0002\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nJ(\u0010\f\u001a\u0004\u0018\u00010\u00062\u0006\u0010\u0007\u001a\u00020\b2\f\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000f0\u000e2\u0006\u0010\u0010\u001a\u00020\u000bH\u0002J\u001a\u0010\u0011\u001a\u0004\u0018\u00010\u00062\u0006\u0010\u0007\u001a\u00020\b2\b\u0010\u0012\u001a\u0004\u0018\u00010\u000bJ\u0010\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0007\u001a\u00020\bH\u0002J\u001e\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u00182\f\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u00160\u000eH\u0002J\u0010\u0010\u001a\u001a\u00020\u000b2\u0006\u0010\u001b\u001a\u00020\u000bH\u0002J\u0018\u0010\u001c\u001a\u00020\u00162\u0006\u0010\u001d\u001a\u00020\u00142\u0006\u0010\u001e\u001a\u00020\u000fH\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006!"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "classify", "Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;", "bitmap", "Landroid/graphics/Bitmap;", "candidateSpecies", "", "", "classifyEntries", "candidates", "", "Lcom/pokerarity/scanner/util/vision/VariantPrototypeStore$Entry;", "scope", "classifyForSpecies", "species", "extractFeatures", "Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$ScreenshotFeatures;", "histogramDistance", "", "observed", "", "reference", "normalizeVariantKey", "assetKey", "score", "features", "entry", "MatchResult", "ScreenshotFeatures", "PokeRarityScanner-v1.8.2_debug"})
public final class VariantPrototypeClassifier {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    
    public VariantPrototypeClassifier(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult classify(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.NotNull()
    java.util.Collection<java.lang.String> candidateSpecies) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult classifyForSpecies(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.Nullable()
    java.lang.String species) {
        return null;
    }
    
    private final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult classifyEntries(android.graphics.Bitmap bitmap, java.util.List<com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry> candidates, java.lang.String scope) {
        return null;
    }
    
    private final java.lang.String normalizeVariantKey(java.lang.String assetKey) {
        return null;
    }
    
    private final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.ScreenshotFeatures extractFeatures(android.graphics.Bitmap bitmap) {
        return null;
    }
    
    private final float score(com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.ScreenshotFeatures features, com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry entry) {
        return 0.0F;
    }
    
    private final float histogramDistance(float[] observed, java.util.List<java.lang.Float> reference) {
        return 0.0F;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0002\b\u0015\n\u0002\u0010 \n\u0002\bD\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u00b3\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0003\u0012\u0006\u0010\u0007\u001a\u00020\b\u0012\u0006\u0010\t\u001a\u00020\b\u0012\u0006\u0010\n\u001a\u00020\u0003\u0012\u0006\u0010\u000b\u001a\u00020\f\u0012\u0006\u0010\r\u001a\u00020\f\u0012\u0006\u0010\u000e\u001a\u00020\f\u0012\u0006\u0010\u000f\u001a\u00020\f\u0012\n\b\u0002\u0010\u0010\u001a\u0004\u0018\u00010\f\u0012\n\b\u0002\u0010\u0011\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u0012\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u0013\u001a\u0004\u0018\u00010\f\u0012\n\b\u0002\u0010\u0014\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u0015\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u0016\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u0017\u001a\u0004\u0018\u00010\u0003\u0012\b\b\u0002\u0010\u0018\u001a\u00020\b\u0012\b\b\u0002\u0010\u0019\u001a\u00020\b\u0012\n\b\u0002\u0010\u001a\u001a\u0004\u0018\u00010\f\u0012\n\b\u0002\u0010\u001b\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u001c\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u001d\u001a\u0004\u0018\u00010\f\u0012\n\b\u0002\u0010\u001e\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u001f\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010 \u001a\u0004\u0018\u00010\u0003\u0012\f\u0010!\u001a\b\u0012\u0004\u0012\u00020\u00030\"\u00a2\u0006\u0002\u0010#J\t\u0010E\u001a\u00020\u0003H\u00c6\u0003J\t\u0010F\u001a\u00020\fH\u00c6\u0003J\t\u0010G\u001a\u00020\fH\u00c6\u0003J\u0010\u0010H\u001a\u0004\u0018\u00010\fH\u00c6\u0003\u00a2\u0006\u0002\u0010(J\u000b\u0010I\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010J\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0010\u0010K\u001a\u0004\u0018\u00010\fH\u00c6\u0003\u00a2\u0006\u0002\u0010(J\u000b\u0010L\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010M\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010N\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010O\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\t\u0010P\u001a\u00020\u0003H\u00c6\u0003J\t\u0010Q\u001a\u00020\bH\u00c6\u0003J\t\u0010R\u001a\u00020\bH\u00c6\u0003J\u0010\u0010S\u001a\u0004\u0018\u00010\fH\u00c6\u0003\u00a2\u0006\u0002\u0010(J\u000b\u0010T\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010U\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0010\u0010V\u001a\u0004\u0018\u00010\fH\u00c6\u0003\u00a2\u0006\u0002\u0010(J\u000b\u0010W\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010X\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010Y\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000f\u0010Z\u001a\b\u0012\u0004\u0012\u00020\u00030\"H\u00c6\u0003J\t\u0010[\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\\\u001a\u00020\u0003H\u00c6\u0003J\t\u0010]\u001a\u00020\bH\u00c6\u0003J\t\u0010^\u001a\u00020\bH\u00c6\u0003J\t\u0010_\u001a\u00020\u0003H\u00c6\u0003J\t\u0010`\u001a\u00020\fH\u00c6\u0003J\t\u0010a\u001a\u00020\fH\u00c6\u0003J\u00d4\u0002\u0010b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u00032\b\b\u0002\u0010\u0007\u001a\u00020\b2\b\b\u0002\u0010\t\u001a\u00020\b2\b\b\u0002\u0010\n\u001a\u00020\u00032\b\b\u0002\u0010\u000b\u001a\u00020\f2\b\b\u0002\u0010\r\u001a\u00020\f2\b\b\u0002\u0010\u000e\u001a\u00020\f2\b\b\u0002\u0010\u000f\u001a\u00020\f2\n\b\u0002\u0010\u0010\u001a\u0004\u0018\u00010\f2\n\b\u0002\u0010\u0011\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0012\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0013\u001a\u0004\u0018\u00010\f2\n\b\u0002\u0010\u0014\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0015\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0016\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0017\u001a\u0004\u0018\u00010\u00032\b\b\u0002\u0010\u0018\u001a\u00020\b2\b\b\u0002\u0010\u0019\u001a\u00020\b2\n\b\u0002\u0010\u001a\u001a\u0004\u0018\u00010\f2\n\b\u0002\u0010\u001b\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u001c\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u001d\u001a\u0004\u0018\u00010\f2\n\b\u0002\u0010\u001e\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u001f\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010 \u001a\u0004\u0018\u00010\u00032\u000e\b\u0002\u0010!\u001a\b\u0012\u0004\u0012\u00020\u00030\"H\u00c6\u0001\u00a2\u0006\u0002\u0010cJ\u0013\u0010d\u001a\u00020\b2\b\u0010e\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010f\u001a\u00020gH\u00d6\u0001J\t\u0010h\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b$\u0010%R\u0013\u0010\u0011\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b&\u0010%R\u0015\u0010\u0010\u001a\u0004\u0018\u00010\f\u00a2\u0006\n\n\u0002\u0010)\u001a\u0004\b\'\u0010(R\u0013\u0010\u001e\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b*\u0010%R\u0015\u0010\u001d\u001a\u0004\u0018\u00010\f\u00a2\u0006\n\n\u0002\u0010)\u001a\u0004\b+\u0010(R\u0013\u0010\u001f\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b,\u0010%R\u0013\u0010\u0012\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b-\u0010%R\u0013\u0010\u0015\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b.\u0010%R\u0011\u0010\u0019\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b/\u00100R\u0011\u0010\u0018\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b1\u00100R\u0015\u0010\u0013\u001a\u0004\u0018\u00010\f\u00a2\u0006\n\n\u0002\u0010)\u001a\u0004\b2\u0010(R\u0013\u0010\u0014\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b3\u0010%R\u0013\u0010\u0016\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b4\u0010%R\u0013\u0010\u0017\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b5\u0010%R\u0013\u0010\u001b\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b6\u0010%R\u0015\u0010\u001a\u001a\u0004\u0018\u00010\f\u00a2\u0006\n\n\u0002\u0010)\u001a\u0004\b7\u0010(R\u0013\u0010\u001c\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b8\u0010%R\u0011\u0010\r\u001a\u00020\f\u00a2\u0006\b\n\u0000\u001a\u0004\b9\u0010:R\u0011\u0010\t\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u00100R\u0011\u0010\u0007\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u00100R\u0013\u0010 \u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b;\u0010%R\u0011\u0010\n\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b<\u0010%R\u0011\u0010\u000b\u001a\u00020\f\u00a2\u0006\b\n\u0000\u001a\u0004\b=\u0010:R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b>\u0010%R\u0011\u0010\u000e\u001a\u00020\f\u00a2\u0006\b\n\u0000\u001a\u0004\b?\u0010:R\u0011\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b@\u0010%R\u0017\u0010!\u001a\b\u0012\u0004\u0012\u00020\u00030\"\u00a2\u0006\b\n\u0000\u001a\u0004\bA\u0010BR\u0011\u0010\u000f\u001a\u00020\f\u00a2\u0006\b\n\u0000\u001a\u0004\bC\u0010:R\u0011\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\bD\u0010%\u00a8\u0006i"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;", "", "species", "", "assetKey", "spriteKey", "variantType", "isShiny", "", "isCostumeLike", "scope", "score", "", "confidence", "speciesMargin", "variantMargin", "bestBaseScore", "bestBaseAssetKey", "bestBaseSpriteKey", "bestNonBaseScore", "bestNonBaseSpecies", "bestNonBaseAssetKey", "bestNonBaseSpriteKey", "bestNonBaseVariantType", "bestNonBaseIsShiny", "bestNonBaseIsCostumeLike", "bestShinyPeerScore", "bestShinyPeerAssetKey", "bestShinyPeerSpriteKey", "bestBaseShinyPeerScore", "bestBaseShinyPeerAssetKey", "bestBaseShinyPeerSpriteKey", "rescueKind", "topSpecies", "", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZLjava/lang/String;FFFFLjava/lang/Float;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Float;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZLjava/lang/Float;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Float;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;)V", "getAssetKey", "()Ljava/lang/String;", "getBestBaseAssetKey", "getBestBaseScore", "()Ljava/lang/Float;", "Ljava/lang/Float;", "getBestBaseShinyPeerAssetKey", "getBestBaseShinyPeerScore", "getBestBaseShinyPeerSpriteKey", "getBestBaseSpriteKey", "getBestNonBaseAssetKey", "getBestNonBaseIsCostumeLike", "()Z", "getBestNonBaseIsShiny", "getBestNonBaseScore", "getBestNonBaseSpecies", "getBestNonBaseSpriteKey", "getBestNonBaseVariantType", "getBestShinyPeerAssetKey", "getBestShinyPeerScore", "getBestShinyPeerSpriteKey", "getConfidence", "()F", "getRescueKind", "getScope", "getScore", "getSpecies", "getSpeciesMargin", "getSpriteKey", "getTopSpecies", "()Ljava/util/List;", "getVariantMargin", "getVariantType", "component1", "component10", "component11", "component12", "component13", "component14", "component15", "component16", "component17", "component18", "component19", "component2", "component20", "component21", "component22", "component23", "component24", "component25", "component26", "component27", "component28", "component29", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZLjava/lang/String;FFFFLjava/lang/Float;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Float;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZLjava/lang/Float;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Float;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;)Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;", "equals", "other", "hashCode", "", "toString", "PokeRarityScanner-v1.8.2_debug"})
    public static final class MatchResult {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String species = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String assetKey = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String spriteKey = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String variantType = null;
        private final boolean isShiny = false;
        private final boolean isCostumeLike = false;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String scope = null;
        private final float score = 0.0F;
        private final float confidence = 0.0F;
        private final float speciesMargin = 0.0F;
        private final float variantMargin = 0.0F;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Float bestBaseScore = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String bestBaseAssetKey = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String bestBaseSpriteKey = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Float bestNonBaseScore = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String bestNonBaseSpecies = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String bestNonBaseAssetKey = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String bestNonBaseSpriteKey = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String bestNonBaseVariantType = null;
        private final boolean bestNonBaseIsShiny = false;
        private final boolean bestNonBaseIsCostumeLike = false;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Float bestShinyPeerScore = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String bestShinyPeerAssetKey = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String bestShinyPeerSpriteKey = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Float bestBaseShinyPeerScore = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String bestBaseShinyPeerAssetKey = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String bestBaseShinyPeerSpriteKey = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String rescueKind = null;
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<java.lang.String> topSpecies = null;
        
        public MatchResult(@org.jetbrains.annotations.NotNull()
        java.lang.String species, @org.jetbrains.annotations.NotNull()
        java.lang.String assetKey, @org.jetbrains.annotations.NotNull()
        java.lang.String spriteKey, @org.jetbrains.annotations.NotNull()
        java.lang.String variantType, boolean isShiny, boolean isCostumeLike, @org.jetbrains.annotations.NotNull()
        java.lang.String scope, float score, float confidence, float speciesMargin, float variantMargin, @org.jetbrains.annotations.Nullable()
        java.lang.Float bestBaseScore, @org.jetbrains.annotations.Nullable()
        java.lang.String bestBaseAssetKey, @org.jetbrains.annotations.Nullable()
        java.lang.String bestBaseSpriteKey, @org.jetbrains.annotations.Nullable()
        java.lang.Float bestNonBaseScore, @org.jetbrains.annotations.Nullable()
        java.lang.String bestNonBaseSpecies, @org.jetbrains.annotations.Nullable()
        java.lang.String bestNonBaseAssetKey, @org.jetbrains.annotations.Nullable()
        java.lang.String bestNonBaseSpriteKey, @org.jetbrains.annotations.Nullable()
        java.lang.String bestNonBaseVariantType, boolean bestNonBaseIsShiny, boolean bestNonBaseIsCostumeLike, @org.jetbrains.annotations.Nullable()
        java.lang.Float bestShinyPeerScore, @org.jetbrains.annotations.Nullable()
        java.lang.String bestShinyPeerAssetKey, @org.jetbrains.annotations.Nullable()
        java.lang.String bestShinyPeerSpriteKey, @org.jetbrains.annotations.Nullable()
        java.lang.Float bestBaseShinyPeerScore, @org.jetbrains.annotations.Nullable()
        java.lang.String bestBaseShinyPeerAssetKey, @org.jetbrains.annotations.Nullable()
        java.lang.String bestBaseShinyPeerSpriteKey, @org.jetbrains.annotations.Nullable()
        java.lang.String rescueKind, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.String> topSpecies) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getSpecies() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getAssetKey() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getSpriteKey() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getVariantType() {
            return null;
        }
        
        public final boolean isShiny() {
            return false;
        }
        
        public final boolean isCostumeLike() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getScope() {
            return null;
        }
        
        public final float getScore() {
            return 0.0F;
        }
        
        public final float getConfidence() {
            return 0.0F;
        }
        
        public final float getSpeciesMargin() {
            return 0.0F;
        }
        
        public final float getVariantMargin() {
            return 0.0F;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Float getBestBaseScore() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getBestBaseAssetKey() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getBestBaseSpriteKey() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Float getBestNonBaseScore() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getBestNonBaseSpecies() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getBestNonBaseAssetKey() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getBestNonBaseSpriteKey() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getBestNonBaseVariantType() {
            return null;
        }
        
        public final boolean getBestNonBaseIsShiny() {
            return false;
        }
        
        public final boolean getBestNonBaseIsCostumeLike() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Float getBestShinyPeerScore() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getBestShinyPeerAssetKey() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getBestShinyPeerSpriteKey() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Float getBestBaseShinyPeerScore() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getBestBaseShinyPeerAssetKey() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getBestBaseShinyPeerSpriteKey() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getRescueKind() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.String> getTopSpecies() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        public final float component10() {
            return 0.0F;
        }
        
        public final float component11() {
            return 0.0F;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Float component12() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component13() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component14() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Float component15() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component16() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component17() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component18() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component19() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        public final boolean component20() {
            return false;
        }
        
        public final boolean component21() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Float component22() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component23() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component24() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Float component25() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component26() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component27() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component28() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.String> component29() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component4() {
            return null;
        }
        
        public final boolean component5() {
            return false;
        }
        
        public final boolean component6() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component7() {
            return null;
        }
        
        public final float component8() {
            return 0.0F;
        }
        
        public final float component9() {
            return 0.0F;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult copy(@org.jetbrains.annotations.NotNull()
        java.lang.String species, @org.jetbrains.annotations.NotNull()
        java.lang.String assetKey, @org.jetbrains.annotations.NotNull()
        java.lang.String spriteKey, @org.jetbrains.annotations.NotNull()
        java.lang.String variantType, boolean isShiny, boolean isCostumeLike, @org.jetbrains.annotations.NotNull()
        java.lang.String scope, float score, float confidence, float speciesMargin, float variantMargin, @org.jetbrains.annotations.Nullable()
        java.lang.Float bestBaseScore, @org.jetbrains.annotations.Nullable()
        java.lang.String bestBaseAssetKey, @org.jetbrains.annotations.Nullable()
        java.lang.String bestBaseSpriteKey, @org.jetbrains.annotations.Nullable()
        java.lang.Float bestNonBaseScore, @org.jetbrains.annotations.Nullable()
        java.lang.String bestNonBaseSpecies, @org.jetbrains.annotations.Nullable()
        java.lang.String bestNonBaseAssetKey, @org.jetbrains.annotations.Nullable()
        java.lang.String bestNonBaseSpriteKey, @org.jetbrains.annotations.Nullable()
        java.lang.String bestNonBaseVariantType, boolean bestNonBaseIsShiny, boolean bestNonBaseIsCostumeLike, @org.jetbrains.annotations.Nullable()
        java.lang.Float bestShinyPeerScore, @org.jetbrains.annotations.Nullable()
        java.lang.String bestShinyPeerAssetKey, @org.jetbrains.annotations.Nullable()
        java.lang.String bestShinyPeerSpriteKey, @org.jetbrains.annotations.Nullable()
        java.lang.Float bestBaseShinyPeerScore, @org.jetbrains.annotations.Nullable()
        java.lang.String bestBaseShinyPeerAssetKey, @org.jetbrains.annotations.Nullable()
        java.lang.String bestBaseShinyPeerSpriteKey, @org.jetbrains.annotations.Nullable()
        java.lang.String rescueKind, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.String> topSpecies) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0014\n\u0002\b\u0004\n\u0002\u0010\u0007\n\u0002\b\u0015\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0082\b\u0018\u00002\u00020\u0001B=\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\u0006\u0010\u0007\u001a\u00020\u0005\u0012\u0006\u0010\b\u001a\u00020\u0005\u0012\u0006\u0010\t\u001a\u00020\n\u0012\u0006\u0010\u000b\u001a\u00020\n\u00a2\u0006\u0002\u0010\fJ\t\u0010\u0017\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0018\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0019\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001a\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001b\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001c\u001a\u00020\nH\u00c6\u0003J\t\u0010\u001d\u001a\u00020\nH\u00c6\u0003JO\u0010\u001e\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00052\b\b\u0002\u0010\u0007\u001a\u00020\u00052\b\b\u0002\u0010\b\u001a\u00020\u00052\b\b\u0002\u0010\t\u001a\u00020\n2\b\b\u0002\u0010\u000b\u001a\u00020\nH\u00c6\u0001J\u0013\u0010\u001f\u001a\u00020 2\b\u0010!\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\"\u001a\u00020#H\u00d6\u0001J\t\u0010$\u001a\u00020%H\u00d6\u0001R\u0011\u0010\u000b\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\b\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u000eR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0010R\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0010R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0011\u0010\u0007\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0010\u00a8\u0006&"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$ScreenshotFeatures;", "", "signature", "Lcom/pokerarity/scanner/util/vision/SpriteSignature$Signature;", "fullHist", "", "headHist", "upperHist", "bodyHist", "foregroundRatio", "", "aspectRatio", "(Lcom/pokerarity/scanner/util/vision/SpriteSignature$Signature;[F[F[F[FFF)V", "getAspectRatio", "()F", "getBodyHist", "()[F", "getForegroundRatio", "getFullHist", "getHeadHist", "getSignature", "()Lcom/pokerarity/scanner/util/vision/SpriteSignature$Signature;", "getUpperHist", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "copy", "equals", "", "other", "hashCode", "", "toString", "", "PokeRarityScanner-v1.8.2_debug"})
    static final class ScreenshotFeatures {
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.util.vision.SpriteSignature.Signature signature = null;
        @org.jetbrains.annotations.NotNull()
        private final float[] fullHist = null;
        @org.jetbrains.annotations.NotNull()
        private final float[] headHist = null;
        @org.jetbrains.annotations.NotNull()
        private final float[] upperHist = null;
        @org.jetbrains.annotations.NotNull()
        private final float[] bodyHist = null;
        private final float foregroundRatio = 0.0F;
        private final float aspectRatio = 0.0F;
        
        public ScreenshotFeatures(@org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.util.vision.SpriteSignature.Signature signature, @org.jetbrains.annotations.NotNull()
        float[] fullHist, @org.jetbrains.annotations.NotNull()
        float[] headHist, @org.jetbrains.annotations.NotNull()
        float[] upperHist, @org.jetbrains.annotations.NotNull()
        float[] bodyHist, float foregroundRatio, float aspectRatio) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.SpriteSignature.Signature getSignature() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final float[] getFullHist() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final float[] getHeadHist() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final float[] getUpperHist() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final float[] getBodyHist() {
            return null;
        }
        
        public final float getForegroundRatio() {
            return 0.0F;
        }
        
        public final float getAspectRatio() {
            return 0.0F;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.SpriteSignature.Signature component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final float[] component2() {
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
        public final float[] component5() {
            return null;
        }
        
        public final float component6() {
            return 0.0F;
        }
        
        public final float component7() {
            return 0.0F;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.ScreenshotFeatures copy(@org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.util.vision.SpriteSignature.Signature signature, @org.jetbrains.annotations.NotNull()
        float[] fullHist, @org.jetbrains.annotations.NotNull()
        float[] headHist, @org.jetbrains.annotations.NotNull()
        float[] upperHist, @org.jetbrains.annotations.NotNull()
        float[] bodyHist, float foregroundRatio, float aspectRatio) {
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