package com.pokerarity.scanner.ui.result;

import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.pokerarity.scanner.R;
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity;
import com.pokerarity.scanner.data.model.RarityTier;
import com.pokerarity.scanner.data.repository.PokemonRepository;
import com.pokerarity.scanner.databinding.ActivityResultBinding;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.inject.Inject;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00008\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0007\u0018\u0000 \u001c2\u00020\u0001:\u0001\u001cB\u0005\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u000eH\u0002J\u0010\u0010\u0010\u001a\u00020\u000e2\u0006\u0010\u0011\u001a\u00020\u0012H\u0002J\b\u0010\u0013\u001a\u00020\fH\u0002J\b\u0010\u0014\u001a\u00020\fH\u0002J\u0012\u0010\u0015\u001a\u00020\f2\b\u0010\u0016\u001a\u0004\u0018\u00010\u0017H\u0014J\b\u0010\u0018\u001a\u00020\fH\u0002J\b\u0010\u0019\u001a\u00020\fH\u0002J\b\u0010\u001a\u001a\u00020\fH\u0002J\b\u0010\u001b\u001a\u00020\fH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u001e\u0010\u0005\u001a\u00020\u00068\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0007\u0010\b\"\u0004\b\t\u0010\n\u00a8\u0006\u001d"}, d2 = {"Lcom/pokerarity/scanner/ui/result/ResultActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "binding", "Lcom/pokerarity/scanner/databinding/ActivityResultBinding;", "repository", "Lcom/pokerarity/scanner/data/repository/PokemonRepository;", "getRepository", "()Lcom/pokerarity/scanner/data/repository/PokemonRepository;", "setRepository", "(Lcom/pokerarity/scanner/data/repository/PokemonRepository;)V", "animateScore", "", "score", "", "color", "getTierColor", "tier", "Lcom/pokerarity/scanner/data/model/RarityTier;", "loadData", "loadEditableData", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "saveEditableData", "setupUI", "shareResult", "showDatePicker", "Companion", "app_debug"})
public final class ResultActivity extends androidx.appcompat.app.AppCompatActivity {
    @javax.inject.Inject()
    public com.pokerarity.scanner.data.repository.PokemonRepository repository;
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_POKEMON_NAME = "extra_pokemon_name";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_CP = "extra_cp";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_HP = "extra_hp";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_SCORE = "extra_score";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_TIER = "extra_tier";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_IS_SHINY = "extra_is_shiny";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_IS_SHADOW = "extra_is_shadow";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_IS_LUCKY = "extra_is_lucky";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_HAS_COSTUME = "extra_has_costume";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_IV_ESTIMATE = "extra_iv_estimate";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_RAW_DEBUG = "extra_raw_debug";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_EXPLANATIONS = "extra_explanations";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_BREAKDOWN_KEYS = "extra_breakdown_keys";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_BREAKDOWN_VALUES = "extra_breakdown_values";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_DATE = "extra_date";
    private com.pokerarity.scanner.databinding.ActivityResultBinding binding;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.ui.result.ResultActivity.Companion Companion = null;
    
    public ResultActivity() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.repository.PokemonRepository getRepository() {
        return null;
    }
    
    public final void setRepository(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.repository.PokemonRepository p0) {
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void setupUI() {
    }
    
    private final void showDatePicker() {
    }
    
    private final void loadEditableData() {
    }
    
    private final void saveEditableData() {
    }
    
    private final void shareResult() {
    }
    
    private final void loadData() {
    }
    
    private final void animateScore(int score, int color) {
    }
    
    private final int getTierColor(com.pokerarity.scanner.data.model.RarityTier tier) {
        return 0;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u000f\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0013"}, d2 = {"Lcom/pokerarity/scanner/ui/result/ResultActivity$Companion;", "", "()V", "EXTRA_BREAKDOWN_KEYS", "", "EXTRA_BREAKDOWN_VALUES", "EXTRA_CP", "EXTRA_DATE", "EXTRA_EXPLANATIONS", "EXTRA_HAS_COSTUME", "EXTRA_HP", "EXTRA_IS_LUCKY", "EXTRA_IS_SHADOW", "EXTRA_IS_SHINY", "EXTRA_IV_ESTIMATE", "EXTRA_POKEMON_NAME", "EXTRA_RAW_DEBUG", "EXTRA_SCORE", "EXTRA_TIER", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}