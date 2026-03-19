package com.pokerarity.scanner.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.ui.theme.*
import kotlin.math.PI

// ── Score Ring ────────────────────────────────────────────────
// Animates arc from 0 → score on first composition
@Composable
fun ScoreRing(
    score: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    strokeWidth: Dp = 4.dp,
    animationDelay: Int = 0,
) {
    val trackColor = color.copy(alpha = 0.12f)
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(score) {
        animatedProgress.animateTo(
            targetValue = score / 100f,
            animationSpec = tween(
                durationMillis = 900,
                delayMillis    = animationDelay,
                easing         = FastOutSlowInEasing,
            )
        )
    }

    val progress by animatedProgress.asState()

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val sw     = strokeWidth.toPx()
                val radius = (this.size.minDimension / 2f) - sw
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val sweep  = 360f * progress
                val arcSize = Size(radius * 2f, radius * 2f)
                val topLeft = Offset(center.x - radius, center.y - radius)

                // Track
                drawArc(
                    color       = trackColor,
                    startAngle  = -90f,
                    sweepAngle  = 360f,
                    useCenter   = false,
                    topLeft     = topLeft,
                    size        = arcSize,
                    style       = Stroke(width = sw, cap = StrokeCap.Round),
                )
                // Fill
                drawArc(
                    color       = color,
                    startAngle  = -90f,
                    sweepAngle  = sweep,
                    useCenter   = false,
                    topLeft     = topLeft,
                    size        = arcSize,
                    style       = Stroke(width = sw, cap = StrokeCap.Round),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = score.toString(),
            color = color,
            fontSize   = (size.value * 0.30f).sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = OutfitFamily,
        )
    }
}

// ── Tag / Badge pill ─────────────────────────────────────────
@Composable
fun PokeBadge(
    text: String,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text     = text,
        color    = textColor,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = OutfitFamily,
        letterSpacing = 1.sp,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ── Pill badge (rounded) ──────────────────────────────────────
@Composable
fun PokeTagPill(
    text: String,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text     = text,
        color    = textColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = OutfitFamily,
        letterSpacing = 1.sp,
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, borderColor, CircleShape)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    )
}

// ── Stat card (CP / HP / Date tiles) ─────────────────────────
@Composable
fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .border(1.dp, Border1, RoundedCornerShape(18.dp))
            .padding(vertical = 14.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = value,
                color      = valueColor,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OutfitFamily,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text       = label,
                color      = TextHint,
                fontSize   = 9.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = OutfitFamily,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

// ── IV Card ───────────────────────────────────────────────────
@Composable
fun IvCard(
    iv: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .border(1.dp, Border1, RoundedCornerShape(18.dp))
            // Left accent bar via drawBehind
            .drawBehind {
                drawRect(
                    color   = Color(0xFF00FF8C),
                    topLeft = Offset.Zero,
                    size    = Size(3.dp.toPx(), size.height),
                )
            }
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text       = "IV Perfection",
            color      = TextMuted,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = OutfitFamily,
        )
        Text(
            text       = "$iv%",
            color      = AccentGreen,
            fontSize   = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = OutfitFamily,
            letterSpacing = (-1).sp,
        )
    }
}

// ── Section label ─────────────────────────────────────────────
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text       = text,
        color      = Color(0x2EFFFFFF),
        fontSize   = 10.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = OutfitFamily,
        letterSpacing = 3.sp,
        modifier   = modifier,
    )
}

// ── Divider ───────────────────────────────────────────────────
@Composable
fun PokeHorizontalDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFF0F0F0F))
    )
}
