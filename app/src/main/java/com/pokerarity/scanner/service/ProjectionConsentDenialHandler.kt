package com.pokerarity.scanner.service

import android.content.Context
import android.content.Intent
import android.util.Log

/** Leaves no stale scanner UI or projection resources after the user declines consent. */
internal object ProjectionConsentDenialHandler {
    private const val TAG = "ProjectionConsentRecovery"

    fun stopScanner(context: Context) {
        ScanRequests.coordinator.onScannerLifecycle(started = false)
        ScreenCaptureManager.release()
        OverlayStateStore.dispatch(OverlayIntent.StopScan)
        if (!ProjectionConsentRecoveryState.clear(context)) {
            Log.e(TAG, "Unable to clear projection recovery state after consent denial")
        }
        context.stopService(Intent(context, ScreenCaptureService::class.java))
        context.stopService(Intent(context, OverlayService::class.java))
    }
}
