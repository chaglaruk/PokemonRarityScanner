package com.pokerarity.scanner.service;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.Toast;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.ViewCompositionStrategy;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.savedstate.SavedStateRegistry;
import androidx.savedstate.SavedStateRegistryController;
import androidx.savedstate.SavedStateRegistryOwner;
import com.pokerarity.scanner.R;
import com.pokerarity.scanner.data.model.Pokemon;
import com.pokerarity.scanner.data.model.RarityAnalysisItem;
import com.pokerarity.scanner.data.model.RarityTier;
import com.pokerarity.scanner.data.model.ScanDecisionSupport;
import com.pokerarity.scanner.data.model.IvSolveMode;
import com.pokerarity.scanner.data.local.ScanUiPreferences;
import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator;
import com.pokerarity.scanner.ui.result.ResultActivity;
import com.pokerarity.scanner.ui.share.ResultShareRenderer;
import com.pokerarity.scanner.util.ClipboardService;
import com.pokerarity.scanner.util.HapticFeedbackManager;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u00e4\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0010\u000e\n\u0002\b\u0002\u0018\u0000 j2\u00020\u00012\u00020\u00022\u00020\u00032\u00020\u0004:\u0001jB\u0005\u00a2\u0006\u0002\u0010\u0005J\b\u0010<\u001a\u00020=H\u0002J\b\u0010>\u001a\u00020=H\u0002J\u001e\u0010?\u001a\b\u0012\u0004\u0012\u00020A0@2\u0006\u0010B\u001a\u00020C2\u0006\u0010D\u001a\u00020\u001aH\u0002J\u0010\u0010E\u001a\u00020F2\u0006\u0010B\u001a\u00020CH\u0002J\u0010\u0010G\u001a\u00020H2\u0006\u0010B\u001a\u00020CH\u0002J\n\u0010I\u001a\u0004\u0018\u00010JH\u0002J\b\u0010K\u001a\u00020=H\u0002J\b\u0010L\u001a\u00020=H\u0002J\u0010\u0010M\u001a\u00020\u001a2\u0006\u0010N\u001a\u00020OH\u0002J\b\u0010P\u001a\u00020\u001dH\u0002J\u0014\u0010Q\u001a\u0004\u0018\u00010R2\b\u0010B\u001a\u0004\u0018\u00010CH\u0016J\b\u0010S\u001a\u00020=H\u0016J\b\u0010T\u001a\u00020=H\u0016J\b\u0010U\u001a\u00020=H\u0002J\"\u0010V\u001a\u00020\u001a2\b\u0010B\u001a\u0004\u0018\u00010C2\u0006\u0010W\u001a\u00020\u001a2\u0006\u0010X\u001a\u00020\u001aH\u0016J\u0012\u0010Y\u001a\u0004\u0018\u00010Z2\u0006\u0010B\u001a\u00020CH\u0002J\b\u0010[\u001a\u00020=H\u0002J\b\u0010\\\u001a\u00020=H\u0002J\u0018\u0010]\u001a\u00020=2\u0006\u0010^\u001a\u00020\r2\u0006\u0010_\u001a\u00020`H\u0002J\u0010\u0010a\u001a\u00020=2\u0006\u0010_\u001a\u00020`H\u0002J\u0018\u0010b\u001a\u00020=2\u0006\u0010B\u001a\u00020C2\u0006\u0010c\u001a\u00020FH\u0002J\b\u0010d\u001a\u00020=H\u0002J\u0010\u0010e\u001a\u00020=2\u0006\u0010B\u001a\u00020CH\u0002J\u0010\u0010f\u001a\u00020=2\u0006\u0010_\u001a\u00020`H\u0002J\u0018\u0010g\u001a\u00020=2\u0006\u0010B\u001a\u00020C2\u0006\u0010h\u001a\u00020iH\u0002R\u001b\u0010\u0006\u001a\u00020\u00078BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\n\u0010\u000b\u001a\u0004\b\b\u0010\tR\u0010\u0010\f\u001a\u0004\u0018\u00010\rX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000e\u001a\u0004\u0018\u00010\rX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001b\u0010\u0011\u001a\u00020\u00128BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0015\u0010\u000b\u001a\u0004\b\u0013\u0010\u0014R\u000e\u0010\u0016\u001a\u00020\u0017X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0018\u001a\u00020\u0017X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\u001aX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001b\u001a\u00020\u001aX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001c\u001a\u00020\u001dX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u001e\u001a\u00020\u001f8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b \u0010!R\u000e\u0010\"\u001a\u00020#X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010$\u001a\u00020%X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010&\u001a\u00020\rX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\'\u001a\u00020(X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010)\u001a\u0004\u0018\u00010\rX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010*\u001a\u00020+8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b,\u0010-R\u000e\u0010.\u001a\u00020/X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001b\u00100\u001a\u0002018BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b4\u0010\u000b\u001a\u0004\b2\u00103R\u000e\u00105\u001a\u000206X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u00107\u001a\u0002068VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b8\u00109R\u000e\u0010:\u001a\u00020;X\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006k"}, d2 = {"Lcom/pokerarity/scanner/service/OverlayService;", "Landroid/app/Service;", "Landroidx/lifecycle/LifecycleOwner;", "Landroidx/savedstate/SavedStateRegistryOwner;", "Landroidx/lifecycle/ViewModelStoreOwner;", "()V", "clipboardService", "Lcom/pokerarity/scanner/util/ClipboardService;", "getClipboardService", "()Lcom/pokerarity/scanner/util/ClipboardService;", "clipboardService$delegate", "Lkotlin/Lazy;", "closeView", "Landroid/view/View;", "debugOverlayView", "handler", "Landroid/os/Handler;", "hapticFeedbackManager", "Lcom/pokerarity/scanner/util/HapticFeedbackManager;", "getHapticFeedbackManager", "()Lcom/pokerarity/scanner/util/HapticFeedbackManager;", "hapticFeedbackManager$delegate", "initialTouchX", "", "initialTouchY", "initialX", "", "initialY", "isLongPress", "", "lifecycle", "Landroidx/lifecycle/Lifecycle;", "getLifecycle", "()Landroidx/lifecycle/Lifecycle;", "lifecycleRegistry", "Landroidx/lifecycle/LifecycleRegistry;", "longPressRunnable", "Ljava/lang/Runnable;", "overlayView", "projectionRequiredReceiver", "Landroid/content/BroadcastReceiver;", "resultOverlayView", "savedStateRegistry", "Landroidx/savedstate/SavedStateRegistry;", "getSavedStateRegistry", "()Landroidx/savedstate/SavedStateRegistry;", "savedStateRegistryController", "Landroidx/savedstate/SavedStateRegistryController;", "scanUiPreferences", "Lcom/pokerarity/scanner/data/local/ScanUiPreferences;", "getScanUiPreferences", "()Lcom/pokerarity/scanner/data/local/ScanUiPreferences;", "scanUiPreferences$delegate", "serviceViewModelStore", "Landroidx/lifecycle/ViewModelStore;", "viewModelStore", "getViewModelStore", "()Landroidx/lifecycle/ViewModelStore;", "windowManager", "Landroid/view/WindowManager;", "addDebugOverlay", "", "addOverlayView", "buildOverlayAnalysis", "", "Lcom/pokerarity/scanner/data/model/RarityAnalysisItem;", "intent", "Landroid/content/Intent;", "fallbackScore", "buildOverlayPokemon", "Lcom/pokerarity/scanner/data/model/Pokemon;", "createResultComposeView", "Landroidx/compose/ui/platform/ComposeView;", "createShareImageUri", "Landroid/net/Uri;", "dismissResultOverlay", "exitApp", "getTierColor", "tier", "Lcom/pokerarity/scanner/data/model/RarityTier;", "isAppInForeground", "onBind", "Landroid/os/IBinder;", "onCreate", "onDestroy", "onOverlayClicked", "onStartCommand", "flags", "startId", "parseDecisionSupport", "Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;", "removeCloseButton", "removeDebugOverlay", "setupResultDrag", "handleView", "params", "Landroid/view/WindowManager$LayoutParams;", "setupTouchListener", "shareResult", "pokemon", "showCloseButton", "showResultOverlay", "snapToEdge", "submitFeedback", "category", "", "Companion", "PokeRarityScanner-v1.8.2_debug"})
public final class OverlayService extends android.app.Service implements androidx.lifecycle.LifecycleOwner, androidx.savedstate.SavedStateRegistryOwner, androidx.lifecycle.ViewModelStoreOwner {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "OverlayService";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_CAPTURE_REQUESTED = "com.pokerarity.scanner.CAPTURE_REQUESTED";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_SHOW_RESULT = "com.pokerarity.scanner.SHOW_RESULT";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_DISMISS_RESULT = "com.pokerarity.scanner.DISMISS_RESULT";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String CHANNEL_ID = "scanner_status_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long LONG_PRESS_DELAY = 500L;
    private static final int CLICK_THRESHOLD_DP = 10;
    @kotlin.jvm.Volatile()
    private static volatile boolean running = false;
    private android.view.WindowManager windowManager;
    private android.view.View overlayView;
    @org.jetbrains.annotations.Nullable()
    private android.view.View closeView;
    @org.jetbrains.annotations.Nullable()
    private android.view.View debugOverlayView;
    @org.jetbrains.annotations.Nullable()
    private android.view.View resultOverlayView;
    private int initialX = 0;
    private int initialY = 0;
    private float initialTouchX = 0.0F;
    private float initialTouchY = 0.0F;
    private boolean isLongPress = false;
    @org.jetbrains.annotations.NotNull()
    private final android.os.Handler handler = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LifecycleRegistry lifecycleRegistry = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.savedstate.SavedStateRegistryController savedStateRegistryController = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.ViewModelStore serviceViewModelStore = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy scanUiPreferences$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy clipboardService$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy hapticFeedbackManager$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final android.content.BroadcastReceiver projectionRequiredReceiver = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.Runnable longPressRunnable = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.service.OverlayService.Companion Companion = null;
    
