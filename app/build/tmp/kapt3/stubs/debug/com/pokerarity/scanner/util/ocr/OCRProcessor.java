package com.pokerarity.scanner.util.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import com.pokerarity.scanner.data.model.PokemonData;
import kotlinx.coroutines.Dispatchers;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000v\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u00002\u00020\u0001:\u00011B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001c\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00110\u00102\u0006\u0010\u0012\u001a\u00020\u0013H\u0082@\u00a2\u0006\u0002\u0010\u0014J,\u0010\u0015\u001a\u00020\u00132\u0006\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0016\u001a\u00020\u00172\u0012\u0010\u0018\u001a\u000e\u0012\u0004\u0012\u00020\u0013\u0012\u0004\u0012\u00020\u00130\u0019H\u0002J\u0018\u0010\u001a\u001a\u00020\u00132\u0006\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u001b\u001a\u00020\u001cH\u0002J\u000e\u0010\u001d\u001a\u00020\u001eH\u0086@\u00a2\u0006\u0002\u0010\u001fJ\u000e\u0010 \u001a\u00020\u001eH\u0086@\u00a2\u0006\u0002\u0010\u001fJ \u0010!\u001a\u00020\"2\u0006\u0010\u0012\u001a\u00020\u00132\b\b\u0002\u0010#\u001a\u00020\u0006H\u0086@\u00a2\u0006\u0002\u0010$J\u001c\u0010%\u001a\b\u0012\u0004\u0012\u00020\'0&2\u0006\u0010\u0012\u001a\u00020\u0013H\u0082@\u00a2\u0006\u0002\u0010\u0014J \u0010(\u001a\u0004\u0018\u00010)2\u0006\u0010\u0012\u001a\u00020\u00132\u0006\u0010#\u001a\u00020\u0006H\u0082@\u00a2\u0006\u0002\u0010$J\u001c\u0010*\u001a\b\u0012\u0004\u0012\u00020\u00110&2\u0006\u0010\u0012\u001a\u00020\u0013H\u0082@\u00a2\u0006\u0002\u0010\u0014J\u0006\u0010+\u001a\u00020\u001eJ7\u0010,\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\'\u0012\u0004\u0012\u00020\'0-0&2\b\u0010.\u001a\u0004\u0018\u00010\'2\f\u0010/\u001a\b\u0012\u0004\u0012\u00020\u00110\u0010H\u0002\u00a2\u0006\u0002\u00100R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001b\u0010\u0007\u001a\u00020\b8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u000b\u0010\f\u001a\u0004\b\t\u0010\nR\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u00062"}, d2 = {"Lcom/pokerarity/scanner/util/ocr/OCRProcessor;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "isInitialized", "", "mlKitOcrProvider", "Lcom/pokerarity/scanner/util/ocr/MLKitOcrProvider;", "getMlKitOcrProvider", "()Lcom/pokerarity/scanner/util/ocr/MLKitOcrProvider;", "mlKitOcrProvider$delegate", "Lkotlin/Lazy;", "textParser", "Lcom/pokerarity/scanner/util/ocr/TextParser;", "collectHpRaws", "", "", "bitmap", "Landroid/graphics/Bitmap;", "(Landroid/graphics/Bitmap;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "cropAndProcess", "region", "Lcom/pokerarity/scanner/util/ocr/ScreenRegions$Region;", "transform", "Lkotlin/Function1;", "cropBitmap", "rect", "Landroid/graphics/Rect;", "ensureInitialized", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "initialize", "processImage", "Lcom/pokerarity/scanner/data/model/PokemonData;", "includeSecondaryFields", "(Landroid/graphics/Bitmap;ZLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "recognizeCp", "Lcom/pokerarity/scanner/util/ocr/OCRProcessor$OcrValue;", "", "recognizeDate", "Ljava/util/Date;", "recognizeName", "release", "resolveHpResult", "Lkotlin/Pair;", "cp", "raws", "(Ljava/lang/Integer;Ljava/util/List;)Lcom/pokerarity/scanner/util/ocr/OCRProcessor$OcrValue;", "OcrValue", "PokeRarityScanner-v1.8.2_debug"})
public final class OCRProcessor {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.util.ocr.TextParser textParser = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy mlKitOcrProvider$delegate = null;
    @kotlin.jvm.Volatile()
    private volatile boolean isInitialized = false;
    
    public OCRProcessor(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    private final com.pokerarity.scanner.util.ocr.MLKitOcrProvider getMlKitOcrProvider() {
        return null;
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
    
    private final java.lang.Object recognizeCp(android.graphics.Bitmap bitmap, kotlin.coroutines.Continuation<? super com.pokerarity.scanner.util.ocr.OCRProcessor.OcrValue<java.lang.Integer>> $completion) {
        return null;
    }
    
    private final java.lang.Object collectHpRaws(android.graphics.Bitmap bitmap, kotlin.coroutines.Continuation<? super java.util.List<java.lang.String>> $completion) {
        return null;
    }
    
    private final com.pokerarity.scanner.util.ocr.OCRProcessor.OcrValue<kotlin.Pair<java.lang.Integer, java.lang.Integer>> resolveHpResult(java.lang.Integer cp, java.util.List<java.lang.String> raws) {
        return null;
    }
    
    private final java.lang.Object recognizeName(android.graphics.Bitmap bitmap, kotlin.coroutines.Continuation<? super com.pokerarity.scanner.util.ocr.OCRProcessor.OcrValue<java.lang.String>> $completion) {
        return null;
    }
    
    private final java.lang.Object recognizeDate(android.graphics.Bitmap bitmap, boolean includeSecondaryFields, kotlin.coroutines.Continuation<? super java.util.Date> $completion) {
        return null;
    }
    
    private final android.graphics.Bitmap cropAndProcess(android.graphics.Bitmap bitmap, com.pokerarity.scanner.util.ocr.ScreenRegions.Region region, kotlin.jvm.functions.Function1<? super android.graphics.Bitmap, android.graphics.Bitmap> transform) {
        return null;
    }
    
    private final android.graphics.Bitmap cropBitmap(android.graphics.Bitmap bitmap, android.graphics.Rect rect) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u000e\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0082\b\u0018\u0000*\u0004\b\u0000\u0010\u00012\u00020\u0002B\u001f\u0012\b\u0010\u0003\u001a\u0004\u0018\u00018\u0000\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0007J\u0010\u0010\u000e\u001a\u0004\u0018\u00018\u0000H\u00c6\u0003\u00a2\u0006\u0002\u0010\fJ\t\u0010\u000f\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0005H\u00c6\u0003J4\u0010\u0011\u001a\b\u0012\u0004\u0012\u00028\u00000\u00002\n\b\u0002\u0010\u0003\u001a\u0004\u0018\u00018\u00002\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0005H\u00c6\u0001\u00a2\u0006\u0002\u0010\u0012J\u0013\u0010\u0013\u001a\u00020\u00142\b\u0010\u0015\u001a\u0004\u0018\u00010\u0002H\u00d6\u0003J\t\u0010\u0016\u001a\u00020\u0017H\u00d6\u0001J\t\u0010\u0018\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\tR\u0015\u0010\u0003\u001a\u0004\u0018\u00018\u0000\u00a2\u0006\n\n\u0002\u0010\r\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u0019"}, d2 = {"Lcom/pokerarity/scanner/util/ocr/OCRProcessor$OcrValue;", "T", "", "value", "raw", "", "source", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V", "getRaw", "()Ljava/lang/String;", "getSource", "getValue", "()Ljava/lang/Object;", "Ljava/lang/Object;", "component1", "component2", "component3", "copy", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Lcom/pokerarity/scanner/util/ocr/OCRProcessor$OcrValue;", "equals", "", "other", "hashCode", "", "toString", "PokeRarityScanner-v1.8.2_debug"})
    static final class OcrValue<T extends java.lang.Object> {
        @org.jetbrains.annotations.Nullable()
        private final T value = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String raw = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String source = null;
        
        public OcrValue(@org.jetbrains.annotations.Nullable()
        T value, @org.jetbrains.annotations.NotNull()
        java.lang.String raw, @org.jetbrains.annotations.NotNull()
        java.lang.String source) {
            super();
        }
        
        @org.jetbrains.annotations.Nullable()
        public final T getValue() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getRaw() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getSource() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final T component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.ocr.OCRProcessor.OcrValue<T> copy(@org.jetbrains.annotations.Nullable()
        T value, @org.jetbrains.annotations.NotNull()
        java.lang.String raw, @org.jetbrains.annotations.NotNull()
        java.lang.String source) {
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