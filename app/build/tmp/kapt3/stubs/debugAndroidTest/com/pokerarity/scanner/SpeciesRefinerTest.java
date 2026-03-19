package com.pokerarity.scanner;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.pokerarity.scanner.data.model.PokemonData;
import com.pokerarity.scanner.data.repository.RarityCalculator;
import com.pokerarity.scanner.util.ocr.SpeciesRefiner;
import org.junit.Test;
import org.junit.runner.RunWith;

@org.junit.runner.RunWith(value = androidx.test.ext.junit.runners.AndroidJUnit4.class)
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\b\u001a\u00020\tH\u0007R\u0016\u0010\u0003\u001a\n \u0005*\u0004\u0018\u00010\u00040\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\n"}, d2 = {"Lcom/pokerarity/scanner/SpeciesRefinerTest;", "", "()V", "context", "Landroid/content/Context;", "kotlin.jvm.PlatformType", "refiner", "Lcom/pokerarity/scanner/util/ocr/SpeciesRefiner;", "exactParsedSpeciesDoesNotDriftWithinFamilyWithoutContradictorySignals", "", "app_debugAndroidTest"})
public final class SpeciesRefinerTest {
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.util.ocr.SpeciesRefiner refiner = null;
    
    public SpeciesRefinerTest() {
        super();
    }
    
    @org.junit.Test()
    public final void exactParsedSpeciesDoesNotDriftWithinFamilyWithoutContradictorySignals() {
    }
}