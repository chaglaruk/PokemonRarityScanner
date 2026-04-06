package com.pokerarity.scanner.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Telemetry consent dialog that appears on first app launch.
 * Explains data collection and requires explicit user opt-in.
 */
@Composable
fun TelemetryConsentDialog(
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = {
            Text(
                "Help Improve PokeRarityScanner",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Data Collection",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "This app can collect scan data to improve accuracy and detection algorithms. The following data may be collected:\n\n" +
                    "• Pokémon species and stats (CP, HP, caught date)\n" +
                    "• Visual features (shiny, shadow, costume, lucky status)\n" +
                    "• Rarity scoring analysis\n" +
                    "• Device information (model, Android version)\n" +
                    "• Processing performance metrics\n\n" +
                    "This data helps us identify edge cases and improve the OCR recognition accuracy.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    "Your Control",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    "✓ Telemetry is completely optional\n" +
                    "✓ You can disable it anytime in Settings\n" +
                    "✓ The app works fully without telemetry (local-only mode)\n" +
                    "✓ All data sent over encrypted HTTPS connection\n" +
                    "✓ Data is deleted after 30 days",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    "Privacy",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    "We do not share your data with third parties. Screenshots are not collected. Your scan history stays on your device.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Enable Telemetry")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onReject
            ) {
                Text("Use Local Only")
            }
        },
        modifier = modifier
    )
}
