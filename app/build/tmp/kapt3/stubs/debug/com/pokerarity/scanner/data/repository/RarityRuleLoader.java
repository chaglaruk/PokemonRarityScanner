package com.pokerarity.scanner.data.repository;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000`\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010$\n\u0002\b\b\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0007 !\"#$%&B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\b\u001a\u00020\u0007H\u0002J\u000e\u0010\t\u001a\u00020\u00072\u0006\u0010\n\u001a\u00020\u000bJ\u0010\u0010\f\u001a\u00020\u00072\u0006\u0010\n\u001a\u00020\u000bH\u0002J\u0016\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000f0\u000e2\u0006\u0010\u0010\u001a\u00020\u0011H\u0002J\u0010\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u0015H\u0002J\u0010\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0014\u001a\u00020\u0015H\u0002J\u0010\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u0014\u001a\u00020\u0015H\u0002J\u0016\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u001b0\u000e2\u0006\u0010\u0010\u001a\u00020\u0011H\u0002J\u0010\u0010\u001c\u001a\u00020\u001d2\u0006\u0010\u0014\u001a\u00020\u0015H\u0002J\u001c\u0010\u001e\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00170\u001f2\u0006\u0010\u0014\u001a\u00020\u0015H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0006\u001a\u0004\u0018\u00010\u0007X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\'"}, d2 = {"Lcom/pokerarity/scanner/data/repository/RarityRuleLoader;", "", "()V", "ASSET_PATH", "", "TAG", "cached", "Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$Rules;", "fallbackRules", "get", "context", "Landroid/content/Context;", "load", "parseAgeTiers", "", "Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$AgeTier;", "arr", "Lorg/json/JSONArray;", "parseAxisCaps", "Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$AxisCaps;", "obj", "Lorg/json/JSONObject;", "parseBonusRule", "Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$BonusRule;", "parseCollector", "Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$CollectorRules;", "parseComboRules", "Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$ComboRule;", "parseConfidence", "Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$ConfidenceWeights;", "parseVariantBonuses", "", "AgeTier", "AxisCaps", "BonusRule", "CollectorRules", "ComboRule", "ConfidenceWeights", "Rules", "app_debug"})
public final class RarityRuleLoader {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "RarityRuleLoader";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ASSET_PATH = "data/rarity_rules.json";
    @kotlin.jvm.Volatile()
    @org.jetbrains.annotations.Nullable()
    private static volatile com.pokerarity.scanner.data.repository.RarityRuleLoader.Rules cached;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.data.repository.RarityRuleLoader INSTANCE = null;
    
    private RarityRuleLoader() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.repository.RarityRuleLoader.Rules get(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.repository.RarityRuleLoader.Rules load(android.content.Context context) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.repository.RarityRuleLoader.AxisCaps parseAxisCaps(org.json.JSONObject obj) {
        return null;
    }
    
    private final java.util.Map<java.lang.String, com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule> parseVariantBonuses(org.json.JSONObject obj) {
        return null;
    }
    
    private final java.util.List<com.pokerarity.scanner.data.repository.RarityRuleLoader.AgeTier> parseAgeTiers(org.json.JSONArray arr) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.repository.RarityRuleLoader.CollectorRules parseCollector(org.json.JSONObject obj) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule parseBonusRule(org.json.JSONObject obj) {
        return null;
    }
    
    private final java.util.List<com.pokerarity.scanner.data.repository.RarityRuleLoader.ComboRule> parseComboRules(org.json.JSONArray arr) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.repository.RarityRuleLoader.ConfidenceWeights parseConfidence(org.json.JSONObject obj) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.repository.RarityRuleLoader.Rules fallbackRules() {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u000b\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000e\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000f\u001a\u00020\u0006H\u00c6\u0003J\'\u0010\u0010\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u0006H\u00c6\u0001J\u0013\u0010\u0011\u001a\u00020\u00122\b\u0010\u0013\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0014\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\u0015\u001a\u00020\u0006H\u00d6\u0001R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\u000b\u00a8\u0006\u0016"}, d2 = {"Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$AgeTier;", "", "minDays", "", "points", "label", "", "(IILjava/lang/String;)V", "getLabel", "()Ljava/lang/String;", "getMinDays", "()I", "getPoints", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "toString", "app_debug"})
    public static final class AgeTier {
        private final int minDays = 0;
        private final int points = 0;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String label = null;
        
        public AgeTier(int minDays, int points, @org.jetbrains.annotations.NotNull()
        java.lang.String label) {
            super();
        }
        
        public final int getMinDays() {
            return 0;
        }
        
        public final int getPoints() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getLabel() {
            return null;
        }
        
        public final int component1() {
            return 0;
        }
        
        public final int component2() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.AgeTier copy(int minDays, int points, @org.jetbrains.annotations.NotNull()
        java.lang.String label) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\u000f\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B%\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0007J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000e\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0003H\u00c6\u0003J1\u0010\u0011\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\u0012\u001a\u00020\u00132\b\u0010\u0014\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0015\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\u0016\u001a\u00020\u0017H\u00d6\u0001R\u0011\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\tR\u0011\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\tR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\t\u00a8\u0006\u0018"}, d2 = {"Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$AxisCaps;", "", "baseSpecies", "", "variant", "age", "collector", "(IIII)V", "getAge", "()I", "getBaseSpecies", "getCollector", "getVariant", "component1", "component2", "component3", "component4", "copy", "equals", "", "other", "hashCode", "toString", "", "app_debug"})
    public static final class AxisCaps {
        private final int baseSpecies = 0;
        private final int variant = 0;
        private final int age = 0;
        private final int collector = 0;
        
        public AxisCaps(int baseSpecies, int variant, int age, int collector) {
            super();
        }
        
        public final int getBaseSpecies() {
            return 0;
        }
        
        public final int getVariant() {
            return 0;
        }
        
        public final int getAge() {
            return 0;
        }
        
        public final int getCollector() {
            return 0;
        }
        
        public final int component1() {
            return 0;
        }
        
        public final int component2() {
            return 0;
        }
        
        public final int component3() {
            return 0;
        }
        
        public final int component4() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.AxisCaps copy(int baseSpecies, int variant, int age, int collector) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\f\u001a\u00020\u0005H\u00c6\u0003J\u001d\u0010\r\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0005H\u00c6\u0001J\u0013\u0010\u000e\u001a\u00020\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0011\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\u0012\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0013"}, d2 = {"Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$BonusRule;", "", "points", "", "label", "", "(ILjava/lang/String;)V", "getLabel", "()Ljava/lang/String;", "getPoints", "()I", "component1", "component2", "copy", "equals", "", "other", "hashCode", "toString", "app_debug"})
    public static final class BonusRule {
        private final int points = 0;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String label = null;
        
        public BonusRule(int points, @org.jetbrains.annotations.NotNull()
        java.lang.String label) {
            super();
        }
        
        public final int getPoints() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getLabel() {
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
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule copy(int points, @org.jetbrains.annotations.NotNull()
        java.lang.String label) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0013\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B5\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u00a2\u0006\u0002\u0010\fJ\t\u0010\u0017\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0018\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0019\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u001a\u001a\u00020\u0007H\u00c6\u0003J\t\u0010\u001b\u001a\u00020\tH\u00c6\u0003J\t\u0010\u001c\u001a\u00020\u000bH\u00c6\u0003JE\u0010\u001d\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\t2\b\b\u0002\u0010\n\u001a\u00020\u000bH\u00c6\u0001J\u0013\u0010\u001e\u001a\u00020\u001f2\b\u0010 \u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010!\u001a\u00020\tH\u00d6\u0001J\t\u0010\"\u001a\u00020\u000bH\u00d6\u0001R\u0011\u0010\n\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0014R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0014\u00a8\u0006#"}, d2 = {"Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$CollectorRules;", "", "xxl", "Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$BonusRule;", "xxs", "rareFemale", "eventWeightScale", "", "eventWeightCap", "", "eventLabel", "", "(Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$BonusRule;Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$BonusRule;Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$BonusRule;DILjava/lang/String;)V", "getEventLabel", "()Ljava/lang/String;", "getEventWeightCap", "()I", "getEventWeightScale", "()D", "getRareFemale", "()Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$BonusRule;", "getXxl", "getXxs", "component1", "component2", "component3", "component4", "component5", "component6", "copy", "equals", "", "other", "hashCode", "toString", "app_debug"})
    public static final class CollectorRules {
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule xxl = null;
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule xxs = null;
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule rareFemale = null;
        private final double eventWeightScale = 0.0;
        private final int eventWeightCap = 0;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String eventLabel = null;
        
        public CollectorRules(@org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule xxl, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule xxs, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule rareFemale, double eventWeightScale, int eventWeightCap, @org.jetbrains.annotations.NotNull()
        java.lang.String eventLabel) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule getXxl() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule getXxs() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule getRareFemale() {
            return null;
        }
        
        public final double getEventWeightScale() {
            return 0.0;
        }
        
        public final int getEventWeightCap() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getEventLabel() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule component3() {
            return null;
        }
        
        public final double component4() {
            return 0.0;
        }
        
        public final int component5() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component6() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.CollectorRules copy(@org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule xxl, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule xxs, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule rareFemale, double eventWeightScale, int eventWeightCap, @org.jetbrains.annotations.NotNull()
        java.lang.String eventLabel) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\r\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B#\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\u0006\u0010\u0007\u001a\u00020\u0004\u00a2\u0006\u0002\u0010\bJ\u000f\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0006H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0004H\u00c6\u0003J-\u0010\u0012\u001a\u00020\u00002\u000e\b\u0002\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u0010\u0007\u001a\u00020\u0004H\u00c6\u0001J\u0013\u0010\u0013\u001a\u00020\u00142\b\u0010\u0015\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0016\u001a\u00020\u0006H\u00d6\u0001J\t\u0010\u0017\u001a\u00020\u0004H\u00d6\u0001R\u0011\u0010\u0007\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0018"}, d2 = {"Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$ComboRule;", "", "requires", "", "", "points", "", "label", "(Ljava/util/List;ILjava/lang/String;)V", "getLabel", "()Ljava/lang/String;", "getPoints", "()I", "getRequires", "()Ljava/util/List;", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "toString", "app_debug"})
    public static final class ComboRule {
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<java.lang.String> requires = null;
        private final int points = 0;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String label = null;
        
        public ComboRule(@org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.String> requires, int points, @org.jetbrains.annotations.NotNull()
        java.lang.String label) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.String> getRequires() {
            return null;
        }
        
        public final int getPoints() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getLabel() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<java.lang.String> component1() {
            return null;
        }
        
        public final int component2() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.ComboRule copy(@org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.String> requires, int points, @org.jetbrains.annotations.NotNull()
        java.lang.String label) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0012\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B-\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0003\u0012\u0006\u0010\u0007\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\bJ\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0012\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0013\u001a\u00020\u0003H\u00c6\u0003J;\u0010\u0014\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u00032\b\b\u0002\u0010\u0007\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\u0015\u001a\u00020\u00162\b\u0010\u0017\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0018\u001a\u00020\u0019H\u00d6\u0001J\t\u0010\u001a\u001a\u00020\u001bH\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\nR\u0011\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\nR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\nR\u0011\u0010\u0007\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\n\u00a8\u0006\u001c"}, d2 = {"Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$ConfidenceWeights;", "", "name", "", "cp", "hp", "date", "variants", "(DDDDD)V", "getCp", "()D", "getDate", "getHp", "getName", "getVariants", "component1", "component2", "component3", "component4", "component5", "copy", "equals", "", "other", "hashCode", "", "toString", "", "app_debug"})
    public static final class ConfidenceWeights {
        private final double name = 0.0;
        private final double cp = 0.0;
        private final double hp = 0.0;
        private final double date = 0.0;
        private final double variants = 0.0;
        
        public ConfidenceWeights(double name, double cp, double hp, double date, double variants) {
            super();
        }
        
        public final double getName() {
            return 0.0;
        }
        
        public final double getCp() {
            return 0.0;
        }
        
        public final double getHp() {
            return 0.0;
        }
        
        public final double getDate() {
            return 0.0;
        }
        
        public final double getVariants() {
            return 0.0;
        }
        
        public final double component1() {
            return 0.0;
        }
        
        public final double component2() {
            return 0.0;
        }
        
        public final double component3() {
            return 0.0;
        }
        
        public final double component4() {
            return 0.0;
        }
        
        public final double component5() {
            return 0.0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.ConfidenceWeights copy(double name, double cp, double hp, double date, double variants) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000L\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0014\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001BM\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0012\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00070\u0005\u0012\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\t\u0012\u0006\u0010\u000b\u001a\u00020\f\u0012\f\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000e0\t\u0012\u0006\u0010\u000f\u001a\u00020\u0010\u00a2\u0006\u0002\u0010\u0011J\t\u0010\u001d\u001a\u00020\u0003H\u00c6\u0003J\u0015\u0010\u001e\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00070\u0005H\u00c6\u0003J\u000f\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\n0\tH\u00c6\u0003J\t\u0010 \u001a\u00020\fH\u00c6\u0003J\u000f\u0010!\u001a\b\u0012\u0004\u0012\u00020\u000e0\tH\u00c6\u0003J\t\u0010\"\u001a\u00020\u0010H\u00c6\u0003J]\u0010#\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\u0014\b\u0002\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00070\u00052\u000e\b\u0002\u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\t2\b\b\u0002\u0010\u000b\u001a\u00020\f2\u000e\b\u0002\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000e0\t2\b\b\u0002\u0010\u000f\u001a\u00020\u0010H\u00c6\u0001J\u0013\u0010$\u001a\u00020%2\b\u0010&\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\'\u001a\u00020(H\u00d6\u0001J\t\u0010)\u001a\u00020\u0006H\u00d6\u0001R\u0017\u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0011\u0010\u000b\u001a\u00020\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0017R\u0017\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000e0\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0013R\u0011\u0010\u000f\u001a\u00020\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u001d\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00070\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u001c\u00a8\u0006*"}, d2 = {"Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$Rules;", "", "axisCaps", "Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$AxisCaps;", "variantBonuses", "", "", "Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$BonusRule;", "ageTiers", "", "Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$AgeTier;", "collector", "Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$CollectorRules;", "combos", "Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$ComboRule;", "confidence", "Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$ConfidenceWeights;", "(Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$AxisCaps;Ljava/util/Map;Ljava/util/List;Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$CollectorRules;Ljava/util/List;Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$ConfidenceWeights;)V", "getAgeTiers", "()Ljava/util/List;", "getAxisCaps", "()Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$AxisCaps;", "getCollector", "()Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$CollectorRules;", "getCombos", "getConfidence", "()Lcom/pokerarity/scanner/data/repository/RarityRuleLoader$ConfidenceWeights;", "getVariantBonuses", "()Ljava/util/Map;", "component1", "component2", "component3", "component4", "component5", "component6", "copy", "equals", "", "other", "hashCode", "", "toString", "app_debug"})
    public static final class Rules {
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.data.repository.RarityRuleLoader.AxisCaps axisCaps = null;
        @org.jetbrains.annotations.NotNull()
        private final java.util.Map<java.lang.String, com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule> variantBonuses = null;
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<com.pokerarity.scanner.data.repository.RarityRuleLoader.AgeTier> ageTiers = null;
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.data.repository.RarityRuleLoader.CollectorRules collector = null;
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<com.pokerarity.scanner.data.repository.RarityRuleLoader.ComboRule> combos = null;
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.data.repository.RarityRuleLoader.ConfidenceWeights confidence = null;
        
        public Rules(@org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.repository.RarityRuleLoader.AxisCaps axisCaps, @org.jetbrains.annotations.NotNull()
        java.util.Map<java.lang.String, com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule> variantBonuses, @org.jetbrains.annotations.NotNull()
        java.util.List<com.pokerarity.scanner.data.repository.RarityRuleLoader.AgeTier> ageTiers, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.repository.RarityRuleLoader.CollectorRules collector, @org.jetbrains.annotations.NotNull()
        java.util.List<com.pokerarity.scanner.data.repository.RarityRuleLoader.ComboRule> combos, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.repository.RarityRuleLoader.ConfidenceWeights confidence) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.AxisCaps getAxisCaps() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.Map<java.lang.String, com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule> getVariantBonuses() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<com.pokerarity.scanner.data.repository.RarityRuleLoader.AgeTier> getAgeTiers() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.CollectorRules getCollector() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<com.pokerarity.scanner.data.repository.RarityRuleLoader.ComboRule> getCombos() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.ConfidenceWeights getConfidence() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.AxisCaps component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.Map<java.lang.String, com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule> component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<com.pokerarity.scanner.data.repository.RarityRuleLoader.AgeTier> component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.CollectorRules component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<com.pokerarity.scanner.data.repository.RarityRuleLoader.ComboRule> component5() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.ConfidenceWeights component6() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.repository.RarityRuleLoader.Rules copy(@org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.repository.RarityRuleLoader.AxisCaps axisCaps, @org.jetbrains.annotations.NotNull()
        java.util.Map<java.lang.String, com.pokerarity.scanner.data.repository.RarityRuleLoader.BonusRule> variantBonuses, @org.jetbrains.annotations.NotNull()
        java.util.List<com.pokerarity.scanner.data.repository.RarityRuleLoader.AgeTier> ageTiers, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.repository.RarityRuleLoader.CollectorRules collector, @org.jetbrains.annotations.NotNull()
        java.util.List<com.pokerarity.scanner.data.repository.RarityRuleLoader.ComboRule> combos, @org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.repository.RarityRuleLoader.ConfidenceWeights confidence) {
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