package com.pokerarity.scanner.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import com.pokerarity.scanner.data.local.db.AppDatabase;
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity;
import com.pokerarity.scanner.data.model.PokemonData;
import com.pokerarity.scanner.data.model.RarityTier;
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry;
import com.pokerarity.scanner.data.repository.PokemonRepository;
import com.pokerarity.scanner.data.repository.RarityCalculator;
import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator;
import com.pokerarity.scanner.ui.result.ResultActivity;
import com.pokerarity.scanner.util.ScanError;
import com.pokerarity.scanner.util.ScanResult;
import com.pokerarity.scanner.util.ocr.OCRProcessor;
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate;
import com.pokerarity.scanner.util.ocr.SpeciesRefiner;
import com.pokerarity.scanner.util.ocr.TextParser;
import com.pokerarity.scanner.util.vision.VariantDecisionEngine;
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier;
import com.pokerarity.scanner.util.vision.VisualFeatureDetector;
import kotlinx.coroutines.Dispatchers;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Orchestrates the full scan pipeline:
 *  Screenshot → OCR → Visual Detection → Rarity Calculation → Save → Show Result
 *
 * Register with [start] from an Activity / Application and unregister with [stop].
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u00d8\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\"\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u000f\u0018\u0000 u2\u00020\u0001:\u0002uvB\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\"\u0010=\u001a\u00020>2\u0006\u0010?\u001a\u00020>2\u0006\u0010@\u001a\u00020A2\b\b\u0002\u0010B\u001a\u00020>H\u0002J\"\u0010C\u001a\u00020D2\u0006\u0010E\u001a\u00020D2\b\u0010@\u001a\u0004\u0018\u00010A2\u0006\u0010B\u001a\u00020>H\u0002J\u001a\u0010F\u001a\u00020D2\u0006\u0010E\u001a\u00020D2\b\u0010@\u001a\u0004\u0018\u00010AH\u0002J\u0016\u0010G\u001a\b\u0012\u0004\u0012\u00020>0H2\u0006\u0010E\u001a\u00020DH\u0002J\b\u0010I\u001a\u00020JH\u0002J\u0010\u0010K\u001a\u00020L2\u0006\u0010M\u001a\u00020NH\u0002J<\u0010O\u001a\u00020D2\f\u0010P\u001a\b\u0012\u0004\u0012\u00020R0Q2\u0006\u0010S\u001a\u00020D2\u0006\u0010T\u001a\u00020D2\f\u0010U\u001a\b\u0012\u0004\u0012\u00020\u001d0Q2\u0006\u0010V\u001a\u00020LH\u0002J\u0010\u0010W\u001a\u00020J2\u0006\u0010X\u001a\u00020YH\u0002J\u0018\u0010Z\u001a\u00020[2\u0006\u0010\\\u001a\u00020D2\u0006\u0010]\u001a\u00020LH\u0002J\u0012\u0010^\u001a\u00020[2\b\u0010_\u001a\u0004\u0018\u00010>H\u0002J\u001a\u0010`\u001a\u00020a2\u0006\u0010b\u001a\u00020a2\b\u0010@\u001a\u0004\u0018\u00010AH\u0002J\u0018\u0010c\u001a\u00020>2\u0006\u0010d\u001a\u00020>2\u0006\u0010e\u001a\u00020>H\u0002J,\u0010f\u001a\u001e\u0012\u0004\u0012\u00020>\u0012\u0004\u0012\u00020>0gj\u000e\u0012\u0004\u0012\u00020>\u0012\u0004\u0012\u00020>`h2\u0006\u0010?\u001a\u00020>H\u0002J\u0016\u0010i\u001a\u00020J2\f\u0010j\u001a\b\u0012\u0004\u0012\u00020>0QH\u0002J&\u0010k\u001a\u0004\u0018\u00010A2\u0006\u0010E\u001a\u00020D2\b\u0010l\u001a\u0004\u0018\u00010A2\b\u0010m\u001a\u0004\u0018\u00010AH\u0002J\u001e\u0010n\u001a\u00020D2\u0006\u0010o\u001a\u00020>2\u0006\u0010S\u001a\u00020DH\u0082@\u00a2\u0006\u0002\u0010pJ\u0010\u0010q\u001a\u00020\u001d2\u0006\u0010\\\u001a\u00020DH\u0002J\u0018\u0010r\u001a\u00020[2\u0006\u0010S\u001a\u00020D2\u0006\u0010]\u001a\u00020LH\u0002J\u0006\u0010s\u001a\u00020JJ\u0006\u0010t\u001a\u00020JR\u001b\u0010\u0005\u001a\u00020\u00068BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\t\u0010\n\u001a\u0004\b\u0007\u0010\bR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001b\u0010\r\u001a\u00020\u000e8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0011\u0010\n\u001a\u0004\b\u000f\u0010\u0010R\u001b\u0010\u0012\u001a\u00020\u00138BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0016\u0010\n\u001a\u0004\b\u0014\u0010\u0015R\u001b\u0010\u0017\u001a\u00020\u00188BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u001b\u0010\n\u001a\u0004\b\u0019\u0010\u001aR\u000e\u0010\u001c\u001a\u00020\u001dX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001e\u001a\u00020\u001fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010 \u001a\u00020!X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\"\u001a\u00020#X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001b\u0010$\u001a\u00020%8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b(\u0010\n\u001a\u0004\b&\u0010\'R\u001b\u0010)\u001a\u00020*8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b-\u0010\n\u001a\u0004\b+\u0010,R\u001b\u0010.\u001a\u00020/8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b2\u0010\n\u001a\u0004\b0\u00101R\u001b\u00103\u001a\u0002048BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b7\u0010\n\u001a\u0004\b5\u00106R\u001b\u00108\u001a\u0002098BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b<\u0010\n\u001a\u0004\b:\u0010;\u00a8\u0006w"}, d2 = {"Lcom/pokerarity/scanner/service/ScanManager;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "consistencyGate", "Lcom/pokerarity/scanner/util/ocr/ScanConsistencyGate;", "getConsistencyGate", "()Lcom/pokerarity/scanner/util/ocr/ScanConsistencyGate;", "consistencyGate$delegate", "Lkotlin/Lazy;", "mainDateFormatter", "Ljava/text/SimpleDateFormat;", "ocrProcessor", "Lcom/pokerarity/scanner/util/ocr/OCRProcessor;", "getOcrProcessor", "()Lcom/pokerarity/scanner/util/ocr/OCRProcessor;", "ocrProcessor$delegate", "rarityCalculator", "Lcom/pokerarity/scanner/data/repository/RarityCalculator;", "getRarityCalculator", "()Lcom/pokerarity/scanner/data/repository/RarityCalculator;", "rarityCalculator$delegate", "repository", "Lcom/pokerarity/scanner/data/repository/PokemonRepository;", "getRepository", "()Lcom/pokerarity/scanner/data/repository/PokemonRepository;", "repository$delegate", "retryCount", "", "scanMutex", "Lkotlinx/coroutines/sync/Mutex;", "scope", "Lkotlinx/coroutines/CoroutineScope;", "screenshotReceiver", "Landroid/content/BroadcastReceiver;", "speciesRefiner", "Lcom/pokerarity/scanner/util/ocr/SpeciesRefiner;", "getSpeciesRefiner", "()Lcom/pokerarity/scanner/util/ocr/SpeciesRefiner;", "speciesRefiner$delegate", "telemetryCoordinator", "Lcom/pokerarity/scanner/data/remote/ScanTelemetryCoordinator;", "getTelemetryCoordinator", "()Lcom/pokerarity/scanner/data/remote/ScanTelemetryCoordinator;", "telemetryCoordinator$delegate", "textParser", "Lcom/pokerarity/scanner/util/ocr/TextParser;", "getTextParser", "()Lcom/pokerarity/scanner/util/ocr/TextParser;", "textParser$delegate", "variantDecisionEngine", "Lcom/pokerarity/scanner/util/vision/VariantDecisionEngine;", "getVariantDecisionEngine", "()Lcom/pokerarity/scanner/util/vision/VariantDecisionEngine;", "variantDecisionEngine$delegate", "visualDetector", "Lcom/pokerarity/scanner/util/vision/VisualFeatureDetector;", "getVisualDetector", "()Lcom/pokerarity/scanner/util/vision/VisualFeatureDetector;", "visualDetector$delegate", "appendClassifierFields", "", "raw", "match", "Lcom/pokerarity/scanner/util/vision/VariantPrototypeClassifier$MatchResult;", "prefix", "appendClassifierTrace", "Lcom/pokerarity/scanner/data/model/PokemonData;", "pokemon", "applyClassifierSpecies", "buildVariantClassifierHints", "", "cleanOldScreenshots", "", "estimateCpQuality", "", "bitmap", "Landroid/graphics/Bitmap;", "fuseResults", "frames", "", "Lcom/pokerarity/scanner/service/ScanManager$FrameResult;", "authoritative", "detailed", "validCpList", "bestCpQuality", "handleError", "failure", "Lcom/pokerarity/scanner/util/ScanResult$Failure;", "isHighConfidence", "", "data", "cpQuality", "isUnknownSpecies", "value", "mergeClassifierVisuals", "Lcom/pokerarity/scanner/data/model/VisualFeatures;", "visualFeatures", "mergeRawOcrText", "primaryRaw", "detailedRaw", "parseRawOcrFields", "Ljava/util/LinkedHashMap;", "Lkotlin/collections/LinkedHashMap;", "processScanSequence", "paths", "resolveVariantClassifierMatch", "globalMatch", "speciesMatch", "runDetailedPassIfNeeded", "path", "(Ljava/lang/String;Lcom/pokerarity/scanner/data/model/PokemonData;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "scoreFor", "shouldRunDetailedPass", "start", "stop", "Companion", "FrameResult", "app_debug"})
public final class ScanManager {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "ScanManager";
    private static final double CP_QUALITY_MIN = 0.55;
    private static final float CLASSIFIER_SPECIES_CONFIDENCE = 0.72F;
    private static final float CLASSIFIER_SPECIES_CONFIDENCE_FAMILY = 0.62F;
    private static final float CLASSIFIER_VARIANT_CONFIDENCE = 0.66F;
    private static final float CLASSIFIER_VARIANT_CONFIDENCE_SPECIES = 0.52F;
    private static final float CLASSIFIER_FORM_CONFIDENCE_SPECIES = 0.34F;
    private static final float CLASSIFIER_VARIANT_CONSENSUS_MARGIN = 0.03F;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope scope = null;
    private int retryCount = 0;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.sync.Mutex scanMutex = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy ocrProcessor$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy textParser$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy visualDetector$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy variantDecisionEngine$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy repository$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy rarityCalculator$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy speciesRefiner$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy consistencyGate$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy telemetryCoordinator$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final java.text.SimpleDateFormat mainDateFormatter = null;
    @org.jetbrains.annotations.NotNull()
    private final android.content.BroadcastReceiver screenshotReceiver = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.service.ScanManager.Companion Companion = null;
    
