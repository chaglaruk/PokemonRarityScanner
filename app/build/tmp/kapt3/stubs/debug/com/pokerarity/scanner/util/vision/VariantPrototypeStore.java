package com.pokerarity.scanner.util.vision;

import android.content.Context;
import com.google.gson.Gson;
import com.pokerarity.scanner.data.model.VariantCatalogEntry;
import com.pokerarity.scanner.data.repository.VariantCatalogLoader;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000F\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010$\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u001e\n\u0002\b\u0004\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0003\u0018\u0019\u001aB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\f\u001a\u00020\u00072\u0006\u0010\r\u001a\u00020\u00072\b\u0010\u000e\u001a\u0004\u0018\u00010\u000fJ\u0010\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0013H\u0002J\u0014\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u0006\u0010\u0012\u001a\u00020\u0013J\"\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u0006\u0010\u0012\u001a\u00020\u00132\f\u0010\u0016\u001a\b\u0012\u0004\u0012\u00020\u00040\u0017R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R \u0010\b\u001a\u0014\u0012\u0004\u0012\u00020\u0004\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00070\u00060\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001b"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VariantPrototypeStore;", "", "()V", "ASSET_PATH", "", "allEntries", "", "Lcom/pokerarity/scanner/util/vision/VariantPrototypeStore$Entry;", "bySpecies", "", "loaded", "", "applyCatalog", "entry", "catalog", "Lcom/pokerarity/scanner/data/model/VariantCatalogEntry;", "ensureLoaded", "", "context", "Landroid/content/Context;", "entries", "entriesForSpecies", "species", "", "Entry", "Payload", "PrototypeFeatures", "PokeRarityScanner-v1.8.2_debug"})
public final class VariantPrototypeStore {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ASSET_PATH = "data/variant_classifier_model.json";
    private static boolean loaded = false;
    @org.jetbrains.annotations.NotNull()
    private static java.util.List<com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry> allEntries;
    @org.jetbrains.annotations.NotNull()
    private static java.util.Map<java.lang.String, ? extends java.util.List<com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry>> bySpecies;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.util.vision.VariantPrototypeStore INSTANCE = null;
    
