package com.pokerarity.scanner.ui.screens;

import androidx.compose.animation.core.RepeatMode;
import androidx.compose.foundation.layout.Arrangement;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.graphics.Brush;
import androidx.compose.ui.text.font.FontWeight;
import com.pokerarity.scanner.data.model.Pokemon;
import com.pokerarity.scanner.data.model.Rarity;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u00006\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0005\u001a@\u0010\u0000\u001a\u00020\u00012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\u0006\u0010\u0005\u001a\u00020\u00062\u0012\u0010\u0007\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00010\b2\f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00010\nH\u0007\u001a\u001a\u0010\u000b\u001a\u00020\u00012\b\b\u0002\u0010\f\u001a\u00020\r2\u0006\u0010\u0005\u001a\u00020\u0006H\u0003\u001a&\u0010\u000e\u001a\u00020\u00012\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u00062\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00010\nH\u0003\u001a(\u0010\u0013\u001a\u00020\u00012\u0006\u0010\u0014\u001a\u00020\u00062\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00010\n2\b\b\u0002\u0010\f\u001a\u00020\rH\u0003\u00a8\u0006\u0015"}, d2 = {"CollectionScreen", "", "pokemonList", "", "Lcom/pokerarity/scanner/data/model/Pokemon;", "isOverlayRunning", "", "onPokemonClick", "Lkotlin/Function1;", "onScanClick", "Lkotlin/Function0;", "EmptyState", "modifier", "Landroidx/compose/ui/Modifier;", "FilterChip", "label", "", "selected", "onClick", "ScanButton", "isActive", "app_debug"})
public final class CollectionScreenKt {
    
    @androidx.compose.runtime.Composable()
    public static final void CollectionScreen(@org.jetbrains.annotations.NotNull()
    java.util.List<com.pokerarity.scanner.data.model.Pokemon> pokemonList, boolean isOverlayRunning, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super com.pokerarity.scanner.data.model.Pokemon, kotlin.Unit> onPokemonClick, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onScanClick) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void ScanButton(boolean isActive, kotlin.jvm.functions.Function0<kotlin.Unit> onClick, androidx.compose.ui.Modifier modifier) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void FilterChip(java.lang.String label, boolean selected, kotlin.jvm.functions.Function0<kotlin.Unit> onClick) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void EmptyState(androidx.compose.ui.Modifier modifier, boolean isOverlayRunning) {
    }
}