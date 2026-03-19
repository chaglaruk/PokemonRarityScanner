package com.pokerarity.scanner.util.vision;

import android.graphics.Bitmap;
import android.graphics.Color;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0014\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\tJ\u000e\u0010\n\u001a\u00020\u000b2\u0006\u0010\b\u001a\u00020\tR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\f"}, d2 = {"Lcom/pokerarity/scanner/util/vision/SpriteColorSignature;", "", "()V", "BINS", "", "STEP", "computeForegroundRatio", "", "maskedSprite", "Landroid/graphics/Bitmap;", "computeHueHistogram", "", "app_debug"})
public final class SpriteColorSignature {
    private static final int BINS = 12;
    private static final int STEP = 2;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.util.vision.SpriteColorSignature INSTANCE = null;
    
    private SpriteColorSignature() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final float[] computeHueHistogram(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap maskedSprite) {
        return null;
    }
    
    public final float computeForegroundRatio(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap maskedSprite) {
        return 0.0F;
    }
}