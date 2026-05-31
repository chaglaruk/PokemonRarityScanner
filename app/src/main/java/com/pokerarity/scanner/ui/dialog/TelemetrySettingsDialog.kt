package com.pokerarity.scanner.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pokerarity.scanner.ui.theme.PokeThemeId
import com.pokerarity.scanner.ui.theme.PokeThemeRegistry
import com.pokerarity.scanner.ui.theme.UiDesignVariantId
import com.pokerarity.scanner.ui.theme.UiDesignVariantRegistry

@Composable
fun TelemetrySettingsDialog(
    currentEnabled: Boolean,
    currentBaseUrl: String,
    currentApiKey: String,
    currentAutoCopyEnabled: Boolean,
    currentHapticsEnabled: Boolean,
    currentThemeId: PokeThemeId,
    currentDesignVariantId: UiDesignVariantId,
    onDismiss: () -> Unit,
    onSave: (
        enabled: Boolean,
        baseUrl: String,
        apiKey: String,
        autoCopyEnabled: Boolean,
        hapticsEnabled: Boolean,
        themeId: PokeThemeId,
        designVariantId: UiDesignVariantId,
    ) -> Unit
) {
    var enabled by remember(currentEnabled) { mutableStateOf(currentEnabled) }
    var baseUrl by remember(currentBaseUrl) { mutableStateOf(currentBaseUrl) }
    var apiKey by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var autoCopyEnabled by remember(currentAutoCopyEnabled) { mutableStateOf(currentAutoCopyEnabled) }
    var hapticsEnabled by remember(currentHapticsEnabled) { mutableStateOf(currentHapticsEnabled) }
    var selectedThemeId by remember(currentThemeId) { mutableStateOf(currentThemeId) }
    var selectedDesignVariantId by remember(currentDesignVariantId) { mutableStateOf(currentDesignVariantId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Telemetry Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable telemetry",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    singleLine = true,
                    enabled = enabled
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    singleLine = true,
                    enabled = enabled,
                    visualTransformation = PasswordVisualTransformation()
                )

                Text(
                    text = "Telemetry secrets are stored locally in encrypted preferences. Release builds do not bundle API keys; enter a key here only for trusted test devices.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-copy scan result",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = autoCopyEnabled,
                        onCheckedChange = { autoCopyEnabled = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Haptic feedback",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = { hapticsEnabled = it }
                    )
                }

                Text(
                    text = "Design theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                PokeThemeRegistry.allThemes.forEach { theme ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = theme.displayName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        RadioButton(
                            selected = selectedThemeId == theme.id,
                            onClick = { selectedThemeId = theme.id }
                        )
                    }
                }

                Text(
                    text = "UI design style",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                UiDesignVariantRegistry.allVariants.forEach { variant ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = variant.displayName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        RadioButton(
                            selected = selectedDesignVariantId == variant.id,
                            onClick = { selectedDesignVariantId = variant.id }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        enabled,
                        baseUrl,
                        apiKey,
                        autoCopyEnabled,
                        hapticsEnabled,
                        selectedThemeId,
                        selectedDesignVariantId,
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
