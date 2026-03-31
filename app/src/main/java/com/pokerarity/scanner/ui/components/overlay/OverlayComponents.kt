package com.pokerarity.scanner.ui.components.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.pokerarity.scanner.ui.components.noRippleClickable
import com.pokerarity.scanner.ui.theme.OutfitFamily

@Composable
fun OverlayBackButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.25f))
            .border(1.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(text = "<", color = Color.White.copy(alpha = 0.90f), fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = OutfitFamily)
        Text(
            text = "Geri",
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = OutfitFamily,
        )
    }
}

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
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = icon, color = tint.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = OutfitFamily)
    }
}

@Composable
fun OverlayTagPill(tag: String) {
    val (bg, border, fg) = when (tag.uppercase()) {
        "LEGENDARY" -> Triple(Color.Black.copy(alpha = 0.28f), Color.White.copy(alpha = 0.42f), Color.White)
        "SHINY" -> Triple(Color.Black.copy(alpha = 0.28f), Color(0xAAFFD700), Color(0xFFFFF08A))
        "HUNDO" -> Triple(Color.Black.copy(alpha = 0.28f), Color(0xAA00FF8C), Color(0xFF8CFFD2))
        "LUCKY" -> Triple(Color.Black.copy(alpha = 0.28f), Color(0xAAFFAA00), Color(0xFFFFC95C))
        else -> Triple(Color.Black.copy(alpha = 0.28f), Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.82f))
    }
    Text(
        text = tag,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        fontFamily = OutfitFamily,
        letterSpacing = 1.1.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, border, CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

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
            text = value,
            color = if (value == "-") Color.White.copy(alpha = 0.44f) else valueColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            fontFamily = OutfitFamily,
            letterSpacing = (-0.3).sp,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = OutfitFamily,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
fun OverlayActionButton(
    text: String,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    gradient: Brush? = null,
    onClick: () -> Unit,
) {
    val bgMod = if (isPrimary && gradient != null) {
        Modifier.background(gradient)
    } else {
        Modifier
            .background(Color(0xFF111111))
            .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(14.dp))
    }

    Text(
        text = text,
        color = if (isPrimary) Color.White else Color.White.copy(alpha = 0.82f),
        fontSize = if (isPrimary) 15.sp else 14.sp,
        fontWeight = FontWeight.Black,
        fontFamily = OutfitFamily,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(bgMod)
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 13.dp),
    )
}
