package com.candorfoods.factoryops.presentation.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOtherSheet(
    isSaving: Boolean,
    saveError: String?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, building: String, subLocation: String, subCategory: String, powerLoad: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var building by remember { mutableStateOf(buildingOptions.first()) }
    var subLocation by remember { mutableStateOf("") }
    var subCategory by remember { mutableStateOf("") }
    var powerLoad by remember { mutableStateOf("") }

    val canSubmit = name.isNotBlank() && subLocation.isNotBlank() && subCategory.isNotBlank() && !isSaving

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add to asset register", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Asset name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            DropdownField(
                label = "Building *",
                value = building,
                options = buildingOptions,
                onSelect = { building = it },
            )

            OutlinedTextField(
                value = subLocation,
                onValueChange = { subLocation = it },
                label = { Text("Sub-location *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = subCategory,
                onValueChange = { subCategory = it },
                label = { Text("Sub-category *") },
                supportingText = { Text("Use \"Production Equipment\" for a production asset; anything else lands in Others.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = powerLoad,
                onValueChange = { powerLoad = it },
                label = { Text("Power load (optional)") },
                placeholder = { Text("e.g. 120Watt, 2.2 kW") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (saveError != null) {
                Text(
                    saveError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(4.dp))

            PrimaryButton(
                text = "Add asset",
                onClick = {
                    onSubmit(
                        name.trim(),
                        building,
                        subLocation.trim(),
                        subCategory.trim(),
                        powerLoad.trim().ifBlank { null },
                    )
                },
                enabled = canSubmit,
                loading = isSaving,
                style = PrimaryButtonStyle.Primary,
                size = PrimaryButtonSize.Primary,
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
            ) {
                Text("Cancel")
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}

private val buildingOptions = listOf("W202", "A185")
