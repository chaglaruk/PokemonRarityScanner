package com.pokerarity.scanner.service

import android.content.Context
import android.os.Handler
import android.util.Log

/** Retries delivery of a durable projection-consent request when Android blocks a background launch. */
internal class ProjectionConsentLaunchRecovery(
    private val context: Context,
    private val handler: Handler,
    private val launchPermissionActivity: () -> Unit,
) {
    companion object {
        private const val TAG = "ProjectionConsentRecovery"
        private const val RETRY_DELAY_MS = 10_000L
        private const val REARM_DELAY_MS = 60_000L
        private const val MAX_ATTEMPTS_PER_CYCLE = 2
    }

    private var launchAttempted = false
    private var unpersistedLaunchAttempted = false
    private var launchAttempts = 0
    private val retryRunnable = Runnable { retryAfterTimeout() }

    fun launchIfPending() {
        if (launchAttempted || !ProjectionConsentRecoveryState.isPending(context)) return
        if (unpersistedLaunchAttempted) {
            // Promote the immediate attempt to durable recovery without opening a duplicate screen.
            unpersistedLaunchAttempted = false
            if (launchAttempts >= MAX_ATTEMPTS_PER_CYCLE) launchAttempts = 0
            launchAttempted = true
            launchAttempts++
            scheduleRetry(RETRY_DELAY_MS)
            return
        }
        if (launchAttempts >= MAX_ATTEMPTS_PER_CYCLE) launchAttempts = 0

        launchAttempted = true
        launchAttempts++
        runCatching(launchPermissionActivity)
            .onFailure { Log.e(TAG, "Unable to launch projection permission flow; recovery remains pending", it) }
        scheduleRetry(RETRY_DELAY_MS)
    }

    /** Gives the explicit failure broadcast one immediate attempt when durable state could not be saved. */
    fun launchOnceWithoutPendingMarker() {
        if (ProjectionConsentRecoveryState.isPending(context)) {
            launchIfPending()
            return
        }
        if (launchAttempted || unpersistedLaunchAttempted) return

        unpersistedLaunchAttempted = true
        runCatching(launchPermissionActivity)
            .onFailure { Log.e(TAG, "Unable to launch projection permission flow after persistence failure", it) }
    }

    fun onRecoveryReset() {
        handler.removeCallbacks(retryRunnable)
        launchAttempted = false
        unpersistedLaunchAttempted = false
        launchAttempts = 0
    }

    fun onDestroy() {
        handler.removeCallbacks(retryRunnable)
    }

    private fun retryAfterTimeout() {
        if (!ProjectionConsentRecoveryState.isPending(context)) {
            launchAttempted = false
            launchAttempts = 0
            return
        }

        launchAttempted = false
        if (launchAttempts >= MAX_ATTEMPTS_PER_CYCLE) {
            // Keep durable recovery pending and retry slowly even if the capture service has stopped.
            launchAttempts = 0
            Log.w(TAG, "Projection permission launch attempts exhausted; retrying after cooldown")
            scheduleRetry(REARM_DELAY_MS)
            return
        }

        launchIfPending()
    }

    private fun scheduleRetry(delayMillis: Long) {
        handler.removeCallbacks(retryRunnable)
        handler.postDelayed(retryRunnable, delayMillis)
    }
}
