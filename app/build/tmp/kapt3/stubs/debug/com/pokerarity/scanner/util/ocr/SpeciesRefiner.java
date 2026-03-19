package com.pokerarity.scanner.util.ocr;

import android.content.Context;
import android.util.Log;
import com.pokerarity.scanner.data.model.PokemonData;
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry;
import com.pokerarity.scanner.data.repository.PokemonMoveRegistry;
import com.pokerarity.scanner.data.repository.RarityCalculator;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0005\u0018\u00002\u00020\u0001:\u0001\u001bB\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0018\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\n2\u0006\u0010\f\u001a\u00020\nH\u0002J\u001a\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\n2\b\u0010\u0010\u001a\u0004\u0018\u00010\nH\u0002J\u0010\u0010\u0011\u001a\u00020\n2\u0006\u0010\u0012\u001a\u00020\nH\u0002J\u000e\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u0014J\u0018\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\n2\u0006\u0010\u0019\u001a\u00020\nH\u0002J\u0018\u0010\u001a\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\n2\u0006\u0010\u0019\u001a\u00020\nH\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001c"}, d2 = {"Lcom/pokerarity/scanner/util/ocr/SpeciesRefiner;", "", "context", "Landroid/content/Context;", "rarityCalculator", "Lcom/pokerarity/scanner/data/repository/RarityCalculator;", "(Landroid/content/Context;Lcom/pokerarity/scanner/data/repository/RarityCalculator;)V", "textParser", "Lcom/pokerarity/scanner/util/ocr/TextParser;", "extractRawField", "", "rawOcrText", "key", "hasStrongSpeciesAnchor", "", "rawValue", "species", "normalizeName", "value", "refine", "Lcom/pokerarity/scanner/data/model/PokemonData;", "pokemon", "sharedPrefixLength", "", "a", "b", "sharedSuffixLength", "CandidateScore", "app_debug"})
public final class SpeciesRefiner {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.data.repository.RarityCalculator rarityCalculator = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.util.ocr.TextParser textParser = null;
    
    public SpeciesRefiner(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.repository.RarityCalculator rarityCalculator) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.model.PokemonData refine(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.PokemonData pokemon) {
        return null;
    }
    
    private final java.lang.String extractRawField(java.lang.String rawOcrText, java.lang.String key) {
        return null;
    }
    
    private final java.lang.String normalizeName(java.lang.String value) {
        return null;
    }
    
    private final boolean hasStrongSpeciesAnchor(java.lang.String rawValue, java.lang.String species) {
        return false;
    }
    
    private final int sharedPrefixLength(java.lang.String a, java.lang.String b) {
        return 0;
    }
    
    private final int sharedSuffixLength(java.lang.String a, java.lang.String b) {
        return 0;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0019\n\u0002\u0010\b\n\u0002\b\u0002\b\u0082\b\u0018\u00002\u00020\u0001BE\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\u0006\u0010\u0007\u001a\u00020\u0005\u0012\u0006\u0010\b\u001a\u00020\u0005\u0012\u0006\u0010\t\u001a\u00020\u0005\u0012\u0006\u0010\n\u001a\u00020\u000b\u0012\u0006\u0010\f\u001a\u00020\u000b\u00a2\u0006\u0002\u0010\rJ\t\u0010\u0019\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u001a\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001b\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001c\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001d\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001e\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001f\u001a\u00020\u000bH\u00c6\u0003J\t\u0010 \u001a\u00020\u000bH\u00c6\u0003JY\u0010!\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00052\b\b\u0002\u0010\u0007\u001a\u00020\u00052\b\b\u0002\u0010\b\u001a\u00020\u00052\b\b\u0002\u0010\t\u001a\u00020\u00052\b\b\u0002\u0010\n\u001a\u00020\u000b2\b\b\u0002\u0010\f\u001a\u00020\u000bH\u00c6\u0001J\u0013\u0010\"\u001a\u00020\u000b2\b\u0010#\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010$\u001a\u00020%H\u00d6\u0001J\t\u0010&\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\f\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u0011\u0010\u0007\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0011\u0010\n\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u000fR\u0011\u0010\b\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0011R\u0011\u0010\t\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0011R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0011R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0011\u00a8\u0006\'"}, d2 = {"Lcom/pokerarity/scanner/util/ocr/SpeciesRefiner$CandidateScore;", "", "species", "", "totalScore", "", "textScore", "fitScore", "moveScore", "sizeScore", "hpPossible", "", "cpPossible", "(Ljava/lang/String;DDDDDZZ)V", "getCpPossible", "()Z", "getFitScore", "()D", "getHpPossible", "getMoveScore", "getSizeScore", "getSpecies", "()Ljava/lang/String;", "getTextScore", "getTotalScore", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "copy", "equals", "other", "hashCode", "", "toString", "app_debug"})
    static final class CandidateScore {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String species = null;
        private final double totalScore = 0.0;
        private final double textScore = 0.0;
        private final double fitScore = 0.0;
        private final double moveScore = 0.0;
        private final double sizeScore = 0.0;
        private final boolean hpPossible = false;
        private final boolean cpPossible = false;
        
        public CandidateScore(@org.jetbrains.annotations.NotNull()
        java.lang.String species, double totalScore, double textScore, double fitScore, double moveScore, double sizeScore, boolean hpPossible, boolean cpPossible) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getSpecies() {
            return null;
        }
        
        public final double getTotalScore() {
            return 0.0;
        }
        
        public final double getTextScore() {
            return 0.0;
        }
        
        public final double getFitScore() {
            return 0.0;
        }
        
        public final double getMoveScore() {
            return 0.0;
        }
        
        public final double getSizeScore() {
            return 0.0;
        }
        
        public final boolean getHpPossible() {
            return false;
        }
        
        public final boolean getCpPossible() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
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
        
        public final double component6() {
            return 0.0;
        }
        
        public final boolean component7() {
            return false;
        }
        
        public final boolean component8() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.ocr.SpeciesRefiner.CandidateScore copy(@org.jetbrains.annotations.NotNull()
        java.lang.String species, double totalScore, double textScore, double fitScore, double moveScore, double sizeScore, boolean hpPossible, boolean cpPossible) {
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