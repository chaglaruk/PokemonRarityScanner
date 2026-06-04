package com.pokerarity.scanner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.ui.theme.LocalUiDesignVariant
import com.pokerarity.scanner.ui.theme.LocalPokeTheme
import com.pokerarity.scanner.ui.theme.OutfitFamily
import com.pokerarity.scanner.ui.theme.UiDesignVariantId
import com.pokerarity.scanner.ui.components.noRippleClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PokemonListCard(
    pokemon: Pokemon,
    onClick: () -> Unit,
    animationDelay: Int = 0,
    modifier: Modifier = Modifier,
) {
    val typeColors = pokemon.typeColors
    val theme = LocalPokeTheme.current
    val designVariantId = LocalUiDesignVariant.current.id
    val alpha = remember { Animatable(0f) }
    val translateY = remember { Animatable(16f) }

    LaunchedEffect(Unit) {
        delay(animationDelay.toLong())
        launch {
            alpha.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
        }
        translateY.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha.value
                translationY = translateY.value
            }
            .clip(RoundedCornerShape(20.dp))
            .noRippleClickable(onClick = onClick)
    ) {
        when (designVariantId) {
            UiDesignVariantId.CLASSIC -> PokemonListCardClassic(pokemon, animationDelay, typeColors.primary, typeColors.secondary)
            UiDesignVariantId.DEX_CONSOLE -> PokemonListCardDexConsole(pokemon, animationDelay, typeColors.primary, typeColors.secondary)
            UiDesignVariantId.COLLECTOR_ALBUM -> PokemonListCardCollectorAlbum(pokemon, animationDelay, typeColors.primary, typeColors.secondary)
            UiDesignVariantId.RESEARCH_LAB -> PokemonListCardResearchLab(pokemon, animationDelay, typeColors.primary, typeColors.secondary)
            UiDesignVariantId.BATTLE_HUD -> PokemonListCardBattleHud(pokemon, animationDelay, typeColors.primary, typeColors.secondary)
            UiDesignVariantId.AURORA_SHOWCASE -> PokemonListCardAuroraShowcase(pokemon, animationDelay, typeColors.primary, typeColors.secondary)
        }
    }
}

@Composable
private fun PokemonListCardClassic(
    pokemon: Pokemon,
    animationDelay: Int,
    typePrimary: androidx.compose.ui.graphics.Color,
    typeSecondary: androidx.compose.ui.graphics.Color,
) {
    val theme = LocalPokeTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(theme.card)
            .border(1.dp, theme.border, RoundedCornerShape(20.dp))
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(listOf(typePrimary, typeSecondary)),
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height),
                )
            }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScoreRing(
            score = pokemon.rarityScore,
            color = typePrimary,
            size = 52.dp,
            animationDelay = animationDelay + 200,
        )
        PokemonCardPrimaryBlock(pokemon, typePrimary, maxTags = 3, modifier = Modifier.weight(1f))
        PokemonCardStatPill(pokemon, typePrimary)
    }
}

@Composable
private fun PokemonListCardDexConsole(
    pokemon: Pokemon,
    animationDelay: Int,
    typePrimary: androidx.compose.ui.graphics.Color,
    typeSecondary: androidx.compose.ui.graphics.Color,
) {
    val theme = LocalPokeTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(theme.surface)
            .border(1.dp, theme.border, RoundedCornerShape(10.dp))
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(listOf(typePrimary, typeSecondary)),
                    topLeft = Offset.Zero,
                    size = Size(size.width, 2.dp.toPx()),
                )
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text(
                text = "DEX CONSOLE",
                color = theme.textMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = OutfitFamily,
                letterSpacing = 1.8.sp,
            )
            Spacer(Modifier.weight(1f))
            ScoreRing(
                score = pokemon.rarityScore,
                color = typePrimary,
                size = 42.dp,
                animationDelay = animationDelay + 150,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            PokemonCardPrimaryBlock(pokemon, typePrimary, maxTags = 2, modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.card)
                    .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                ConsoleDataCell("CP", pokemon.cp.takeIf { it > 0 }?.toString() ?: "-", typePrimary)
                Spacer(Modifier.height(6.dp))
                ConsoleDataCell("HP", pokemon.hp?.takeIf { it > 0 }?.toString() ?: "-", theme.textSecondary)
            }
        }
    }
}

