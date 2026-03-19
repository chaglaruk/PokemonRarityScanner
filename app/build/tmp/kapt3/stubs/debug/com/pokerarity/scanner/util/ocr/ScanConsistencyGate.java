package com.pokerarity.scanner.util.ocr;

import android.content.Context;
import android.util.Log;
import com.pokerarity.scanner.data.model.PokemonData;
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry;
import com.pokerarity.scanner.data.repository.RarityCalculator;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000B\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001:\u0001\u0019B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0018\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\n2\u0006\u0010\f\u001a\u00020\rH\u0002J\u0016\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\n2\u0006\u0010\u0011\u001a\u00020\nJ\u0018\u0010\u0012\u001a\u00020\r2\u0006\u0010\u0013\u001a\u00020\r2\u0006\u0010\u0014\u001a\u00020\rH\u0002J\u001a\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0010\u001a\u00020\n2\b\u0010\f\u001a\u0004\u0018\u00010\rH\u0002J\u001c\u0010\u0017\u001a\u0004\u0018\u00010\u00182\u0006\u0010\u000b\u001a\u00020\n2\b\u0010\f\u001a\u0004\u0018\u00010\rH\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001a"}, d2 = {"Lcom/pokerarity/scanner/util/ocr/ScanConsistencyGate;", "", "context", "Landroid/content/Context;", "rarityCalculator", "Lcom/pokerarity/scanner/data/repository/RarityCalculator;", "(Landroid/content/Context;Lcom/pokerarity/scanner/data/repository/RarityCalculator;)V", "textParser", "Lcom/pokerarity/scanner/util/ocr/TextParser;", "correctSpecies", "Lcom/pokerarity/scanner/data/model/PokemonData;", "pokemon", "species", "", "evaluate", "Lcom/pokerarity/scanner/util/ocr/ScanConsistencyGate$Decision;", "authoritative", "candidate", "extractRawField", "rawOcrText", "key", "hasStrongAuthoritativeAnchor", "", "score", "Lcom/pokerarity/scanner/data/repository/RarityCalculator$SpeciesFit;", "Decision", "app_debug"})
public final class ScanConsistencyGate {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.data.repository.RarityCalculator rarityCalculator = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.util.ocr.TextParser textParser = null;
    
    public ScanConsistencyGate(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.repository.RarityCalculator rarityCalculator) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.util.ocr.ScanConsistencyGate.Decision evaluate(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.PokemonData authoritative, @org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.PokemonData candidate) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.repository.RarityCalculator.SpeciesFit score(com.pokerarity.scanner.data.model.PokemonData pokemon, java.lang.String species) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.model.PokemonData correctSpecies(com.pokerarity.scanner.data.model.PokemonData pokemon, java.lang.String species) {
        return null;
    }
    
    private final boolean hasStrongAuthoritativeAnchor(com.pokerarity.scanner.data.model.PokemonData authoritative, java.lang.String species) {
        return false;
    }
    
    private final java.lang.String extractRawField(java.lang.String rawOcrText, java.lang.String key) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u000e\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0007H\u00c6\u0003J\'\u0010\u0012\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0007H\u00c6\u0001J\u0013\u0010\u0013\u001a\u00020\u00052\b\u0010\u0014\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0015\u001a\u00020\u0016H\u00d6\u0001J\t\u0010\u0017\u001a\u00020\u0007H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0018"}, d2 = {"Lcom/pokerarity/scanner/util/ocr/ScanConsistencyGate$Decision;", "", "pokemon", "Lcom/pokerarity/scanner/data/model/PokemonData;", "shouldRetry", "", "reason", "", "(Lcom/pokerarity/scanner/data/model/PokemonData;ZLjava/lang/String;)V", "getPokemon", "()Lcom/pokerarity/scanner/data/model/PokemonData;", "getReason", "()Ljava/lang/String;", "getShouldRetry", "()Z", "component1", "component2", "component3", "copy", "equals", "other", "hashCode", "", "toString", "app_debug"})
    public static final class Decision {
        @org.jetbrains.annotations.NotNull()
        private final com.pokerarity.scanner.data.model.PokemonData pokemon = null;
        private final boolean shouldRetry = false;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String reason = null;
        
        public Decision(@org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.model.PokemonData pokemon, boolean shouldRetry, @org.jetbrains.annotations.NotNull()
        java.lang.String reason) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.model.PokemonData getPokemon() {
            return null;
        }
        
        public final boolean getShouldRetry() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getReason() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.model.PokemonData component1() {
            return null;
        }
        
        public final boolean component2() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.util.ocr.ScanConsistencyGate.Decision copy(@org.jetbrains.annotations.NotNull()
        com.pokerarity.scanner.data.model.PokemonData pokemon, boolean shouldRetry, @org.jetbrains.annotations.NotNull()
        java.lang.String reason) {
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