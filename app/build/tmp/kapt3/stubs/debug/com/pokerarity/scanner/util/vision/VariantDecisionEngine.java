package com.pokerarity.scanner.util.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import com.pokerarity.scanner.data.model.FullVariantCandidate;
import com.pokerarity.scanner.data.model.FullVariantMatch;
import com.pokerarity.scanner.data.model.PokemonData;
import com.pokerarity.scanner.data.model.VisualFeatures;
import com.pokerarity.scanner.data.repository.AuthoritativeVariantDbLoader;
import com.pokerarity.scanner.data.repository.GlobalRarityLegacyLoader;
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry;
import com.pokerarity.scanner.util.ocr.ScanAuthorityLogic;
import com.pokerarity.scanner.util.ocr.TextParser;
import java.util.Locale;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0092\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\"\n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0002\b\b\u0018\u0000 H2\u00020\u0001:\u0002GHB\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\"\u0010\u0019\u001a\u00020\t2\u0006\u0010\u001a\u001a\u00020\t2\u0006\u0010\u001b\u001a\u00020\u001c2\b\b\u0002\u0010\u001d\u001a\u00020\tH\u0002J\"\u0010\u001e\u001a\u00020\u001f2\u0006\u0010 \u001a\u00020\u001f2\b\u0010\u001b\u001a\u0004\u0018\u00010\u001c2\u0006\u0010\u001d\u001a\u00020\tH\u0002J\u001a\u0010!\u001a\u00020\u001f2\u0006\u0010 \u001a\u00020\u001f2\b\u0010\"\u001a\u0004\u0018\u00010#H\u0002J\u001a\u0010$\u001a\u00020\u001f2\u0006\u0010 \u001a\u00020\u001f2\b\u0010\u001b\u001a\u0004\u0018\u00010\u001cH\u0002J \u0010%\u001a\b\u0012\u0004\u0012\u00020&0\n2\u0006\u0010\'\u001a\u00020\t2\b\u0010(\u001a\u0004\u0018\u00010\u001cH\u0002J\u0016\u0010)\u001a\b\u0012\u0004\u0012\u00020\t0*2\u0006\u0010 \u001a\u00020\u001fH\u0002J<\u0010+\u001a\u0004\u0018\u00010\t2\b\u0010,\u001a\u0004\u0018\u00010\t2\b\u0010-\u001a\u0004\u0018\u00010\t2\b\u0010.\u001a\u0004\u0018\u00010\t2\b\u0010/\u001a\u0004\u0018\u00010\t2\b\u00100\u001a\u0004\u0018\u00010\tH\u0002J\u001c\u00101\u001a\u0004\u0018\u00010\t2\u0006\u0010 \u001a\u00020\u001f2\b\u0010(\u001a\u0004\u0018\u00010\u001cH\u0002J\u0016\u00102\u001a\u0002032\u0006\u00104\u001a\u0002052\u0006\u0010 \u001a\u00020\u001fJ\u0012\u00106\u001a\u0002072\b\u00108\u001a\u0004\u0018\u00010\tH\u0002J\"\u00109\u001a\u00020:2\u0006\u0010;\u001a\u00020:2\b\u0010\"\u001a\u0004\u0018\u00010#2\b\u0010<\u001a\u0004\u0018\u00010\u001cJ\u0018\u00109\u001a\u00020:2\u0006\u0010;\u001a\u00020:2\b\u0010\u001b\u001a\u0004\u0018\u00010\u001cJ,\u0010=\u001a\u001e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\t0>j\u000e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\t`?2\u0006\u0010\u001a\u001a\u00020\tH\u0002J%\u0010@\u001a\u0004\u0018\u00010A2\f\u0010B\u001a\b\u0012\u0004\u0012\u00020\t0\n2\u0006\u0010C\u001a\u00020\tH\u0002\u00a2\u0006\u0002\u0010DJ&\u0010E\u001a\u0004\u0018\u00010\u001c2\u0006\u0010 \u001a\u00020\u001f2\b\u0010(\u001a\u0004\u0018\u00010\u001c2\b\u0010F\u001a\u0004\u0018\u00010\u001cH\u0002R-\u0010\u0007\u001a\u0014\u0012\u0004\u0012\u00020\t\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000b0\n0\b8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u000e\u0010\u000f\u001a\u0004\b\f\u0010\rR\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R-\u0010\u0010\u001a\u0014\u0012\u0004\u0012\u00020\t\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00110\n0\b8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0013\u0010\u000f\u001a\u0004\b\u0012\u0010\rR\u001b\u0010\u0014\u001a\u00020\u00158BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0018\u0010\u000f\u001a\u0004\b\u0016\u0010\u0017\u00a8\u0006I"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VariantDecisionEngine;", "", "context", "Landroid/content/Context;", "classifier", "Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier;", "(Landroid/content/Context;Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier;)V", "authoritativeVariantBySpecies", "", "", "", "Lcom/pokerarity/scanner/data/model/AuthoritativeVariantEntry;", "getAuthoritativeVariantBySpecies", "()Ljava/util/Map;", "authoritativeVariantBySpecies$delegate", "Lkotlin/Lazy;", "globalLegacyBySpecies", "Lcom/pokerarity/scanner/data/model/GlobalRarityLegacyEntry;", "getGlobalLegacyBySpecies", "globalLegacyBySpecies$delegate", "textParser", "Lcom/pokerarity/scanner/util/ocr/TextParser;", "getTextParser", "()Lcom/pokerarity/scanner/util/ocr/TextParser;", "textParser$delegate", "appendClassifierFields", "raw", "match", "Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;", "prefix", "appendClassifierTrace", "Lcom/pokerarity/scanner/data/model/PokemonData;", "pokemon", "appendFullVariantTrace", "fullMatch", "Lcom/pokerarity/scanner/data/model/FullVariantMatch;", "applyClassifierSpecies", "buildFamilyCostumeSupportCandidates", "Lcom/pokerarity/scanner/data/model/FullVariantCandidate;", "finalSpecies", "globalMatch", "buildHints", "", "chooseLockedCurrentSpecies", "rawName", "fallbackName", "parsedRawSpecies", "parsedFallbackSpecies", "storedSpecies", "chooseSpeciesScopeTarget", "classify", "Lcom/pokerarity/scanner/util/vision/VariantDecisionEngine$ClassificationResult;", "bitmap", "Landroid/graphics/Bitmap;", "isUnknownSpecies", "", "value", "mergeVisualFeatures", "Lcom/pokerarity/scanner/data/model/VisualFeatures;", "visualFeatures", "fallbackMatch", "parseRawOcrFields", "Ljava/util/LinkedHashMap;", "Lkotlin/collections/LinkedHashMap;", "parseSpeciesScore", "", "topSpecies", "species", "(Ljava/util/List;Ljava/lang/String;)Ljava/lang/Float;", "resolveVariantClassifierMatch", "speciesMatch", "ClassificationResult", "Companion", "PokeRarityScanner-v1.8.2_debug"})
public final class VariantDecisionEngine {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier classifier = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy textParser$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy authoritativeVariantBySpecies$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy globalLegacyBySpecies$delegate = null;
    private static final float CLASSIFIER_SPECIES_CONFIDENCE = 0.68F;
    private static final float CLASSIFIER_SPECIES_CONFIDENCE_FAMILY = 0.62F;
    private static final float CLASSIFIER_VARIANT_CONFIDENCE = 0.66F;
    private static final float CLASSIFIER_VARIANT_CONFIDENCE_SPECIES = 0.52F;
    private static final float CLASSIFIER_FORM_CONFIDENCE_SPECIES = 0.34F;
    private static final float CLASSIFIER_COSTUME_RESCUE_CONFIDENCE_SPECIES = 0.44F;
    private static final float CLASSIFIER_FAMILY_COSTUME_SUPPORT_CONFIDENCE = 0.58F;
    private static final float CLASSIFIER_BASE_SHINY_CONFIDENCE = 0.8F;
    private static final float CLASSIFIER_VARIANT_CONSENSUS_MARGIN = 0.03F;
    private static final float CLASSIFIER_FAMILY_COSTUME_RESCUE_CONFIDENCE = 0.52F;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.util.vision.VariantDecisionEngine.Companion Companion = null;
    
