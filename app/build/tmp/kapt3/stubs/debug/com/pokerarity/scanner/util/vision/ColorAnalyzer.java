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
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0001\u0019B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\u0007J\u001a\u0010\t\u001a\u00020\n2\u0006\u0010\b\u001a\u00020\u00072\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\fJ\u000e\u0010\r\u001a\u00020\f2\u0006\u0010\b\u001a\u00020\u0007J6\u0010\u000e\u001a\u00020\n2\u0006\u0010\b\u001a\u00020\u00072\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\f2\u0006\u0010\u000f\u001a\u00020\u00102\b\b\u0002\u0010\u0011\u001a\u00020\n2\b\b\u0002\u0010\u0012\u001a\u00020\nJ\u001a\u0010\u0013\u001a\u00020\n2\u0006\u0010\b\u001a\u00020\u00072\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\fJ\u000e\u0010\u0014\u001a\u00020\f2\u0006\u0010\b\u001a\u00020\u0007J\u000e\u0010\u0015\u001a\u00020\f2\u0006\u0010\b\u001a\u00020\u0007J<\u0010\u0016\u001a\u00020\u00172\u0006\u0010\b\u001a\u00020\u00072\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u000f\u001a\u00020\u00102\b\b\u0002\u0010\u0011\u001a\u00020\n2\b\b\u0002\u0010\u0012\u001a\u00020\n2\b\b\u0002\u0010\u0018\u001a\u00020\nR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001a"}, d2 = {"Lcom/pokerarity/scanner/util/vision/ColorAnalyzer;", "", "()V", "ANALYSIS_WIDTH", "", "SAMPLE_STEP", "downscaleForAnalysis", "Landroid/graphics/Bitmap;", "bitmap", "getAverageBrightness", "", "region", "Landroid/graphics/Rect;", "getBackgroundRegion", "getColorPercentage", "hueRange", "Lkotlin/ranges/IntRange;", "minSaturation", "minValue", "getDominantHue", "getSpriteBorderRegion", "getSpriteRegion", "hasColorInRegion", "", "threshold", "HSVPixel", "app_debug"})
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
     * Get the sprite area rectangle (center 30% of screen).
     */
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Rect getSpriteRegion(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap) {
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
}