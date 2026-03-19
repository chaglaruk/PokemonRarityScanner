package com.pokerarity.scanner.util.vision;

import android.graphics.Bitmap;
import android.graphics.Color;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u0015\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0018\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0014\n\u0002\b\u0004\n\u0002\u0010\u0007\n\u0002\b\u0005\n\u0002\u0010\f\n\u0002\b\u0005\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0001%B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0002J\u000e\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u0011J\u0018\u0010\u0012\u001a\u00020\u000b2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0013\u001a\u00020\u0004H\u0002J\u0018\u0010\u0014\u001a\u00020\u000b2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0013\u001a\u00020\u0004H\u0002J \u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0013\u001a\u00020\u00042\u0006\u0010\u0017\u001a\u00020\u0004H\u0002J\u000e\u0010\u0018\u001a\u00020\u000f2\u0006\u0010\u0019\u001a\u00020\u0011J\u0016\u0010\u001a\u001a\u00020\u001b2\u0006\u0010\u001c\u001a\u00020\u00162\u0006\u0010\u001d\u001a\u00020\u0016J\u0016\u0010\u001e\u001a\u00020\u00042\u0006\u0010\u001c\u001a\u00020\u000b2\u0006\u0010\u001d\u001a\u00020\u000bJ\u0010\u0010\u001f\u001a\u00020\u00042\u0006\u0010 \u001a\u00020!H\u0002J\u0010\u0010\"\u001a\u00020\u00042\u0006\u0010#\u001a\u00020\u0004H\u0002J\u0010\u0010$\u001a\u00020\u00112\u0006\u0010\u0010\u001a\u00020\u0011H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006&"}, d2 = {"Lcom/pokerarity/scanner/util/vision/SpriteSignature;", "", "()V", "EDGE_BINS", "", "EDGE_SIZE", "HASH_SIZE", "NIBBLE_BITS", "", "TRIM_STEP", "bitsToHex", "", "bits", "", "compute", "Lcom/pokerarity/scanner/util/vision/SpriteSignature$Signature;", "bitmap", "Landroid/graphics/Bitmap;", "computeAHash", "size", "computeDHash", "computeEdgeHistogram", "", "bins", "computeFromMaskedSprite", "maskedSprite", "edgeDistance", "", "a", "b", "hammingHex", "hexNibble", "c", "", "luminance", "pixel", "trimToContent", "Signature", "app_debug"})
public final class SpriteSignature {
    private static final int HASH_SIZE = 8;
    private static final int EDGE_SIZE = 48;
    private static final int EDGE_BINS = 8;
    private static final int TRIM_STEP = 2;
    @org.jetbrains.annotations.NotNull()
    private static final int[] NIBBLE_BITS = {0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4};
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.util.vision.SpriteSignature INSTANCE = null;
    
    private SpriteSignature() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.vision.SpriteSignature.Signature compute(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.vision.SpriteSignature.Signature computeFromMaskedSprite(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap maskedSprite) {
        return null;
    }
    
    public final int hammingHex(@org.jetbrains.annotations.NotNull()
    java.lang.String a, @org.jetbrains.annotations.NotNull()
    java.lang.String b) {
        return 0;
    }
    
    public final float edgeDistance(@org.jetbrains.annotations.NotNull()
    float[] a, @org.jetbrains.annotations.NotNull()
    float[] b) {
        return 0.0F;
    }
    
    private final android.graphics.Bitmap trimToContent(android.graphics.Bitmap bitmap) {
        return null;
    }
    
    private final java.lang.String computeAHash(android.graphics.Bitmap bitmap, int size) {
        return null;
    }
    
    private final java.lang.String computeDHash(android.graphics.Bitmap bitmap, int size) {
        return null;
    }
    
    private final float[] computeEdgeHistogram(android.graphics.Bitmap bitmap, int size, int bins) {
        return null;
    }
    
    private final int luminance(int pixel) {
        return 0;
    }
    
    private final java.lang.String bitsToHex(boolean[] bits) {
        return null;
    }
    
    private final int hexNibble(char c) {
        return 0;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0014\n\u0002\b\u000b\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000e\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000f\u001a\u00020\u0006H\u00c6\u0003J\'\u0010\u0010\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u0006H\u00c6\u0001J\u0013\u0010\u0011\u001a\u00020\u00122\b\u0010\u0013\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0014\u001a\u00020\u0015H\u00d6\u0001J\t\u0010\u0016\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\tR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u0017"}, d2 = {"Lcom/pokerarity/scanner/util/vision/SpriteSignature$Signature;", "", "aHash", "", "dHash", "edge", "", "(Ljava/lang/String;Ljava/lang/String;[F)V", "getAHash", "()Ljava/lang/String;", "getDHash", "getEdge", "()[F", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "", "toString", "app_debug"})
    public static final class Signature {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String aHash = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String dHash = null;
        @org.jetbrains.annotations.NotNull()
        private final float[] edge = null;
        
        public Signature(@org.jetbrains.annotations.NotNull()
        java.lang.String aHash, @org.jetbrains.annotations.NotNull()
        java.lang.String dHash, @org.jetbrains.annotations.NotNull()
        float[] edge) {
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
        public final com.pokerarity.scanner.util.vision.SpriteSignature.Signature copy(@org.jetbrains.annotations.NotNull()
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
}