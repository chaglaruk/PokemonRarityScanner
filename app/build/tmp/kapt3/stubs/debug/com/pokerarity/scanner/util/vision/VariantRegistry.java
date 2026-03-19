package com.pokerarity.scanner.util.vision;

import android.content.Context;
import org.json.JSONObject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\"\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0002J\u0018\u0010\r\u001a\u00020\b2\u0006\u0010\u000b\u001a\u00020\f2\b\u0010\u000e\u001a\u0004\u0018\u00010\u0004R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00040\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000f"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VariantRegistry;", "", "()V", "ASSET_PATH", "", "costumeLikeSpecies", "", "loaded", "", "ensureLoaded", "", "context", "Landroid/content/Context;", "hasCostumeLikeSpecies", "species", "app_debug"})
public final class VariantRegistry {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ASSET_PATH = "data/variant_registry.json";
    private static boolean loaded = false;
    @org.jetbrains.annotations.NotNull()
    private static java.util.Set<java.lang.String> costumeLikeSpecies;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.util.vision.VariantRegistry INSTANCE = null;
    
    private VariantRegistry() {
        super();
    }
    
    public final boolean hasCostumeLikeSpecies(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.Nullable()
    java.lang.String species) {
        return false;
    }
    
    private final void ensureLoaded(android.content.Context context) {
    }
}