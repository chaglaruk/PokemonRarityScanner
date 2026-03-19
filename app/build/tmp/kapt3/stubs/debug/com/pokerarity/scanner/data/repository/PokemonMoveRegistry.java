package com.pokerarity.scanner.data.repository;

import android.content.Context;
import org.json.JSONObject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010 \n\u0000\n\u0002\u0010$\n\u0000\n\u0002\u0010\"\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\b\n\u0000\n\u0002\u0010\r\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0002\b\u0004\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u0010H\u0002J\u001a\u0010\u0011\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u000f\u001a\u00020\u00102\b\u0010\u0012\u001a\u0004\u0018\u00010\u0004J\u001e\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00040\b2\u0006\u0010\u000f\u001a\u00020\u00102\b\u0010\u0014\u001a\u0004\u0018\u00010\u0004J\u0018\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u00182\u0006\u0010\u0019\u001a\u00020\u0018H\u0002J\"\u0010\u001a\u001a\u00020\u001b2\u0006\u0010\u000f\u001a\u00020\u00102\b\u0010\u001c\u001a\u0004\u0018\u00010\u00042\b\u0010\u0014\u001a\u0004\u0018\u00010\u0004J\u0010\u0010\u001d\u001a\u00020\u00042\u0006\u0010\u001e\u001a\u00020\u0004H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00040\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R \u0010\t\u001a\u0014\u0012\u0004\u0012\u00020\u0004\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00040\b0\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R \u0010\u000b\u001a\u0014\u0012\u0004\u0012\u00020\u0004\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00040\f0\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001f"}, d2 = {"Lcom/pokerarity/scanner/data/repository/PokemonMoveRegistry;", "", "()V", "ASSET_PATH", "", "loaded", "", "moveKeysByLength", "", "moveToSpecies", "", "speciesToMoves", "", "ensureLoaded", "", "context", "Landroid/content/Context;", "extractMoveHint", "rawText", "getSpeciesForMove", "normalizedMove", "levenshtein", "", "lhs", "", "rhs", "moveMatchScore", "", "species", "normalize", "value", "app_debug"})
public final class PokemonMoveRegistry {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ASSET_PATH = "data/pokemon_moves.json";
    private static boolean loaded = false;
    @org.jetbrains.annotations.NotNull()
    private static java.util.Map<java.lang.String, ? extends java.util.Set<java.lang.String>> speciesToMoves;
    @org.jetbrains.annotations.NotNull()
    private static java.util.Map<java.lang.String, ? extends java.util.List<java.lang.String>> moveToSpecies;
    @org.jetbrains.annotations.NotNull()
    private static java.util.List<java.lang.String> moveKeysByLength;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.data.repository.PokemonMoveRegistry INSTANCE = null;
    
    private PokemonMoveRegistry() {
        super();
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String extractMoveHint(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.Nullable()
    java.lang.String rawText) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> getSpeciesForMove(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.Nullable()
    java.lang.String normalizedMove) {
        return null;
    }
    
    public final double moveMatchScore(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.Nullable()
    java.lang.String species, @org.jetbrains.annotations.Nullable()
    java.lang.String normalizedMove) {
        return 0.0;
    }
    
    private final void ensureLoaded(android.content.Context context) {
    }
    
    private final java.lang.String normalize(java.lang.String value) {
        return null;
    }
    
    private final int levenshtein(java.lang.CharSequence lhs, java.lang.CharSequence rhs) {
        return 0;
    }
}