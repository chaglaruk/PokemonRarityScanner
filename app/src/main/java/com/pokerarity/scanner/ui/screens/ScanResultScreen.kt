package com.pokerarity.scanner.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.RarityAnalysisItem
import com.pokerarity.scanner.ui.components.IvCard
import com.pokerarity.scanner.ui.components.PokeHorizontalDivider
import com.pokerarity.scanner.ui.components.PokeTagPill
import com.pokerarity.scanner.ui.components.ScoreRing
import com.pokerarity.scanner.ui.components.SectionLabel
import com.pokerarity.scanner.ui.components.StatCard
import com.pokerarity.scanner.ui.theme.Black
import com.pokerarity.scanner.ui.theme.Border1
import com.pokerarity.scanner.ui.theme.OutfitFamily
import com.pokerarity.scanner.ui.theme.Surface1
import com.pokerarity.scanner.ui.theme.TextHint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ScanResultOverlayCard(
    pokemon: Pokemon,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onShare: () -> Unit = {},
    onSave: () -> Unit = {},
) {
    val typeColors = pokemon.typeColors
    val heroAlpha = remember { Animatable(0f) }
    val heroSlide = remember { Animatable(18f) }
    val contentAlpha = remember { Animatable(0f) }
    val contentSlide = remember { Animatable(26f) }
    val scoreAnim = remember { Animatable(0f) }
    val displayScore by remember { derivedStateOf { scoreAnim.value.toInt() } }

    LaunchedEffect(pokemon.name, pokemon.rarityScore, pokemon.tags) {
        heroAlpha.snapTo(0f)
        heroSlide.snapTo(18f)
        contentAlpha.snapTo(0f)
        contentSlide.snapTo(26f)
        scoreAnim.snapTo(0f)

        launch {
            heroAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            heroSlide.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        }
        launch {
            delay(120)
            contentAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
            contentSlide.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        }
        delay(700)
        scoreAnim.animateTo(
            targetValue = pokemon.rarityScore.toFloat(),
            animationSpec = tween(900, easing = FastOutSlowInEasing),
        )
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Surface1)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(30.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(typeColors.primary, typeColors.secondary, typeColors.tertiary),
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.42f))
            )
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SCAN RESULT",
                        color = Color.White.copy(alpha = 0.52f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OutfitFamily,
                        letterSpacing = 3.sp,
                    )
                    NavIconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = heroAlpha.value
                            translationY = heroSlide.value
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ScoreRing(
                        score = displayScore,
                        color = Color.White,
                        size = 86.dp,
                        strokeWidth = 7.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pokemon.name,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = OutfitFamily,
                            letterSpacing = (-1.5).sp,
                            lineHeight = 32.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (pokemon.tags.isEmpty()) {
                                TagPill(text = "COMMON")
                            } else {
                                pokemon.tags.forEach { tag -> TagPill(text = tag) }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = contentAlpha.value
                    translationY = contentSlide.value
                }
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    value = pokemon.cp.toString(),
                    label = "CP",
                    valueColor = typeColors.primary,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = pokemon.hp?.toString() ?: "--",
                    label = "HP",
                    valueColor = typeColors.primary,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = pokemon.caughtDate.take(8),
                    label = "CAUGHT",
                    valueColor = typeColors.primary,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))

            IvCard(iv = pokemon.iv)
            Spacer(Modifier.height(18.dp))

            SectionLabel("RARITY BREAKDOWN")
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Black.copy(alpha = 0.35f))
                    .border(1.dp, Border1, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                pokemon.analysis.forEachIndexed { index, item ->
                    AnalysisRow(
                        item = item,
                        accentColor = typeColors.primary,
                        delay = index * 70,
                    )
                    if (index < pokemon.analysis.lastIndex) {
                        PokeHorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionButton(
                    text = "Close",
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                ActionButton(
                    text = "Save",
                    modifier = Modifier.weight(1f),
                    isPrimary = true,
                    gradient = Brush.linearGradient(listOf(typeColors.primary, typeColors.secondary)),
                    onClick = onSave,
                )
                ActionButton(
                    text = "Share",
                    modifier = Modifier.weight(1f),
                    onClick = onShare,
                )
            }
        }
    }
}

