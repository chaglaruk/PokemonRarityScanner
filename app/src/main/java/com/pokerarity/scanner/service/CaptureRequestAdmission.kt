package com.pokerarity.scanner.service

import android.content.Intent
import android.util.Log
import com.pokerarity.scanner.util.RateLimiter
import com.pokerarity.scanner.util.perf.DebugScanTiming

/** Applies ownership, duplicate, and rate-limit checks before a capture reaches the service. */
internal class CaptureRequestAdmission(
    private val rateLimiter: RateLimiter,
    private val terminalizeOwnedRefusal: (Intent, String) -> Unit,
) {
    companion object {
        private const val TAG = "ScreenCaptureService"
    }

    fun handle(
        intent: Intent,
        activeOwnership: ScanRequestToken?,
        pendingOwnership: ScanRequestToken?,
        completedCaptureOwnership: ScanRequestToken?,
        capture: (ScanRequestToken) -> Unit,
    ) {
        val ownership = intent.parseOwnership()
        when {
            ownership != null && isAlreadyOwnedCapture(
                ownership,
                activeOwnership,
                pendingOwnership,
                completedCaptureOwnership
            ) -> {
                // Coalesced taps may rebroadcast their surviving token. Once that token
                // owns a capture slot or completed capture, ignore it without affecting OCR.
                Log.d(TAG, "captureReceiver: duplicate owned request ignored")
            }
            ownership != null && !ScanRequests.coordinator.isLiveRequest(ownership) -> {
                // Stale requests must not spend a rate-limit permit for newer work.
                Log.d(TAG, "captureReceiver: stale owned request ignored")
            }
            !rateLimiter.canProcess(TAG) -> {
                // Production requests already own a logical token. Terminalize those if
                // refused; bare legacy broadcasts remain rejected before ownership synthesis.
                Log.w(TAG, "Capture request rate-limited (${rateLimiter.getRequestCount()}/min)")
                terminalizeOwnedRefusal(intent, "rate_limited")
            }
            else -> resolveCaptureOwnership(intent)?.let(capture)
        }
    }

    private fun isAlreadyOwnedCapture(
        ownership: ScanRequestToken,
        activeOwnership: ScanRequestToken?,
        pendingOwnership: ScanRequestToken?,
        completedCaptureOwnership: ScanRequestToken?,
    ): Boolean {
        val alreadyCaptured = ownership == activeOwnership ||
            ownership == pendingOwnership ||
            ownership == completedCaptureOwnership
        return alreadyCaptured && ScanRequests.coordinator.isLiveRequest(ownership)
    }

    /** Resolve production metadata or synthesize ownership for a bare internal broadcast. */
    private fun resolveCaptureOwnership(intent: Intent): ScanRequestToken? {
        val ownership = when {
            intent.hasAnyOwnershipExtras() -> intent.parseOwnership()
            else -> acceptSynthesizedOwnership()
        }
        val refusalReason = when {
            ownership == null -> "malformed ownership metadata or scanner stopped"
            !ScanRequests.coordinator.isLiveRequest(ownership) -> "request superseded"
            else -> null
        }
        if (refusalReason != null) {
            Log.w(TAG, "captureReceiver: capture refused ($refusalReason)")
            return null
        }
        return ownership
    }

    private fun acceptSynthesizedOwnership(): ScanRequestToken? =
        when (val acceptance = ScanRequests.coordinator.acceptRequest(RequestOrigin.USER)) {
            is RequestAcceptance.Accepted -> acceptance.token.also(DebugScanTiming::requestAccepted)
            is RequestAcceptance.Coalesced -> acceptance.survivingToken
            RequestAcceptance.RejectedStopped -> null
        }
}
