package com.pokerarity.scanner.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokerarity.scanner.data.model.EditDetailsCatalogOption
import com.pokerarity.scanner.data.model.EditDetailsCatalogOptions
import com.pokerarity.scanner.data.model.EditedScanDetails
import com.pokerarity.scanner.data.model.Pokemon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EditDetailsDialog(
    pokemon: Pokemon,
    catalogOptions: EditDetailsCatalogOptions = EditDetailsCatalogOptions.EMPTY,
    onDismiss: () -> Unit,
    onApply: (EditedScanDetails) -> Unit
) {
    var shiny by remember(pokemon.tags) { mutableStateOf("SHINY" in pokemon.tags) }
    var lucky by remember(pokemon.tags) { mutableStateOf("LUCKY" in pokemon.tags) }
    var shadow by remember(pokemon.tags) { mutableStateOf("SHADOW" in pokemon.tags) }
    var purified by remember(pokemon.tags) { mutableStateOf("PURIFIED" in pokemon.tags) }
    var locationCard by remember(pokemon.tags) { mutableStateOf("LOCATION" in pokemon.tags) }
    var specialForm by remember(pokemon.tags) { mutableStateOf("FORM" in pokemon.tags) }
    var species by remember(pokemon.name) {
        mutableStateOf(pokemon.name.takeUnless { it.equals("Unknown", ignoreCase = true) }.orEmpty())
    }
    var caughtDateText by remember(pokemon.caughtDate) {
        mutableStateOf(pokemon.caughtDate.takeUnless { it.equals("Unknown", ignoreCase = true) }.orEmpty())
    }
    var catalogQuery by remember { mutableStateOf("") }
    var selectedCostume by remember { mutableStateOf<EditDetailsCatalogOption?>(null) }
    var selectedEvent by remember { mutableStateOf<EditDetailsCatalogOption?>(null) }
    var selectedSpecialStatus by remember { mutableStateOf<EditDetailsCatalogOption?>(null) }
    var selectedRegional by remember { mutableStateOf<EditDetailsCatalogOption?>(null) }
    var dateError by remember { mutableStateOf(false) }
    val filteredCatalogOptions = remember(catalogOptions, catalogQuery) {
        catalogOptions.filter(catalogQuery)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Details",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = pokemon.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Edits apply only to this scan record. Events and costumes must come from catalog-backed matches.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = species,
                    onValueChange = { species = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Species") }
                )
                OutlinedTextField(
                    value = caughtDateText,
                    onValueChange = {
                        caughtDateText = it
                        dateError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Caught date") },
                    placeholder = { Text("MMM d, yyyy or yyyy-MM-dd") },
                    isError = dateError,
                    supportingText = {
                        if (dateError) Text("Use MMM d, yyyy or yyyy-MM-dd.")
                    }
                )
                OutlinedTextField(
                    value = catalogQuery,
                    onValueChange = { catalogQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Catalog search") }
                )
                CatalogOptionGroup("Costume", filteredCatalogOptions.costumes, selectedCostume) {
                    selectedCostume = it
                }
                CatalogOptionGroup("Event", filteredCatalogOptions.events, selectedEvent) {
                    selectedEvent = it
                }
                CatalogOptionGroup("Special", filteredCatalogOptions.specialStatuses, selectedSpecialStatus) {
                    selectedSpecialStatus = it
                }
                CatalogOptionGroup("Regional", filteredCatalogOptions.regionals, selectedRegional) {
                    selectedRegional = it
                }
                if (catalogOptions.all.isEmpty()) {
                    Text(
                        text = "No verified catalog choices for this species.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                EditCheckRow("Shiny", shiny) { shiny = it }
                EditCheckRow("Lucky", lucky) { lucky = it }
                EditCheckRow("Shadow", shadow) { shadow = it }
                EditCheckRow("Purified", purified) { purified = it }
                EditCheckRow("Location card", locationCard) { locationCard = it }
                EditCheckRow("Special form", specialForm) { specialForm = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDate = parseEditedDate(caughtDateText)
                    if (caughtDateText.isNotBlank() && parsedDate == null) {
                        dateError = true
                        return@Button
                    }
                    onApply(
                        EditedScanDetails(
                            species = species.trim().ifBlank { null },
                            costumeId = selectedCostume?.id,
                            eventId = selectedEvent?.id,
                            regionalRecordId = selectedRegional?.id,
                            specialStatusOverride = selectedSpecialStatus?.id,
                            isShiny = shiny,
                            isLucky = lucky,
                            isShadow = shadow,
                            isPurified = purified,
                            hasLocationCard = locationCard,
                            caughtDate = parsedDate,
                            formId = if (specialForm) "special" else null
                        )
                    )
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CatalogOptionGroup(
    label: String,
    options: List<EditDetailsCatalogOption>,
    selected: EditDetailsCatalogOption?,
    onSelect: (EditDetailsCatalogOption?) -> Unit
) {
    if (options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (selected == null) label else "$label: ${selected.label}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
        options.take(MAX_VISIBLE_OPTIONS_PER_GROUP).forEach { option ->
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSelect(if (selected?.id == option.id) null else option) }
            ) {
                Text(
                    text = option.subtitle?.let { "${option.label} - $it" } ?: option.label,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun parseEditedDate(value: String): Date? {
    if (value.isBlank()) return null
    val formats = listOf("MMM d, yyyy", "MMM dd, yyyy", "yyyy-MM-dd")
    return formats.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value.trim())
        }.getOrNull()
    }
}

private fun EditDetailsCatalogOptions.filter(query: String): EditDetailsCatalogOptions {
    val normalized = query.trim()
    if (normalized.isBlank()) return this
    fun List<EditDetailsCatalogOption>.matches() =
        filter {
            it.label.contains(normalized, ignoreCase = true) ||
                it.subtitle.orEmpty().contains(normalized, ignoreCase = true)
        }
    return copy(
        costumes = costumes.matches(),
        events = events.matches(),
        specialStatuses = specialStatuses.matches(),
        regionals = regionals.matches()
    )
}

private const val MAX_VISIBLE_OPTIONS_PER_GROUP = 3
