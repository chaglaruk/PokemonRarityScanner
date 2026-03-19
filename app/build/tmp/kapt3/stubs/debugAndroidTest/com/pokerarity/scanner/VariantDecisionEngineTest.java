package com.pokerarity.scanner;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.pokerarity.scanner.data.model.VisualFeatures;
import com.pokerarity.scanner.util.vision.VariantDecisionEngine;
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier;
import org.junit.Test;
import org.junit.runner.RunWith;

@org.junit.runner.RunWith(value = androidx.test.ext.junit.runners.AndroidJUnit4.class)
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0004\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0005\u001a\u00020\u0006H\u0007J\b\u0010\u0007\u001a\u00020\u0006H\u0007J\b\u0010\b\u001a\u00020\u0006H\u0007J\b\u0010\t\u001a\u00020\u0006H\u0007R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\n"}, d2 = {"Lcom/pokerarity/scanner/VariantDecisionEngineTest;", "", "()V", "engine", "Lcom/pokerarity/scanner/util/vision/VariantDecisionEngine;", "denseSpeciesCostumeNearTieStillPromotesCostume", "", "lowConfidenceSpeciesFormMatchDoesNotLeakShinyFlag", "speciesScopedCostumeRescueDoesNotPromoteWhenBaseIsTooClose", "speciesScopedCostumeRescuePromotesCostumeBelowMainThreshold", "app_debugAndroidTest"})
public final class VariantDecisionEngineTest {
    @org.jetbrains.annotations.NotNull()
    private final com.pokerarity.scanner.util.vision.VariantDecisionEngine engine = null;
    
    public VariantDecisionEngineTest() {
        super();
    }
    
    @org.junit.Test()
    public final void speciesScopedCostumeRescuePromotesCostumeBelowMainThreshold() {
    }
    
    @org.junit.Test()
    public final void speciesScopedCostumeRescueDoesNotPromoteWhenBaseIsTooClose() {
    }
    
    @org.junit.Test()
    public final void lowConfidenceSpeciesFormMatchDoesNotLeakShinyFlag() {
    }
    
    @org.junit.Test()
    public final void denseSpeciesCostumeNearTieStillPromotesCostume() {
    }
}