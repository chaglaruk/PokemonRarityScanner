package com.pokerarity.scanner

import android.app.Application
import com.pokerarity.scanner.data.local.DataRetentionManager
import com.pokerarity.scanner.data.local.db.SqlCipherInitializer
import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator
import com.pokerarity.scanner.data.repository.RarityManifestLoader
import com.pokerarity.scanner.data.repository.RarityUpdater
import com.pokerarity.scanner.service.OverlayStateStore
import com.pokerarity.scanner.service.ScanManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class PokeRarityApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var scanManager: ScanManager
        private set

    override fun onCreate() {
        super.onCreate()
        SqlCipherInitializer.ensureLoaded()
        OverlayStateStore.resetToIdle()
        // Load rarity manifest (species tiers, costume data, etc.) once at startup
        RarityManifestLoader.initialize(applicationContext)
        RarityUpdater.getInstance(applicationContext).syncAsync()
        ScanTelemetryCoordinator.getInstance(applicationContext).flushPendingAsync()
        scanManager = ScanManager(applicationContext)
        scanManager.start()
        
        // 🔴 SECURITY: Enforce data retention policy on app startup
        applicationScope.launch {
            val retentionManager = DataRetentionManager(applicationContext)
            retentionManager.deleteOldScans()
            retentionManager.deleteOldTelemetry()
        }
    }
}
