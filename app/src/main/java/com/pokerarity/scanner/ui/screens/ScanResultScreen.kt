package com.pokerarity.scanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.ui.components.StitchBottomNavigation
import com.pokerarity.scanner.ui.components.StitchNavDestination
import com.pokerarity.scanner.ui.overlay.ScanResultOverlayCard
import com.pokerarity.scanner.ui.theme.OutfitFamily

@Composable
fun ScanResultScreen(
    pokemon: Pokemon,
    onBack: () -> Unit,
    onShare: () -> Unit = {},
    onSave: () -> Unit = {},
    onFeedback: (String) -> Unit = {},
    onHome: () -> Unit = {},
    onHistory: () -> Unit = {},
    onScan: () -> Unit = {},
    onCollection: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val typeColors = pokemon.typeColors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            typeColors.primary.copy(alpha = 0.82f),
                            typeColors.secondary.copy(alpha = 0.62f),
                            Color(0xFF101010)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.34f))
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopEnd)
                .graphicsLayer(alpha = 0.16f)
                .clip(CircleShape)
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.BottomStart)
                .graphicsLayer(alpha = 0.10f)
                .clip(CircleShape)
                .background(typeColors.tertiary)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 92.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "PokeRarity",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = OutfitFamily,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x66FFFFFF))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                ) {
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.nav_settings),
                            tint = Color(0xFFB79D58),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                ScanResultOverlayCard(
                    pokemon = pokemon,
                    onDismiss = onBack,
                    onSave = onSave,
                    onShare = onShare,
                    onFeedback = onFeedback,
                )
            }
        }

        StitchBottomNavigation(
            activeDestination = StitchNavDestination.SCAN,
            onHomeClick = onHome,
            onHistoryClick = onHistory,
            onScanClick = onScan,
            onCollectionClick = onCollection,
            onSettingsClick = onSettings,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
