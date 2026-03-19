package com.pokerarity.scanner;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.pokerarity.scanner.data.model.PokemonData;
import com.pokerarity.scanner.data.repository.RarityCalculator;
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate;
import org.junit.Test;
import org.junit.runner.RunWith;

@org.junit.runner.RunWith(value = androidx.test.ext.junit.runners.AndroidJUnit4.class)
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0003\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002JQ\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\r2\b\u0010\u000e\u001a\u0004\u0018\u00010\r2\b\u0010\u000f\u001a\u0004\u0018\u00010\r2\b\u0010\u0010\u001a\u0004\u0018\u00010\u00112\b\u0010\u0012\u001a\u0004\u0018\u00010\u00112\b\u0010\u0013\u001a\u0004\u0018\u00010\u00112\u0006\u0010\u0014\u001a\u00020\rH\u0002\u00a2\u0006\u0002\u0010\u0015J\b\u0010\u0016\u001a\u00020\u0017H\u0007J\b\u0010\u0018\u001a\u00020\u0017H\u0007J\b\u0010\u0019\u001a\u00020\u0017H\u0007R\u0016\u0010\u0003\u001a\n \u0005*\u0004\u0018\u00010\u00040\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001a"}, d2 = {"Lcom/pokerarity/scanner/ScanConsistencyGateTest;", "", "()V", "context", "Landroid/content/Context;", "kotlin.jvm.PlatformType", "gate", "Lcom/pokerarity/scanner/util/ocr/ScanConsistencyGate;", "rarityCalculator", "Lcom/pokerarity/scanner/data/repository/RarityCalculator;", "pokemon", "Lcom/pokerarity/scanner/data/model/PokemonData;", "name", "", "realName", "candyName", "cp", "", "hp", "maxHp", "rawOcrText", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;)Lcom/pokerarity/scanner/data/model/PokemonData;", "prefersUniqueCandySpeciesWhenResolvedSpeciesDrifts", "", "restoresAuthoritativeSpeciesWhenCandyFamilyConflicts", "retriesUnknownSpeciesWithoutAnyStableAnchor", "app_debugAndroidTest"})
public final class ScanConsistencyGateTest {
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.data.repository.RarityCalculator rarityCalculator = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.util.ocr.ScanConsistencyGate gate = null;
    
    public ScanConsistencyGateTest() {
        super();
    }
    
    @org.junit.Test()
    public final void restoresAuthoritativeSpeciesWhenCandyFamilyConflicts() {
    }
    
    @org.junit.Test()
    public final void prefersUniqueCandySpeciesWhenResolvedSpeciesDrifts() {
    }
    
    @org.junit.Test()
    public final void retriesUnknownSpeciesWithoutAnyStableAnchor() {
    }
    
    private final com.pokerarity.scanner.data.model.PokemonData pokemon(java.lang.String name, java.lang.String realName, java.lang.String candyName, java.lang.Integer cp, java.lang.Integer hp, java.lang.Integer maxHp, java.lang.String rawOcrText) {
        return null;
    }
}