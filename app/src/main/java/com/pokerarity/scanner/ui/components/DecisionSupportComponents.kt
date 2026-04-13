package com.pokerarity.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.ui.theme.OutfitFamily

@Composable
fun DecisionSupportSection(
    support: ScanDecisionSupport,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    if (!support.hasVisibleUiContent()) return

    Column(modifier = modifier.fillMaxWidth()) {
        if (support.eventConfidenceLabel.isNotBlank()) {
            SupportNoteCard(
                title = if (support.eventConfidenceCode == "LIVE_EVENT") "Live Event" else "Event Context",
                body = listOf(support.eventConfidenceLabel, support.eventConfidenceDetail)
                    .filter { it.isNotBlank() }
                    .joinToString(" • "),
                accentColor = Color(0xFF7DFFB8)
            )
        }
        if (!support.mismatchGuardTitle.isNullOrBlank()) {
            SupportNoteCard(
                title = support.mismatchGuardTitle,
                body = support.mismatchGuardDetail.orEmpty(),
                accentColor = Color(0xFFFF6B6B)
            )
        }
        if (!support.whyNotExact.isNullOrBlank()) {
            SupportNoteCard(
                title = "IV Solver",
                body = support.whyNotExact.orEmpty(),
                accentColor = accentColor
            )
        }
    }
}

@Composable
fun FeedbackSection(
    enabled: Boolean,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return

    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF171A20))
                .border(2.dp, Color(0xFF2A2E39), RoundedCornerShape(16.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.report_result_expand_title),
                        color = Color.White.copy(alpha = 0.96f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = OutfitFamily,
                    )
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = stringResource(R.string.report_result_expand_hint),
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OutfitFamily,
                    )
                }
                Text(
                    text = if (expanded) "▲" else "▼",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
        
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FeedbackButton(
                        text = stringResource(R.string.feedback_wrong_species),
                        containerColor = Color(0xFF171A20),
                        borderColor = Color(0xFF6AA8FF),
                        modifier = Modifier.weight(1f),
                        onClick = { onFeedback("wrong_species") }
                    )
                    FeedbackButton(
                        text = stringResource(R.string.feedback_wrong_event),
                        containerColor = Color(0xFF171A20),
                        borderColor = Color(0xFFFFB347),
                        modifier = Modifier.weight(1f),
                        onClick = { onFeedback("wrong_event") }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FeedbackButton(
                        text = stringResource(R.string.feedback_wrong_costume),
                        containerColor = Color(0xFF171A20),
                        borderColor = Color(0xFFFF7B7B),
                        modifier = Modifier.weight(1f),
                        onClick = { onFeedback("wrong_costume") }
                    )
                    FeedbackButton(
                        text = stringResource(R.string.feedback_wrong_shiny),
                        containerColor = Color(0xFF171A20),
                        borderColor = Color(0xFFFFD76A),
                        modifier = Modifier.weight(1f),
                        onClick = { onFeedback("wrong_shiny") }
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportNoteCard(
    title: String,
    body: String,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
            .border(1.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                fontFamily = OutfitFamily,
                letterSpacing = 0.6.sp,
            )
            Text(
                text = body,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OutfitFamily,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun FeedbackButton(
    text: String,
    containerColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            fontFamily = OutfitFamily,
            letterSpacing = 0.1.sp,
            textAlign = TextAlign.Center,
        )
    }
}
