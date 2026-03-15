package com.pokerarity.scanner.util.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.pokerarity.scanner.data.model.PokemonData;
import kotlinx.coroutines.Dispatchers;
import java.io.File;
import java.io.FileOutputStream;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000P\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\u000b\u001a\u00020\fH\u0086@\u00a2\u0006\u0002\u0010\rJ\u000e\u0010\u000e\u001a\u00020\fH\u0086@\u00a2\u0006\u0002\u0010\rJ\u0016\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0012H\u0086@\u00a2\u0006\u0002\u0010\u0013J,\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00152\n\b\u0002\u0010\u0019\u001a\u0004\u0018\u00010\u0015H\u0002J,\u0010\u001a\u001a\u00020\u00152\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00152\n\b\u0002\u0010\u0019\u001a\u0004\u0018\u00010\u0015H\u0002J,\u0010\u001b\u001a\u00020\u00152\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u001c\u001a\u00020\u001d2\u0006\u0010\u0018\u001a\u00020\u00152\n\b\u0002\u0010\u0019\u001a\u0004\u0018\u00010\u0015H\u0002J\u0006\u0010\u001e\u001a\u00020\fR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001f"}, d2 = {"Lcom/pokerarity/scanner/util/ocr/OCRProcessor;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "isInitialized", "", "tess", "Lcom/googlecode/tesseract/android/TessBaseAPI;", "textParser", "Lcom/pokerarity/scanner/util/ocr/TextParser;", "ensureInitialized", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "initialize", "processImage", "Lcom/pokerarity/scanner/data/model/PokemonData;", "bitmap", "Landroid/graphics/Bitmap;", "(Landroid/graphics/Bitmap;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "region", "", "r", "Lcom/pokerarity/scanner/util/ocr/ScreenRegions$Region;", "label", "wl", "regionBlock", "regionFromRect", "rect", "Landroid/graphics/Rect;", "release", "app_debug"})
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
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.pokerarity.scanner.data.model.PokemonData> $completion) {
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