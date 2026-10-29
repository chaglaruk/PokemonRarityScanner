package com.pokerarity.scanner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.pokerarity.scanner.R
import com.pokerarity.scanner.ui.permission.ProjectionPermissionActivity

/** Owns overlay consent recovery registration and bounded retries for one service lifetime. */
internal class OverlayProjectionRecovery(private val context: Context, handler: Handler) {
    private var registered = false
    private val recovery = ProjectionConsentLaunchRecovery(context, handler, ::launchPermissionActivity)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ScreenCaptureService.ACTION_PROJECTION_REQUIRED -> {
                    if (intent.getBooleanExtra(ScreenCaptureService.EXTRA_PROJECTION_RECOVERY_PERSISTED, true)) {
                        launchIfPending()
                    } else {
                        recovery.launchOnceWithoutPendingMarker()
                    }
                }
                OverlayService.ACTION_PROJECTION_PERMISSION_ACTIVITY_SHOWN,
                ScreenCaptureService.ACTION_PROJECTION_RECOVERY_RESET -> recovery.onRecoveryReset()
                ScreenCaptureService.ACTION_PROJECTION_STOPPED -> {
                    Log.i("OverlayService", "Screen capture stopped; waiting for a user restart")
                    Toast.makeText(context, R.string.projection_stopped_message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(ScreenCaptureService.ACTION_PROJECTION_REQUIRED)
            addAction(ScreenCaptureService.ACTION_PROJECTION_RECOVERY_RESET)
            addAction(OverlayService.ACTION_PROJECTION_PERMISSION_ACTIVITY_SHOWN)
            addAction(ScreenCaptureService.ACTION_PROJECTION_STOPPED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(context, receiver, filter,
                ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION, null, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        registered = true
        launchIfPending()
    }

    fun launchIfPending() { recovery.launchIfPending() }

    fun destroy() {
        recovery.onDestroy()
        if (registered) {
            context.unregisterReceiver(receiver)
            registered = false
        }
    }

    private fun launchPermissionActivity() {
        Log.w("OverlayService", "Projection required; launching permission flow")
        context.startActivity(Intent(context, ProjectionPermissionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(ProjectionPermissionActivity.EXTRA_AUTO_CAPTURE, false)
        })
    }
}
