package com.pokerarity.scanner.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

/**
 * Manages the overlay service lifecycle and permission handling.
 *
 * Usage:
 *   1. Check [canDrawOverlays] before starting.
 *   2. If false, call [requestOverlayPermission] with an ActivityResultLauncher.
 *   3. Once granted, call [startOverlay] to show the floating PokeBall.
 *   4. Call [stopOverlay] to remove it.
 */
object OverlayManager {

    /**
     * Check if the app has SYSTEM_ALERT_WINDOW permission.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Open system settings to request overlay permission.
     * The result is delivered to the [launcher] callback.
     */
    fun requestOverlayPermission(launcher: ActivityResultLauncher<Intent>, context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        launcher.launch(intent)
    }

    /**
     * Start the [OverlayService] as a foreground service.
     * Requires overlay permission to already be granted.
     */
    fun startOverlay(context: Context) {
        val intent = Intent(context, OverlayService::class.java)
        context.startForegroundService(intent)
    }

    /**
     * Stop the [OverlayService] and remove the overlay.
     */
    fun stopOverlay(context: Context) {
        val intent = Intent(context, OverlayService::class.java)
        context.stopService(intent)
    }

    /**
     * Check if the [OverlayService] is currently running.
     */
    @Suppress("DEPRECATION")
    fun isOverlayRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (OverlayService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
