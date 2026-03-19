package com.pokerarity.scanner.ui.screens;

import androidx.compose.foundation.layout.Arrangement;
import androidx.compose.material.icons.Icons;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.graphics.Brush;
import androidx.compose.ui.text.font.FontWeight;
import androidx.compose.ui.text.style.TextAlign;
import com.pokerarity.scanner.data.model.Pokemon;
import com.pokerarity.scanner.data.model.RarityAnalysisItem;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000L\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\u001a>\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\t2\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00010\u000bH\u0003\u001a*\u0010\f\u001a\u00020\u00012\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0012H\u0003\u00f8\u0001\u0000\u00a2\u0006\u0004\b\u0013\u0010\u0014\u001a\u0016\u0010\u0015\u001a\u00020\u00012\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00010\u000bH\u0003\u001a)\u0010\u0016\u001a\u00020\u00012\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00010\u000b2\u0011\u0010\u0017\u001a\r\u0012\u0004\u0012\u00020\u00010\u000b\u00a2\u0006\u0002\b\u0018H\u0003\u001aH\u0010\u0019\u001a\u00020\u00012\u0006\u0010\u001a\u001a\u00020\u001b2\b\b\u0002\u0010\u0004\u001a\u00020\u00052\f\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u00010\u000b2\u000e\b\u0002\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\u00010\u000b2\u000e\b\u0002\u0010\u001e\u001a\b\u0012\u0004\u0012\u00020\u00010\u000bH\u0007\u001a>\u0010\u001f\u001a\u00020\u00012\u0006\u0010\u001a\u001a\u00020\u001b2\f\u0010 \u001a\b\u0012\u0004\u0012\u00020\u00010\u000b2\u000e\b\u0002\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\u00010\u000b2\u000e\b\u0002\u0010\u001e\u001a\b\u0012\u0004\u0012\u00020\u00010\u000bH\u0007\u001a\u0010\u0010!\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u0003H\u0003\u0082\u0002\u0007\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006\""}, d2 = {"ActionButton", "", "text", "", "modifier", "Landroidx/compose/ui/Modifier;", "isPrimary", "", "gradient", "Landroidx/compose/ui/graphics/Brush;", "onClick", "Lkotlin/Function0;", "AnalysisRow", "item", "Lcom/pokerarity/scanner/data/model/RarityAnalysisItem;", "accentColor", "Landroidx/compose/ui/graphics/Color;", "delay", "", "AnalysisRow-bw27NRU", "(Lcom/pokerarity/scanner/data/model/RarityAnalysisItem;JI)V", "BackButton", "NavIconButton", "content", "Landroidx/compose/runtime/Composable;", "ScanResultOverlayCard", "pokemon", "Lcom/pokerarity/scanner/data/model/Pokemon;", "onDismiss", "onShare", "onSave", "ScanResultScreen", "onBack", "TagPill", "app_debug"})
public final class ScanResultScreenKt {
    
    @androidx.compose.runtime.Composable()
    public static final void ScanResultOverlayCard(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.Pokemon pokemon, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onDismiss, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onShare, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onSave) {
    }
    
    @androidx.compose.runtime.Composable()
    public static final void ScanResultScreen(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.model.Pokemon pokemon, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onBack, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onShare, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onSave) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void TagPill(java.lang.String text) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void BackButton(kotlin.jvm.functions.Function0<kotlin.Unit> onClick) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void NavIconButton(kotlin.jvm.functions.Function0<kotlin.Unit> onClick, kotlin.jvm.functions.Function0<kotlin.Unit> content) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void ActionButton(java.lang.String text, androidx.compose.ui.Modifier modifier, boolean isPrimary, androidx.compose.ui.graphics.Brush gradient, kotlin.jvm.functions.Function0<kotlin.Unit> onClick) {
    }
}