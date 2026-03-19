package com.pokerarity.scanner.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.pokerarity.scanner.ui.theme.Border1
import com.pokerarity.scanner.ui.theme.OutfitFamily
import com.pokerarity.scanner.ui.theme.Surface1
import com.pokerarity.scanner.ui.theme.TextHint
import com.pokerarity.scanner.ui.theme.TextMuted
import com.pokerarity.scanner.ui.theme.TextPrimary
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
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Surface1)
                .border(1.dp, Border1, RoundedCornerShape(20.dp))
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(listOf(typeColors.primary, typeColors.secondary)),
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
                color = typeColors.primary,
                size = 52.dp,
                animationDelay = animationDelay + 200,
            )

            Column(modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Text(
                    text = pokemon.name,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OutfitFamily,
                    letterSpacing = (-0.3).sp,
                )
                Spacer(Modifier.height(4.dp))

                if (pokemon.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        pokemon.tags.take(3).forEach { tag ->
                            PokeBadge(
                                text = tag,
                                bgColor = typeColors.primary.copy(alpha = 0.12f),
                                borderColor = typeColors.primary.copy(alpha = 0.30f),
                                textColor = typeColors.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                }

                androidx.compose.material3.Text(
                    text = pokemon.displayDate,
                    color = TextHint,
                    fontSize = 11.sp,
                    fontFamily = OutfitFamily,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                androidx.compose.material3.Text(
                    text = "CP",
                    color = TextHint,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = OutfitFamily,
                    letterSpacing = 1.5.sp,
                )
                androidx.compose.material3.Text(
                    text = pokemon.cp.toString(),
                    color = TextMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OutfitFamily,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    text = pokemon.type.uppercase(),
                    color = typeColors.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OutfitFamily,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(typeColors.primary.copy(alpha = 0.13f))
                        .border(1.dp, typeColors.primary.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
}
