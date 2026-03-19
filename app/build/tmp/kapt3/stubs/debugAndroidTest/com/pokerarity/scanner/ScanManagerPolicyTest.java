package com.pokerarity.scanner;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.pokerarity.scanner.data.model.PokemonData;
import com.pokerarity.scanner.service.ScanManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Date;

@org.junit.runner.RunWith(value = androidx.test.ext.junit.runners.AndroidJUnit4.class)
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0003\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H\u0007J\b\u0010\u0005\u001a\u00020\u0004H\u0007J\b\u0010\u0006\u001a\u00020\u0004H\u0007\u00a8\u0006\u0007"}, d2 = {"Lcom/pokerarity/scanner/ScanManagerPolicyTest;", "", "()V", "keepsDetailedPassWhenDateIsMissing", "", "keepsDetailedPassWhenNameConfidenceIsWeak", "skipsDetailedPassForReliableQuickScan", "app_debugAndroidTest"})
public final class ScanManagerPolicyTest {
    
    public ScanManagerPolicyTest() {
        super();
    }
    
    @org.junit.Test()
    public final void skipsDetailedPassForReliableQuickScan() {
    }
    
    @org.junit.Test()
    public final void keepsDetailedPassWhenDateIsMissing() {
    }
    
    @org.junit.Test()
    public final void keepsDetailedPassWhenNameConfidenceIsWeak() {
    }
}