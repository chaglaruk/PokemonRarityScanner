package com.pokerarity.scanner.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.Rarity
import com.pokerarity.scanner.ui.components.noRippleClickable
import com.pokerarity.scanner.ui.components.PokemonListCard
import com.pokerarity.scanner.ui.components.SectionLabel
import com.pokerarity.scanner.ui.theme.OutfitFamily
import com.pokerarity.scanner.ui.theme.TextHint
import com.pokerarity.scanner.ui.theme.TextPrimary

// ── Stitch brand colours ──────────────────────────────────────────────────────
private val BG          = Color(0xFF131313)
private val CardHigh    = Color(0xFF2A2A2A)
private val CardMid     = Color(0xFF1C1B1B)
private val AccentRed   = Color(0xFFE3350D)
private val RedLight    = Color(0xFFFF5632)
private val TextMuted   = Color(0xFFAC8880)
private val TextOnDark  = Color(0xFFE5E2E1)
private val Divider     = Color(0x33FFFFFF)

enum class FilterOption(val label: String) {
    ALL("All"),
    LEGENDARY("Legendary"),
    RARE("Rare"),
    SHINY("Shiny"),
    LUCKY("Lucky"),
}

@Composable
fun CollectionScreen(
    pokemonList: List<Pokemon>,
    isOverlayRunning: Boolean,
    onPokemonClick: (Pokemon) -> Unit,
    onScanClick: () -> Unit,
    onTelemetrySettingsClick: () -> Unit,
) {
    var activeFilter by remember { mutableStateOf(FilterOption.ALL) }

    val filtered = remember(activeFilter, pokemonList) {
        when (activeFilter) {
            FilterOption.ALL       -> pokemonList
            FilterOption.LEGENDARY -> pokemonList.filter { it.rarity == Rarity.LEGENDARY }
            FilterOption.RARE      -> pokemonList.filter { it.rarity == Rarity.RARE || it.rarity == Rarity.LEGENDARY }
            FilterOption.SHINY     -> pokemonList.filter { "SHINY" in it.tags }
            FilterOption.LUCKY     -> pokemonList.filter { "LUCKY" in it.tags }
        }
    }

    val headerAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        headerAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }

    val shinyCount  = pokemonList.count { "SHINY"  in it.tags }
    val rareCount   = pokemonList.count { it.rarity == Rarity.RARE || it.rarity == Rarity.LEGENDARY }
    val commonCount = pokemonList.size - rareCount

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        // ── TopBar ────────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "PokeRarity",
                    color = AccentRed,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = OutfitFamily,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onTelemetrySettingsClick) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Telemetry Settings",
                        tint = TextOnDark
                    )
                }
            }
        }

        // ── Hero stat block ───────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .graphicsLayer { alpha = headerAlpha.value }
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 4.dp)
            ) {
                Text(
                    text = "LIVE FREQUENCY",
                    color = AccentRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OutfitFamily,
                    letterSpacing = 1.5.sp,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = pokemonList.size.toString(),
                        color = TextOnDark,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = OutfitFamily,
                        lineHeight = 52.sp,
                    )
                    Text(
                        text = "  SCANS",
                        color = TextMuted,
                        fontSize = 16.sp,
                        fontFamily = OutfitFamily,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(
                    text = "Active scanning enabled. Sensors optimized for rare signatures.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontFamily = OutfitFamily,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Bento grid ────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .graphicsLayer { alpha = headerAlpha.value }
                    .padding(horizontal = 20.dp)
                    .height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Wide card – Today's Finds
                Column(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardHigh)
                        .padding(16.dp),
                ) {
                    Text("Today's Finds", color = TextOnDark, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = OutfitFamily)
                    Text("Last 24h metrics", color = TextMuted, fontSize = 11.sp, fontFamily = OutfitFamily)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BentoStatMini(label = "COMMON", value = commonCount.toString(), valueColor = TextOnDark, modifier = Modifier.weight(1f))
                        BentoStatMini(label = "RARE", value = rareCount.toString(), valueColor = AccentRed, modifier = Modifier.weight(1f))
                    }
                }
                // Narrow card – Top Rarity
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardMid)
                        .padding(14.dp),
                ) {
                    Text("⭐", fontSize = 22.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Top Rarity", color = TextOnDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = OutfitFamily)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (shinyCount > 0) "✨ $shinyCount" else "—",
                        color = Color(0xFFFFD700),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OutfitFamily,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Central FAB ───────────────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = headerAlpha.value }
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                StitchScanButton(isActive = isOverlayRunning, onClick = onScanClick)
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Filter chips ──────────────────────────────────────────────────────
        item {
            LazyRow(
                modifier = Modifier.graphicsLayer { alpha = headerAlpha.value },
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(FilterOption.entries) { option ->
                    StitchFilterChip(
                        label = option.label,
                        selected = activeFilter == option,
                        onClick = { activeFilter = option },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Section label ─────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(text = if (filtered.isEmpty()) "NO SCANS" else "RECENT SCANS")
                Text(
                    text = "LIVE STREAM",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OutfitFamily,
                    letterSpacing = 1.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── List ──────────────────────────────────────────────────────────────
        if (filtered.isEmpty()) {
            item {
                StitchEmptyState(
                    isOverlayRunning = isOverlayRunning,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        } else {
            itemsIndexed(
                items = filtered,
                key = { _, pokemon -> pokemon.id },
            ) { index, pokemon ->
                PokemonListCard(
                    pokemon = pokemon,
                    onClick = { onPokemonClick(pokemon) },
                    animationDelay = index * 70,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun BentoStatMini(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x1AFFFFFF))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = OutfitFamily)
        Text(value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = OutfitFamily)
    }
}

@Composable
private fun StitchScanButton(isActive: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "scanPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    val gradient = Brush.radialGradient(
        colors = if (isActive) listOf(RedLight, AccentRed) else listOf(AccentRed, Color(0xFFB52A0A)),
    )

    Box(contentAlignment = Alignment.Center) {
        // Glow ring
        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 0.3f }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(AccentRed, Color(0x00E3350D)))
                )
        )
        Column(
            modifier = Modifier
                .size(170.dp)
                .clip(CircleShape)
                .background(gradient)
                .noRippleClickable(onClick = onClick),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (isActive) Icons.Rounded.StopCircle else Icons.Rounded.PhotoCamera,
                contentDescription = if (isActive) "Stop scan" else "Start scan",
                tint = Color.White,
                modifier = Modifier
                    .size(40.dp)
                    .offset(y = (-6).dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (isActive) "STOP" else "SCAN NOW",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OutfitFamily,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StitchFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg     = if (selected) Color(0x1AFFFFFF) else Color.Transparent
    val border = if (selected) Color(0x4DFFFFFF) else Color(0x1AFFFFFF)
    val tc     = if (selected) TextPrimary else TextHint

    Text(
        text = label,
        color = tc,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = OutfitFamily,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

@Composable
private fun StitchEmptyState(isOverlayRunning: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardMid)
            .border(1.dp, Divider, RoundedCornerShape(20.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No scans yet", color = TextOnDark, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = OutfitFamily)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isOverlayRunning) "Use the floating scan button in Pokémon GO." else "Press Scan Now to start the overlay.",
            color = TextMuted,
            fontSize = 13.sp,
            fontFamily = OutfitFamily,
        )
    }
}
