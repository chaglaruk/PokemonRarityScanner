package com.pokerarity.scanner.ui.result;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.pokerarity.scanner.R;
import com.pokerarity.scanner.data.local.db.AppDatabase;
import com.pokerarity.scanner.data.repository.PokemonRepository;
import com.pokerarity.scanner.databinding.ActivityHistoryBinding;
import com.pokerarity.scanner.ui.main.ScanHistoryAdapter;
import dagger.hilt.android.AndroidEntryPoint;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0002J\b\u0010\r\u001a\u00020\nH\u0002J\b\u0010\u000e\u001a\u00020\nH\u0002J\u0012\u0010\u000f\u001a\u00020\n2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0011H\u0014J\b\u0010\u0012\u001a\u00020\nH\u0002J\b\u0010\u0013\u001a\u00020\nH\u0002J\b\u0010\u0014\u001a\u00020\nH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0015"}, d2 = {"Lcom/pokerarity/scanner/ui/result/HistoryActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "adapter", "Lcom/pokerarity/scanner/ui/main/ScanHistoryAdapter;", "binding", "Lcom/pokerarity/scanner/databinding/ActivityHistoryBinding;", "repository", "Lcom/pokerarity/scanner/data/repository/PokemonRepository;", "loadRareScans", "", "minScore", "", "loadScans", "loadShinyScans", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "setupFilters", "setupRecyclerView", "setupToolbar", "app_debug"})
public final class HistoryActivity extends androidx.appcompat.app.AppCompatActivity {
    private com.pokerarity.scanner.databinding.ActivityHistoryBinding binding;
    private com.pokerarity.scanner.ui.main.ScanHistoryAdapter adapter;
    private com.pokerarity.scanner.data.repository.PokemonRepository repository;
    
    public HistoryActivity() {
        super();
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void setupToolbar() {
    }
    
    private final void setupRecyclerView() {
    }
    
    private final void setupFilters() {
    }
    
    private final void loadScans() {
    }
    
    private final void loadRareScans(int minScore) {
    }
    
    private final void loadShinyScans() {
    }
}