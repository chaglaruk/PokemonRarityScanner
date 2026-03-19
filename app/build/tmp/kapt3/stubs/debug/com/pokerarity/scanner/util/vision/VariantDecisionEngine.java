package com.pokerarity.scanner.util.vision;

import android.content.Context;
import android.graphics.Bitmap;
import com.pokerarity.scanner.data.model.PokemonData;
import com.pokerarity.scanner.data.model.VisualFeatures;
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry;
import java.util.Locale;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000^\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\"\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u0000 $2\u00020\u0001:\u0002#$B\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\"\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\b2\u0006\u0010\n\u001a\u00020\u000b2\b\b\u0002\u0010\f\u001a\u00020\bH\u0002J\"\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u000e2\b\u0010\n\u001a\u0004\u0018\u00010\u000b2\u0006\u0010\f\u001a\u00020\bH\u0002J\u001a\u0010\u0010\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u000e2\b\u0010\n\u001a\u0004\u0018\u00010\u000bH\u0002J\u0016\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\b0\u00122\u0006\u0010\u000f\u001a\u00020\u000eH\u0002J\u0016\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u000f\u001a\u00020\u000eJ\u0012\u0010\u0017\u001a\u00020\u00182\b\u0010\u0019\u001a\u0004\u0018\u00010\bH\u0002J\u0018\u0010\u001a\u001a\u00020\u001b2\u0006\u0010\u001c\u001a\u00020\u001b2\b\u0010\n\u001a\u0004\u0018\u00010\u000bJ,\u0010\u001d\u001a\u001e\u0012\u0004\u0012\u00020\b\u0012\u0004\u0012\u00020\b0\u001ej\u000e\u0012\u0004\u0012\u00020\b\u0012\u0004\u0012\u00020\b`\u001f2\u0006\u0010\t\u001a\u00020\bH\u0002J&\u0010 \u001a\u0004\u0018\u00010\u000b2\u0006\u0010\u000f\u001a\u00020\u000e2\b\u0010!\u001a\u0004\u0018\u00010\u000b2\b\u0010\"\u001a\u0004\u0018\u00010\u000bH\u0002R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006%"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VariantDecisionEngine;", "", "context", "Landroid/content/Context;", "classifier", "Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier;", "(Landroid/content/Context;Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier;)V", "appendClassifierFields", "", "raw", "match", "Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;", "prefix", "appendClassifierTrace", "Lcom/pokerarity/scanner/data/model/PokemonData;", "pokemon", "applyClassifierSpecies", "buildHints", "", "classify", "Lcom/pokerarity/scanner/util/vision/VariantDecisionEngine$ClassificationResult;", "bitmap", "Landroid/graphics/Bitmap;", "isUnknownSpecies", "", "value", "mergeVisualFeatures", "Lcom/pokerarity/scanner/data/model/VisualFeatures;", "visualFeatures", "parseRawOcrFields", "Ljava/util/LinkedHashMap;", "Lkotlin/collections/LinkedHashMap;", "resolveVariantClassifierMatch", "globalMatch", "speciesMatch", "ClassificationResult", "Companion", "app_debug"})
public final class VariantDecisionEngine {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier classifier = null;
    private static final float CLASSIFIER_SPECIES_CONFIDENCE = 0.72F;
    private static final float CLASSIFIER_SPECIES_CONFIDENCE_FAMILY = 0.62F;
    private static final float CLASSIFIER_VARIANT_CONFIDENCE = 0.66F;
    private static final float CLASSIFIER_VARIANT_CONFIDENCE_SPECIES = 0.52F;
    private static final float CLASSIFIER_FORM_CONFIDENCE_SPECIES = 0.34F;
    private static final float CLASSIFIER_COSTUME_RESCUE_CONFIDENCE_SPECIES = 0.44F;
    private static final float CLASSIFIER_BASE_SHINY_CONFIDENCE = 0.8F;
    private static final float CLASSIFIER_VARIANT_CONSENSUS_MARGIN = 0.03F;
    private static final float CLASSIFIER_FAMILY_COSTUME_RESCUE_CONFIDENCE = 0.43F;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.util.vision.VariantDecisionEngine.Companion Companion = null;
    
