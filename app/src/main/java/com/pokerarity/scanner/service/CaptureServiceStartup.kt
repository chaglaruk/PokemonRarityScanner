package com.pokerarity.scanner.service

import android.content.Context
import android.content.Intent

/** Starts capture and its overlay as one recoverable startup attempt. */
object CaptureServiceStartup {

    /**
     * If either startup step fails, roll back any partial session so the next user
     * attempt cannot mistake a cached projection grant for a running capture service.
     */
    fun runOrRollback(
        context: Context,
        startCaptureService: () -> Unit,
        startOverlay: () -> Unit
    ): Boolean {
        return try {
            startCaptureService()
            startOverlay()
            true
        } catch (_: Exception) {
            runCatching { context.stopService(Intent(context, ScreenCaptureService::class.java)) }
            runCatching { OverlayManager.stopOverlay(context) }
            ScreenCaptureManager.release()
            OverlayStateStore.dispatch(OverlayIntent.StopScan)
            false
        }
    }
}