    public VariantDecisionEngine(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.util.vision.VariantPrototypeClassifier classifier) {
        super();
    }
    
    private final com.pokerarity.scanner.util.ocr.TextParser getTextParser() {
        return null;
    }
    
    private final java.util.Map<java.lang.String, java.util.List<com.pokerarity.scanner.data.model.AuthoritativeVariantEntry>> getAuthoritativeVariantBySpecies() {
        return null;
    }
    
    private final java.util.Map<java.lang.String, java.util.List<com.pokerarity.scanner.data.model.GlobalRarityLegacyEntry>> getGlobalLegacyBySpecies() {
        return null;
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
    com.pokerarity.scanner.data.model.FullVariantMatch fullMatch, @org.jetbrains.annotations.Nullable()
    com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult fallbackMatch) {
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
    
    private final java.lang.String chooseSpeciesScopeTarget(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult globalMatch) {
        return null;
    }
    
    private final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult resolveVariantClassifierMatch(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult globalMatch, com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult speciesMatch) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.model.PokemonData appendClassifierTrace(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult match, java.lang.String prefix) {
        return null;
    }
    
    private final java.util.List<com.pokerarity.scanner.data.model.FullVariantCandidate> buildFamilyCostumeSupportCandidates(java.lang.String finalSpecies, com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult globalMatch) {
        return null;
    }
    
    private final java.lang.String chooseLockedCurrentSpecies(java.lang.String rawName, java.lang.String fallbackName, java.lang.String parsedRawSpecies, java.lang.String parsedFallbackSpecies, java.lang.String storedSpecies) {
        return null;
    }
    
    private final java.lang.String appendClassifierFields(java.lang.String raw, com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult match, java.lang.String prefix) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.model.PokemonData appendFullVariantTrace(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.data.model.FullVariantMatch fullMatch) {
        return null;
    }
    
    private final java.util.LinkedHashMap<java.lang.String, java.lang.String> parseRawOcrFields(java.lang.String raw) {
        return null;
    }
    
    private final java.lang.Float parseSpeciesScore(java.util.List<java.lang.String> topSpecies, java.lang.String species) {
        return null;
    }
    
    private final boolean isUnknownSpecies(java.lang.String value) {
        return false;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0010\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B5\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\u0005\u0012\b\u0010\b\u001a\u0004\u0018\u00010\t\u00a2\u0006\u0002\u0010\nJ\t\u0010\u0013\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\u0014\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\u000b\u0010\u0015\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\u000b\u0010\u0016\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\u000b\u0010\u0017\u001a\u0004\u0018\u00010\tH\u00c6\u0003JC\u0010\u0018\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\tH\u00c6\u0001J\u0013\u0010\u0019\u001a\u00020\u001a2\b\u0010\u001b\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u001c\u001a\u00020\u001dH\u00d6\u0001J\t\u0010\u001e\u001a\u00020\u001fH\u00d6\u0001R\u0013\u0010\b\u001a\u0004\u0018\u00010\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0013\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0013\u0010\u0007\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u000eR\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u000e\u00a8\u0006 "}, d2 = {"Lcom/pokerarity/scanner/util/vision/VariantDecisionEngine$ClassificationResult;", "", "pokemon", "Lcom/pokerarity/scanner/data/model/PokemonData;", "globalMatch", "Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;", "speciesMatch", "resolvedMatch", "fullMatch", "Lcom/pokerarity/scanner/data/model/FullVariantMatch;", "(Lcom/pokerarity/scanner/data/model/PokemonData;Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;Lcom/pokerarity/scanner/data/model/FullVariantMatch;)V", "getFullMatch", "()Lcom/pokerarity/scanner/data/model/FullVariantMatch;", "getGlobalMatch", "()Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;", "getPokemon", "()Lcom/pokerarity/scanner/data/model/PokemonData;", "getResolvedMatch", "getSpeciesMatch", "component1", "component2", "component3", "component4", "component5", "copy", "equals", "", "other", "hashCode", "", "toString", "", "PokeRarityScanner-v1.8.2_debug"})
    public static final class ClassificationResult {
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.data.model.PokemonData pokemon = null;
        @org.jetbrains.annotations.Nullable()
        private final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult globalMatch = null;
        @org.jetbrains.annotations.Nullable()
        private final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult speciesMatch = null;
        @org.jetbrains.annotations.Nullable()
        private final com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult resolvedMatch = null;
        @org.jetbrains.annotations.Nullable()
        private final com.pokerarity.scanner.data.model.FullVariantMatch fullMatch = null;
        
        public ClassificationResult(@org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.model.PokemonData pokemon, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult globalMatch, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult speciesMatch, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult resolvedMatch, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.data.model.FullVariantMatch fullMatch) {
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
        
        @org.jetbrains.annotations.Nullable()
        public final com.pokerarity.scanner.data.model.FullVariantMatch getFullMatch() {
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
        
        @org.jetbrains.annotations.Nullable()
        public final com.pokerarity.scanner.data.model.FullVariantMatch component5() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.vision.VariantDecisionEngine.ClassificationResult copy(@org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.model.PokemonData pokemon, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult globalMatch, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult speciesMatch, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult resolvedMatch, @org.jetbrains.annotations.Nullable()
        com.pokerarity.scanner.data.model.FullVariantMatch fullMatch) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\n\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000e"}, d2 = {"Lcom/pokerarity/scanner/util/vision/VariantDecisionEngine$Companion;", "", "()V", "CLASSIFIER_BASE_SHINY_CONFIDENCE", "", "CLASSIFIER_COSTUME_RESCUE_CONFIDENCE_SPECIES", "CLASSIFIER_FAMILY_COSTUME_RESCUE_CONFIDENCE", "CLASSIFIER_FAMILY_COSTUME_SUPPORT_CONFIDENCE", "CLASSIFIER_FORM_CONFIDENCE_SPECIES", "CLASSIFIER_SPECIES_CONFIDENCE", "CLASSIFIER_SPECIES_CONFIDENCE_FAMILY", "CLASSIFIER_VARIANT_CONFIDENCE", "CLASSIFIER_VARIANT_CONFIDENCE_SPECIES", "CLASSIFIER_VARIANT_CONSENSUS_MARGIN", "PokeRarityScanner-v1.8.2_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}