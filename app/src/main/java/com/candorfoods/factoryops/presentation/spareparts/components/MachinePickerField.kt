package com.candorfoods.factoryops.presentation.spareparts.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** One selectable machine from the asset register. */
data class MachineOption(
    val assetId: String,
    val assetName: String,
    val building: String,
)

/**
 * Searchable machine dropdown over the whole asset register, following the pattern
 * already used at presentation/checklist/BreakdownFormScreen.kt:219.
 *
 * The last item is always "Other — type machine name", which calls [onChooseOther]
 * so the caller can swap in a free-text field plus a warehouse dropdown. When
 * [options] is empty (the register failed to load) only that item shows, so adding
 * a part never hard-blocks on a failed fetch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachinePickerField(
    query: String,
    options: List<MachineOption>,
    onQueryChange: (String) -> Unit,
    onSelect: (MachineOption) -> Unit,
    onChooseOther: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(query, options) {
        if (query.isBlank()) options
        else options.filter {
            it.assetName.contains(query, ignoreCase = true) ||
                it.assetId.contains(query, ignoreCase = true)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it); expanded = true },
            label = { Text("Machine * — search the asset list") },
            singleLine = true,
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            filtered.take(8).forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.assetName)
                            Text(
                                "${option.assetId} · ${option.building}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    },
                    onClick = { onSelect(option); expanded = false },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        "Other — type machine name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = { onChooseOther(); expanded = false },
            )
        }
    }
}
