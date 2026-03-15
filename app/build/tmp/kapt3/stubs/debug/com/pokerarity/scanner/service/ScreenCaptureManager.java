package com.pokerarity.scanner.service;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.Log;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;

/**
 * Manages the [MediaProjection] lifecycle.
 *
 * Usage from an Activity:
 * 1. Register a launcher with [createLauncher].
 * 2. Call [requestProjection] to prompt the user.
 * 3. On result, call [handleResult] which stores the projection intent.
 * 4. Call [getProjection] to obtain the live [MediaProjection].
 * 5. Call [release] when done.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000J\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u000e\u001a\u0004\u0018\u00010\r2\u0006\u0010\u000f\u001a\u00020\u0010J\u0010\u0010\u0011\u001a\u0004\u0018\u00010\t2\u0006\u0010\u000f\u001a\u00020\u0010J\u000e\u0010\u0012\u001a\u00020\u00062\u0006\u0010\u0013\u001a\u00020\u0014J\u0006\u0010\u0015\u001a\u00020\u0016J\u001c\u0010\u0017\u001a\u00020\u00162\f\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\r0\u00192\u0006\u0010\u000f\u001a\u00020\u0010R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u0011\u0010\u0005\u001a\u00020\u00068F\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0007R\u0010\u0010\b\u001a\u0004\u0018\u00010\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\f\u001a\u0004\u0018\u00010\rX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001a"}, d2 = {"Lcom/pokerarity/scanner/service/ScreenCaptureManager;", "", "()V", "TAG", "", "isGranted", "", "()Z", "projection", "Landroid/media/projection/MediaProjection;", "resultCode", "", "resultData", "Landroid/content/Intent;", "buildServiceIntent", "context", "Landroid/content/Context;", "getProjection", "handleResult", "result", "Landroidx/activity/result/ActivityResult;", "release", "", "requestProjection", "launcher", "Landroidx/activity/result/ActivityResultLauncher;", "app_debug"})
public final class ScreenCaptureManager {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "ScreenCaptureManager";
    private static int resultCode = android.app.Activity.RESULT_CANCELED;
    @org.jetbrains.annotations.Nullable()
    private static android.content.Intent resultData;
    
    /**
     * Stored projection – only one active at a time.
     */
    @org.jetbrains.annotations.Nullable()
    private static android.media.projection.MediaProjection projection;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.service.ScreenCaptureManager INSTANCE = null;
    
    private ScreenCaptureManager() {
        super();
    }
    
    public final boolean isGranted() {
        return false;
    }
    
    /**
     * Launch the system cast / projection permission dialog.
     */
    public final void requestProjection(@org.jetbrains.annotations.NotNull()
    androidx.activity.result.ActivityResultLauncher<android.content.Intent> launcher, @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    /**
     * Call from the ActivityResultCallback.  Returns `true` if the user granted.
     */
    public final boolean handleResult(@org.jetbrains.annotations.NotNull()
    androidx.activity.result.ActivityResult result) {
        return false;
    }
    
    /**
     * Obtain or create a [MediaProjection] from the stored grant.
     * Returns `null` if [handleResult] was never called successfully.
     */
    @org.jetbrains.annotations.Nullable()
    public final android.media.projection.MediaProjection getProjection(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    /**
     * Build the intent used to start [ScreenCaptureService].
     * The service needs the result code + data to create its own projection
     * (required on Android 14+, where the service must call startForeground
     * before obtaining the projection).
     */
    @org.jetbrains.annotations.Nullable()
    public final android.content.Intent buildServiceIntent(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    /**
     * Release the projection and clear stored state.
     */
    public final void release() {
    }
}