@Composable
private fun PokemonListCardCollectorAlbum(
    pokemon: Pokemon,
    animationDelay: Int,
    typePrimary: androidx.compose.ui.graphics.Color,
    typeSecondary: androidx.compose.ui.graphics.Color,
) {
    val theme = LocalPokeTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(theme.card)
            .border(2.dp, theme.rarityLegendary.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text(
                text = "COLLECTOR ALBUM",
                color = theme.rarityLegendary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                fontFamily = OutfitFamily,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.weight(1f))
            PokeBadge(
                text = pokemon.rarityTierLabel.uppercase(),
                bgColor = theme.rarityLegendary.copy(alpha = 0.16f),
                borderColor = theme.rarityLegendary,
                textColor = theme.rarityLegendary,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreRing(
                score = pokemon.rarityScore,
                color = typePrimary,
                size = 62.dp,
                animationDelay = animationDelay + 100,
            )
            PokemonCardPrimaryBlock(pokemon, typePrimary, maxTags = 3, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PokeBadge("CP ${pokemon.cp.takeIf { it > 0 } ?: "-"}", theme.accentSoft, theme.accent, theme.accent, Modifier.weight(1f))
            PokeBadge("HP ${pokemon.hp?.takeIf { it > 0 } ?: "-"}", theme.surface, theme.border, theme.textSecondary, Modifier.weight(1f))
            PokeBadge(pokemon.type.uppercase(), typePrimary.copy(alpha = 0.13f), typePrimary.copy(alpha = 0.40f), typePrimary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PokemonListCardResearchLab(
    pokemon: Pokemon,
    animationDelay: Int,
    typePrimary: androidx.compose.ui.graphics.Color,
    typeSecondary: androidx.compose.ui.graphics.Color,
) {
    val theme = LocalPokeTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.background)
            .border(1.dp, theme.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text(
                text = "RESEARCH LAB",
                color = theme.textMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = OutfitFamily,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.weight(1f))
            ScoreRing(
                score = pokemon.rarityScore,
                color = typePrimary,
                size = 40.dp,
                animationDelay = animationDelay + 120,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ConsoleDataCell("NAME", pokemon.name.ifBlank { "Unknown" }, theme.textPrimary, Modifier.weight(1f))
            ConsoleDataCell("TYPE", pokemon.type.uppercase(), typePrimary, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ConsoleDataCell("CP", pokemon.cp.takeIf { it > 0 }?.toString() ?: "-", theme.textPrimary, Modifier.weight(1f))
            ConsoleDataCell("HP", pokemon.hp?.takeIf { it > 0 }?.toString() ?: "-", theme.textPrimary, Modifier.weight(1f))
            ConsoleDataCell("DATE", pokemon.displayDate, theme.textSecondary, Modifier.weight(2f))
        }
    }
}

@Composable
private fun PokemonListCardBattleHud(
    pokemon: Pokemon,
    animationDelay: Int,
    typePrimary: androidx.compose.ui.graphics.Color,
    typeSecondary: androidx.compose.ui.graphics.Color,
) {
    val theme = LocalPokeTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.elevatedSurface)
            .border(1.dp, theme.border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreRing(
            score = pokemon.rarityScore,
            color = typePrimary,
            size = 56.dp,
            animationDelay = animationDelay + 120,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.Text(
                text = pokemon.name,
                color = theme.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = OutfitFamily,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PokeBadge(
                    text = "RANK ${pokemon.rarityTierLabel.uppercase()}",
                    bgColor = theme.accentSoft,
                    borderColor = theme.accent,
                    textColor = theme.accent,
                )
                PokeBadge(
                    text = "TYPE ${pokemon.type.uppercase()}",
                    bgColor = typePrimary.copy(alpha = 0.14f),
                    borderColor = typePrimary.copy(alpha = 0.36f),
                    textColor = typePrimary,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.border)
                    .drawBehind {
                        val fillWidth = (size.width * (pokemon.rarityScore.coerceIn(0, 100) / 100f))
                        drawRect(
                            brush = Brush.horizontalGradient(listOf(typePrimary, typeSecondary)),
                            topLeft = Offset.Zero,
                            size = Size(fillWidth, size.height),
                        )
                    }
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            ConsoleDataCell("CP", pokemon.cp.takeIf { it > 0 }?.toString() ?: "-", theme.textPrimary)
            Spacer(Modifier.height(4.dp))
            ConsoleDataCell("HP", pokemon.hp?.takeIf { it > 0 }?.toString() ?: "-", theme.textSecondary)
        }
    }
}

@Composable
private fun PokemonListCardAuroraShowcase(
    pokemon: Pokemon,
    animationDelay: Int,
    typePrimary: androidx.compose.ui.graphics.Color,
    typeSecondary: androidx.compose.ui.graphics.Color,
) {
    val theme = LocalPokeTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        theme.elevatedSurface,
                        typeSecondary.copy(alpha = 0.24f),
                    )
                )
            )
            .border(1.dp, typePrimary.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(
                score = pokemon.rarityScore,
                color = theme.rarityShiny,
                size = 50.dp,
                animationDelay = animationDelay + 100,
            )
            Spacer(Modifier.width(10.dp))
            PokemonCardPrimaryBlock(pokemon, typePrimary, maxTags = 3, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PokeBadge(
                text = "SHOWCASE",
                bgColor = theme.rarityShiny.copy(alpha = 0.14f),
                borderColor = theme.rarityShiny.copy(alpha = 0.42f),
                textColor = theme.rarityShiny,
            )
            PokeBadge(
                text = "CP ${pokemon.cp.takeIf { it > 0 } ?: "-"}",
                bgColor = theme.accentSoft,
                borderColor = theme.accent,
                textColor = theme.accent,
            )
        }
    }
}

@Composable
private fun PokemonCardPrimaryBlock(
    pokemon: Pokemon,
    typePrimary: androidx.compose.ui.graphics.Color,
    maxTags: Int,
    modifier: Modifier = Modifier,
) {
    val theme = LocalPokeTheme.current
    Column(modifier = modifier) {
        androidx.compose.material3.Text(
            text = pokemon.name.ifBlank { "Unknown" },
            color = theme.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFamily,
            letterSpacing = (-0.3).sp,
        )
        Spacer(Modifier.height(4.dp))
        if (pokemon.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                pokemon.tags.take(maxTags).forEach { tag ->
                    PokeBadge(
                        text = tag,
                        bgColor = typePrimary.copy(alpha = 0.12f),
                        borderColor = typePrimary.copy(alpha = 0.30f),
                        textColor = typePrimary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        androidx.compose.material3.Text(
            text = pokemon.displayDate.ifBlank { "Unknown" },
            color = theme.textMuted,
            fontSize = 11.sp,
            fontFamily = OutfitFamily,
        )
    }
}

@Composable
private fun PokemonCardStatPill(
    pokemon: Pokemon,
    typePrimary: androidx.compose.ui.graphics.Color,
) {
    val theme = LocalPokeTheme.current
    Column(horizontalAlignment = Alignment.End) {
        androidx.compose.material3.Text(
            text = "CP",
            color = theme.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = OutfitFamily,
            letterSpacing = 1.5.sp,
        )
        androidx.compose.material3.Text(
            text = pokemon.cp.takeIf { it > 0 }?.toString() ?: "-",
            color = theme.textSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFamily,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.Text(
            text = pokemon.type.uppercase(),
            color = typePrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFamily,
            letterSpacing = 1.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(typePrimary.copy(alpha = 0.13f))
                .border(1.dp, typePrimary.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ConsoleDataCell(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val theme = LocalPokeTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(theme.card)
            .border(1.dp, theme.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        androidx.compose.material3.Text(
            text = label,
            color = theme.textMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            fontFamily = OutfitFamily,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(2.dp))
        androidx.compose.material3.Text(
            text = value.ifBlank { "-" },
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFamily,
        )
    }
}