    public OverlayService() {
        super();
    }
    
    private final com.pokerarity.scanner.data.local.ScanUiPreferences getScanUiPreferences() {
        return null;
    }
    
    private final com.pokerarity.scanner.util.ClipboardService getClipboardService() {
        return null;
    }
    
    private final com.pokerarity.scanner.util.HapticFeedbackManager getHapticFeedbackManager() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public android.os.IBinder onBind(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public androidx.lifecycle.Lifecycle getLifecycle() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public androidx.savedstate.SavedStateRegistry getSavedStateRegistry() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public androidx.lifecycle.ViewModelStore getViewModelStore() {
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
    
    private final void showResultOverlay(android.content.Intent intent) {
    }
    
    private final androidx.compose.ui.platform.ComposeView createResultComposeView(android.content.Intent intent) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.model.Pokemon buildOverlayPokemon(android.content.Intent intent) {
        return null;
    }
    
    private final com.pokerarity.scanner.data.model.ScanDecisionSupport parseDecisionSupport(android.content.Intent intent) {
        return null;
    }
    
    private final java.util.List<com.pokerarity.scanner.data.model.RarityAnalysisItem> buildOverlayAnalysis(android.content.Intent intent, int fallbackScore) {
        return null;
    }
    
    private final void shareResult(android.content.Intent intent, com.pokerarity.scanner.data.model.Pokemon pokemon) {
    }
    
    private final void submitFeedback(android.content.Intent intent, java.lang.String category) {
    }
    
    private final android.net.Uri createShareImageUri() {
        return null;
    }
    
    @kotlin.Suppress(names = {"ClickableViewAccessibility"})
    private final void setupResultDrag(android.view.View handleView, android.view.WindowManager.LayoutParams params) {
    }
    
    private final void dismissResultOverlay() {
    }
    
    @kotlin.Suppress(names = {"ClickableViewAccessibility"})
    private final void setupTouchListener(android.view.WindowManager.LayoutParams params) {
    }
    
    private final void snapToEdge(android.view.WindowManager.LayoutParams params) {
    }
    
    private final void onOverlayClicked() {
    }
    
    /**
     * Check if the PokeRarityScanner app is currently in the foreground.
     * Used to prevent overlay attacks and phishing.
     * 🟡 SECURITY: Only show overlay when user is directly interacting with our app.
     */
    private final boolean isAppInForeground() {
        return false;
    }
    
    private final void showCloseButton() {
    }
    
    private final void removeCloseButton() {
    }
    
    private final void exitApp() {
    }
    
    private final int getTierColor(com.pokerarity.scanner.data.model.RarityTier tier) {
        return 0;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010\b\n\u0000\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\u0010\u001a\u00020\u000fR\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\tX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0011"}, d2 = {"Lcom/pokerarity/scanner/service/OverlayService$Companion;", "", "()V", "ACTION_CAPTURE_REQUESTED", "", "ACTION_DISMISS_RESULT", "ACTION_SHOW_RESULT", "CHANNEL_ID", "CLICK_THRESHOLD_DP", "", "LONG_PRESS_DELAY", "", "NOTIFICATION_ID", "TAG", "running", "", "isRunning", "PokeRarityScanner-v1.8.2_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        public final boolean isRunning() {
            return false;
        }
    }
}