// Purpose: Render the floating scan-result card used by overlay and result screens.
package com.pokerarity.scanner.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.valuableSummary
import com.pokerarity.scanner.ui.components.FeedbackSection
import com.pokerarity.scanner.ui.components.RarityTierCard
import com.pokerarity.scanner.ui.components.overlay.OverlayActionButton
import com.pokerarity.scanner.ui.components.overlay.OverlayTagPill
import com.pokerarity.scanner.ui.theme.OutfitFamily
import com.pokerarity.scanner.ui.theme.StripeEnd
import com.pokerarity.scanner.ui.theme.StripeMid
import com.pokerarity.scanner.ui.theme.StripeStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanResultOverlayCard(
    pokemon: Pokemon,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onFeedback: (String) -> Unit = {},
) {
    val tc = pokemon.typeColors
    val outerShape = RoundedCornerShape(26.dp)
    val innerShape = RoundedCornerShape(24.dp)
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp * 0.76f).dp
    val valueSummary = remember(pokemon) { pokemon.valuableSummary() }

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

    val summaryAlpha = remember { Animatable(0f) }
    LaunchedEffect(valueSummary) {
        summaryAlpha.snapTo(0f)
        delay(720)
        summaryAlpha.animateTo(1f, tween(320))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxCardHeight)
            .graphicsLayer {
                alpha = cardAlpha.value
                translationY = slideY.value
            }
            .clip(outerShape)
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.07f), outerShape),
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
                    text = stringResource(R.string.scan_result_title),
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            pokemon.tags.forEach { tag -> OverlayTagPill(tag) }
                        }
                    }

                    RarityTierCard(
                        label = pokemon.rarityTierLabel,
                        score = displayScore,
                        tierCode = pokemon.rarityTierCode,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .widthIn(min = 176.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(innerShape)
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.07f), innerShape)
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.why_its_valuable),
                    color = tc.primary.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = OutfitFamily,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = summaryAlpha.value }
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0D0D0D))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = valueSummary,
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OutfitFamily,
                        lineHeight = 23.sp,
                    )
                }

                Spacer(Modifier.height(18.dp))

                FeedbackSection(
                    enabled = !pokemon.telemetryUploadId.isNullOrBlank(),
                    onFeedback = onFeedback,
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OverlayActionButton(
                    text = stringResource(R.string.save),
                    modifier = Modifier.weight(1f),
                    isPrimary = false,
                    onClick = onSave,
                )
                OverlayActionButton(
                    text = stringResource(R.string.close),
                    modifier = Modifier.weight(1f),
                    isPrimary = true,
                    gradient = Brush.linearGradient(listOf(StripeStart, StripeMid)),
                    onClick = onDismiss,
                )
                OverlayActionButton(stringResource(R.string.share), Modifier.weight(1f), onClick = onShare)
            }
        }
    }
}
