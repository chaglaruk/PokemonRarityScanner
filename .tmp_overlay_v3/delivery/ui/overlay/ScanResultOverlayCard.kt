package com.pokerarity.scanner.ui.overlay

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.ui.components.overlay.*
import com.pokerarity.scanner.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Scan result overlay card — Deep AMOLED v3
 *
 * Değişiklikler (önceki versiyona göre):
 *  - Hero şerit SABIT turuncu→kırmızı→mor gradyan
 *    Pokémon GO'nun mavi arka planıyla artık çakışmıyor
 *  - Tip rengi (water/fire/vb.) sadece:
 *      • IV accent çizgisi (sol kenar)
 *      • IV değeri rengi
 *      • Rarity breakdown aktif nokta rengi
 *    Her yerde garantili kontrast
 *  - Boş değerler (null IV, HP vb.) "—" ile sessizce gösteriliyor
 *  - Tag pill'ler daha kalın border + yüksek opacity → okunabilir
 *
 * WindowManager kurulumu (OverlayService içinde):
 *   val params = WindowManager.LayoutParams(
 *       MATCH_PARENT, WRAP_CONTENT,
 *       TYPE_APPLICATION_OVERLAY,
 *       FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_IN_SCREEN,
 *       PixelFormat.TRANSLUCENT
 *   ).apply { gravity = Gravity.BOTTOM }
 */
@Composable
fun ScanResultOverlayCard(
    pokemon: Pokemon,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val tc = pokemon.typeColors   // sadece aksan detaylarında kullanılıyor

    // ── Animasyonlar ──────────────────────────────────────────
    val slideY    = remember { Animatable(400f) }
    val cardAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { cardAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
        slideY.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
    }

    // Score sayacı
    val scoreAnim    = remember { Animatable(0f) }
    val displayScore by remember { derivedStateOf { scoreAnim.value.toInt() } }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        scoreAnim.animateTo(
            pokemon.rarityScore.toFloat(),
            tween(900, easing = FastOutSlowInEasing),
        )
    }

    // Breakdown satır stagger
    val rowAlphas = pokemon.analysis.indices.map { remember { Animatable(0f) } }
    LaunchedEffect(Unit) {
        pokemon.analysis.indices.forEach { i ->
            launch {
                kotlinx.coroutines.delay((750 + i * 70).toLong())
                rowAlphas[i].animateTo(1f, tween(280))
            }
        }
    }

    // ── Kart ana gövdesi ──────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = cardAlpha.value; translationY = slideY.value }
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(Color.Black)
            .border(
                1.dp,
                Color.White.copy(alpha = 0.07f),
                RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            ),
    ) {

        // ── HERO: sabit sıcak gradient ────────────────────────
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
                        start = Offset.Zero,
                        end   = Offset(Float.MAX_VALUE, Float.MAX_VALUE),
                    )
                )
                .background(Color.Black.copy(alpha = 0.18f))   // kontrast kaplama
                .padding(horizontal = 20.dp, bottom = 16.dp),
        ) {
            Column {
                // Drag handle
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .size(width = 38.dp, height = 4.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                )
                Spacer(Modifier.height(8.dp))

                // Nav
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    OverlayBackButton(onClick = onDismiss)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverlayNavCircle(icon = "↗", onClick = onShare)
                        OverlayNavCircle(icon = "★", tint = AccentGold)
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Eyebrow
                Text(
                    "SCAN RESULT",
                    color         = Color.White.copy(alpha = 0.55f),
                    fontSize      = 9.sp,
                    fontWeight    = FontWeight.Bold,
                    fontFamily    = OutfitFamily,
                    letterSpacing = 4.sp,
                )
                Spacer(Modifier.height(4.dp))

                // Ad + skor
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text          = pokemon.name,
                            color         = Color.White,
                            fontSize      = 30.sp,
                            fontWeight    = FontWeight.Black,
                            fontFamily    = OutfitFamily,
                            letterSpacing = (-1).sp,
                            lineHeight    = 30.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        // Tag'ler
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pokemon.tags.forEach { tag -> OverlayTagPill(tag) }
                        }
                    }

                    // Skor — dominant element
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier          = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            text          = displayScore.toString(),
                            color         = Color.White,
                            fontSize      = 72.sp,
                            fontWeight    = FontWeight.Black,
                            fontFamily    = OutfitFamily,
                            letterSpacing = (-4).sp,
                            lineHeight    = 68.sp,
                        )
                        Text(
                            "/100",
                            color    = Color.White.copy(alpha = 0.38f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = OutfitFamily,
                            modifier = Modifier.padding(bottom = 10.dp, start = 3.dp),
                        )
                    }
                }
            }
        }

        // ── SHEET: siyah ─────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.Black)
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.07f),
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                )
                .padding(horizontal = 20.dp, bottom = 22.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 18.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            )

            // Stats
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OverlayStatCell("CP",     pokemon.cp.toString(),              Modifier.weight(1f))
                OverlayStatCell("HP",     pokemon.hp?.toString() ?: "—",      Modifier.weight(1f))
                OverlayStatCell("CAUGHT", pokemon.caughtDate.take(8),          Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))

            // IV — tip rengi burada
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0D0D0D))
                    .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(14.dp))
                    .drawBehind {
                        drawRect(
                            brush   = Brush.verticalGradient(listOf(tc.primary, tc.secondary)),
                            topLeft = Offset.Zero,
                            size    = Size(3.dp.toPx(), size.height),
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("IV Perfection", color = Color.White.copy(0.30f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = OutfitFamily)
                Text(
                    text       = pokemon.iv?.let { "$it%" } ?: "—",
                    color      = if (pokemon.iv != null) tc.primary else Color.White.copy(0.25f),
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = OutfitFamily,
                    letterSpacing = (-0.5).sp,
                )
            }
            Spacer(Modifier.height(12.dp))

            // Breakdown label
            Text(
                "RARITY BREAKDOWN",
                color         = Color.White.copy(0.18f),
                fontSize      = 9.sp,
                fontWeight    = FontWeight.Bold,
                fontFamily    = OutfitFamily,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(8.dp))

            // Breakdown satırları
            Column {
                pokemon.analysis.forEachIndexed { i, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = rowAlphas[i].value }
                            .padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            // Aktif satırda sıcak gradient nokta, pasif satırda karanlık
                            Box(
                                Modifier.size(6.dp).clip(CircleShape).background(
                                    if (item.isPositive)
                                        Brush.linearGradient(listOf(StripeStart, StripeMid))
                                    else
                                        Brush.linearGradient(listOf(Color(0xFF2A2A2A), Color(0xFF2A2A2A)))
                                )
                            )
                            Text(
                                text       = item.label,
                                color      = if (item.isPositive) Color.White.copy(0.78f) else Color.White.copy(0.20f),
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = OutfitFamily,
                            )
                        }
                        Text(
                            text       = item.points,
                            color      = if (item.isPositive) StripeStart else Color(0xFF222222),
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = OutfitFamily,
                        )
                    }
                    if (i < pokemon.analysis.lastIndex)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF0F0F0F)))
                }
            }
            Spacer(Modifier.height(18.dp))

            // Butonlar
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OverlayActionButton("Geri",   Modifier.weight(1f), onClick = onDismiss)
                OverlayActionButton(
                    text      = "Kaydet",
                    modifier  = Modifier.weight(2f),
                    isPrimary = true,
                    gradient  = Brush.linearGradient(listOf(StripeStart, StripeMid)),
                    onClick   = onSave,
                )
                OverlayActionButton("Paylaş", Modifier.weight(1f), onClick = onShare)
            }
        }
    }
}
