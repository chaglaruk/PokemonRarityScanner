package com.pokerarity.scanner.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.ui.components.*
import com.pokerarity.scanner.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ScanResultScreen(
    pokemon: Pokemon,
    onBack: () -> Unit,
) {
    val tc = pokemon.typeColors
    val scope = rememberCoroutineScope()

    // ── Animation states ──────────────────────────────────────
    // 1. Stripe slides down from top
    val stripeAlpha  = remember { Animatable(0f) }
    val stripeSlide  = remember { Animatable(-20f) }
    // 2. Nav fades up
    val navAlpha     = remember { Animatable(0f) }
    // 3. Hero content fades up
    val heroAlpha    = remember { Animatable(0f) }
    val heroSlide    = remember { Animatable(12f) }
    // 4. Tags stagger — handled per-tag with delay
    val tagsAlpha    = remember { Animatable(0f) }
    // 5. Sheet slides up
    val sheetSlide   = remember { Animatable(40f) }
    val sheetAlpha   = remember { Animatable(0f) }
    // 6. Score counter (driven by integer animation)
    val scoreAnim    = remember { Animatable(0f) }
    val displayScore by remember { derivedStateOf { scoreAnim.value.toInt() } }

    LaunchedEffect(Unit) {
        // Stripe
        launch {
            stripeAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
        stripeSlide.animateTo(0f, tween(700, easing = FastOutSlowInEasing))

        // Nav (delay 300)
        launch {
            kotlinx.coroutines.delay(300)
            navAlpha.animateTo(1f, tween(500))
        }

        // Hero (delay 450)
        launch {
            kotlinx.coroutines.delay(450)
            launch { heroAlpha.animateTo(1f, tween(500)) }
            heroSlide.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        }

        // Tags (delay 600)
        launch {
            kotlinx.coroutines.delay(600)
            tagsAlpha.animateTo(1f, tween(400))
        }

        // Sheet (delay 550)
        launch {
            kotlinx.coroutines.delay(550)
            launch { sheetAlpha.animateTo(1f, tween(600)) }
            sheetSlide.animateTo(
                targetValue    = 0f,
                animationSpec  = tween(600, easing = FastOutSlowInEasing),
            )
        }

        // Score counter (delay 700)
        kotlinx.coroutines.delay(700)
        scoreAnim.animateTo(
            targetValue    = pokemon.rarityScore.toFloat(),
            animationSpec  = tween(900, easing = FastOutSlowInEasing),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
    ) {

        // ── Diagonal colour stripe ────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .graphicsLayer {
                    alpha        = stripeAlpha.value
                    translationY = stripeSlide.value
                }
        ) {
            // Gradient background
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors  = listOf(tc.primary, tc.secondary, tc.tertiary),
                            start   = Offset(0f, 0f),
                            end     = Offset(Float.MAX_VALUE, Float.MAX_VALUE),
                        )
                    )
            )
            // Dark overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.48f))
            )
        }

        // ── Nav bar ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = navAlpha.value }
                // Overlap the stripe
                .offset(y = (-280).dp)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavIconButton {
                    // Share icon
                    ShareIcon()
                }
                NavIconButton {
                    // Star icon
                    StarIcon()
                }
            }
        }

        // ── Hero: name, tags, score ───────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-280).dp)
                .graphicsLayer {
                    alpha        = heroAlpha.value
                    translationY = heroSlide.value
                }
                .padding(horizontal = 22.dp)
        ) {
            Text(
                text       = "SCAN RESULT",
                color      = Color.White.copy(alpha = 0.45f),
                fontSize   = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = OutfitFamily,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text       = pokemon.name,
                color      = Color.White,
                fontSize   = 46.sp,
                fontWeight = FontWeight.Black,
                fontFamily = OutfitFamily,
                letterSpacing = (-2).sp,
                lineHeight = 44.sp,
            )
            Spacer(Modifier.height(12.dp))

            // Staggered tag pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.graphicsLayer { alpha = tagsAlpha.value },
            ) {
                if (pokemon.tags.contains("LEGENDARY")) TagPill("LEGENDARY", TagStyle.Legend)
                if (pokemon.tags.contains("SHINY"))     TagPill("SHINY",     TagStyle.Shiny)
                if (pokemon.tags.contains("HUNDO"))     TagPill("HUNDO",     TagStyle.Hundo)
                if (pokemon.tags.contains("LUCKY"))     TagPill("LUCKY",     TagStyle.Lucky)
            }
            Spacer(Modifier.height(18.dp))

            // Score counter
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text       = displayScore.toString(),
                    color      = Color.White,
                    fontSize   = 90.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = OutfitFamily,
                    letterSpacing = (-5).sp,
                    lineHeight = 88.sp,
                )
                Text(
                    text       = "/100",
                    color      = Color.White.copy(alpha = 0.25f),
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = OutfitFamily,
                    letterSpacing = (-1).sp,
                    modifier   = Modifier.padding(bottom = 14.dp, start = 8.dp),
                )
            }
        }

        // ── Bottom sheet ──────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-280).dp)
                .graphicsLayer {
                    alpha        = sheetAlpha.value
                    translationY = sheetSlide.value
                }
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Black)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                )
        ) {
            // Handle
            Box(
                Modifier
                    .padding(top = 14.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Spacer(Modifier.height(24.dp))

            Column(modifier = Modifier.padding(horizontal = 22.dp)) {

                // Stats
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatCard(
                        value    = pokemon.cp.toString(),
                        label    = "CP",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        value    = pokemon.hp.toString(),
                        label    = "HP",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        value    = pokemon.caughtDate.take(8),
                        label    = "CAUGHT",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))

                // IV card
                IvCard(iv = pokemon.iv)
                Spacer(Modifier.height(14.dp))

                // Rarity breakdown
                SectionLabel("RARITY BREAKDOWN")
                Spacer(Modifier.height(12.dp))

                Column {
                    pokemon.analysis.forEachIndexed { index, item ->
                        AnalysisRow(
                            item       = item,
                            accentColor = tc.primary,
                            delay      = index * 80,
                        )
                        if (index < pokemon.analysis.lastIndex) {
                            PokeHorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionButton(
                        text     = "Back",
                        modifier = Modifier.weight(1f),
                        onClick  = onBack,
                    )
                    ActionButton(
                        text       = "Save to Collection",
                        modifier   = Modifier.weight(2f),
                        isPrimary  = true,
                        gradient   = Brush.linearGradient(listOf(tc.primary, tc.secondary)),
                        onClick    = { },
                    )
                    ActionButton(
                        text     = "Share",
                        modifier = Modifier.weight(1f),
                        onClick  = { },
                    )
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

// ── Analysis row with slide-in animation ─────────────────────
@Composable
private fun AnalysisRow(
    item: com.pokerarity.scanner.data.model.RarityAnalysisItem,
    accentColor: Color,
    delay: Int,
) {
    val alpha = remember { Animatable(0f) }
    val slide = remember { Animatable(-12f) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((800 + delay).toLong())
        kotlinx.coroutines.launch { alpha.animateTo(1f, tween(350)) }
        slide.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha.value; translationX = slide.value }
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isPositive)
                            Brush.linearGradient(listOf(accentColor, accentColor.copy(alpha = 0.7f)))
                        else
                            Brush.linearGradient(listOf(Color(0xFF222222), Color(0xFF222222)))
                    )
            )
            Text(
                text       = item.label,
                color      = if (item.isPositive) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OutfitFamily,
            )
        }
        Text(
            text       = item.points,
            color      = if (item.isPositive) accentColor else Color(0xFF1A1A1A),
            fontSize   = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFamily,
        )
    }
}

// ── Small helpers ─────────────────────────────────────────────
private enum class TagStyle { Legend, Shiny, Hundo, Lucky }

@Composable
private fun TagPill(text: String, style: TagStyle) {
    val (bg, border, fg) = when (style) {
        TagStyle.Legend -> Triple(Color(0x26FFFFFF), Color(0x4DFFFFFF), Color.White)
        TagStyle.Shiny  -> Triple(Color(0x2EFFD700), Color(0x73FFD700), Color(0xFFFFD700))
        TagStyle.Hundo  -> Triple(Color(0x2600FF8C), Color(0x5900FF8C), Color(0xFF00FF8C))
        TagStyle.Lucky  -> Triple(Color(0x26FF7800), Color(0x59FF7800), Color(0xFFFF7800))
    }
    PokeTagPill(text, bg, border, fg)
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.25f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("←", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontFamily = OutfitFamily)
        Text("Back", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OutfitFamily)
    }
}

@Composable
private fun NavIconButton(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.25f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable { },
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}

@Composable
private fun ShareIcon() {
    // Simple share icon drawn with Canvas — no vector asset needed
    Box(
        Modifier.size(15.dp).drawBehind {
            val c = Color(0x80FFFFFF)
            // draw 3 circles + lines as share icon
        }
    )
    // In production: use Icons.Default.Share from material-icons-extended
    Text("↗", color = Color.White.copy(0.5f), fontSize = 14.sp)
}

@Composable
private fun StarIcon() {
    Text("★", color = Color(0xFFFFD700), fontSize = 14.sp)
}

@Composable
private fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    gradient: Brush? = null,
    onClick: () -> Unit,
) {
    val bgModifier = if (isPrimary && gradient != null) {
        Modifier.background(gradient)
    } else {
        Modifier
            .background(Surface1)
            .border(1.dp, Border1, RoundedCornerShape(16.dp))
    }

    Text(
        text       = text,
        color      = if (isPrimary) Color.White else Color.White.copy(alpha = 0.3f),
        fontSize   = if (isPrimary) 14.sp else 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = OutfitFamily,
        textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
        modifier   = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(bgModifier)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
    )
}