    private VariantPrototypeStore() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry> entries(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry> entriesForSpecies(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    java.util.Collection<java.lang.String> species) {
        return null;
    }
    
    private final void ensureLoaded(android.content.Context context) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry applyCatalog(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry entry, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.data.model.VariantCatalogEntry catalog) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00008\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b+\b\u0086\b\u0018\u00002\u00020\u0001B\u009f\u0001\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u0005\u0012\u0006\u0010\b\u001a\u00020\u0005\u0012\u0006\u0010\t\u001a\u00020\u0005\u0012\u0006\u0010\n\u001a\u00020\u000b\u0012\u0006\u0010\f\u001a\u00020\u000b\u0012\u0006\u0010\r\u001a\u00020\u0005\u0012\u000e\b\u0002\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00050\u000f\u0012\b\b\u0002\u0010\u0010\u001a\u00020\u000b\u0012\n\b\u0002\u0010\u0011\u001a\u0004\u0018\u00010\u0012\u0012\u000e\b\u0002\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00050\u000f\u0012\u0006\u0010\u0014\u001a\u00020\u0005\u0012\u0006\u0010\u0015\u001a\u00020\u0003\u0012\u0006\u0010\u0016\u001a\u00020\u0017\u00a2\u0006\u0002\u0010\u0018J\t\u0010-\u001a\u00020\u0003H\u00c6\u0003J\u000f\u0010.\u001a\b\u0012\u0004\u0012\u00020\u00050\u000fH\u00c6\u0003J\t\u0010/\u001a\u00020\u000bH\u00c6\u0003J\u000b\u00100\u001a\u0004\u0018\u00010\u0012H\u00c6\u0003J\u000f\u00101\u001a\b\u0012\u0004\u0012\u00020\u00050\u000fH\u00c6\u0003J\t\u00102\u001a\u00020\u0005H\u00c6\u0003J\t\u00103\u001a\u00020\u0003H\u00c6\u0003J\t\u00104\u001a\u00020\u0017H\u00c6\u0003J\t\u00105\u001a\u00020\u0005H\u00c6\u0003J\t\u00106\u001a\u00020\u0005H\u00c6\u0003J\u000b\u00107\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\t\u00108\u001a\u00020\u0005H\u00c6\u0003J\t\u00109\u001a\u00020\u0005H\u00c6\u0003J\t\u0010:\u001a\u00020\u000bH\u00c6\u0003J\t\u0010;\u001a\u00020\u000bH\u00c6\u0003J\t\u0010<\u001a\u00020\u0005H\u00c6\u0003J\u00b9\u0001\u0010=\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00052\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u00052\b\b\u0002\u0010\b\u001a\u00020\u00052\b\b\u0002\u0010\t\u001a\u00020\u00052\b\b\u0002\u0010\n\u001a\u00020\u000b2\b\b\u0002\u0010\f\u001a\u00020\u000b2\b\b\u0002\u0010\r\u001a\u00020\u00052\u000e\b\u0002\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00050\u000f2\b\b\u0002\u0010\u0010\u001a\u00020\u000b2\n\b\u0002\u0010\u0011\u001a\u0004\u0018\u00010\u00122\u000e\b\u0002\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00050\u000f2\b\b\u0002\u0010\u0014\u001a\u00020\u00052\b\b\u0002\u0010\u0015\u001a\u00020\u00032\b\b\u0002\u0010\u0016\u001a\u00020\u0017H\u00c6\u0001J\u0013\u0010>\u001a\u00020\u000b2\b\u0010?\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010@\u001a\u00020\u0003H\u00d6\u0001J\t\u0010A\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\b\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u001cR\u0017\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00050\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001eR\u0011\u0010\u0014\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010\u001aR\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u001aR\u0017\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00050\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b!\u0010\u001eR\u0011\u0010\u0010\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\"\u0010#R\u0011\u0010\f\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010#R\u0011\u0010\n\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010#R\u0011\u0010\u0016\u001a\u00020\u0017\u00a2\u0006\b\n\u0000\u001a\u0004\b$\u0010%R\u0013\u0010\u0011\u001a\u0004\u0018\u00010\u0012\u00a2\u0006\b\n\u0000\u001a\u0004\b&\u0010\'R\u0011\u0010\u0015\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b(\u0010\u001cR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b)\u0010\u001aR\u0011\u0010\t\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b*\u0010\u001aR\u0013\u0010\u0007\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b+\u0010\u001aR\u0011\u0010\r\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b,\u0010\u001a\u00a8\u0006B"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VariantPrototypeStore$Entry;", "", "dex", "", "species", "", "formId", "variantId", "assetKey", "spriteKey", "isShiny", "", "isCostumeLike", "variantType", "eventTags", "", "hasEventMetadata", "releaseWindow", "Lcom/pokerarity/scanner/data/model/ReleaseWindow;", "gameMasterCostumeForms", "filename", "sampleCount", "prototype", "Lcom/pokerarity/scanner/util/vision/VariantPrototypeStore$PrototypeFeatures;", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZLjava/lang/String;Ljava/util/List;ZLcom/pokerarity/scanner/data/model/ReleaseWindow;Ljava/util/List;Ljava/lang/String;ILcom/pokerarity/scanner/util/vision/VariantPrototypeStore$PrototypeFeatures;)V", "getAssetKey", "()Ljava/lang/String;", "getDex", "()I", "getEventTags", "()Ljava/util/List;", "getFilename", "getFormId", "getGameMasterCostumeForms", "getHasEventMetadata", "()Z", "getPrototype", "()Lcom/pokerarity/scanner/util/vision/VariantPrototypeStore$PrototypeFeatures;", "getReleaseWindow", "()Lcom/pokerarity/scanner/data/model/ReleaseWindow;", "getSampleCount", "getSpecies", "getSpriteKey", "getVariantId", "getVariantType", "component1", "component10", "component11", "component12", "component13", "component14", "component15", "component16", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "equals", "other", "hashCode", "toString", "PokeRarityScanner-v1.8.2_debug"})
    public static final class Entry {
        private final int dex = 0;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String species = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String formId = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String variantId = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String assetKey = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String spriteKey = null;
        private final boolean isShiny = false;
        private final boolean isCostumeLike = false;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String variantType = null;
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<java.lang.String> eventTags = null;
        private final boolean hasEventMetadata = false;
        @org.jetbrains.annotations.Nullable()
        private final com.pokerarity.scanner.data.model.ReleaseWindow releaseWindow = null;
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<java.lang.String> gameMasterCostumeForms = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String filename = null;
        private final int sampleCount = 0;
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.util.vision.VariantPrototypeStore.PrototypeFeatures prototype = null;
        
        public Entry(int dex, @org.jetbrains.annotations.NotNull()
        java.lang.String species, @org.jetbrains.annotations.NotNull()
        java.lang.String formId, @org.jetbrains.annotations.Nullable()
        java.lang.String variantId, @org.jetbrains.annotations.NotNull()
        java.lang.String assetKey, @org.jetbrains.annotations.NotNull()
        java.lang.String spriteKey, boolean isShiny, boolean isCostumeLike, @org.jetbrains.annotations.NotNull()
        java.lang.String variantType, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.String> eventTags, boolean hasEventMetadata, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.data.model.ReleaseWindow releaseWindow, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.String> gameMasterCostumeForms, @org.jetbrains.annotations.NotNull()
        java.lang.String filename, int sampleCount, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.util.vision.VariantPrototypeStore.PrototypeFeatures prototype) {
            super();
        }
        
        public final int getDex() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getSpecies() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getFormId() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getVariantId() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getAssetKey() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getSpriteKey() {
            return null;
        }
        
        public final boolean isShiny() {
            return false;
        }
        
        public final boolean isCostumeLike() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getVariantType() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.String> getEventTags() {
            return null;
        }
        
        public final boolean getHasEventMetadata() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final com.pokerarity.scanner.data.model.ReleaseWindow getReleaseWindow() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.String> getGameMasterCostumeForms() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getFilename() {
            return null;
        }
        
        public final int getSampleCount() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.VariantPrototypeStore.PrototypeFeatures getPrototype() {
            return null;
        }
        
        public final int component1() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.String> component10() {
            return null;
        }
        
        public final boolean component11() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final com.pokerarity.scanner.data.model.ReleaseWindow component12() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.String> component13() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component14() {
            return null;
        }
        
        public final int component15() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.VariantPrototypeStore.PrototypeFeatures component16() {
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
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component5() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component6() {
            return null;
        }
        
        public final boolean component7() {
            return false;
        }
        
        public final boolean component8() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component9() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry copy(int dex, @org.jetbrains.annotations.NotNull()
        java.lang.String species, @org.jetbrains.annotations.NotNull()
        java.lang.String formId, @org.jetbrains.annotations.Nullable()
        java.lang.String variantId, @org.jetbrains.annotations.NotNull()
        java.lang.String assetKey, @org.jetbrains.annotations.NotNull()
        java.lang.String spriteKey, boolean isShiny, boolean isCostumeLike, @org.jetbrains.annotations.NotNull()
        java.lang.String variantType, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.String> eventTags, boolean hasEventMetadata, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.data.model.ReleaseWindow releaseWindow, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.String> gameMasterCostumeForms, @org.jetbrains.annotations.NotNull()
        java.lang.String filename, int sampleCount, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.util.vision.VariantPrototypeStore.PrototypeFeatures prototype) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0012\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0082\b\u0018\u00002\u00020\u0001B;\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\u0006\u0010\u0007\u001a\u00020\u0003\u0012\u0006\u0010\b\u001a\u00020\u0003\u0012\f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\n\u00a2\u0006\u0002\u0010\fJ\t\u0010\u0016\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0017\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0018\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0019\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u001a\u001a\u00020\u0003H\u00c6\u0003J\u000f\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u000b0\nH\u00c6\u0003JK\u0010\u001c\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00052\b\b\u0002\u0010\u0007\u001a\u00020\u00032\b\b\u0002\u0010\b\u001a\u00020\u00032\u000e\b\u0002\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nH\u00c6\u0001J\u0013\u0010\u001d\u001a\u00020\u001e2\b\u0010\u001f\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010 \u001a\u00020\u0003H\u00d6\u0001J\t\u0010!\u001a\u00020\u0005H\u00d6\u0001R\u0017\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0007\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0012R\u0011\u0010\b\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0010R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0010\u00a8\u0006\""}, d2 = {"Lcom/pokerarity/scanner/util/vision/VariantPrototypeStore$Payload;", "", "version", "", "generatedAt", "", "source", "entryCount", "speciesCount", "entries", "", "Lcom/pokerarity/scanner/util/vision/VariantPrototypeStore$Entry;", "(ILjava/lang/String;Ljava/lang/String;IILjava/util/List;)V", "getEntries", "()Ljava/util/List;", "getEntryCount", "()I", "getGeneratedAt", "()Ljava/lang/String;", "getSource", "getSpeciesCount", "getVersion", "component1", "component2", "component3", "component4", "component5", "component6", "copy", "equals", "", "other", "hashCode", "toString", "PokeRarityScanner-v1.8.2_debug"})
    static final class Payload {
        private final int version = 0;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String generatedAt = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String source = null;
        private final int entryCount = 0;
        private final int speciesCount = 0;
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry> entries = null;
        
        public Payload(int version, @org.jetbrains.annotations.NotNull()
        java.lang.String generatedAt, @org.jetbrains.annotations.NotNull()
        java.lang.String source, int entryCount, int speciesCount, @org.jetbrains.annotations.NotNull()
        java.util.List<com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry> entries) {
            super();
        }
        
        public final int getVersion() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getGeneratedAt() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getSource() {
            return null;
        }
        
        public final int getEntryCount() {
            return 0;
        }
        
        public final int getSpeciesCount() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry> getEntries() {
            return null;
        }
        
        public final int component1() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component3() {
            return null;
        }
        
        public final int component4() {
            return 0;
        }
        
        public final int component5() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry> component6() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.VariantPrototypeStore.Payload copy(int version, @org.jetbrains.annotations.NotNull()
        java.lang.String generatedAt, @org.jetbrains.annotations.NotNull()
        java.lang.String source, int entryCount, int speciesCount, @org.jetbrains.annotations.NotNull()
        java.util.List<com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry> entries) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u0007\n\u0002\b\u001e\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001Bk\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\f\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u0012\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u0012\f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u0012\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u0012\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u0012\u0006\u0010\f\u001a\u00020\u0007\u0012\u0006\u0010\r\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\u000eJ\t\u0010\u001b\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u001c\u001a\u00020\u0003H\u00c6\u0003J\u000f\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006H\u00c6\u0003J\u000f\u0010\u001e\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006H\u00c6\u0003J\u000f\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006H\u00c6\u0003J\u000f\u0010 \u001a\b\u0012\u0004\u0012\u00020\u00070\u0006H\u00c6\u0003J\u000f\u0010!\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006H\u00c6\u0003J\t\u0010\"\u001a\u00020\u0007H\u00c6\u0003J\t\u0010#\u001a\u00020\u0007H\u00c6\u0003J\u0081\u0001\u0010$\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\u000e\b\u0002\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u000e\b\u0002\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u000e\b\u0002\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u000e\b\u0002\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u000e\b\u0002\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\b\b\u0002\u0010\f\u001a\u00020\u00072\b\b\u0002\u0010\r\u001a\u00020\u0007H\u00c6\u0001J\u0013\u0010%\u001a\u00020&2\b\u0010\'\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010(\u001a\u00020)H\u00d6\u0001J\t\u0010*\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\r\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0017\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0010R\u0017\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0014R\u0011\u0010\f\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0012R\u0017\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0014R\u0017\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u0014R\u0017\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0014\u00a8\u0006+"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VariantPrototypeStore$PrototypeFeatures;", "", "aHash", "", "dHash", "edge", "", "", "fullHist", "headHist", "upperHist", "bodyHist", "foregroundRatio", "aspectRatio", "(Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/List;FF)V", "getAHash", "()Ljava/lang/String;", "getAspectRatio", "()F", "getBodyHist", "()Ljava/util/List;", "getDHash", "getEdge", "getForegroundRatio", "getFullHist", "getHeadHist", "getUpperHist", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "equals", "", "other", "hashCode", "", "toString", "PokeRarityScanner-v1.8.2_debug"})
    public static final class PrototypeFeatures {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String aHash = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String dHash = null;
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<java.lang.Float> edge = null;
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<java.lang.Float> fullHist = null;
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<java.lang.Float> headHist = null;
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<java.lang.Float> upperHist = null;
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<java.lang.Float> bodyHist = null;
        private final float foregroundRatio = 0.0F;
        private final float aspectRatio = 0.0F;
        
        public PrototypeFeatures(@org.jetbrains.annotations.NotNull()
        java.lang.String aHash, @org.jetbrains.annotations.NotNull()
        java.lang.String dHash, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Float> edge, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Float> fullHist, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Float> headHist, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Float> upperHist, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Float> bodyHist, float foregroundRatio, float aspectRatio) {
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
        public final java.util.List<java.lang.Float> getEdge() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.Float> getFullHist() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.Float> getHeadHist() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.Float> getUpperHist() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.Float> getBodyHist() {
            return null;
        }
        
        public final float getForegroundRatio() {
            return 0.0F;
        }
        
        public final float getAspectRatio() {
            return 0.0F;
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
        public final java.util.List<java.lang.Float> component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.Float> component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.Float> component5() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.Float> component6() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.Float> component7() {
            return null;
        }
        
        public final float component8() {
            return 0.0F;
        }
        
        public final float component9() {
            return 0.0F;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.VariantPrototypeStore.PrototypeFeatures copy(@org.jetbrains.annotations.NotNull()
        java.lang.String aHash, @org.jetbrains.annotations.NotNull()
        java.lang.String dHash, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Float> edge, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Float> fullHist, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Float> headHist, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Float> upperHist, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Float> bodyHist, float foregroundRatio, float aspectRatio) {
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