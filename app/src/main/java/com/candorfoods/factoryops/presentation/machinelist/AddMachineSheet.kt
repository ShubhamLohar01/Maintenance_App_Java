package com.candorfoods.factoryops.presentation.machinelist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.deriveMachineSection
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle

/**
 * Full-field "Add new machine" bottom sheet over the mt_asset_list columns. asset_id is
 * backend-assigned and rated_kw is backend-computed, so neither is entered here. Builds a
 * fully-populated MtMachine and hands it to [onSubmit]; the ViewModel POSTs it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMachineSheet(
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (MtMachine) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var assetName by remember { mutableStateOf("") }
    var building by remember { mutableStateOf(buildingOptions.first()) }
    var category by remember { mutableStateOf("") }
    var subLocation by remember { mutableStateOf("") }
    var powerLoad by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var modelNo by remember { mutableStateOf("") }
    var serialNo by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("") }
    var assignedTo by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    val canSubmit = assetName.isNotBlank() && building.isNotBlank() &&
        category.isNotBlank() && subLocation.isNotBlank() && !isSaving

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add new machine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Field("Asset name *", assetName) { assetName = it }
            BuildingDropdown(building) { building = it }
            Field("Category *", category) { category = it }
            Text(
                "Use \"Production Equipment\" for a production asset; anything else lands in Others.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Field("Sub-location *", subLocation) { subLocation = it }
            Field("Power load (e.g. 120Watt)", powerLoad) { powerLoad = it }
            Field("Quantity", quantity, keyboardType = KeyboardType.Number) { quantity = it.filter(Char::isDigit) }
            Field("Model No.", modelNo) { modelNo = it }
            Field("Serial No.", serialNo) { serialNo = it }
            Field("Condition", condition) { condition = it }
            Field("Assigned to", assignedTo) { assignedTo = it }
            Field("Remarks", remarks, singleLine = false) { remarks = it }

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(4.dp))

            PrimaryButton(
                text = "Add machine",
                onClick = {
                    onSubmit(
                        MtMachine(
                            assetId = "",
                            building = building,
                            assetName = assetName.trim(),
                            category = category.trim(),
                            subLocation = subLocation.trim(),
                            ratedKw = null,
                            powerLoadRaw = powerLoad.trim().ifBlank { null },
                            section = deriveMachineSection(category.trim()),
                            quantity = quantity.trim().toIntOrNull(),
                            condition = condition.trim().ifBlank { null },
                            assignedTo = assignedTo.trim().ifBlank { null },
                            modelNo = modelNo.trim().ifBlank { null },
                            serialNo = serialNo.trim().ifBlank { null },
                            remarks = remarks.trim().ifBlank { null },
                        )
                    )
                },
                enabled = canSubmit,
                loading = isSaving,
                style = PrimaryButtonStyle.Primary,
                size = PrimaryButtonSize.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), enabled = !isSaving) {
                Text("Cancel")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildingDropdown(value: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text("Building *") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            buildingOptions.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

private val buildingOptions = listOf("W-202", "A-185")
