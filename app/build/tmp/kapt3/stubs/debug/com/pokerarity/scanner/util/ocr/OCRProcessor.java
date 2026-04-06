package com.pokerarity.scanner.util.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.pokerarity.scanner.data.model.PokemonData;
import kotlinx.coroutines.Dispatchers;
import java.io.File;
import java.io.FileOutputStream;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000X\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\b\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\"\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\b\u0010\u000f\u001a\u0004\u0018\u00010\u00102\u0006\u0010\u0011\u001a\u00020\fH\u0002J\"\u0010\u0012\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\b\u0010\u000f\u001a\u0004\u0018\u00010\u00102\u0006\u0010\u0011\u001a\u00020\fH\u0002J(\u0010\u0013\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0011\u001a\u00020\f2\u0006\u0010\u0016\u001a\u00020\u0006H\u0002J\"\u0010\u0017\u001a\u0004\u0018\u00010\u000e2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0018\u001a\u00020\u00102\u0006\u0010\u0019\u001a\u00020\u0006H\u0002J\u000e\u0010\u001a\u001a\u00020\u001bH\u0086@\u00a2\u0006\u0002\u0010\u001cJ\u000e\u0010\u001d\u001a\u00020\u001bH\u0086@\u00a2\u0006\u0002\u0010\u001cJ \u0010\u001e\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0011\u001a\u00020\fH\u0002J \u0010\u001f\u001a\u00020 2\u0006\u0010\r\u001a\u00020\u000e2\b\b\u0002\u0010!\u001a\u00020\u0006H\u0086@\u00a2\u0006\u0002\u0010\"J,\u0010#\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0011\u001a\u00020\f2\u0006\u0010$\u001a\u00020%2\n\b\u0002\u0010&\u001a\u0004\u0018\u00010\fH\u0002J,\u0010\u0014\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\'\u001a\u00020\u00152\u0006\u0010\u0011\u001a\u00020\f2\n\b\u0002\u0010(\u001a\u0004\u0018\u00010\fH\u0002J,\u0010)\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\'\u001a\u00020\u00152\u0006\u0010\u0011\u001a\u00020\f2\n\b\u0002\u0010(\u001a\u0004\u0018\u00010\fH\u0002J,\u0010*\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0018\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\f2\n\b\u0002\u0010(\u001a\u0004\u0018\u00010\fH\u0002J\u0006\u0010+\u001a\u00020\u001bR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006,"}, d2 = {"Lcom/pokerarity/scanner/util/ocr/OCRProcessor;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "isInitialized", "", "tess", "Lcom/googlecode/tesseract/android/TessBaseAPI;", "textParser", "Lcom/pokerarity/scanner/util/ocr/TextParser;", "badgeRegionDirect", "", "bitmap", "Landroid/graphics/Bitmap;", "dynamicRect", "Landroid/graphics/Rect;", "label", "badgeRegionProcessed", "candyRegionProcessed", "region", "Lcom/pokerarity/scanner/util/ocr/ScreenRegions$Region;", "block", "cropBadgeRect", "rect", "focusTextOnly", "ensureInitialized", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "initialize", "numericRegionProcessed", "processImage", "Lcom/pokerarity/scanner/data/model/PokemonData;", "includeSecondaryFields", "(Landroid/graphics/Bitmap;ZLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "readBitmap", "pageSegMode", "", "whitelist", "r", "wl", "regionBlock", "regionFromRect", "release", "app_debug"})
public final class OCRProcessor {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.util.ocr.TextParser textParser = null;
    private boolean isInitialized = false;
    @org.jetbrains.annotations.Nullable()
    private com.googlecode.tesseract.android.TessBaseAPI tess;
    
    public OCRProcessor(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object initialize(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object ensureInitialized(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    public final void release() {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object processImage(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, boolean includeSecondaryFields, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.pokerarity.scanner.data.model.PokemonData> $completion) {
        return null;
    }
    
    private final java.lang.String candyRegionProcessed(android.graphics.Bitmap bitmap, com.pokerarity.scanner.util.ocr.ScreenRegions.Region region, java.lang.String label, boolean block) {
        return null;
    }
    
    private final java.lang.String numericRegionProcessed(android.graphics.Bitmap bitmap, com.pokerarity.scanner.util.ocr.ScreenRegions.Region region, java.lang.String label) {
        return null;
    }
    
    private final java.lang.String badgeRegionProcessed(android.graphics.Bitmap bitmap, android.graphics.Rect dynamicRect, java.lang.String label) {
        return null;
    }
    
    private final java.lang.String badgeRegionDirect(android.graphics.Bitmap bitmap, android.graphics.Rect dynamicRect, java.lang.String label) {
        return null;
    }
    
    private final android.graphics.Bitmap cropBadgeRect(android.graphics.Bitmap bitmap, android.graphics.Rect rect, boolean focusTextOnly) {
        return null;
    }
    
    private final java.lang.String readBitmap(android.graphics.Bitmap bitmap, java.lang.String label, int pageSegMode, java.lang.String whitelist) {
        return null;
    }
    
    private final java.lang.String region(android.graphics.Bitmap bitmap, com.pokerarity.scanner.util.ocr.ScreenRegions.Region r, java.lang.String label, java.lang.String wl) {
        return null;
    }
    
    private final java.lang.String regionBlock(android.graphics.Bitmap bitmap, com.pokerarity.scanner.util.ocr.ScreenRegions.Region r, java.lang.String label, java.lang.String wl) {
        return null;
    }
    
    private final java.lang.String regionFromRect(android.graphics.Bitmap bitmap, android.graphics.Rect rect, java.lang.String label, java.lang.String wl) {
        return null;
    }
}