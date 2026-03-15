package com.pokerarity.scanner.ui.main;

import android.Manifest;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.pokerarity.scanner.R;
import com.pokerarity.scanner.data.local.db.AppDatabase;
import com.pokerarity.scanner.data.repository.PokemonRepository;
import com.pokerarity.scanner.databinding.ActivityMainBinding;
import com.pokerarity.scanner.service.OverlayManager;
import com.pokerarity.scanner.PokeRarityApp;
import com.pokerarity.scanner.service.ScreenCaptureManager;
import com.pokerarity.scanner.service.ScreenCaptureService;
import com.pokerarity.scanner.ui.result.HistoryActivity;
import com.pokerarity.scanner.ui.result.ResultActivity;
import dagger.hilt.android.AndroidEntryPoint;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\n\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u000f\u001a\u00020\u0010H\u0002J\b\u0010\u0011\u001a\u00020\u0010H\u0002J\u0012\u0010\u0012\u001a\u00020\u00102\b\u0010\u0013\u001a\u0004\u0018\u00010\u0014H\u0014J\b\u0010\u0015\u001a\u00020\u0010H\u0014J\b\u0010\u0016\u001a\u00020\u0010H\u0002J\b\u0010\u0017\u001a\u00020\u0010H\u0002J\b\u0010\u0018\u001a\u00020\u0010H\u0002J\u0010\u0010\u0019\u001a\u00020\u00102\u0006\u0010\u001a\u001a\u00020\u000bH\u0002J\b\u0010\u001b\u001a\u00020\u0010H\u0002J\b\u0010\u001c\u001a\u00020\u0010H\u0002J\b\u0010\u001d\u001a\u00020\u0010H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u000b0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\f\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001e"}, d2 = {"Lcom/pokerarity/scanner/ui/main/MainActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "adapter", "Lcom/pokerarity/scanner/ui/main/ScanHistoryAdapter;", "binding", "Lcom/pokerarity/scanner/databinding/ActivityMainBinding;", "mediaProjectionLauncher", "Landroidx/activity/result/ActivityResultLauncher;", "Landroid/content/Intent;", "notificationPermissionLauncher", "", "overlayPermissionLauncher", "repository", "Lcom/pokerarity/scanner/data/repository/PokemonRepository;", "handleStartPressed", "", "observeData", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onResume", "requestMediaProjection", "setupRecyclerView", "setupUI", "showToast", "msg", "startCapture", "stopCapture", "updateButtonState", "app_debug"})
public final class MainActivity extends androidx.appcompat.app.AppCompatActivity {
    private com.pokerarity.scanner.databinding.ActivityMainBinding binding;
    private com.pokerarity.scanner.ui.main.ScanHistoryAdapter adapter;
    private com.pokerarity.scanner.data.repository.PokemonRepository repository;
    
    /**
     * Overlay (SYSTEM_ALERT_WINDOW) permission result
     */
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> overlayPermissionLauncher = null;
    
    /**
     * MediaProjection permission result
     */
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> mediaProjectionLauncher = null;
    
    /**
     * Android 13+ notification permission result
     */
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<java.lang.String> notificationPermissionLauncher = null;
    
    public MainActivity() {
        super();
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    @java.lang.Override()
    protected void onResume() {
    }
    
    private final void setupUI() {
    }
    
    private final void setupRecyclerView() {
    }
    
    private final void observeData() {
    }
    
    private final void handleStartPressed() {
    }
    
    private final void requestMediaProjection() {
    }
    
    private final void startCapture() {
    }
    
    private final void stopCapture() {
    }
    
    private final void updateButtonState() {
    }
    
    private final void showToast(java.lang.String msg) {
    }
}