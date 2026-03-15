package com.pokerarity.scanner;

import android.app.Application;
import com.pokerarity.scanner.data.repository.RarityManifestLoader;
import com.pokerarity.scanner.service.ScanManager;
import dagger.hilt.android.HiltAndroidApp;

@dagger.hilt.android.HiltAndroidApp()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\b\u001a\u00020\tH\u0016R\u001e\u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0003\u001a\u00020\u0004@BX\u0086.\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\n"}, d2 = {"Lcom/pokerarity/scanner/PokeRarityApp;", "Landroid/app/Application;", "()V", "<set-?>", "Lcom/pokerarity/scanner/service/ScanManager;", "scanManager", "getScanManager", "()Lcom/pokerarity/scanner/service/ScanManager;", "onCreate", "", "app_debug"})
public final class PokeRarityApp extends android.app.Application {
    private com.pokerarity.scanner.service.ScanManager scanManager;
    
    public PokeRarityApp() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.service.ScanManager getScanManager() {
        return null;
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
}