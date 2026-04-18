package com.pokerarity.scanner.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.compose.runtime.Composable;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavType;
import com.pokerarity.scanner.data.local.db.AppDatabase;
import com.pokerarity.scanner.data.local.TelemetryConfigPreferences;
import com.pokerarity.scanner.data.local.TelemetryPreferences;
import com.pokerarity.scanner.data.local.ScanUiPreferences;
import com.pokerarity.scanner.data.model.Pokemon;
import com.pokerarity.scanner.data.repository.PokemonRepository;
import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator;
import com.pokerarity.scanner.service.OverlayIntent;
import com.pokerarity.scanner.service.OverlayManager;
import com.pokerarity.scanner.service.OverlayStateStore;
import com.pokerarity.scanner.service.ScanStartupPolicy;
import com.pokerarity.scanner.service.ScreenCaptureManager;
import com.pokerarity.scanner.service.ScreenCaptureService;
import com.pokerarity.scanner.ui.result.HistoryActivity;
import com.pokerarity.scanner.ui.share.ResultShareRenderer;
import dagger.hilt.android.AndroidEntryPoint;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000Z\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0007\b\u0007\u0018\u0000 *2\u00020\u0001:\u0001*B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0016\u001a\u00020\u0017H\u0002J\u0012\u0010\u0018\u001a\u00020\u00172\b\u0010\u0019\u001a\u0004\u0018\u00010\u0005H\u0002J\u0012\u0010\u001a\u001a\u00020\u00172\b\u0010\u001b\u001a\u0004\u0018\u00010\u001cH\u0014J\u0010\u0010\u001d\u001a\u00020\u00172\u0006\u0010\u001e\u001a\u00020\u0005H\u0014J\b\u0010\u001f\u001a\u00020\u0017H\u0014J\b\u0010 \u001a\u00020\u0017H\u0002J\b\u0010!\u001a\u00020\u0017H\u0002J\u0010\u0010\"\u001a\u00020\u00172\u0006\u0010#\u001a\u00020$H\u0002J\u0010\u0010%\u001a\u00020\u00172\u0006\u0010&\u001a\u00020\u0007H\u0002J\u0012\u0010\'\u001a\u00020\u00172\b\b\u0002\u0010(\u001a\u00020\u000bH\u0002J\b\u0010)\u001a\u00020\u0017H\u0002R\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00070\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082.\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0015X\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006+"}, d2 = {"Lcom/pokerarity/scanner/ui/main/MainActivity;", "Landroidx/activity/ComponentActivity;", "()V", "mediaProjectionLauncher", "Landroidx/activity/result/ActivityResultLauncher;", "Landroid/content/Intent;", "notificationPermissionLauncher", "", "overlayPermissionLauncher", "overlayRunning", "Landroidx/compose/runtime/MutableState;", "", "repository", "Lcom/pokerarity/scanner/data/repository/PokemonRepository;", "scanUiPreferences", "Lcom/pokerarity/scanner/data/local/ScanUiPreferences;", "showConsentDialog", "showTelemetrySettings", "telemetryConfigPrefs", "Lcom/pokerarity/scanner/data/local/TelemetryConfigPreferences;", "telemetryPrefs", "Lcom/pokerarity/scanner/data/local/TelemetryPreferences;", "handleStartPressed", "", "handleStartupIntent", "startupIntent", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onNewIntent", "intent", "onResume", "refreshOverlayState", "requestMediaProjection", "sharePokemon", "pokemon", "Lcom/pokerarity/scanner/data/model/Pokemon;", "showToast", "message", "startCapture", "autoCapture", "stopCapture", "Companion", "PokeRarityScanner-v1.8.2_debug"})
public final class MainActivity extends androidx.activity.ComponentActivity {
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_OPEN_TELEMETRY_SETTINGS = "extra_open_telemetry_settings";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_AUTO_START_SCAN = "extra_auto_start_scan";
    private com.pokerarity.scanner.data.repository.PokemonRepository repository;
    private com.pokerarity.scanner.data.local.TelemetryPreferences telemetryPrefs;
    private com.pokerarity.scanner.data.local.TelemetryConfigPreferences telemetryConfigPrefs;
    private com.pokerarity.scanner.data.local.ScanUiPreferences scanUiPreferences;
    @org.jetbrains.annotations.NotNull()
    private final androidx.compose.runtime.MutableState<java.lang.Boolean> overlayRunning = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.compose.runtime.MutableState<java.lang.Boolean> showConsentDialog = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.compose.runtime.MutableState<java.lang.Boolean> showTelemetrySettings = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> overlayPermissionLauncher = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> mediaProjectionLauncher = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<java.lang.String> notificationPermissionLauncher = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.ui.main.MainActivity.Companion Companion = null;
    
    public MainActivity() {
        super(0);
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    @java.lang.Override()
    protected void onResume() {
    }
    
    @java.lang.Override()
    protected void onNewIntent(@org.jetbrains.annotations.NotNull()
    android.content.Intent intent) {
    }
    
    private final void handleStartPressed() {
    }
    
    private final void requestMediaProjection() {
    }
    
    private final void startCapture(boolean autoCapture) {
    }
    
    private final void stopCapture() {
    }
    
    private final void refreshOverlayState() {
    }
    
    private final void handleStartupIntent(android.content.Intent startupIntent) {
    }
    
    private final void sharePokemon(com.pokerarity.scanner.data.model.Pokemon pokemon) {
    }
    
    private final void showToast(java.lang.String message) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0006"}, d2 = {"Lcom/pokerarity/scanner/ui/main/MainActivity$Companion;", "", "()V", "EXTRA_AUTO_START_SCAN", "", "EXTRA_OPEN_TELEMETRY_SETTINGS", "PokeRarityScanner-v1.8.2_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}