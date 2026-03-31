package com.pokerarity.scanner.ui.result;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.compose.runtime.Composable;
import com.pokerarity.scanner.R;
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity;
import com.pokerarity.scanner.data.model.ScanDecisionSupport;
import com.pokerarity.scanner.data.repository.PokemonRepository;
import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator;
import com.pokerarity.scanner.ui.share.ResultShareRenderer;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.Locale;
import javax.inject.Inject;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\b\u0007\u0018\u0000 \u001c2\u00020\u0001:\u0001\u001cB\u0005\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u000f\u001a\u00020\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u0012H\u0014J\n\u0010\u0013\u001a\u0004\u0018\u00010\u0014H\u0002J\b\u0010\u0015\u001a\u00020\u0010H\u0002J\u0010\u0010\u0016\u001a\u00020\u00102\u0006\u0010\u0017\u001a\u00020\u0018H\u0002J\u0010\u0010\u0019\u001a\u00020\u00102\u0006\u0010\u001a\u001a\u00020\u001bH\u0002R\u001e\u0010\u0003\u001a\u00020\u00048\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR\u001b\u0010\t\u001a\u00020\n8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\r\u0010\u000e\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u001d"}, d2 = {"Lcom/pokerarity/scanner/ui/result/ResultActivity;", "Landroidx/activity/ComponentActivity;", "()V", "repository", "Lcom/pokerarity/scanner/data/repository/PokemonRepository;", "getRepository", "()Lcom/pokerarity/scanner/data/repository/PokemonRepository;", "setRepository", "(Lcom/pokerarity/scanner/data/repository/PokemonRepository;)V", "telemetryCoordinator", "Lcom/pokerarity/scanner/data/remote/ScanTelemetryCoordinator;", "getTelemetryCoordinator", "()Lcom/pokerarity/scanner/data/remote/ScanTelemetryCoordinator;", "telemetryCoordinator$delegate", "Lkotlin/Lazy;", "onCreate", "", "savedInstanceState", "Landroid/os/Bundle;", "parseDecisionSupport", "Lcom/pokerarity/scanner/data/model/ScanDecisionSupport;", "saveSnapshot", "shareResult", "pokemon", "Lcom/pokerarity/scanner/data/model/Pokemon;", "submitFeedback", "category", "", "Companion", "app_debug"})
public final class ResultActivity extends androidx.activity.ComponentActivity {
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
    public static final java.lang.String EXTRA_HAS_SPECIAL_FORM = "extra_has_special_form";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_IV_ESTIMATE = "extra_iv_estimate";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_EXPLANATIONS = "extra_explanations";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_BREAKDOWN_KEYS = "extra_breakdown_keys";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_BREAKDOWN_VALUES = "extra_breakdown_values";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_DATE = "extra_date";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_TELEMETRY_UPLOAD_ID = "extra_telemetry_upload_id";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_EVENT_CONFIDENCE_CODE = "extra_event_confidence_code";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_EVENT_CONFIDENCE_LABEL = "extra_event_confidence_label";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_EVENT_CONFIDENCE_DETAIL = "extra_event_confidence_detail";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_SCAN_CONFIDENCE_SCORE = "extra_scan_confidence_score";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_SCAN_CONFIDENCE_LABEL = "extra_scan_confidence_label";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_SCAN_CONFIDENCE_DETAIL = "extra_scan_confidence_detail";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_MISMATCH_GUARD_TITLE = "extra_mismatch_guard_title";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_MISMATCH_GUARD_DETAIL = "extra_mismatch_guard_detail";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_WHY_NOT_EXACT = "extra_why_not_exact";
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy telemetryCoordinator$delegate = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.ui.result.ResultActivity.Companion Companion = null;
    
    public ResultActivity() {
        super(0);
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pokerarity.scanner.data.repository.PokemonRepository getRepository() {
        return null;
    }
    
    public final void setRepository(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.repository.PokemonRepository p0) {
    }
    
    private final com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator getTelemetryCoordinator() {
        return null;
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final com.pokerarity.scanner.data.model.ScanDecisionSupport parseDecisionSupport() {
        return null;
    }
    
    private final void submitFeedback(java.lang.String category) {
    }
    
    private final void saveSnapshot() {
    }
    
    private final void shareResult(com.pokerarity.scanner.data.model.Pokemon pokemon) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0019\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0018\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001a\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001c\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001d"}, d2 = {"Lcom/pokerarity/scanner/ui/result/ResultActivity$Companion;", "", "()V", "EXTRA_BREAKDOWN_KEYS", "", "EXTRA_BREAKDOWN_VALUES", "EXTRA_CP", "EXTRA_DATE", "EXTRA_EVENT_CONFIDENCE_CODE", "EXTRA_EVENT_CONFIDENCE_DETAIL", "EXTRA_EVENT_CONFIDENCE_LABEL", "EXTRA_EXPLANATIONS", "EXTRA_HAS_COSTUME", "EXTRA_HAS_SPECIAL_FORM", "EXTRA_HP", "EXTRA_IS_LUCKY", "EXTRA_IS_SHADOW", "EXTRA_IS_SHINY", "EXTRA_IV_ESTIMATE", "EXTRA_MISMATCH_GUARD_DETAIL", "EXTRA_MISMATCH_GUARD_TITLE", "EXTRA_POKEMON_NAME", "EXTRA_SCAN_CONFIDENCE_DETAIL", "EXTRA_SCAN_CONFIDENCE_LABEL", "EXTRA_SCAN_CONFIDENCE_SCORE", "EXTRA_SCORE", "EXTRA_TELEMETRY_UPLOAD_ID", "EXTRA_TIER", "EXTRA_WHY_NOT_EXACT", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}