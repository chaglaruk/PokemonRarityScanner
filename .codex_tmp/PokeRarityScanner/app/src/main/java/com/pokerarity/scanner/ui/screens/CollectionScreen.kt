package com.pokerarity.scanner.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.Rarity
import com.pokerarity.scanner.ui.components.*
import com.pokerarity.scanner.ui.theme.*

enum class FilterOption(val label: String) {
    ALL("All"), LEGENDARY("Legendary"), RARE("Rare"),
    SHINY("Shiny"), HUNDO("Hundo")
}

@Composable
fun CollectionScreen(
    pokemonList: List<Pokemon>,
    onPokemonClick: (Pokemon) -> Unit,
) {
    var activeFilter by remember { mutableStateOf(FilterOption.ALL) }

    val filtered = remember(activeFilter, pokemonList) {
        when (activeFilter) {
            FilterOption.ALL       -> pokemonList
            FilterOption.LEGENDARY -> pokemonList.filter { it.rarity == Rarity.LEGENDARY }
            FilterOption.RARE      -> pokemonList.filter { it.rarity == Rarity.RARE }
            FilterOption.SHINY     -> pokemonList.filter { it.tags.contains("SHINY") }
            FilterOption.HUNDO     -> pokemonList.filter { it.iv == 100 }
        }
    }

    // Header fade-in
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

        // ── Header ──────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .graphicsLayer { alpha = headerAlpha.value }
                    .padding(start = 20.dp, end = 20.dp, top = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        text       = "Collection",
                        color      = TextPrimary,
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = OutfitFamily,
                        letterSpacing = (-1).sp,
                    )
                    ScanButton()
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text       = "${pokemonList.size} Pokémon scanned",
                    color      = TextHint,
                    fontSize   = 13.sp,
                    fontFamily = OutfitFamily,
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        // ── Stats row ────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .graphicsLayer { alpha = headerAlpha.value }
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    value      = pokemonList.size.toString(),
                    label      = "SCANS",
                    valueColor = Color(0xFF60A5FA),
                    modifier   = Modifier.weight(1f),
                )
                StatCard(
                    value      = pokemonList.count { it.rarity == Rarity.LEGENDARY || it.rarity == Rarity.RARE }.toString(),
                    label      = "RARE",
                    valueColor = Color(0xFFFBBF24),
                    modifier   = Modifier.weight(1f),
                )
                StatCard(
                    value      = pokemonList.count { it.tags.contains("SHINY") }.toString(),
                    label      = "SHINIES",
                    valueColor = Color(0xFFC084FC),
                    modifier   = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Filter row ───────────────────────────────────────
        item {
            LazyRow(
                modifier            = Modifier.graphicsLayer { alpha = headerAlpha.value },
                contentPadding      = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(FilterOption.values().size) { i ->
                    val opt = FilterOption.values()[i]
                    FilterChip(
                        label    = opt.label,
                        selected = activeFilter == opt,
                        onClick  = { activeFilter = opt },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Section label ─────────────────────────────────────
        item {
            SectionLabel(
                text     = "RECENT SCANS",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        // ── Pokemon cards ─────────────────────────────────────
        itemsIndexed(
            items = filtered,
            key   = { _, p -> p.id },
        ) { index, pokemon ->
            PokemonListCard(
                pokemon        = pokemon,
                onClick        = { onPokemonClick(pokemon) },
                animationDelay = index * 70,
                modifier       = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ── Scan button with pulsing dot ─────────────────────────────
@Composable
private fun ScanButton() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotScale by infiniteTransition.animateFloat(
        initialValue   = 1f,
        targetValue    = 0.6f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotPulse",
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFFF4400), Color(0xFFCC0055))
                )
            )
            .clickable { /* trigger overlay scan */ }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer { scaleX = dotScale; scaleY = dotScale }
                .clip(CircleShape)
                .background(Color.White)
        )
        Text(
            text       = "Scan",
            color      = Color.White,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFamily,
        )
    }
}

// ── Filter chip ───────────────────────────────────────────────
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor     = if (selected) Color(0x1AFFFFFF) else Color.Transparent
    val borderColor = if (selected) Color(0x4DFFFFFF) else Color(0x1AFFFFFF)
    val textColor   = if (selected) TextPrimary      else TextHint

    Text(
        text       = label,
        color      = textColor,
        fontSize   = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = OutfitFamily,
        letterSpacing = 0.5.sp,
        modifier   = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

// Fix missing import
import androidx.compose.ui.graphics.Brush