    public VariantDecisionEngine(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.util.vision.VariantPrototypeClassifier classifier) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.vision.VariantDecisionEngine.ClassificationResult classify(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.PokemonData pokemon) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.VisualFeatures mergeVisualFeatures(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.VisualFeatures visualFeatures, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult match) {
        return null;
    }
    
    private final java.util.Set<java.lang.String> buildHints(com.pokerarity.scanner.data.model.PokemonData pokemon) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.model.PokemonData applyClassifierSpecies(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult match) {
        return null;
    }
    
    private final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult resolveVariantClassifierMatch(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult globalMatch, com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult speciesMatch) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.model.PokemonData appendClassifierTrace(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult match, java.lang.String prefix) {
        return null;
    }
    
    private final java.lang.String appendClassifierFields(java.lang.String raw, com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult match, java.lang.String prefix) {
        return null;
    }
    
    private final java.util.LinkedHashMap<java.lang.String, java.lang.String> parseRawOcrFields(java.lang.String raw) {
        return null;
    }
    
    private final boolean isUnknownSpecies(java.lang.String value) {
        return false;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u000f\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B+\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\u0002\u0010\bJ\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\u0010\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\u000b\u0010\u0011\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\u000b\u0010\u0012\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J7\u0010\u0013\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u0005H\u00c6\u0001J\u0013\u0010\u0014\u001a\u00020\u00152\b\u0010\u0016\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0017\u001a\u00020\u0018H\u00d6\u0001J\t\u0010\u0019\u001a\u00020\u001aH\u00d6\u0001R\u0013\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0013\u0010\u0007\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\nR\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\n\u00a8\u0006\u001b"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VariantDecisionEngine$ClassificationResult;", "", "pokemon", "Lcom/pokerarity/scanner/data/model/PokemonData;", "globalMatch", "Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;", "speciesMatch", "resolvedMatch", "(Lcom/pokerarity/scanner/data/model/PokemonData;Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;)V", "getGlobalMatch", "()Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;", "getPokemon", "()Lcom/pokerarity/scanner/data/model/PokemonData;", "getResolvedMatch", "getSpeciesMatch", "component1", "component2", "component3", "component4", "copy", "equals", "", "other", "hashCode", "", "toString", "", "app_debug"})
    public static final class ClassificationResult {
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.data.model.PokemonData pokemon = null;
        @org.jetbrains.annotations.Nullable()
        private final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult globalMatch = null;
        @org.jetbrains.annotations.Nullable()
        private final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult speciesMatch = null;
        @org.jetbrains.annotations.Nullable()
        private final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult resolvedMatch = null;
        
        public ClassificationResult(@org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.model.PokemonData pokemon, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult globalMatch, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult speciesMatch, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult resolvedMatch) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.model.PokemonData getPokemon() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult getGlobalMatch() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult getSpeciesMatch() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult getResolvedMatch() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.model.PokemonData component1() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult component2() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult component3() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.VariantDecisionEngine.ClassificationResult copy(@org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.model.PokemonData pokemon, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult globalMatch, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult speciesMatch, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult resolvedMatch) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\t\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\r"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VariantDecisionEngine$Companion;", "", "()V", "CLASSIFIER_BASE_SHINY_CONFIDENCE", "", "CLASSIFIER_COSTUME_RESCUE_CONFIDENCE_SPECIES", "CLASSIFIER_FAMILY_COSTUME_RESCUE_CONFIDENCE", "CLASSIFIER_FORM_CONFIDENCE_SPECIES", "CLASSIFIER_SPECIES_CONFIDENCE", "CLASSIFIER_SPECIES_CONFIDENCE_FAMILY", "CLASSIFIER_VARIANT_CONFIDENCE", "CLASSIFIER_VARIANT_CONFIDENCE_SPECIES", "CLASSIFIER_VARIANT_CONSENSUS_MARGIN", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}