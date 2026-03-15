package com.pokerarity.scanner.service;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import androidx.core.app.NotificationCompat;
import com.pokerarity.scanner.R;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000d\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0018\u0002\n\u0002\b\u0004\u0018\u0000 ,2\u00020\u0001:\u0001,B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0015\u001a\u00020\u0016H\u0002J\b\u0010\u0017\u001a\u00020\u0016H\u0002J\b\u0010\u0018\u001a\u00020\u0019H\u0002J\b\u0010\u001a\u001a\u00020\u0016H\u0002J\u0014\u0010\u001b\u001a\u0004\u0018\u00010\u001c2\b\u0010\u001d\u001a\u0004\u0018\u00010\u001eH\u0016J\b\u0010\u001f\u001a\u00020\u0016H\u0016J\b\u0010 \u001a\u00020\u0016H\u0016J\b\u0010!\u001a\u00020\u0016H\u0002J\"\u0010\"\u001a\u00020\f2\b\u0010\u001d\u001a\u0004\u0018\u00010\u001e2\u0006\u0010#\u001a\u00020\f2\u0006\u0010$\u001a\u00020\fH\u0016J\b\u0010%\u001a\u00020\u0016H\u0002J\b\u0010&\u001a\u00020\u0016H\u0002J\u0010\u0010\'\u001a\u00020\u00162\u0006\u0010(\u001a\u00020)H\u0002J\b\u0010*\u001a\u00020\u0016H\u0002J\u0010\u0010+\u001a\u00020\u00162\u0006\u0010(\u001a\u00020)H\u0002R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0014X\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006-"}, d2 = {"Lcom/pokerarity/scanner/service/OverlayService;", "Landroid/app/Service;", "()V", "closeView", "Landroid/view/View;", "debugOverlayView", "handler", "Landroid/os/Handler;", "initialTouchX", "", "initialTouchY", "initialX", "", "initialY", "isLongPress", "", "longPressRunnable", "Ljava/lang/Runnable;", "overlayView", "windowManager", "Landroid/view/WindowManager;", "addDebugOverlay", "", "addOverlayView", "createNotification", "Landroid/app/Notification;", "createNotificationChannel", "onBind", "Landroid/os/IBinder;", "intent", "Landroid/content/Intent;", "onCreate", "onDestroy", "onOverlayClicked", "onStartCommand", "flags", "startId", "removeCloseButton", "removeDebugOverlay", "setupTouchListener", "params", "Landroid/view/WindowManager$LayoutParams;", "showCloseButton", "snapToEdge", "Companion", "app_debug"})
public final class OverlayService extends android.app.Service {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "OverlayService";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_CAPTURE_REQUESTED = "com.pokerarity.scanner.CAPTURE_REQUESTED";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String CHANNEL_ID = "overlay_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long LONG_PRESS_DELAY = 500L;
    private static final int CLICK_THRESHOLD_DP = 10;
    private android.view.WindowManager windowManager;
    private android.view.View overlayView;
    @org.jetbrains.annotations.Nullable()
    private android.view.View closeView;
    @org.jetbrains.annotations.Nullable()
    private android.view.View debugOverlayView;
    private int initialX = 0;
    private int initialY = 0;
    private float initialTouchX = 0.0F;
    private float initialTouchY = 0.0F;
    private boolean isLongPress = false;
    @org.jetbrains.annotations.NotNull()
    private final android.os.Handler handler = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.Runnable longPressRunnable = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.service.OverlayService.Companion Companion = null;
    
    public OverlayService() {
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
    
    private final void addDebugOverlay() {
    }
    
    private final void removeDebugOverlay() {
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    private final void addOverlayView() {
    }
    
    @kotlin.Suppress(names = {"ClickableViewAccessibility"})
    private final void setupTouchListener(android.view.WindowManager.LayoutParams params) {
    }
    
    /**
     * Animate the overlay to the nearest horizontal screen edge.
     */
    private final void snapToEdge(android.view.WindowManager.LayoutParams params) {
    }
    
    /**
     * Called when the user taps (not drags) the overlay button.
     * Plays a pulse animation and broadcasts a capture request.
     */
    private final void onOverlayClicked() {
    }
    
    /**
     * Show the close button at the bottom of the screen on long press.
     */
    private final void showCloseButton() {
    }
    
    /**
     * Remove the close button from the window.
     */
    private final void removeCloseButton() {
    }
    
    private final void createNotificationChannel() {
    }
    
    private final android.app.Notification createNotification() {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\t\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0007X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\f"}, d2 = {"Lcom/pokerarity/scanner/service/OverlayService$Companion;", "", "()V", "ACTION_CAPTURE_REQUESTED", "", "CHANNEL_ID", "CLICK_THRESHOLD_DP", "", "LONG_PRESS_DELAY", "", "NOTIFICATION_ID", "TAG", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}