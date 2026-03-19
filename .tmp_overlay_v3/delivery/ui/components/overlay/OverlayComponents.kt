package com.pokerarity.scanner.ui.components.overlay

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.ui.theme.AccentGold
import com.pokerarity.scanner.ui.theme.OutfitFamily

// ── Geri butonu ───────────────────────────────────────────────
@Composable
fun OverlayBackButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.25f))
            .border(1.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 7.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("←", color = Color.White.copy(0.6f), fontSize = 14.sp, fontFamily = OutfitFamily)
        Text("Geri", color = Color.White.copy(0.6f), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = OutfitFamily)
    }
}

// ── Yuvarlak nav ikonu ────────────────────────────────────────
@Composable
fun OverlayNavCircle(
    icon: String,
    tint: Color = Color.White.copy(alpha = 0.55f),
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.25f))
            .border(1.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, color = tint, fontSize = 14.sp)
    }
}

// ── Tag pill ──────────────────────────────────────────────────
@Composable
fun OverlayTagPill(tag: String) {
    val (bg, border, fg) = when (tag.uppercase()) {
        "LEGENDARY" -> Triple(Color.White.copy(0.15f),         Color.White.copy(0.35f),         Color.White)
        "SHINY"     -> Triple(Color(0x33FFD700),               Color(0x88FFD700),               Color(0xFFFFE55C))
        "HUNDO"     -> Triple(Color(0x2600FF8C),               Color(0x6600FF8C),               Color(0xFF00FF8C))
        "LUCKY"     -> Triple(Color(0x26FFAA00),               Color(0x66FFAA00),               Color(0xFFFFAA00))
        else        -> Triple(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.20f), Color.White.copy(0.5f))
    }
    Text(
        text          = tag,
        color         = fg,
        fontSize      = 10.sp,
        fontWeight    = FontWeight.ExtraBold,
        fontFamily    = OutfitFamily,
        letterSpacing = 1.sp,
        modifier      = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, border, CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

// ── Stat kutusu ───────────────────────────────────────────────
@Composable
fun OverlayStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D0D0D))
            .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(14.dp))
            .padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text          = value,
            color         = if (value == "—") Color.White.copy(0.25f) else valueColor,
            fontSize      = 16.sp,
            fontWeight    = FontWeight.Bold,
            fontFamily    = OutfitFamily,
            letterSpacing = (-0.3).sp,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text          = label,
            color         = Color.White.copy(0.20f),
            fontSize      = 8.sp,
            fontWeight    = FontWeight.SemiBold,
            fontFamily    = OutfitFamily,
            letterSpacing = 2.sp,
        )
    }
}

// ── Aksiyon butonu ────────────────────────────────────────────
@Composable
fun OverlayActionButton(
    text: String,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    gradient: Brush? = null,
    onClick: () -> Unit,
) {
    val bgMod = if (isPrimary && gradient != null)
        Modifier.background(gradient)
    else
        Modifier
            .background(Color(0xFF111111))
            .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(14.dp))

    Text(
        text       = text,
        color      = if (isPrimary) Color.White else Color.White.copy(0.35f),
        fontSize   = if (isPrimary) 14.sp else 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = OutfitFamily,
        textAlign  = TextAlign.Center,
        modifier   = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(bgMod)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
    )
}
