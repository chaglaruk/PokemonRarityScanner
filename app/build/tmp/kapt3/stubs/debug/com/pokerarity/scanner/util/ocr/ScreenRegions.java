package com.pokerarity.scanner.util.ocr;

import android.graphics.Bitmap;
import android.graphics.Rect;

/**
 * Referans ekran: 1080x2340px (Samsung, 450dpi)
 * 13 Mart 2026 - koordinatlar log analizine gore duzeltildi
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0019\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0001\"B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u001d\u001a\u00020\u001e2\u0006\u0010\u001f\u001a\u00020 2\u0006\u0010!\u001a\u00020\u0004R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u0011\u0010\u0007\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0006R\u0011\u0010\t\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u0006R\u0011\u0010\u000b\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\u0006R\u0011\u0010\r\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u0006R\u0011\u0010\u000f\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0006R\u0011\u0010\u0011\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0006R\u0011\u0010\u0013\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0006R\u0011\u0010\u0015\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0006R\u0011\u0010\u0017\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0006R\u0011\u0010\u0019\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0006R\u0011\u0010\u001b\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0006\u00a8\u0006#"}, d2 = {"Lcom/pokerarity/scanner/util/ocr/ScreenRegions;", "", "()V", "REGION_CANDY", "Lcom/pokerarity/scanner/util/ocr/ScreenRegions$Region;", "getREGION_CANDY", "()Lcom/pokerarity/scanner/util/ocr/ScreenRegions$Region;", "REGION_CANDY_WIDE", "getREGION_CANDY_WIDE", "REGION_CP", "getREGION_CP", "REGION_DATE_BADGE", "getREGION_DATE_BADGE", "REGION_DATE_BOTTOM", "getREGION_DATE_BOTTOM", "REGION_HEIGHT", "getREGION_HEIGHT", "REGION_HP", "getREGION_HP", "REGION_LUCKY_LABEL", "getREGION_LUCKY_LABEL", "REGION_MEGA_ENERGY", "getREGION_MEGA_ENERGY", "REGION_NAME", "getREGION_NAME", "REGION_STARDUST", "getREGION_STARDUST", "REGION_WEIGHT", "getREGION_WEIGHT", "getRectForRegion", "Landroid/graphics/Rect;", "bitmap", "Landroid/graphics/Bitmap;", "region", "Region", "app_debug"})
public final class ScreenRegions {
    @org.jetbrains.annotations.NotNull()
    private static final com.pokerarity.scanner.util.ocr.ScreenRegions.Region REGION_CP = null;
    @org.jetbrains.annotations.NotNull()
    private static final com.pokerarity.scanner.util.ocr.ScreenRegions.Region REGION_NAME = null;
    @org.jetbrains.annotations.NotNull()
    private static final com.pokerarity.scanner.util.ocr.ScreenRegions.Region REGION_HP = null;
    @org.jetbrains.annotations.NotNull()
    private static final com.pokerarity.scanner.util.ocr.ScreenRegions.Region REGION_LUCKY_LABEL = null;
    @org.jetbrains.annotations.NotNull()
    private static final com.pokerarity.scanner.util.ocr.ScreenRegions.Region REGION_CANDY = null;
    @org.jetbrains.annotations.NotNull()
    private static final com.pokerarity.scanner.util.ocr.ScreenRegions.Region REGION_CANDY_WIDE = null;
    @org.jetbrains.annotations.NotNull()
    private static final com.pokerarity.scanner.util.ocr.ScreenRegions.Region REGION_MEGA_ENERGY = null;
    @org.jetbrains.annotations.NotNull()
    private static final com.pokerarity.scanner.util.ocr.ScreenRegions.Region REGION_WEIGHT = null;
    @org.jetbrains.annotations.NotNull()
    private static final com.pokerarity.scanner.util.ocr.ScreenRegions.Region REGION_HEIGHT = null;
    @org.jetbrains.annotations.NotNull()
    private static final com.pokerarity.scanner.util.ocr.ScreenRegions.Region REGION_STARDUST = null;
    @org.jetbrains.annotations.NotNull()
    private static final com.pokerarity.scanner.util.ocr.ScreenRegions.Region REGION_DATE_BADGE = null;
    @org.jetbrains.annotations.NotNull()
    private static final com.pokerarity.scanner.util.ocr.ScreenRegions.Region REGION_DATE_BOTTOM = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.util.ocr.ScreenRegions INSTANCE = null;
    
    private ScreenRegions() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.ocr.ScreenRegions.Region getREGION_CP() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.ocr.ScreenRegions.Region getREGION_NAME() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.ocr.ScreenRegions.Region getREGION_HP() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.ocr.ScreenRegions.Region getREGION_LUCKY_LABEL() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.ocr.ScreenRegions.Region getREGION_CANDY() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.ocr.ScreenRegions.Region getREGION_CANDY_WIDE() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.ocr.ScreenRegions.Region getREGION_MEGA_ENERGY() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.ocr.ScreenRegions.Region getREGION_WEIGHT() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.ocr.ScreenRegions.Region getREGION_HEIGHT() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.ocr.ScreenRegions.Region getREGION_STARDUST() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.ocr.ScreenRegions.Region getREGION_DATE_BADGE() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.ocr.ScreenRegions.Region getREGION_DATE_BOTTOM() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Rect getRectForRegion(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.util.ocr.ScreenRegions.Region region) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u000f\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B%\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0007J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000e\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0003H\u00c6\u0003J1\u0010\u0011\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\u0012\u001a\u00020\u00132\b\u0010\u0014\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0015\u001a\u00020\u0016H\u00d6\u0001J\t\u0010\u0017\u001a\u00020\u0018H\u00d6\u0001R\u0011\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\tR\u0011\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\t\u00a8\u0006\u0019"}, d2 = {"Lcom/pokerarity/scanner/util/ocr/ScreenRegions$Region;", "", "topPercent", "", "leftPercent", "widthPercent", "heightPercent", "(FFFF)V", "getHeightPercent", "()F", "getLeftPercent", "getTopPercent", "getWidthPercent", "component1", "component2", "component3", "component4", "copy", "equals", "", "other", "hashCode", "", "toString", "", "app_debug"})
    public static final class Region {
        private final float topPercent = 0.0F;
        private final float leftPercent = 0.0F;
        private final float widthPercent = 0.0F;
        private final float heightPercent = 0.0F;
        
        public Region(float topPercent, float leftPercent, float widthPercent, float heightPercent) {
            super();
        }
        
        public final float getTopPercent() {
            return 0.0F;
        }
        
        public final float getLeftPercent() {
            return 0.0F;
        }
        
        public final float getWidthPercent() {
            return 0.0F;
        }
        
        public final float getHeightPercent() {
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
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.ocr.ScreenRegions.Region copy(float topPercent, float leftPercent, float widthPercent, float heightPercent) {
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