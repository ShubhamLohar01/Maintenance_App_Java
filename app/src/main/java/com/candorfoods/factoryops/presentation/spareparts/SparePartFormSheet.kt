package com.candorfoods.factoryops.presentation.spareparts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.spareparts.SparePart
import com.candorfoods.factoryops.presentation.spareparts.components.MachinePickerField

/** Warehouse code -> the label the factory uses on signage. */
private val WAREHOUSE_LABELS = listOf("W202" to "W-202", "A185" to "A-185")

/**
 * Add or edit one spare part.
 *
 * Pass [editing] = null to add. In edit mode the caller also supplies the machine
 * the part currently sits under ([machineName] / [assetId] / [warehouse]), since
 * that lives on the machine group rather than on the part itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparePartFormSheet(
    editing: SparePart?,
    machineName: String = "",
    assetId: String? = null,
    warehouse: String? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SparePartFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(editing?.id) {
        if (editing != null) {
            viewModel.startEditing(
                partId = editing.id, machineName = machineName, assetId = assetId,
                warehouse = warehouse, partName = editing.partName,
                unit = editing.unit, minQuantity = editing.minQuantity,
            )
        }
    }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (editing == null) "Add Spare Part" else "Edit Spare Part",
                style = MaterialTheme.typography.titleLarge,
            )

            if (!state.otherMode) {
                MachinePickerField(
                    query = state.machineQuery,
                    options = state.assetOptions,
                    onQueryChange = viewModel::onMachineQueryChange,
                    onSelect = viewModel::onAssetSelected,
                    onChooseOther = viewModel::onChooseOther,
                    supportingText = state.assetLoadError,
                )
            } else {
                OutlinedTextField(
                    value = state.machineName.orEmpty(),
                    onValueChange = viewModel::onMachineNameChange,
                    label = { Text("Machine name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                WarehouseDropdown(
                    selected = state.warehouse,
                    onSelect = viewModel::onWarehouseChange,
                )
                TextButton(onClick = { viewModel.onMachineQueryChange("") }) {
                    Text("Pick from the asset list instead")
                }
            }

            if (state.warehouseLocked) {
                OutlinedTextField(
                    value = WAREHOUSE_LABELS.firstOrNull { it.first == state.warehouse }?.second
                        ?: state.warehouse.orEmpty(),
                    onValueChange = {},
                    label = { Text("Warehouse") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = state.partName,
                onValueChange = viewModel::onPartNameChange,
                label = { Text("Part name *") },
                singleLine = true,
                isError = state.error != null,
                supportingText = state.error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.unit,
                onValueChange = viewModel::onUnitChange,
                label = { Text("Unit (e.g. nos, pcs, litre)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (editing == null) {
                OutlinedTextField(
                    value = state.quantity,
                    onValueChange = viewModel::onQuantityChange,
                    label = { Text("Opening quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = state.minQuantity,
                onValueChange = viewModel::onMinQuantityChange,
                label = { Text("Low-stock threshold") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = viewModel::save, enabled = state.canSave) { Text("Save") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarehouseDropdown(selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = WAREHOUSE_LABELS.firstOrNull { it.first == selected }?.second.orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Warehouse *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WAREHOUSE_LABELS.forEach { (code, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(code); expanded = false },
                )
            }
        }
    }
}
