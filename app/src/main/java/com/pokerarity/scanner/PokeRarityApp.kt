package com.pokerarity.scanner

import android.app.Application
import android.util.Log
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

    companion object {
        private const val TAG = "PokeRarityApp"
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var scanManager: ScanManager
        private set

    override fun onCreate() {
        super.onCreate()
        SqlCipherInitializer.ensureLoaded()
        OverlayStateStore.resetToIdle()
        // Load rarity manifest (species tiers, costume data, etc.) once at startup
        RarityManifestLoader.initialize(applicationContext)
        try {
            RarityUpdater.getInstance(applicationContext).syncAsync()
        } catch (_: Exception) {
            // MainActivity presents the typed, user-facing History recovery state.
            // Do not prevent it from opening when the database is preserved but unavailable.
            Log.w(TAG, "Skipped database-backed rarity sync because History is unavailable")
        }
        try {
            ScanTelemetryCoordinator.getInstance(applicationContext).flushPendingAsync()
        } catch (_: Exception) {
            // The telemetry repository also opens History during construction.
            Log.w(TAG, "Skipped telemetry startup because History is unavailable")
        }
        scanManager = ScanManager(applicationContext)
        scanManager.start()
        
        // 🔴 SECURITY: Enforce data retention policy on app startup
        applicationScope.launch {
            try {
                val retentionManager = DataRetentionManager(applicationContext)
                retentionManager.deleteOldScans()
                retentionManager.deleteOldTelemetry()
            } catch (_: Exception) {
                // Database recovery is surfaced in the main screen; maintenance is deferred.
                Log.w(TAG, "Skipped startup History maintenance because History is unavailable")
            }
        }
    }
}
