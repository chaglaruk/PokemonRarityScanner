package com.pokerarity.scanner

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
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

    internal fun hasStartedScannerRuntime(): Boolean = this::scanManager.isInitialized

    override fun onCreate() {
        super.onCreate()
        if (isDatabaseInitializationLockProbeProcess()) return

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

    private fun isDatabaseInitializationLockProbeProcess(): Boolean {
        if (!BuildConfig.DEBUG) return false

        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            getSystemService(ActivityManager::class.java)
                ?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }
                ?.processName
        }
        return processName == "$packageName$DATABASE_LOCK_PROBE_PROCESS_SUFFIX"
    }

    private companion object {
        const val DATABASE_LOCK_PROBE_PROCESS_SUFFIX = ":database_init_lock_test"
    }
}
