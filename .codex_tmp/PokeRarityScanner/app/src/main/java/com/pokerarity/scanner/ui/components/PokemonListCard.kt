package com.pokerarity.scanner.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.ui.theme.*

@Composable
fun PokemonListCard(
    pokemon: Pokemon,
    onClick: () -> Unit,
    animationDelay: Int = 0,
    modifier: Modifier = Modifier,
) {
    val tc = pokemon.typeColors
    val rc = pokemon.rarityColor

    // Entry animation: fade + slide up
    val alpha     = remember { Animatable(0f) }
    val translateY = remember { Animatable(16f) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        kotlinx.coroutines.launch {
            alpha.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
        }
        translateY.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
    }

    val badgeBg     = rc.copy(alpha = 0.12f)
    val badgeBorder = rc.copy(alpha = 0.30f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha      = alpha.value
                translationY    = translateY.value
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Surface1)
                .border(1.dp, Border1, RoundedCornerShape(20.dp))
                // Type-colored left stripe
                .drawBehind {
                    drawRect(
                        brush   = Brush.verticalGradient(listOf(tc.primary, tc.secondary)),
                        topLeft = Offset.Zero,
                        size    = Size(3.dp.toPx(), size.height),
                    )
                }
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Score ring
            ScoreRing(
                score          = pokemon.rarityScore,
                color          = rc,
                size           = 52.dp,
                animationDelay = animationDelay + 200,
            )

            // Info column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = pokemon.name,
                    color      = TextPrimary,
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OutfitFamily,
                    letterSpacing = (-0.3).sp,
                )
                Spacer(Modifier.height(4.dp))

                // Tag row
                if (pokemon.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        pokemon.tags.forEach { tag ->
                            PokeBadge(
                                text        = tag,
                                bgColor     = badgeBg,
                                borderColor = badgeBorder,
                                textColor   = rc,
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                }

                Text(
                    text       = pokemon.displayDate,
                    color      = TextHint,
                    fontSize   = 11.sp,
                    fontFamily = OutfitFamily,
                )
            }

            // Right: CP + type badge
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text       = "CP",
                    color      = TextHint,
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = OutfitFamily,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    text       = pokemon.cp.toString(),
                    color      = TextMuted,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OutfitFamily,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(4.dp))
                // Type chip
                Text(
                    text       = pokemon.type.uppercase(),
                    color      = tc.primary,
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OutfitFamily,
                    letterSpacing = 1.sp,
                    modifier   = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(tc.primary.copy(alpha = 0.13f))
                        .border(1.dp, tc.primary.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
}
