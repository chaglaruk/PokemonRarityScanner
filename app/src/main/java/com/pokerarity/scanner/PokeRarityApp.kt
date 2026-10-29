package com.pokerarity.scanner

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal var sqlCipherInitializationFailed: Boolean = false
        private set

    lateinit var scanManager: ScanManager
        private set

    internal fun hasStartedScannerRuntime(): Boolean = this::scanManager.isInitialized

    override fun onCreate() {
        super.onCreate()
        if (isDatabaseInitializationLockProbeProcess()) return

        try {
            SqlCipherInitializer.ensureLoaded()
        } catch (_: UnsatisfiedLinkError) {
            // Let MainActivity show the safe History recovery state instead of crashing
            // before the user can see why database-backed features are unavailable.
            sqlCipherInitializationFailed = true
            Log.e(TAG, "SQLCipher native library unavailable; History access is disabled")
        }
        OverlayStateStore.resetToIdle()
        // Load rarity manifest (species tiers, costume data, etc.) once at startup
        RarityManifestLoader.initialize(applicationContext)
        if (!sqlCipherInitializationFailed) {
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
        }
        scanManager = ScanManager(applicationContext)
        scanManager.start()
        
        // 🔴 SECURITY: Enforce data retention policy on app startup
        if (!sqlCipherInitializationFailed) {
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
        private const val TAG = "PokeRarityApp"
        const val DATABASE_LOCK_PROBE_PROCESS_SUFFIX = ":database_init_lock_test"
    }
}
