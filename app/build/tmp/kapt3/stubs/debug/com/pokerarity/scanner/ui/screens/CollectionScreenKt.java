package com.pokerarity.scanner.ui.screens;

import androidx.compose.animation.core.RepeatMode;
import androidx.compose.foundation.layout.Arrangement;
import androidx.compose.material.icons.Icons;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.graphics.Brush;
import androidx.compose.ui.text.style.TextAlign;
import androidx.compose.ui.text.font.FontWeight;
import com.pokerarity.scanner.data.model.Pokemon;
import com.pokerarity.scanner.data.model.Rarity;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000>\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\u001a4\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\r2\u0006\u0010\u000f\u001a\u00020\u00012\b\b\u0002\u0010\u0010\u001a\u00020\u0011H\u0003\u00f8\u0001\u0000\u00a2\u0006\u0004\b\u0012\u0010\u0013\u001a@\u0010\u0014\u001a\u00020\u000b2\f\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\u00170\u00162\u0006\u0010\u0018\u001a\u00020\u00192\u0012\u0010\u001a\u001a\u000e\u0012\u0004\u0012\u00020\u0017\u0012\u0004\u0012\u00020\u000b0\u001b2\f\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u000b0\u001dH\u0007\u001a\u001a\u0010\u001e\u001a\u00020\u000b2\u0006\u0010\u0018\u001a\u00020\u00192\b\b\u0002\u0010\u0010\u001a\u00020\u0011H\u0003\u001a&\u0010\u001f\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010 \u001a\u00020\u00192\f\u0010!\u001a\b\u0012\u0004\u0012\u00020\u000b0\u001dH\u0003\u001a\u001e\u0010\"\u001a\u00020\u000b2\u0006\u0010#\u001a\u00020\u00192\f\u0010!\u001a\b\u0012\u0004\u0012\u00020\u000b0\u001dH\u0003\"\u0010\u0010\u0000\u001a\u00020\u0001X\u0082\u0004\u00a2\u0006\u0004\n\u0002\u0010\u0002\"\u0010\u0010\u0003\u001a\u00020\u0001X\u0082\u0004\u00a2\u0006\u0004\n\u0002\u0010\u0002\"\u0010\u0010\u0004\u001a\u00020\u0001X\u0082\u0004\u00a2\u0006\u0004\n\u0002\u0010\u0002\"\u0010\u0010\u0005\u001a\u00020\u0001X\u0082\u0004\u00a2\u0006\u0004\n\u0002\u0010\u0002\"\u0010\u0010\u0006\u001a\u00020\u0001X\u0082\u0004\u00a2\u0006\u0004\n\u0002\u0010\u0002\"\u0010\u0010\u0007\u001a\u00020\u0001X\u0082\u0004\u00a2\u0006\u0004\n\u0002\u0010\u0002\"\u0010\u0010\b\u001a\u00020\u0001X\u0082\u0004\u00a2\u0006\u0004\n\u0002\u0010\u0002\"\u0010\u0010\t\u001a\u00020\u0001X\u0082\u0004\u00a2\u0006\u0004\n\u0002\u0010\u0002\u0082\u0002\u0007\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006$"}, d2 = {"AccentRed", "Landroidx/compose/ui/graphics/Color;", "J", "BG", "CardHigh", "CardMid", "Divider", "RedLight", "TextMuted", "TextOnDark", "BentoStatMini", "", "label", "", "value", "valueColor", "modifier", "Landroidx/compose/ui/Modifier;", "BentoStatMini-9LQNqLg", "(Ljava/lang/String;Ljava/lang/String;JLandroidx/compose/ui/Modifier;)V", "CollectionScreen", "pokemonList", "", "Lcom/pokerarity/scanner/data/model/Pokemon;", "isOverlayRunning", "", "onPokemonClick", "Lkotlin/Function1;", "onScanClick", "Lkotlin/Function0;", "StitchEmptyState", "StitchFilterChip", "selected", "onClick", "StitchScanButton", "isActive", "app_debug"})
public final class CollectionScreenKt {
    private static final long BG = 0L;
    private static final long CardHigh = 0L;
    private static final long CardMid = 0L;
    private static final long AccentRed = 0L;
    private static final long RedLight = 0L;
    private static final long TextMuted = 0L;
    private static final long TextOnDark = 0L;
    private static final long Divider = 0L;
    
    @androidx.compose.runtime.Composable()
    public static final void CollectionScreen(@org.jetbrains.annotations.NotNull()
    java.util.List<com.pokerarity.scanner.data.model.Pokemon> pokemonList, boolean isOverlayRunning, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super com.pokerarity.scanner.data.model.Pokemon, kotlin.Unit> onPokemonClick, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onScanClick) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void StitchScanButton(boolean isActive, kotlin.jvm.functions.Function0<kotlin.Unit> onClick) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void StitchFilterChip(java.lang.String label, boolean selected, kotlin.jvm.functions.Function0<kotlin.Unit> onClick) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void StitchEmptyState(boolean isOverlayRunning, androidx.compose.ui.Modifier modifier) {
    }
}