package com.pokerarity.scanner.ui.splash;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.animation.AnimationUtils;
import androidx.appcompat.app.AppCompatActivity;
import com.pokerarity.scanner.R;
import com.pokerarity.scanner.databinding.ActivitySplashBinding;
import com.pokerarity.scanner.ui.main.MainActivity;
import com.pokerarity.scanner.util.ocr.OCRProcessor;
import kotlinx.coroutines.Dispatchers;

/**
 * Animated splash screen.
 *
 * While the PokeBall animation plays (~1.5 s), the OCR engine's trained-data
 * file is copied from assets to internal storage in the background so the
 * first scan doesn't have to wait.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0005\u001a\u00020\u00062\b\u0010\u0007\u001a\u0004\u0018\u00010\bH\u0014R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\t"}, d2 = {"Lcom/pokerarity/scanner/ui/splash/SplashActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "binding", "Lcom/pokerarity/scanner/databinding/ActivitySplashBinding;", "onCreate", "", "savedInstanceState", "Landroid/os/Bundle;", "app_debug"})
@android.annotation.SuppressLint(value = {"CustomSplashScreen"})
public final class SplashActivity extends androidx.appcompat.app.AppCompatActivity {
    private com.pokerarity.scanner.databinding.ActivitySplashBinding binding;
    
    public SplashActivity() {
        super();
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
}