@Composable
fun ScanResultScreen(
    pokemon: Pokemon,
    onBack: () -> Unit,
    onShare: () -> Unit = {},
    onSave: () -> Unit = {},
) {
    val typeColors = pokemon.typeColors
    val overlapPx = with(LocalDensity.current) { 280.dp.toPx() }

    val stripeAlpha = remember { Animatable(0f) }
    val stripeSlide = remember { Animatable(-20f) }
    val navAlpha = remember { Animatable(0f) }
    val heroAlpha = remember { Animatable(0f) }
    val heroSlide = remember { Animatable(12f) }
    val tagsAlpha = remember { Animatable(0f) }
    val sheetSlide = remember { Animatable(40f) }
    val sheetAlpha = remember { Animatable(0f) }
    val scoreAnim = remember { Animatable(0f) }
    val displayScore by remember { derivedStateOf { scoreAnim.value.toInt() } }

    LaunchedEffect(pokemon.id, pokemon.rarityScore) {
        stripeAlpha.snapTo(0f)
        stripeSlide.snapTo(-20f)
        navAlpha.snapTo(0f)
        heroAlpha.snapTo(0f)
        heroSlide.snapTo(12f)
        tagsAlpha.snapTo(0f)
        sheetSlide.snapTo(40f)
        sheetAlpha.snapTo(0f)
        scoreAnim.snapTo(0f)

        launch {
            stripeAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
        stripeSlide.animateTo(0f, tween(700, easing = FastOutSlowInEasing))

        launch {
            delay(300)
            navAlpha.animateTo(1f, tween(500))
        }

        launch {
            delay(450)
            launch { heroAlpha.animateTo(1f, tween(500)) }
            heroSlide.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        }

        launch {
            delay(600)
            tagsAlpha.animateTo(1f, tween(400))
        }

        launch {
            delay(550)
            launch { sheetAlpha.animateTo(1f, tween(600)) }
            sheetSlide.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
        }

        delay(700)
        scoreAnim.animateTo(
            targetValue = pokemon.rarityScore.toFloat(),
            animationSpec = tween(900, easing = FastOutSlowInEasing),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .graphicsLayer {
                    alpha = stripeAlpha.value
                    translationY = stripeSlide.value
                }
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(typeColors.primary, typeColors.secondary, typeColors.tertiary),
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.48f))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = navAlpha.value
                    translationY = -overlapPx
                }
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavIconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Rounded.IosShare,
                        contentDescription = "Share",
                        tint = Color.White.copy(alpha = 0.7f),
                    )
                }
                NavIconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Rounded.StarBorder,
                        contentDescription = "Favorite",
                        tint = Color(0xFFFFD700),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = heroAlpha.value
                    translationY = -overlapPx + heroSlide.value
                }
                .padding(horizontal = 22.dp)
        ) {
            Text(
                text = "SCAN RESULT",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = OutfitFamily,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = pokemon.name,
                color = Color.White,
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                fontFamily = OutfitFamily,
                letterSpacing = (-2).sp,
                lineHeight = 44.sp,
            )
            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.graphicsLayer { alpha = tagsAlpha.value },
            ) {
                pokemon.tags.forEach { tag ->
                    TagPill(text = tag)
                }
            }
            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = displayScore.toString(),
                    color = Color.White,
                    fontSize = 90.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = OutfitFamily,
                    letterSpacing = (-5).sp,
                    lineHeight = 88.sp,
                )
                Text(
                    text = "/100",
                    color = Color.White.copy(alpha = 0.25f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = OutfitFamily,
                    letterSpacing = (-1).sp,
                    modifier = Modifier.padding(bottom = 14.dp, start = 8.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = sheetAlpha.value
                    translationY = -overlapPx + sheetSlide.value
                }
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Black)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                )
        ) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatCard(
                        value = pokemon.cp.toString(),
                        label = "CP",
                        valueColor = typeColors.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        value = pokemon.hp?.toString() ?: "--",
                        label = "HP",
                        valueColor = typeColors.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        value = pokemon.caughtDate.take(8),
                        label = "CAUGHT",
                        valueColor = typeColors.primary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))

                IvCard(iv = pokemon.iv)
                Spacer(Modifier.height(14.dp))

                SectionLabel("RARITY BREAKDOWN")
                Spacer(Modifier.height(12.dp))

                Column {
                    pokemon.analysis.forEachIndexed { index, item ->
                        AnalysisRow(
                            item = item,
                            accentColor = typeColors.primary,
                            delay = index * 80,
                        )
                        if (index < pokemon.analysis.lastIndex) {
                            PokeHorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionButton(
                        text = "Back",
                        modifier = Modifier.weight(1f),
                        onClick = onBack,
                    )
                    ActionButton(
                        text = "Save to Collection",
                        modifier = Modifier.weight(2f),
                        isPrimary = true,
                        gradient = Brush.linearGradient(listOf(typeColors.primary, typeColors.secondary)),
                        onClick = onSave,
                    )
                    ActionButton(
                        text = "Share",
                        modifier = Modifier.weight(1f),
                        onClick = onShare,
                    )
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun AnalysisRow(
    item: RarityAnalysisItem,
    accentColor: Color,
    delay: Int,
) {
    val alpha = remember { Animatable(0f) }
    val slideX = remember { Animatable(-12f) }

    LaunchedEffect(Unit) {
        delay((800 + delay).toLong())
        launch { alpha.animateTo(1f, tween(350)) }
        slideX.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha.value
                translationX = slideX.value
            }
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (item.isPositive) accentColor else Color(0xFF222222))
            )
            Text(
                text = item.label,
                color = if (item.isPositive) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OutfitFamily,
            )
        }
        Text(
            text = item.points,
            color = if (item.isPositive) accentColor else Color(0xFF1A1A1A),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFamily,
        )
    }
}

@Composable
private fun TagPill(text: String) {
    val colors = when (text) {
        "LEGENDARY" -> Triple(Color(0x26FFFFFF), Color(0x4DFFFFFF), Color.White)
        "SHINY" -> Triple(Color(0x2EFFD700), Color(0x73FFD700), Color(0xFFFFD700))
        "LUCKY" -> Triple(Color(0x26FF7800), Color(0x59FF7800), Color(0xFFFF7800))
        "COSTUME" -> Triple(Color(0x2660A5FA), Color(0x5960A5FA), Color(0xFF60A5FA))
        "SHADOW" -> Triple(Color(0x26333333), Color(0x59555555), Color(0xFFAAAAAA))
        else -> Triple(Color(0x1AFFFFFF), Color(0x33FFFFFF), Color.White)
    }
    PokeTagPill(
        text = text,
        bgColor = colors.first,
        borderColor = colors.second,
        textColor = colors.third,
    )
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
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Back",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = OutfitFamily,
        )
    }
}

@Composable
private fun NavIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.25f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    gradient: Brush? = null,
    onClick: () -> Unit,
) {
    val textColor = if (isPrimary) Color.White else Color.White.copy(alpha = 0.65f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isPrimary && gradient != null) {
                    Modifier.background(gradient)
                } else {
                    Modifier
                        .background(Surface1)
                        .border(1.dp, Border1, RoundedCornerShape(16.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = if (isPrimary) 14.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFamily,
            textAlign = TextAlign.Center,
        )
    }
}
