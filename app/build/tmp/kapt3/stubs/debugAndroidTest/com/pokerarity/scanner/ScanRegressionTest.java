package com.pokerarity.scanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.pokerarity.scanner.data.model.PokemonData;
import com.pokerarity.scanner.data.model.RarityScore;
import com.pokerarity.scanner.data.model.VisualFeatures;
import com.pokerarity.scanner.data.repository.RarityCalculator;
import com.pokerarity.scanner.util.ocr.OCRProcessor;
import com.pokerarity.scanner.util.ocr.SpeciesRefiner;
import com.pokerarity.scanner.util.vision.VariantDecisionEngine;
import com.pokerarity.scanner.util.vision.VisualFeatureDetector;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;

@org.junit.runner.RunWith(value = androidx.test.ext.junit.runners.AndroidJUnit4.class)
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0088\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0007\b\u0007\u0018\u00002\u00020\u0001:\u00044567B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\u0004H\u0002J\u001e\u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\t2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eH\u0002J\u001a\u0010\u000f\u001a\u0004\u0018\u00010\u00102\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\nH\u0002J\u0018\u0010\u0014\u001a\u00020\n2\u0006\u0010\u0015\u001a\u00020\n2\u0006\u0010\u0016\u001a\u00020\nH\u0002J\u0016\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\u00180\t2\u0006\u0010\u0011\u001a\u00020\u0012H\u0002JF\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\u001b\u001a\u00020\u00122\u0006\u0010\u001c\u001a\u00020\u00182\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010\u001f\u001a\u00020 2\u0006\u0010!\u001a\u00020\"2\u0006\u0010#\u001a\u00020$2\u0006\u0010%\u001a\u00020&H\u0082@\u00a2\u0006\u0002\u0010\'J\b\u0010(\u001a\u00020)H\u0007J\u001e\u0010*\u001a\u00020)2\u0006\u0010\u0011\u001a\u00020\u00122\f\u0010+\u001a\b\u0012\u0004\u0012\u00020\u001a0\tH\u0002J\u001b\u0010,\u001a\u0004\u0018\u00010-*\u00020.2\u0006\u0010\u0016\u001a\u00020\nH\u0002\u00a2\u0006\u0002\u0010/J\u001b\u00100\u001a\u0004\u0018\u000101*\u00020.2\u0006\u0010\u0016\u001a\u00020\nH\u0002\u00a2\u0006\u0002\u00102J\u0016\u00103\u001a\u0004\u0018\u00010\n*\u00020.2\u0006\u0010\u0016\u001a\u00020\nH\u0002\u00a8\u00068"}, d2 = {"Lcom/pokerarity/scanner/ScanRegressionTest;", "", "()V", "applyOcrOverrides", "Lcom/pokerarity/scanner/data/model/VisualFeatures;", "pokemon", "Lcom/pokerarity/scanner/data/model/PokemonData;", "visual", "compare", "", "", "expected", "Lcom/pokerarity/scanner/ScanRegressionTest$ExpectedResult;", "actual", "Lcom/pokerarity/scanner/ScanRegressionTest$ActualResult;", "decodeFixtureBitmap", "Landroid/graphics/Bitmap;", "context", "Landroid/content/Context;", "assetPath", "extractRawField", "rawOcrText", "key", "loadCases", "Lcom/pokerarity/scanner/ScanRegressionTest$RegressionCase;", "runCase", "Lcom/pokerarity/scanner/ScanRegressionTest$RegressionOutcome;", "fixtureContext", "case", "ocrProcessor", "Lcom/pokerarity/scanner/util/ocr/OCRProcessor;", "speciesRefiner", "Lcom/pokerarity/scanner/util/ocr/SpeciesRefiner;", "variantDecisionEngine", "Lcom/pokerarity/scanner/util/vision/VariantDecisionEngine;", "visualDetector", "Lcom/pokerarity/scanner/util/vision/VisualFeatureDetector;", "rarityCalculator", "Lcom/pokerarity/scanner/data/repository/RarityCalculator;", "(Landroid/content/Context;Lcom/pokerarity/scanner/ScanRegressionTest$RegressionCase;Lcom/pokerarity/scanner/util/ocr/OCRProcessor;Lcom/pokerarity/scanner/util/ocr/SpeciesRefiner;Lcom/pokerarity/scanner/util/vision/VariantDecisionEngine;Lcom/pokerarity/scanner/util/vision/VisualFeatureDetector;Lcom/pokerarity/scanner/data/repository/RarityCalculator;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "runRegressionFixtures", "", "writeReport", "outcomes", "optNullableBoolean", "", "Lorg/json/JSONObject;", "(Lorg/json/JSONObject;Ljava/lang/String;)Ljava/lang/Boolean;", "optNullableInt", "", "(Lorg/json/JSONObject;Ljava/lang/String;)Ljava/lang/Integer;", "optNullableString", "ActualResult", "ExpectedResult", "RegressionCase", "RegressionOutcome", "app_debugAndroidTest"})
public final class ScanRegressionTest {
    
    public ScanRegressionTest() {
        super();
    }
    
    @org.junit.Test()
    public final void runRegressionFixtures() {
    }
    
    private final java.lang.Object runCase(android.content.Context fixtureContext, com.pokerarity.scanner.ScanRegressionTest.RegressionCase p1_1523096, com.pokerarity.scanner.util.ocr.OCRProcessor ocrProcessor, com.pokerarity.scanner.util.ocr.SpeciesRefiner speciesRefiner, com.pokerarity.scanner.util.vision.VariantDecisionEngine variantDecisionEngine, com.pokerarity.scanner.util.vision.VisualFeatureDetector visualDetector, com.pokerarity.scanner.data.repository.RarityCalculator rarityCalculator, kotlin.coroutines.Continuation<? super com.pokerarity.scanner.ScanRegressionTest.RegressionOutcome> $completion) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.model.VisualFeatures applyOcrOverrides(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.data.model.VisualFeatures visual) {
        return null;
    }
    
    private final java.util.List<java.lang.String> compare(com.pokerarity.scanner.ScanRegressionTest.ExpectedResult expected, com.pokerarity.scanner.ScanRegressionTest.ActualResult actual) {
        return null;
    }
    
    private final java.util.List<com.pokerarity.scanner.ScanRegressionTest.RegressionCase> loadCases(android.content.Context context) {
        return null;
    }
    
    private final void writeReport(android.content.Context context, java.util.List<com.pokerarity.scanner.ScanRegressionTest.RegressionOutcome> outcomes) {
    }
    
    private final android.graphics.Bitmap decodeFixtureBitmap(android.content.Context context, java.lang.String assetPath) {
        return null;
    }
    
    private final java.lang.String extractRawField(java.lang.String rawOcrText, java.lang.String key) {
        return null;
    }
    
    private final java.lang.String optNullableString(org.json.JSONObject $this$optNullableString, java.lang.String key) {
        return null;
    }
    
    private final java.lang.Integer optNullableInt(org.json.JSONObject $this$optNullableInt, java.lang.String key) {
        return null;
    }
    
    private final java.lang.Boolean optNullableBoolean(org.json.JSONObject $this$optNullableBoolean, java.lang.String key) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b%\b\u0082\b\u0018\u00002\u00020\u0001B_\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\u0005\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\t\u0012\u0006\u0010\u000b\u001a\u00020\t\u0012\u0006\u0010\f\u001a\u00020\t\u0012\b\u0010\r\u001a\u0004\u0018\u00010\u0003\u0012\u0006\u0010\u000e\u001a\u00020\t\u00a2\u0006\u0002\u0010\u000fJ\u000b\u0010\u001e\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\t\u0010\u001f\u001a\u00020\tH\u00c6\u0003J\u0010\u0010 \u001a\u0004\u0018\u00010\u0005H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0013J\u0010\u0010!\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0013J\u0010\u0010\"\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0013J\t\u0010#\u001a\u00020\tH\u00c6\u0003J\t\u0010$\u001a\u00020\tH\u00c6\u0003J\t\u0010%\u001a\u00020\tH\u00c6\u0003J\t\u0010&\u001a\u00020\tH\u00c6\u0003J\u000b\u0010\'\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J|\u0010(\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u00052\b\b\u0002\u0010\b\u001a\u00020\t2\b\b\u0002\u0010\n\u001a\u00020\t2\b\b\u0002\u0010\u000b\u001a\u00020\t2\b\b\u0002\u0010\f\u001a\u00020\t2\n\b\u0002\u0010\r\u001a\u0004\u0018\u00010\u00032\b\b\u0002\u0010\u000e\u001a\u00020\tH\u00c6\u0001\u00a2\u0006\u0002\u0010)J\u0013\u0010*\u001a\u00020\t2\b\u0010+\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010,\u001a\u00020\u0005H\u00d6\u0001J\t\u0010-\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u000b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0015\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\n\n\u0002\u0010\u0014\u001a\u0004\b\u0012\u0010\u0013R\u0011\u0010\u000e\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0011R\u0015\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\n\n\u0002\u0010\u0014\u001a\u0004\b\u0016\u0010\u0013R\u0013\u0010\r\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u0011\u0010\f\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u0011R\u0011\u0010\n\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0011R\u0015\u0010\u0007\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\n\n\u0002\u0010\u0014\u001a\u0004\b\u001b\u0010\u0013R\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0011R\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u0018\u00a8\u0006."}, d2 = {"Lcom/pokerarity/scanner/ScanRegressionTest$ActualResult;", "", "species", "", "cp", "", "hp", "maxHp", "shiny", "", "lucky", "costume", "locationCard", "ivText", "datePresent", "(Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;ZZZZLjava/lang/String;Z)V", "getCostume", "()Z", "getCp", "()Ljava/lang/Integer;", "Ljava/lang/Integer;", "getDatePresent", "getHp", "getIvText", "()Ljava/lang/String;", "getLocationCard", "getLucky", "getMaxHp", "getShiny", "getSpecies", "component1", "component10", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "(Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;ZZZZLjava/lang/String;Z)Lcom/pokerarity/scanner/ScanRegressionTest$ActualResult;", "equals", "other", "hashCode", "toString", "app_debugAndroidTest"})
    static final class ActualResult {
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String species = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Integer cp = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Integer hp = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Integer maxHp = null;
        private final boolean shiny = false;
        private final boolean lucky = false;
        private final boolean costume = false;
        private final boolean locationCard = false;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String ivText = null;
        private final boolean datePresent = false;
        
        public ActualResult(@org.jetbrains.annotations.Nullable()
        java.lang.String species, @org.jetbrains.annotations.Nullable()
        java.lang.Integer cp, @org.jetbrains.annotations.Nullable()
        java.lang.Integer hp, @org.jetbrains.annotations.Nullable()
        java.lang.Integer maxHp, boolean shiny, boolean lucky, boolean costume, boolean locationCard, @org.jetbrains.annotations.Nullable()
        java.lang.String ivText, boolean datePresent) {
            super();
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getSpecies() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer getCp() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer getHp() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer getMaxHp() {
            return null;
        }
        
        public final boolean getShiny() {
            return false;
        }
        
        public final boolean getLucky() {
            return false;
        }
        
        public final boolean getCostume() {
            return false;
        }
        
        public final boolean getLocationCard() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getIvText() {
            return null;
        }
        
        public final boolean getDatePresent() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component1() {
            return null;
        }
        
        public final boolean component10() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer component2() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer component3() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer component4() {
            return null;
        }
        
        public final boolean component5() {
            return false;
        }
        
        public final boolean component6() {
            return false;
        }
        
        public final boolean component7() {
            return false;
        }
        
        public final boolean component8() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component9() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.ScanRegressionTest.ActualResult copy(@org.jetbrains.annotations.Nullable()
        java.lang.String species, @org.jetbrains.annotations.Nullable()
        java.lang.Integer cp, @org.jetbrains.annotations.Nullable()
        java.lang.Integer hp, @org.jetbrains.annotations.Nullable()
        java.lang.Integer maxHp, boolean shiny, boolean lucky, boolean costume, boolean locationCard, @org.jetbrains.annotations.Nullable()
        java.lang.String ivText, boolean datePresent) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b#\b\u0082\b\u0018\u00002\u00020\u0001B_\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\u0005\u0012\b\u0010\b\u001a\u0004\u0018\u00010\t\u0012\b\u0010\n\u001a\u0004\u0018\u00010\t\u0012\b\u0010\u000b\u001a\u0004\u0018\u00010\t\u0012\b\u0010\f\u001a\u0004\u0018\u00010\t\u0012\b\u0010\r\u001a\u0004\u0018\u00010\t\u00a2\u0006\u0002\u0010\u000eJ\u000b\u0010\u001d\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0010\u0010\u001e\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0013J\u0010\u0010\u001f\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0013J\u0010\u0010 \u001a\u0004\u0018\u00010\u0005H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0013J\u0010\u0010!\u001a\u0004\u0018\u00010\tH\u00c6\u0003\u00a2\u0006\u0002\u0010\u0010J\u0010\u0010\"\u001a\u0004\u0018\u00010\tH\u00c6\u0003\u00a2\u0006\u0002\u0010\u0010J\u0010\u0010#\u001a\u0004\u0018\u00010\tH\u00c6\u0003\u00a2\u0006\u0002\u0010\u0010J\u0010\u0010$\u001a\u0004\u0018\u00010\tH\u00c6\u0003\u00a2\u0006\u0002\u0010\u0010J\u0010\u0010%\u001a\u0004\u0018\u00010\tH\u00c6\u0003\u00a2\u0006\u0002\u0010\u0010Jz\u0010&\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\t2\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\t2\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\t2\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\t2\n\b\u0002\u0010\r\u001a\u0004\u0018\u00010\tH\u00c6\u0001\u00a2\u0006\u0002\u0010\'J\u0013\u0010(\u001a\u00020\t2\b\u0010)\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010*\u001a\u00020\u0005H\u00d6\u0001J\t\u0010+\u001a\u00020\u0003H\u00d6\u0001R\u0015\u0010\u000b\u001a\u0004\u0018\u00010\t\u00a2\u0006\n\n\u0002\u0010\u0011\u001a\u0004\b\u000f\u0010\u0010R\u0015\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\n\n\u0002\u0010\u0014\u001a\u0004\b\u0012\u0010\u0013R\u0015\u0010\r\u001a\u0004\u0018\u00010\t\u00a2\u0006\n\n\u0002\u0010\u0011\u001a\u0004\b\u0015\u0010\u0010R\u0015\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\n\n\u0002\u0010\u0014\u001a\u0004\b\u0016\u0010\u0013R\u0015\u0010\f\u001a\u0004\u0018\u00010\t\u00a2\u0006\n\n\u0002\u0010\u0011\u001a\u0004\b\u0017\u0010\u0010R\u0015\u0010\n\u001a\u0004\u0018\u00010\t\u00a2\u0006\n\n\u0002\u0010\u0011\u001a\u0004\b\u0018\u0010\u0010R\u0015\u0010\u0007\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\n\n\u0002\u0010\u0014\u001a\u0004\b\u0019\u0010\u0013R\u0015\u0010\b\u001a\u0004\u0018\u00010\t\u00a2\u0006\n\n\u0002\u0010\u0011\u001a\u0004\b\u001a\u0010\u0010R\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u001c\u00a8\u0006,"}, d2 = {"Lcom/pokerarity/scanner/ScanRegressionTest$ExpectedResult;", "", "species", "", "cp", "", "hp", "maxHp", "shiny", "", "lucky", "costume", "locationCard", "datePresent", "(Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Boolean;Ljava/lang/Boolean;Ljava/lang/Boolean;Ljava/lang/Boolean;Ljava/lang/Boolean;)V", "getCostume", "()Ljava/lang/Boolean;", "Ljava/lang/Boolean;", "getCp", "()Ljava/lang/Integer;", "Ljava/lang/Integer;", "getDatePresent", "getHp", "getLocationCard", "getLucky", "getMaxHp", "getShiny", "getSpecies", "()Ljava/lang/String;", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "(Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Boolean;Ljava/lang/Boolean;Ljava/lang/Boolean;Ljava/lang/Boolean;Ljava/lang/Boolean;)Lcom/pokerarity/scanner/ScanRegressionTest$ExpectedResult;", "equals", "other", "hashCode", "toString", "app_debugAndroidTest"})
    static final class ExpectedResult {
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String species = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Integer cp = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Integer hp = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Integer maxHp = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Boolean shiny = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Boolean lucky = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Boolean costume = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Boolean locationCard = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Boolean datePresent = null;
        
        public ExpectedResult(@org.jetbrains.annotations.Nullable()
        java.lang.String species, @org.jetbrains.annotations.Nullable()
        java.lang.Integer cp, @org.jetbrains.annotations.Nullable()
        java.lang.Integer hp, @org.jetbrains.annotations.Nullable()
        java.lang.Integer maxHp, @org.jetbrains.annotations.Nullable()
        java.lang.Boolean shiny, @org.jetbrains.annotations.Nullable()
        java.lang.Boolean lucky, @org.jetbrains.annotations.Nullable()
        java.lang.Boolean costume, @org.jetbrains.annotations.Nullable()
        java.lang.Boolean locationCard, @org.jetbrains.annotations.Nullable()
        java.lang.Boolean datePresent) {
            super();
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getSpecies() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer getCp() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer getHp() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer getMaxHp() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Boolean getShiny() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Boolean getLucky() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Boolean getCostume() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Boolean getLocationCard() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Boolean getDatePresent() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer component2() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer component3() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Integer component4() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Boolean component5() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Boolean component6() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Boolean component7() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Boolean component8() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Boolean component9() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.ScanRegressionTest.ExpectedResult copy(@org.jetbrains.annotations.Nullable()
        java.lang.String species, @org.jetbrains.annotations.Nullable()
        java.lang.Integer cp, @org.jetbrains.annotations.Nullable()
        java.lang.Integer hp, @org.jetbrains.annotations.Nullable()
        java.lang.Integer maxHp, @org.jetbrains.annotations.Nullable()
        java.lang.Boolean shiny, @org.jetbrains.annotations.Nullable()
        java.lang.Boolean lucky, @org.jetbrains.annotations.Nullable()
        java.lang.Boolean costume, @org.jetbrains.annotations.Nullable()
        java.lang.Boolean locationCard, @org.jetbrains.annotations.Nullable()
        java.lang.Boolean datePresent) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0012\n\u0002\u0010\b\n\u0002\b\u0002\b\u0082\b\u0018\u00002\u00020\u0001B-\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\u0006\u0010\u0007\u001a\u00020\u0003\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nJ\t\u0010\u0013\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0014\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0015\u001a\u00020\u0006H\u00c6\u0003J\t\u0010\u0016\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0017\u001a\u00020\tH\u00c6\u0003J;\u0010\u0018\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u0010\u0007\u001a\u00020\u00032\b\b\u0002\u0010\b\u001a\u00020\tH\u00c6\u0001J\u0013\u0010\u0019\u001a\u00020\u00062\b\u0010\u001a\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u001b\u001a\u00020\u001cH\u00d6\u0001J\t\u0010\u001d\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\fR\u0011\u0010\u0007\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\fR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012\u00a8\u0006\u001e"}, d2 = {"Lcom/pokerarity/scanner/ScanRegressionTest$RegressionCase;", "", "id", "", "assetPath", "strict", "", "notes", "expected", "Lcom/pokerarity/scanner/ScanRegressionTest$ExpectedResult;", "(Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;Lcom/pokerarity/scanner/ScanRegressionTest$ExpectedResult;)V", "getAssetPath", "()Ljava/lang/String;", "getExpected", "()Lcom/pokerarity/scanner/ScanRegressionTest$ExpectedResult;", "getId", "getNotes", "getStrict", "()Z", "component1", "component2", "component3", "component4", "component5", "copy", "equals", "other", "hashCode", "", "toString", "app_debugAndroidTest"})
    static final class RegressionCase {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String id = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String assetPath = null;
        private final boolean strict = false;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String notes = null;
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.ScanRegressionTest.ExpectedResult expected = null;
        
        public RegressionCase(@org.jetbrains.annotations.NotNull()
        java.lang.String id, @org.jetbrains.annotations.NotNull()
        java.lang.String assetPath, boolean strict, @org.jetbrains.annotations.NotNull()
        java.lang.String notes, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.ScanRegressionTest.ExpectedResult expected) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getId() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getAssetPath() {
            return null;
        }
        
        public final boolean getStrict() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getNotes() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.ScanRegressionTest.ExpectedResult getExpected() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        public final boolean component3() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.ScanRegressionTest.ExpectedResult component5() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.ScanRegressionTest.RegressionCase copy(@org.jetbrains.annotations.NotNull()
        java.lang.String id, @org.jetbrains.annotations.NotNull()
        java.lang.String assetPath, boolean strict, @org.jetbrains.annotations.NotNull()
        java.lang.String notes, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.ScanRegressionTest.ExpectedResult expected) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010 \n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0013\n\u0002\u0010\b\n\u0002\b\u0002\b\u0082\b\u0018\u00002\u00020\u0001B3\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00030\b\u0012\u0006\u0010\t\u001a\u00020\n\u00a2\u0006\u0002\u0010\u000bJ\t\u0010\u0015\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0016\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0017\u001a\u00020\u0005H\u00c6\u0003J\u000f\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u00030\bH\u00c6\u0003J\t\u0010\u0019\u001a\u00020\nH\u00c6\u0003JA\u0010\u001a\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00052\u000e\b\u0002\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00030\b2\b\b\u0002\u0010\t\u001a\u00020\nH\u00c6\u0001J\u0013\u0010\u001b\u001a\u00020\u00052\b\u0010\u001c\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u001d\u001a\u00020\u001eH\u00d6\u0001J\t\u0010\u001f\u001a\u00020\u0003H\u00d6\u0001R\u0017\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00030\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0011R\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014\u00a8\u0006 "}, d2 = {"Lcom/pokerarity/scanner/ScanRegressionTest$RegressionOutcome;", "", "id", "", "strict", "", "pass", "failures", "", "summary", "Lorg/json/JSONObject;", "(Ljava/lang/String;ZZLjava/util/List;Lorg/json/JSONObject;)V", "getFailures", "()Ljava/util/List;", "getId", "()Ljava/lang/String;", "getPass", "()Z", "getStrict", "getSummary", "()Lorg/json/JSONObject;", "component1", "component2", "component3", "component4", "component5", "copy", "equals", "other", "hashCode", "", "toString", "app_debugAndroidTest"})
    static final class RegressionOutcome {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String id = null;
        private final boolean strict = false;
        private final boolean pass = false;
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<java.lang.String> failures = null;
        @org.jetbrains.annotations.NotNull()
        private final org.json.JSONObject summary = null;
        
        public RegressionOutcome(@org.jetbrains.annotations.NotNull()
        java.lang.String id, boolean strict, boolean pass, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.String> failures, @org.jetbrains.annotations.NotNull()
        org.json.JSONObject summary) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getId() {
            return null;
        }
        
        public final boolean getStrict() {
            return false;
        }
        
        public final boolean getPass() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.String> getFailures() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final org.json.JSONObject getSummary() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        public final boolean component2() {
            return false;
        }
        
        public final boolean component3() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.String> component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final org.json.JSONObject component5() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.ScanRegressionTest.RegressionOutcome copy(@org.jetbrains.annotations.NotNull()
        java.lang.String id, boolean strict, boolean pass, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.String> failures, @org.jetbrains.annotations.NotNull()
        org.json.JSONObject summary) {
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