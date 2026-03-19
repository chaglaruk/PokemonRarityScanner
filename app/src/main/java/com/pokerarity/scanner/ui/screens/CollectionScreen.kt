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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.Rarity
import com.pokerarity.scanner.ui.components.PokemonListCard
import com.pokerarity.scanner.ui.components.SectionLabel
import com.pokerarity.scanner.ui.components.StatCard
import com.pokerarity.scanner.ui.theme.Black
import com.pokerarity.scanner.ui.theme.OutfitFamily
import com.pokerarity.scanner.ui.theme.TextHint
import com.pokerarity.scanner.ui.theme.TextPrimary

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
) {
    var activeFilter by remember { mutableStateOf(FilterOption.ALL) }

    val filtered = remember(activeFilter, pokemonList) {
        when (activeFilter) {
            FilterOption.ALL -> pokemonList
            FilterOption.LEGENDARY -> pokemonList.filter { it.rarity == Rarity.LEGENDARY }
            FilterOption.RARE -> pokemonList.filter { it.rarity == Rarity.RARE || it.rarity == Rarity.LEGENDARY }
            FilterOption.SHINY -> pokemonList.filter { "SHINY" in it.tags }
            FilterOption.LUCKY -> pokemonList.filter { "LUCKY" in it.tags }
        }
    }

    val headerAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        headerAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Black),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .graphicsLayer { alpha = headerAlpha.value }
                    .padding(start = 20.dp, end = 20.dp, top = 28.dp)
            ) {
                Column {
                    Text(
                        text = "Collection",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = OutfitFamily,
                        letterSpacing = (-1).sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${pokemonList.size} Pokemon scanned",
                        color = TextHint,
                        fontSize = 13.sp,
                        fontFamily = OutfitFamily,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        item {
            Row(
                modifier = Modifier
                    .graphicsLayer { alpha = headerAlpha.value }
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    value = pokemonList.size.toString(),
                    label = "SCANS",
                    valueColor = Color(0xFF60A5FA),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = pokemonList.count { it.rarity == Rarity.LEGENDARY || it.rarity == Rarity.RARE }.toString(),
                    label = "RARE",
                    valueColor = Color(0xFFFBBF24),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = pokemonList.count { "SHINY" in it.tags }.toString(),
                    label = "SHINIES",
                    valueColor = Color(0xFFC084FC),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = headerAlpha.value }
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                ScanButton(
                    isActive = isOverlayRunning,
                    onClick = onScanClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(18.dp))
        }

        item {
            LazyRow(
                modifier = Modifier.graphicsLayer { alpha = headerAlpha.value },
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(FilterOption.entries) { option ->
                    FilterChip(
                        label = option.label,
                        selected = activeFilter == option,
                        onClick = { activeFilter = option },
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        item {
            SectionLabel(
                text = if (filtered.isEmpty()) "NO SCANS" else "RECENT SCANS",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    isOverlayRunning = isOverlayRunning,
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

@Composable
private fun ScanButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "scanPulse")
    val dotScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotPulse",
    )

    val gradient = if (isActive) {
        Brush.horizontalGradient(listOf(Color(0xFFFF6B35), Color(0xFFB00020)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFFF4400), Color(0xFFCC0055)))
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 42.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .graphicsLayer {
                    scaleX = dotScale
                    scaleY = dotScale
                }
                .clip(CircleShape)
                .background(Color.White)
        )
        Text(
            text = if (isActive) "Stop" else "Scan",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFamily,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor = if (selected) Color(0x1AFFFFFF) else Color.Transparent
    val borderColor = if (selected) Color(0x4DFFFFFF) else Color(0x1AFFFFFF)
    val textColor = if (selected) TextPrimary else TextHint

    Text(
        text = label,
        color = textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = OutfitFamily,
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    isOverlayRunning: Boolean,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0D0D0D))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No scans yet",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFamily,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isOverlayRunning) "Use the floating scan button in Pokemon GO." else "Press Scan to start the overlay.",
            color = TextHint,
            fontSize = 13.sp,
            fontFamily = OutfitFamily,
        )
    }
}
