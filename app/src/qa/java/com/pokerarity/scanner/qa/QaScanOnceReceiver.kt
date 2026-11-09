package com.pokerarity.scanner.qa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.service.OverlayManager
import com.pokerarity.scanner.service.OverlayService
import com.pokerarity.scanner.service.RequestAcceptance
import com.pokerarity.scanner.service.RequestOrigin
import com.pokerarity.scanner.service.ScanRequests
import com.pokerarity.scanner.service.ScreenCaptureManager
import com.pokerarity.scanner.service.ScreenCaptureService
import com.pokerarity.scanner.service.putOwnershipExtras
import com.pokerarity.scanner.util.perf.DebugScanTiming

/**
 * QA-only, shell-triggered single scan. Not included in debug or release builds.
 *
 * Does not grant projection, launch a game, inject game input, or start a scanner
 * session. The user must already have granted MediaProjection for the active
 * scanner session; the host must independently check the intended screen.
 */
class QaScanOnceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val senderUid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            sentFromUid
        } else {
            Process.INVALID_UID
        }
        val sessionActive = OverlayManager.isOverlayRunning(context) &&
            ScreenCaptureManager.isGranted
        if (!QaScanOncePolicy.mayTrigger(
                action = intent.action,
                sdkInt = Build.VERSION.SDK_INT,
                senderUid = senderUid,
                sourceSha = BuildConfig.QA_SOURCE_SHA,
                sessionActive = sessionActive,
                coordinatorStopped = ScanRequests.coordinator.isStopped
            )
        ) {
            Log.w(TAG, "QA scan request rejected: shell identity, source SHA or session unavailable")
            return
        }

        when (val accepted = ScanRequests.coordinator.acceptRequest(RequestOrigin.AUTO)) {
            is RequestAcceptance.Accepted -> {
                DebugScanTiming.requestAccepted(accepted.token)
                dispatchCapture(context, accepted.token)
                Log.i(TAG, "QA capture request accepted: requestId=${accepted.token.requestId}")
            }
            is RequestAcceptance.Coalesced -> {
                // Avoid sending a second broadcast for an already-reserved request.
                Log.i(TAG, "QA capture request coalesced without duplicate dispatch")
            }
            RequestAcceptance.RejectedStopped -> {
                Log.w(TAG, "QA capture refused: scanner stopped")
            }
        }
    }

    private fun dispatchCapture(
        context: Context,
        ownership: com.pokerarity.scanner.service.ScanRequestToken
    ) {
        context.sendBroadcast(Intent(OverlayService.ACTION_CAPTURE_REQUESTED).apply {
            setPackage(context.packageName)
            putOwnershipExtras(ownership)
        }, ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION)
    }

    companion object {
        private const val TAG = "QaScanOnceReceiver"
    }
}

/** Pure authorization predicate; all checks must pass before accepting ownership. */
internal object QaScanOncePolicy {
    const val ACTION = "com.pokerarity.scanner.QA_SCAN_ONCE"
    private val SHA_PATTERN = Regex("[a-fA-F0-9]{40}")

    fun mayTrigger(
        action: String?,
        sdkInt: Int,
        senderUid: Int,
        sourceSha: String,
        sessionActive: Boolean,
        coordinatorStopped: Boolean
    ): Boolean = action == ACTION &&
        sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        senderUid == Process.SHELL_UID &&
        SHA_PATTERN.matches(sourceSha) &&
        sessionActive &&
        !coordinatorStopped
}