    public ScanManager(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    private final com.pokerarity.scanner.util.ocr.OCRProcessor getOcrProcessor() {
        return null;
    }
    
    private final com.pokerarity.scanner.util.ocr.TextParser getTextParser() {
        return null;
    }
    
    private final com.pokerarity.scanner.util.vision.VisualFeatureDetector getVisualDetector() {
        return null;
    }
    
    private final com.pokerarity.scanner.util.vision.VariantDecisionEngine getVariantDecisionEngine() {
        return null;
    }
    
    private final com.pokerarity.scanner.data.repository.PokemonRepository getRepository() {
        return null;
    }
    
    private final com.pokerarity.scanner.data.repository.RarityCalculator getRarityCalculator() {
        return null;
    }
    
    private final com.pokerarity.scanner.util.ocr.SpeciesRefiner getSpeciesRefiner() {
        return null;
    }
    
    private final com.pokerarity.scanner.util.ocr.ScanConsistencyGate getConsistencyGate() {
        return null;
    }
    
    private final com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator getTelemetryCoordinator() {
        return null;
    }
    
    public final void start() {
    }
    
    public final void stop() {
    }
    
    private final void processScanSequence(java.util.List<java.lang.String> paths) {
    }
    
    private final void handleError(com.pokerarity.scanner.util.ScanResult.Failure failure) {
    }
    
    private final void cleanOldScreenshots() {
    }
    
    private final int scoreFor(com.pokerarity.scanner.data.model.PokemonData data) {
        return 0;
    }
    
    private final boolean isHighConfidence(com.pokerarity.scanner.data.model.PokemonData data, double cpQuality) {
        return false;
    }
    
    private final com.pokerarity.scanner.data.model.PokemonData fuseResults(java.util.List<com.pokerarity.scanner.service.ScanManager.FrameResult> frames, com.pokerarity.scanner.data.model.PokemonData authoritative, com.pokerarity.scanner.data.model.PokemonData detailed, java.util.List<java.lang.Integer> validCpList, double bestCpQuality) {
        return null;
    }
    
    private final java.lang.Object runDetailedPassIfNeeded(java.lang.String path, com.pokerarity.scanner.data.model.PokemonData authoritative, kotlin.coroutines.Continuation<? super com.pokerarity.scanner.data.model.PokemonData> $completion) {
        return null;
    }
    
    private final boolean shouldRunDetailedPass(com.pokerarity.scanner.data.model.PokemonData authoritative, double cpQuality) {
        return false;
    }
    
    private final java.lang.String mergeRawOcrText(java.lang.String primaryRaw, java.lang.String detailedRaw) {
        return null;
    }
    
    private final java.util.Set<java.lang.String> buildVariantClassifierHints(com.pokerarity.scanner.data.model.PokemonData pokemon) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.model.PokemonData applyClassifierSpecies(com.pokerarity.scanner.data.model.PokemonData pokemon, com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult match) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.model.VisualFeatures mergeClassifierVisuals(com.pokerarity.scanner.data.model.VisualFeatures visualFeatures, com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult match) {
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
    
    private final double estimateCpQuality(android.graphics.Bitmap bitmap) {
        return 0.0;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\u0006\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u000e\u001a\u00020\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\rH\u0002J%\u0010\u0011\u001a\u00020\u000f2\u0006\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u000b2\u0006\u0010\u0015\u001a\u00020\u000bH\u0000\u00a2\u0006\u0002\b\u0016R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0017"}, d2 = {"Lcom/pokerarity/scanner/service/ScanManager$Companion;", "", "()V", "CLASSIFIER_FORM_CONFIDENCE_SPECIES", "", "CLASSIFIER_SPECIES_CONFIDENCE", "CLASSIFIER_SPECIES_CONFIDENCE_FAMILY", "CLASSIFIER_VARIANT_CONFIDENCE", "CLASSIFIER_VARIANT_CONFIDENCE_SPECIES", "CLASSIFIER_VARIANT_CONSENSUS_MARGIN", "CP_QUALITY_MIN", "", "TAG", "", "isUnknownSpeciesStatic", "", "value", "shouldRunDetailedPassForAuthoritative", "pokemon", "Lcom/pokerarity/scanner/data/model/PokemonData;", "cpQuality", "topTextConfidence", "shouldRunDetailedPassForAuthoritative$app_debug", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        public final boolean shouldRunDetailedPassForAuthoritative$app_debug(@org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.model.PokemonData pokemon, double cpQuality, double topTextConfidence) {
            return false;
        }
        
        private final boolean isUnknownSpeciesStatic(java.lang.String value) {
            return false;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0002\b\f\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0082\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0007H\u00c6\u0003J\'\u0010\u0012\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0007H\u00c6\u0001J\u0013\u0010\u0013\u001a\u00020\u00142\b\u0010\u0015\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0016\u001a\u00020\u0017H\u00d6\u0001J\t\u0010\u0018\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0019"}, d2 = {"Lcom/pokerarity/scanner/service/ScanManager$FrameResult;", "", "path", "", "data", "Lcom/pokerarity/scanner/data/model/PokemonData;", "cpQuality", "", "(Ljava/lang/String;Lcom/pokerarity/scanner/data/model/PokemonData;D)V", "getCpQuality", "()D", "getData", "()Lcom/pokerarity/scanner/data/model/PokemonData;", "getPath", "()Ljava/lang/String;", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "", "toString", "app_debug"})
    static final class FrameResult {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String path = null;
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.data.model.PokemonData data = null;
        private final double cpQuality = 0.0;
        
        public FrameResult(@org.jetbrains.annotations.NotNull()
        java.lang.String path, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.model.PokemonData data, double cpQuality) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getPath() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.model.PokemonData getData() {
            return null;
        }
        
        public final double getCpQuality() {
            return 0.0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.model.PokemonData component2() {
            return null;
        }
        
        public final double component3() {
            return 0.0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.service.ScanManager.FrameResult copy(@org.jetbrains.annotations.NotNull()
        java.lang.String path, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.model.PokemonData data, double cpQuality) {
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