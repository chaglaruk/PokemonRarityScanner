package com.pokerarity.scanner

import android.app.Application
import android.util.Log
import com.pokerarity.scanner.data.local.DataRetentionManager
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
        OverlayStateStore.resetToIdle()
        scanManager = ScanManager(applicationContext)
        scanManager.start()

        // Database opening, key creation, and encrypted preferences can load native
        // crypto. Keep cold-start storage initialization away from the main thread.
        applicationScope.launch {
            RarityManifestLoader.initialize(applicationContext)
            runCatching {
                RarityUpdater.getInstance(applicationContext).syncAsync()
            }.onFailure { error ->
                Log.w(TAG, "Rarity startup sync skipped (${error.javaClass.simpleName})")
            }
            runCatching {
                ScanTelemetryCoordinator.getInstance(applicationContext).flushPendingAsync()
            }.onFailure { error ->
                Log.w(TAG, "Telemetry startup skipped (${error.javaClass.simpleName})")
            }
            runCatching {
                val retentionManager = DataRetentionManager(applicationContext)
                retentionManager.deleteOldScans()
                retentionManager.deleteOldTelemetry()
            }.onFailure { error ->
                Log.w(TAG, "Startup retention cleanup skipped (${error.javaClass.simpleName})")
            }
        }
    }
}
