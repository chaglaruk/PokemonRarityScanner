package com.pokerarity.scanner.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.pokerarity.scanner.R;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Foreground service that holds a [MediaProjection] and captures screenshots
 * on demand when an [OverlayService.ACTION_CAPTURE_REQUESTED] broadcast arrives.
 *
 * Android 14 / targetSdk 35 fix — two-phase foreground promotion:
 *
 *  Phase 1 — onCreate():
 *    startForeground(id, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
 *    No MediaProjection token exists yet; SPECIAL_USE requires no token.
 *    Manifest declares foregroundServiceType="specialUse|mediaProjection".
 *
 *  Phase 2 — setupProjection(), AFTER getMediaProjection() succeeds:
 *    startForeground(id, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
 *    Android now validates the token — promotion succeeds without SecurityException.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000V\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\b\u0018\u0000 #2\u00020\u0001:\u0001#B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u000f\u001a\u00020\u0010H\u0002J\b\u0010\u0011\u001a\u00020\u0010H\u0002J\b\u0010\u0012\u001a\u00020\u0013H\u0002J\b\u0010\u0014\u001a\u00020\u0010H\u0002J\u0014\u0010\u0015\u001a\u0004\u0018\u00010\u00162\b\u0010\u0017\u001a\u0004\u0018\u00010\u0018H\u0016J\b\u0010\u0019\u001a\u00020\u0010H\u0016J\b\u0010\u001a\u001a\u00020\u0010H\u0016J\"\u0010\u001b\u001a\u00020\u001c2\b\u0010\u0017\u001a\u0004\u0018\u00010\u00182\u0006\u0010\u001d\u001a\u00020\u001c2\u0006\u0010\u001e\u001a\u00020\u001cH\u0016J\u0018\u0010\u001f\u001a\u00020\u00102\u0006\u0010 \u001a\u00020\u001c2\u0006\u0010!\u001a\u00020\u0018H\u0002J\b\u0010\"\u001a\u00020\u0010H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\r\u001a\u0004\u0018\u00010\u000eX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006$"}, d2 = {"Lcom/pokerarity/scanner/service/ScreenCaptureService;", "Landroid/app/Service;", "()V", "captureReceiver", "Landroid/content/BroadcastReceiver;", "handler", "Landroid/os/Handler;", "imageReader", "Landroid/media/ImageReader;", "isCapturing", "", "mediaProjection", "Landroid/media/projection/MediaProjection;", "virtualDisplay", "Landroid/hardware/display/VirtualDisplay;", "broadcastError", "", "captureSequence", "createNotification", "Landroid/app/Notification;", "createNotificationChannel", "onBind", "Landroid/os/IBinder;", "intent", "Landroid/content/Intent;", "onCreate", "onDestroy", "onStartCommand", "", "flags", "startId", "setupProjection", "resultCode", "resultData", "tearDown", "Companion", "app_debug"})
public final class ScreenCaptureService extends android.app.Service {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "ScreenCaptureService";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_RESULT_CODE = "extra_result_code";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_RESULT_DATA = "extra_result_data";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_SCREENSHOT_READY = "com.pokerarity.scanner.SCREENSHOT_READY";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_SCREENSHOT_PATHS = "extra_screenshot_paths";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String CHANNEL_ID = "capture_channel";
    private static final int NOTIFICATION_ID = 1002;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String VIRTUAL_DISPLAY_NAME = "PokeRarityCapture";
    @org.jetbrains.annotations.Nullable()
    private android.media.projection.MediaProjection mediaProjection;
    @org.jetbrains.annotations.Nullable()
    private android.hardware.display.VirtualDisplay virtualDisplay;
    @org.jetbrains.annotations.Nullable()
    private android.media.ImageReader imageReader;
    @org.jetbrains.annotations.NotNull()
    private final android.os.Handler handler = null;
    private boolean isCapturing = false;
    @org.jetbrains.annotations.NotNull()
    private final android.content.BroadcastReceiver captureReceiver = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.service.ScreenCaptureService.Companion Companion = null;
    
    public ScreenCaptureService() {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public android.os.IBinder onBind(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent) {
        return null;
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    @java.lang.Override()
    public int onStartCommand(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent, int flags, int startId) {
        return 0;
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    private final void setupProjection(int resultCode, android.content.Intent resultData) {
    }
    
    private final void captureSequence() {
    }
    
    private final void broadcastError() {
    }
    
    private final void tearDown() {
    }
    
    private final void createNotificationChannel() {
    }
    
    private final android.app.Notification createNotification() {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\b\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\r"}, d2 = {"Lcom/pokerarity/scanner/service/ScreenCaptureService$Companion;", "", "()V", "ACTION_SCREENSHOT_READY", "", "CHANNEL_ID", "EXTRA_RESULT_CODE", "EXTRA_RESULT_DATA", "EXTRA_SCREENSHOT_PATHS", "NOTIFICATION_ID", "", "TAG", "VIRTUAL_DISPLAY_NAME", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}