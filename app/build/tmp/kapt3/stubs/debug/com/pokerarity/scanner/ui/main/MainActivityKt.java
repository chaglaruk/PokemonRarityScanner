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
import com.pokerarity.scanner.data.model.Pokemon;
import com.pokerarity.scanner.data.repository.PokemonRepository;
import com.pokerarity.scanner.service.OverlayManager;
import com.pokerarity.scanner.service.ScreenCaptureManager;
import com.pokerarity.scanner.service.ScreenCaptureService;
import com.pokerarity.scanner.ui.share.ResultShareRenderer;
import dagger.hilt.android.AndroidEntryPoint;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000&\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\u001aH\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00010\u00072\u0012\u0010\b\u001a\u000e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u00010\t2\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00010\u0007H\u0003\u00a8\u0006\f"}, d2 = {"MainContent", "", "repository", "Lcom/pokerarity/scanner/data/repository/PokemonRepository;", "isOverlayRunning", "", "onScanClick", "Lkotlin/Function0;", "onSharePokemon", "Lkotlin/Function1;", "Lcom/pokerarity/scanner/data/model/Pokemon;", "onTelemetrySettingsClick", "app_debug"})
public final class MainActivityKt {
    
    @androidx.compose.runtime.Composable()
    private static final void MainContent(com.pokerarity.scanner.data.repository.PokemonRepository repository, boolean isOverlayRunning, kotlin.jvm.functions.Function0<kotlin.Unit> onScanClick, kotlin.jvm.functions.Function1<? super com.pokerarity.scanner.data.model.Pokemon, kotlin.Unit> onSharePokemon, kotlin.jvm.functions.Function0<kotlin.Unit> onTelemetrySettingsClick) {
    }
}