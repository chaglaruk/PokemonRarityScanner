package com.pokerarity.scanner.service;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import androidx.activity.result.ActivityResultLauncher;

/**
 * Manages the overlay service lifecycle and permission handling.
 *
 * Usage:
 *  1. Check [canDrawOverlays] before starting.
 *  2. If false, call [requestOverlayPermission] with an ActivityResultLauncher.
 *  3. Once granted, call [startOverlay] to show the floating PokeBall.
 *  4. Call [stopOverlay] to remove it.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006J\u000e\u0010\u0007\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006J\u001c\u0010\b\u001a\u00020\t2\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\f0\u000b2\u0006\u0010\u0005\u001a\u00020\u0006J\u000e\u0010\r\u001a\u00020\t2\u0006\u0010\u0005\u001a\u00020\u0006J\u000e\u0010\u000e\u001a\u00020\t2\u0006\u0010\u0005\u001a\u00020\u0006\u00a8\u0006\u000f"}, d2 = {"Lcom/pokerarity/scanner/service/OverlayManager;", "", "()V", "canDrawOverlays", "", "context", "Landroid/content/Context;", "isOverlayRunning", "requestOverlayPermission", "", "launcher", "Landroidx/activity/result/ActivityResultLauncher;", "Landroid/content/Intent;", "startOverlay", "stopOverlay", "app_debug"})
public final class OverlayManager {
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.service.OverlayManager INSTANCE = null;
    
    private OverlayManager() {
        super();
    }
    
    /**
     * Check if the app has SYSTEM_ALERT_WINDOW permission.
     */
    public final boolean canDrawOverlays(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return false;
    }
    
    /**
     * Open system settings to request overlay permission.
     * The result is delivered to the [launcher] callback.
     */
    public final void requestOverlayPermission(@org.jetbrains.annotations.NotNull()
    androidx.activity.result.ActivityResultLauncher<android.content.Intent> launcher, @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    /**
     * Start the [OverlayService] as a foreground service.
     * Requires overlay permission to already be granted.
     */
    public final void startOverlay(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    /**
     * Stop the [OverlayService] and remove the overlay.
     */
    public final void stopOverlay(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    /**
     * Check if the [OverlayService] is currently running.
     */
    @kotlin.Suppress(names = {"DEPRECATION"})
    public final boolean isOverlayRunning(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return false;
    }
}