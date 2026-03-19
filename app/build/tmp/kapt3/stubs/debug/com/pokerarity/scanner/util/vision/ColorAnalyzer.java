package com.pokerarity.scanner.util.vision;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;

/**
 * Low-level color analysis utilities for detecting visual features in Pokemon screenshots.
 * Uses native Android Bitmap pixel access — no OpenCV dependency needed.
 *
 * All analysis samples every [SAMPLE_STEP]th pixel for performance.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000R\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0014\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010\u000b\n\u0002\b\t\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0002+,B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\u0007J\u0010\u0010\t\u001a\u00020\n2\u0006\u0010\b\u001a\u00020\u0007H\u0002J\u000e\u0010\u000b\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\u0007J\u001a\u0010\f\u001a\u00020\r2\u0006\u0010\b\u001a\u00020\u00072\n\b\u0002\u0010\u000e\u001a\u0004\u0018\u00010\u000fJ\u001a\u0010\u0010\u001a\u00020\r2\u0006\u0010\b\u001a\u00020\u00072\n\b\u0002\u0010\u000e\u001a\u0004\u0018\u00010\u000fJ\u0014\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u000f0\u00122\u0006\u0010\b\u001a\u00020\u0007J\u000e\u0010\u0013\u001a\u00020\u000f2\u0006\u0010\b\u001a\u00020\u0007J6\u0010\u0014\u001a\u00020\r2\u0006\u0010\b\u001a\u00020\u00072\n\b\u0002\u0010\u000e\u001a\u0004\u0018\u00010\u000f2\u0006\u0010\u0015\u001a\u00020\u00162\b\b\u0002\u0010\u0017\u001a\u00020\r2\b\b\u0002\u0010\u0018\u001a\u00020\rJ\u001a\u0010\u0019\u001a\u00020\r2\u0006\u0010\b\u001a\u00020\u00072\n\b\u0002\u0010\u000e\u001a\u0004\u0018\u00010\u000fJ\u001a\u0010\u001a\u001a\u00020\u00042\u0006\u0010\b\u001a\u00020\u00072\n\b\u0002\u0010\u000e\u001a\u0004\u0018\u00010\u000fJ8\u0010\u001b\u001a\u00020\u001c2\u0006\u0010\b\u001a\u00020\u00072\u0006\u0010\u000e\u001a\u00020\u000f2\f\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\u00160\u00122\b\b\u0002\u0010\u0017\u001a\u00020\r2\b\b\u0002\u0010\u0018\u001a\u00020\rJ\u000e\u0010\u001e\u001a\u00020\u000f2\u0006\u0010\b\u001a\u00020\u0007J\u0014\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\u000f0\u00122\u0006\u0010\b\u001a\u00020\u0007J\u000e\u0010 \u001a\u00020\u000f2\u0006\u0010\b\u001a\u00020\u0007J\u000e\u0010!\u001a\u00020\u000f2\u0006\u0010\b\u001a\u00020\u0007J\u000e\u0010\"\u001a\u00020\u000f2\u0006\u0010\b\u001a\u00020\u0007J<\u0010#\u001a\u00020$2\u0006\u0010\b\u001a\u00020\u00072\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0015\u001a\u00020\u00162\b\b\u0002\u0010\u0017\u001a\u00020\r2\b\b\u0002\u0010\u0018\u001a\u00020\r2\b\b\u0002\u0010%\u001a\u00020\rJ\u0018\u0010&\u001a\u00020\r2\u0006\u0010\'\u001a\u00020\r2\u0006\u0010(\u001a\u00020\rH\u0002J\u0016\u0010)\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\u00072\u0006\u0010*\u001a\u00020\u000fR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006-"}, d2 = {"Lcom/pokerarity/scanner/util/vision/ColorAnalyzer;", "", "()V", "ANALYSIS_WIDTH", "", "SAMPLE_STEP", "downscaleForAnalysis", "Landroid/graphics/Bitmap;", "bitmap", "estimateBackgroundHSV", "", "extractMaskedSprite", "getAverageBrightness", "", "region", "Landroid/graphics/Rect;", "getAverageSaturation", "getBackgroundCornerRegions", "", "getBackgroundRegion", "getColorPercentage", "hueRange", "Lkotlin/ranges/IntRange;", "minSaturation", "minValue", "getDominantHue", "getDominantRgb", "getHueStats", "Lcom/pokerarity/scanner/util/vision/ColorAnalyzer$HueStats;", "standardRanges", "getLuckyFocusRegion", "getLuckySupportRegions", "getSpriteBorderRegion", "getSpriteRegion", "getSpriteRegionAdaptive", "hasColorInRegion", "", "threshold", "hueDistance", "h1", "h2", "maskSpriteBackground", "sprite", "HSVPixel", "HueStats", "app_debug"})
public final class ColorAnalyzer {
    
    /**
     * Sample every 4th pixel for speed.
     */
    private static final int SAMPLE_STEP = 4;
    
    /**
     * Target width for analysis (360p for speed).
     */
    private static final int ANALYSIS_WIDTH = 360;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.util.vision.ColorAnalyzer INSTANCE = null;
    
    private ColorAnalyzer() {
        super();
    }
    
    /**
     * Downscale a bitmap to [ANALYSIS_WIDTH] for faster pixel analysis.
     */
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Bitmap downscaleForAnalysis(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap) {
        return null;
    }
    
    /**
     * Get the dominant hue (0-360) from a bitmap region.
     * Builds a histogram of hues (ignoring very dark or desaturated pixels)
     * and returns the peak bin.
     */
    public final float getDominantHue(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.Nullable()
    android.graphics.Rect region) {
        return 0.0F;
    }
    
    /**
     * Compute hue statistics for a region, with "standard" hue ranges excluded.
     */
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.vision.ColorAnalyzer.HueStats getHueStats(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.NotNull()
    android.graphics.Rect region, @org.jetbrains.annotations.NotNull()
    java.util.List<kotlin.ranges.IntRange> standardRanges, float minSaturation, float minValue) {
        return null;
    }
    
    /**
     * Returns the percentage (0.0 - 1.0) of sampled pixels in a region
     * whose hue falls within [hueRange] and passes saturation/value thresholds.
     */
    public final float getColorPercentage(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.Nullable()
    android.graphics.Rect region, @org.jetbrains.annotations.NotNull()
    kotlin.ranges.IntRange hueRange, float minSaturation, float minValue) {
        return 0.0F;
    }
    
    /**
     * Check if a specific color range exists in a region at a given threshold.
     */
    public final boolean hasColorInRegion(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.NotNull()
    android.graphics.Rect region, @org.jetbrains.annotations.NotNull()
    kotlin.ranges.IntRange hueRange, float minSaturation, float minValue, float threshold) {
        return false;
    }
    
    /**
     * Get the average brightness (V channel) of a region. Used for shadow detection.
     */
    public final float getAverageBrightness(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.Nullable()
    android.graphics.Rect region) {
        return 0.0F;
    }
    
    /**
     * Get the average saturation (S channel) of a region.
     */
    public final float getAverageSaturation(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.Nullable()
    android.graphics.Rect region) {
        return 0.0F;
    }
    
    /**
     * Get the dominant RGB color from a bitmap region.
     * Filters out near-white/near-black and low-saturation pixels to avoid UI noise.
     */
    public final int getDominantRgb(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.Nullable()
    android.graphics.Rect region) {
        return 0;
    }
    
    /**
     * Get the sprite area rectangle (center 30% of screen).
     */
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Rect getSpriteRegion(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap) {
        return null;
    }
    
    /**
     * Adaptive sprite region based on background HSV separation.
     */
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Rect getSpriteRegionAdaptive(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap) {
        return null;
    }
    
    /**
     * Extract the sprite region and mask background pixels to black.
     */
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Bitmap extractMaskedSprite(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap) {
        return null;
    }
    
    /**
     * Mask background-like pixels in the sprite region using a background HSV model.
     */
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Bitmap maskSpriteBackground(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.NotNull()
    android.graphics.Rect sprite) {
        return null;
    }
    
    /**
     * Get the background region (area behind the Pokemon, excluding the sprite).
     * Uses a ring around the sprite center.
     */
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Rect getBackgroundRegion(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap) {
        return null;
    }
    
    /**
     * Background corners: avoids sprite center and CP area.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<android.graphics.Rect> getBackgroundCornerRegions(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap) {
        return null;
    }
    
    /**
     * Lucky background is usually more visible around the mid/lower side bands
     * than in the very top corners.
     */
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Rect getLuckyFocusRegion(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap) {
        return null;
    }
    
    /**
     * Support regions around the sprite where lucky gold tends to remain visible
     * without being fully occluded by the Pokemon model.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<android.graphics.Rect> getLuckySupportRegions(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap) {
        return null;
    }
    
    private final float[] estimateBackgroundHSV(android.graphics.Bitmap bitmap) {
        return null;
    }
    
    private final float hueDistance(float h1, float h2) {
        return 0.0F;
    }
    
    /**
     * Get the border region of the sprite area for aura/shadow detection.
     */
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Rect getSpriteBorderRegion(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap) {
        return null;
    }
    
    /**
     * HSV color data for a sampled pixel.
     */
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0007\n\u0002\b\f\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\'\u0010\u000e\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\u000f\u001a\u00020\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0012\u001a\u00020\u0013H\u00d6\u0001J\t\u0010\u0014\u001a\u00020\u0015H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\bR\u0011\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\b\u00a8\u0006\u0016"}, d2 = {"Lcom/pokerarity/scanner/util/vision/ColorAnalyzer$HSVPixel;", "", "h", "", "s", "v", "(FFF)V", "getH", "()F", "getS", "getV", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "", "toString", "", "app_debug"})
    public static final class HSVPixel {
        private final float h = 0.0F;
        private final float s = 0.0F;
        private final float v = 0.0F;
        
        public HSVPixel(float h, float s, float v) {
            super();
        }
        
        public final float getH() {
            return 0.0F;
        }
        
        public final float getS() {
            return 0.0F;
        }
        
        public final float getV() {
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
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.ColorAnalyzer.HSVPixel copy(float h, float s, float v) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u000f\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B%\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\u0006\u0010\u0007\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\bJ\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0012\u001a\u00020\u0005H\u00c6\u0003J1\u0010\u0013\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00052\b\b\u0002\u0010\u0007\u001a\u00020\u0005H\u00c6\u0001J\u0013\u0010\u0014\u001a\u00020\u00152\b\u0010\u0016\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0017\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\u0018\u001a\u00020\u0019H\u00d6\u0001R\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0007\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\nR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u001a"}, d2 = {"Lcom/pokerarity/scanner/util/vision/ColorAnalyzer$HueStats;", "", "total", "", "outsideStandardRatio", "", "avgSaturation", "avgValue", "(IFFF)V", "getAvgSaturation", "()F", "getAvgValue", "getOutsideStandardRatio", "getTotal", "()I", "component1", "component2", "component3", "component4", "copy", "equals", "", "other", "hashCode", "toString", "", "app_debug"})
    public static final class HueStats {
        private final int total = 0;
        private final float outsideStandardRatio = 0.0F;
        private final float avgSaturation = 0.0F;
        private final float avgValue = 0.0F;
        
        public HueStats(int total, float outsideStandardRatio, float avgSaturation, float avgValue) {
            super();
        }
        
        public final int getTotal() {
            return 0;
        }
        
        public final float getOutsideStandardRatio() {
            return 0.0F;
        }
        
        public final float getAvgSaturation() {
            return 0.0F;
        }
        
        public final float getAvgValue() {
            return 0.0F;
        }
        
        public final int component1() {
            return 0;
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
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.ColorAnalyzer.HueStats copy(int total, float outsideStandardRatio, float avgSaturation, float avgValue) {
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