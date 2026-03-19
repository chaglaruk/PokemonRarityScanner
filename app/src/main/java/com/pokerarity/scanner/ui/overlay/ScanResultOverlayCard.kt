package com.pokerarity.scanner.ui.overlay

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.ui.components.overlay.OverlayActionButton
import com.pokerarity.scanner.ui.components.overlay.OverlayStatCell
import com.pokerarity.scanner.ui.components.overlay.OverlayTagPill
import com.pokerarity.scanner.ui.theme.OutfitFamily
import com.pokerarity.scanner.ui.theme.StripeEnd
import com.pokerarity.scanner.ui.theme.StripeMid
import com.pokerarity.scanner.ui.theme.StripeStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ScanResultOverlayCard(
    pokemon: Pokemon,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val tc = pokemon.typeColors
    val outerShape = RoundedCornerShape(26.dp)
    val innerShape = RoundedCornerShape(24.dp)
    val sortedAnalysis = remember(pokemon.analysis) {
        val (positive, neutral) = pokemon.analysis.partition { it.isPositive }
        positive + neutral
    }

    val slideY = remember { Animatable(400f) }
    val cardAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { cardAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
        slideY.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
    }

    val scoreAnim = remember { Animatable(0f) }
    val displayScore by remember { derivedStateOf { scoreAnim.value.toInt() } }
    LaunchedEffect(Unit) {
        delay(500)
        scoreAnim.animateTo(
            pokemon.rarityScore.toFloat(),
            tween(900, easing = FastOutSlowInEasing),
        )
    }

    val rowAlphas = remember(sortedAnalysis.size) {
        List(sortedAnalysis.size) { Animatable(0f) }
    }
    LaunchedEffect(sortedAnalysis.size) {
        rowAlphas.forEach { it.snapTo(0f) }
        sortedAnalysis.indices.forEach { i ->
            launch {
                delay((750 + i * 70).toLong())
                rowAlphas[i].animateTo(1f, tween(280))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlpha.value
                translationY = slideY.value
            }
            .clip(outerShape)
            .background(Color.Black)
            .border(
                1.dp,
                Color.White.copy(alpha = 0.07f),
                outerShape,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0.00f to StripeStart,
                            0.55f to StripeMid,
                            1.00f to StripeEnd,
                        ),
                    )
                )
                .background(Color.Black.copy(alpha = 0.18f))
                .padding(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 16.dp),
        ) {
            Column {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .size(width = 38.dp, height = 4.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                )
                Spacer(Modifier.height(10.dp))

                Text(
                    text = "SCAN RESULT",
                    color = Color.White.copy(alpha = 0.84f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = OutfitFamily,
                    letterSpacing = 4.sp,
                )
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pokemon.name,
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = OutfitFamily,
                            letterSpacing = (-1.2).sp,
                            lineHeight = 34.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pokemon.tags.forEach { tag -> OverlayTagPill(tag) }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            text = displayScore.toString(),
                            color = Color.White,
                            fontSize = 78.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = OutfitFamily,
                            letterSpacing = (-4).sp,
                            lineHeight = 74.sp,
                        )
                        Text(
                            text = "/100",
                            color = Color.White.copy(alpha = 0.58f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OutfitFamily,
                            modifier = Modifier.padding(bottom = 10.dp, start = 3.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(innerShape)
                .background(Color.Black)
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.07f),
                    innerShape,
                )
                .padding(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 22.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 18.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OverlayStatCell("CP", pokemon.cp.toString(), Modifier.weight(1f))
                OverlayStatCell("HP", pokemon.hp?.toString() ?: "-", Modifier.weight(1f))
                OverlayStatCell("CAUGHT", pokemon.caughtDate, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0D0D0D))
                    .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "IV Perfection",
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = OutfitFamily,
                )
                Text(
                    text = pokemon.ivText ?: pokemon.iv?.let { "$it%" } ?: "-",
                    color = if (!pokemon.ivText.isNullOrBlank() || pokemon.iv != null) tc.primary else Color.White.copy(alpha = 0.25f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = OutfitFamily,
                    letterSpacing = (-0.5).sp,
                )
            }
            Spacer(Modifier.height(12.dp))

            Text(
                text = "RARITY BREAKDOWN",
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                fontFamily = OutfitFamily,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(8.dp))

            Column {
                sortedAnalysis.forEachIndexed { i, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = rowAlphas[i].value }
                            .padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (item.isPositive) {
                                            Brush.linearGradient(listOf(StripeStart, StripeMid))
                                        } else {
                                            Brush.linearGradient(listOf(Color(0xFF2A2A2A), Color(0xFF2A2A2A)))
                                        }
                                    )
                            )
                            Text(
                                text = item.label,
                                color = if (item.isPositive) Color.White.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.52f),
                                fontSize = 13.sp,
                                fontWeight = if (item.isPositive) FontWeight.Black else FontWeight.Bold,
                                fontFamily = OutfitFamily,
                            )
                        }
                        Text(
                            text = item.points,
                            color = if (item.isPositive) StripeStart else Color.White.copy(alpha = 0.50f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = OutfitFamily,
                        )
                    }
                    if (i < sortedAnalysis.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF0F0F0F))
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OverlayActionButton(
                    text = "Kaydet",
                    modifier = Modifier.weight(1f),
                    isPrimary = false,
                    onClick = onSave,
                )
                OverlayActionButton(
                    text = "Kapat",
                    modifier = Modifier.weight(1f),
                    isPrimary = true,
                    gradient = Brush.linearGradient(listOf(StripeStart, StripeMid)),
                    onClick = onDismiss,
                )
                OverlayActionButton("Paylas", Modifier.weight(1f), onClick = onShare)
            }
        }
    }
}
