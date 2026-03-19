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
import com.pokerarity.scanner.data.model.Pokemon;
import com.pokerarity.scanner.data.repository.PokemonRepository;
import com.pokerarity.scanner.service.OverlayManager;
import com.pokerarity.scanner.service.ScreenCaptureManager;
import com.pokerarity.scanner.service.ScreenCaptureService;
import dagger.hilt.android.AndroidEntryPoint;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000F\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u000e\u001a\u00020\u000fH\u0002J\u0012\u0010\u0010\u001a\u00020\u000f2\b\u0010\u0011\u001a\u0004\u0018\u00010\u0012H\u0014J\b\u0010\u0013\u001a\u00020\u000fH\u0014J\b\u0010\u0014\u001a\u00020\u000fH\u0002J\b\u0010\u0015\u001a\u00020\u000fH\u0002J\u0010\u0010\u0016\u001a\u00020\u000f2\u0006\u0010\u0017\u001a\u00020\u0018H\u0002J\u0010\u0010\u0019\u001a\u00020\u000f2\u0006\u0010\u001a\u001a\u00020\u0007H\u0002J\b\u0010\u001b\u001a\u00020\u000fH\u0002J\b\u0010\u001c\u001a\u00020\u000fH\u0002R\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00070\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001d"}, d2 = {"Lcom/pokerarity/scanner/ui/main/MainActivity;", "Landroidx/activity/ComponentActivity;", "()V", "mediaProjectionLauncher", "Landroidx/activity/result/ActivityResultLauncher;", "Landroid/content/Intent;", "notificationPermissionLauncher", "", "overlayPermissionLauncher", "overlayRunning", "Landroidx/compose/runtime/MutableState;", "", "repository", "Lcom/pokerarity/scanner/data/repository/PokemonRepository;", "handleStartPressed", "", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onResume", "refreshOverlayState", "requestMediaProjection", "sharePokemon", "pokemon", "Lcom/pokerarity/scanner/data/model/Pokemon;", "showToast", "message", "startCapture", "stopCapture", "app_debug"})
public final class MainActivity extends androidx.activity.ComponentActivity {
    private com.pokerarity.scanner.data.repository.PokemonRepository repository;
    @org.jetbrains.annotations.NotNull()
    private final androidx.compose.runtime.MutableState<java.lang.Boolean> overlayRunning = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> overlayPermissionLauncher = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> mediaProjectionLauncher = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<java.lang.String> notificationPermissionLauncher = null;
    
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
    
    private final void handleStartPressed() {
    }
    
    private final void requestMediaProjection() {
    }
    
    private final void startCapture() {
    }
    
    private final void stopCapture() {
    }
    
    private final void refreshOverlayState() {
    }
    
    private final void sharePokemon(com.pokerarity.scanner.data.model.Pokemon pokemon) {
    }
    
    private final void showToast(java.lang.String message) {
    }
}