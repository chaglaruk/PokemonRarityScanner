package com.pokerarity.scanner.util.ocr;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.util.Date;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000H\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\r\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0002\b\n\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\tH\u0002J\u0018\u0010\r\u001a\u00020\t2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u000fH\u0002J\u0016\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u0006\u0010\u0002\u001a\u00020\u0003H\u0002J \u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\t2\u0006\u0010\u0015\u001a\u00020\t2\u0006\u0010\u0016\u001a\u00020\tH\u0002J\u0010\u0010\u0017\u001a\u0004\u0018\u00010\u00132\u0006\u0010\u0018\u001a\u00020\u0007J\u0015\u0010\u0019\u001a\u0004\u0018\u00010\t2\u0006\u0010\u0018\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\u001aJ\u0010\u0010\u001b\u001a\u0004\u0018\u00010\u00072\u0006\u0010\u0018\u001a\u00020\u0007J\u0010\u0010\u001c\u001a\u0004\u0018\u00010\u00132\u0006\u0010\u001d\u001a\u00020\u0007J\u0010\u0010\u001e\u001a\u0004\u0018\u00010\u00072\u0006\u0010\u0018\u001a\u00020\u0007J\u001c\u0010\u001f\u001a\u0010\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\t\u0018\u00010 2\u0006\u0010\u0018\u001a\u00020\u0007J\u0015\u0010!\u001a\u0004\u0018\u00010\"2\u0006\u0010\u0018\u001a\u00020\u0007\u00a2\u0006\u0002\u0010#J\u0015\u0010$\u001a\u0004\u0018\u00010\t2\u0006\u0010\u0018\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\u001aJ\u0010\u0010%\u001a\u0004\u0018\u00010\u00072\u0006\u0010&\u001a\u00020\u0007J\u0010\u0010\'\u001a\u0004\u0018\u00010\u00072\u0006\u0010(\u001a\u00020\u0007J\u0010\u0010)\u001a\u0004\u0018\u00010\u00072\u0006\u0010\u0018\u001a\u00020\u0007J\u0015\u0010*\u001a\u0004\u0018\u00010\t2\u0006\u0010\u0018\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\u001aJ\u0015\u0010+\u001a\u0004\u0018\u00010\"2\u0006\u0010\u0018\u001a\u00020\u0007\u00a2\u0006\u0002\u0010#R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\t0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006,"}, d2 = {"Lcom/pokerarity/scanner/util/ocr/TextParser;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "pokemonNames", "", "", "validListDescending", "", "isValidStardust", "", "v", "levenshtein", "lhs", "", "rhs", "loadPokemonNames", "makeDate", "Ljava/util/Date;", "year", "month", "day", "parseBottomDate", "text", "parseCP", "(Ljava/lang/String;)Ljava/lang/Integer;", "parseCandyName", "parseDate", "allText", "parseGender", "parseHPPair", "Lkotlin/Pair;", "parseHeight", "", "(Ljava/lang/String;)Ljava/lang/Float;", "parseMegaEnergy", "parseName", "ocrText", "parseNameFromFullText", "fullText", "parseSizeTag", "parseStardust", "parseWeight", "app_debug"})
public final class TextParser {
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.String> pokemonNames = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.Integer> validListDescending = null;
    
    public TextParser(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer parseCP(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.Pair<java.lang.Integer, java.lang.Integer> parseHPPair(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.util.Date parseDate(@org.jetbrains.annotations.NotNull()
    java.lang.String allText) {
        return null;
    }
    
    private final java.util.Date makeDate(int year, int month, int day) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.util.Date parseBottomDate(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String parseCandyName(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String parseNameFromFullText(@org.jetbrains.annotations.NotNull()
    java.lang.String fullText) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String parseName(@org.jetbrains.annotations.NotNull()
    java.lang.String ocrText) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer parseMegaEnergy(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float parseWeight(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Float parseHeight(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String parseGender(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String parseSizeTag(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    /**
     * Power Up (Güçlendirme) maliyetini ayrıştırır.
     * OCR gürültüsünü temizlemek için virgülleri ve rakam dışı karakterleri atar.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer parseStardust(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
        return null;
    }
    
    private final boolean isValidStardust(int v) {
        return false;
    }
    
    private final java.util.List<java.lang.String> loadPokemonNames(android.content.Context context) {
        return null;
    }
    
    private final int levenshtein(java.lang.CharSequence lhs, java.lang.CharSequence rhs) {
        return 0;
    }
}