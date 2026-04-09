package com.pokerarity.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.R
import com.pokerarity.scanner.ui.theme.OutfitFamily

enum class StitchNavDestination {
    HOME,
    HISTORY,
    SCAN,
    COLLECTION,
    SETTINGS,
}

@Composable
fun StitchBottomNavigation(
    activeDestination: StitchNavDestination,
    onHomeClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onScanClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF171616).copy(alpha = 0.96f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        StitchBottomNavItem(
            icon = Icons.Rounded.Home,
            label = stringResource(R.string.nav_home),
            selected = activeDestination == StitchNavDestination.HOME,
            onClick = onHomeClick,
        )
        StitchBottomNavItem(
            icon = Icons.Rounded.History,
            label = stringResource(R.string.nav_history),
            selected = activeDestination == StitchNavDestination.HISTORY,
            onClick = onHistoryClick,
        )
        StitchCenterScanItem(
            selected = activeDestination == StitchNavDestination.SCAN,
            label = stringResource(R.string.nav_scan),
            onClick = onScanClick,
        )
        StitchBottomNavItem(
            icon = Icons.Rounded.CollectionsBookmark,
            label = stringResource(R.string.nav_collection),
            selected = activeDestination == StitchNavDestination.COLLECTION,
            onClick = onCollectionClick,
        )
        StitchBottomNavItem(
            icon = Icons.Rounded.Settings,
            label = stringResource(R.string.nav_settings),
            selected = activeDestination == StitchNavDestination.SETTINGS,
            onClick = onSettingsClick,
        )
    }
}

@Composable
private fun StitchBottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) Color(0xFFFF7A2D) else Color.White.copy(alpha = 0.48f)
    Column(
        modifier = Modifier
            .width(60.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFamily,
        )
    }
}

@Composable
private fun StitchCenterScanItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val gradient = Brush.radialGradient(
        colors = if (selected) {
            listOf(Color(0xFFFF8B2B), Color(0xFFFF5A1B))
        } else {
            listOf(Color(0xFFFF7840), Color(0xFFB63A10))
        }
    )
    Column(
        modifier = Modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(gradient)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoCamera,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = label,
            color = Color(0xFFFF7A2D),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFamily,
        )
    }
}
