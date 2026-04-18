package com.pokerarity.scanner.ui.components;

import androidx.compose.runtime.Composable;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.graphics.StrokeCap;
import androidx.compose.ui.graphics.drawscope.Stroke;
import androidx.compose.ui.text.font.FontWeight;
import androidx.compose.ui.unit.Dp;
import java.util.Locale;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u00004\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u000e\n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0002\b\u0002\u001a!\u0010\u0000\u001a\u00020\u00012\b\u0010\u0002\u001a\u0004\u0018\u00010\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0005H\u0007\u00a2\u0006\u0002\u0010\u0006\u001a<\u0010\u0007\u001a\u00020\u00012\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u000b2\u0006\u0010\r\u001a\u00020\u000b2\b\b\u0002\u0010\u0004\u001a\u00020\u0005H\u0007\u00f8\u0001\u0000\u00a2\u0006\u0004\b\u000e\u0010\u000f\u001a\b\u0010\u0010\u001a\u00020\u0001H\u0007\u001a<\u0010\u0011\u001a\u00020\u00012\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u000b2\u0006\u0010\r\u001a\u00020\u000b2\b\b\u0002\u0010\u0004\u001a\u00020\u0005H\u0007\u00f8\u0001\u0000\u00a2\u0006\u0004\b\u0012\u0010\u000f\u001a*\u0010\u0013\u001a\u00020\u00012\u0006\u0010\u0014\u001a\u00020\t2\u0006\u0010\u0015\u001a\u00020\u00032\u0006\u0010\u0016\u001a\u00020\t2\b\b\u0002\u0010\u0004\u001a\u00020\u0005H\u0007\u001aJ\u0010\u0017\u001a\u00020\u00012\u0006\u0010\u0015\u001a\u00020\u00032\u0006\u0010\u0018\u001a\u00020\u000b2\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0019\u001a\u00020\u001a2\b\b\u0002\u0010\u001b\u001a\u00020\u001a2\b\b\u0002\u0010\u001c\u001a\u00020\u0003H\u0007\u00f8\u0001\u0000\u00a2\u0006\u0004\b\u001d\u0010\u001e\u001a\u001a\u0010\u001f\u001a\u00020\u00012\u0006\u0010\b\u001a\u00020\t2\b\b\u0002\u0010\u0004\u001a\u00020\u0005H\u0007\u001a6\u0010 \u001a\u00020\u00012\u0006\u0010!\u001a\u00020\t2\u0006\u0010\u0014\u001a\u00020\t2\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\"\u001a\u00020\u000bH\u0007\u00f8\u0001\u0000\u00a2\u0006\u0004\b#\u0010$\u001a\u000e\u0010%\u001a\u00020&2\u0006\u0010\'\u001a\u00020\t\u0082\u0002\u0007\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006("}, d2 = {"IvCard", "", "iv", "", "modifier", "Landroidx/compose/ui/Modifier;", "(Ljava/lang/Integer;Landroidx/compose/ui/Modifier;)V", "PokeBadge", "text", "", "bgColor", "Landroidx/compose/ui/graphics/Color;", "borderColor", "textColor", "PokeBadge-f1JAnFk", "(Ljava/lang/String;JJJLandroidx/compose/ui/Modifier;)V", "PokeHorizontalDivider", "PokeTagPill", "PokeTagPill-f1JAnFk", "RarityTierCard", "label", "score", "tierCode", "ScoreRing", "color", "size", "Landroidx/compose/ui/unit/Dp;", "strokeWidth", "animationDelay", "ScoreRing-knJZjKc", "(IJLandroidx/compose/ui/Modifier;FFI)V", "SectionLabel", "StatCard", "value", "valueColor", "StatCard-g2O1Hgs", "(Ljava/lang/String;Ljava/lang/String;Landroidx/compose/ui/Modifier;J)V", "tierVisuals", "Lcom/pokerarity/scanner/ui/components/TierVisuals;", "code", "PokeRarityScanner-v1.8.2_debug"})
public final class ComponentsKt {
    
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.ui.components.TierVisuals tierVisuals(@org.jetbrains.annotations.NotNull()
    java.lang.String code) {
        return null;
    }
    
    @androidx.compose.runtime.Composable()
    public static final void RarityTierCard(@org.jetbrains.annotations.NotNull()
    java.lang.String label, int score, @org.jetbrains.annotations.NotNull()
    java.lang.String tierCode, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier) {
    }
    
    @androidx.compose.runtime.Composable()
    public static final void IvCard(@org.jetbrains.annotations.Nullable()
    java.lang.Integer iv, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier) {
    }
    
    @androidx.compose.runtime.Composable()
    public static final void SectionLabel(@org.jetbrains.annotations.NotNull()
    java.lang.String text, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier) {
    }
    
    @androidx.compose.runtime.Composable()
    public static final void PokeHorizontalDivider() {
    }
}