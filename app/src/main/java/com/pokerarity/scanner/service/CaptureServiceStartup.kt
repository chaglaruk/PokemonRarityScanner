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
            rollback(context, stopOverlay = true)
            false
        }
    }

    /**
     * Rolls back capture when OverlayService reports an attachment failure after
     * the startup call has already returned. The failing overlay service stops
     * itself, so only capture and shared session state need to be retired here.
     */
    fun rollbackAfterOverlayStartupFailure(context: Context) {
        rollback(context, stopOverlay = false)
    }

    private fun rollback(context: Context, stopOverlay: Boolean) {
        runCatching { context.stopService(Intent(context, ScreenCaptureService::class.java)) }
        if (stopOverlay) runCatching { OverlayManager.stopOverlay(context) }
        ScreenCaptureManager.release()
        OverlayStateStore.dispatch(OverlayIntent.StopScan)
    }
}
