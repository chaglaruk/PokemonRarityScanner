package com.pokerarity.scanner

import android.app.Application
import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator
import com.pokerarity.scanner.data.repository.RarityManifestLoader
import com.pokerarity.scanner.data.repository.RarityUpdater
import com.pokerarity.scanner.service.ScanManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PokeRarityApp : Application() {

    lateinit var scanManager: ScanManager
        private set

    override fun onCreate() {
        super.onCreate()
        // Load rarity manifest (species tiers, costume data, etc.) once at startup
        RarityManifestLoader.initialize(applicationContext)
        RarityUpdater.getInstance(applicationContext).syncAsync()
        ScanTelemetryCoordinator.getInstance(applicationContext).flushPendingAsync()
        scanManager = ScanManager(applicationContext)
        scanManager.start()
    }
}
