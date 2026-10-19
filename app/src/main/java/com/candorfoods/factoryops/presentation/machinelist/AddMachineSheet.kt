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
 *
 * Category and Floor (sub-location) suggest the values that already exist in the
 * register — [categoryOptions] globally, [subLocationsByBuilding] scoped to the chosen
 * building — so entries stay consistent, while typing a brand-new value stays possible.
 *
 * [lockedCategory] pins the category instead: the caller already decided it (the IT
 * screens drill in by category), so it renders read-only and can't be typo'd into a
 * different category. [categoryOptions] is then unused.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMachineSheet(
    isSaving: Boolean,
    error: String?,
    categoryOptions: List<String>,
    subLocationsByBuilding: Map<String, List<String>>,
    onDismiss: () -> Unit,
    onSubmit: (MtMachine) -> Unit,
    title: String = "Add new machine",
    lockedCategory: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var assetName by remember { mutableStateOf("") }
    var building by remember { mutableStateOf(buildingOptions.first()) }
    var category by remember { mutableStateOf(lockedCategory ?: "") }
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
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Field("Asset name *", assetName) { assetName = it }
            BuildingDropdown(building) { building = it }
            if (lockedCategory != null) {
                // Category was fixed by the caller's drill-down — read-only, and the
                // "Production Equipment" hint below would be wrong advice here.
                OutlinedTextField(
                    value = lockedCategory,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SuggestField(
                    label = "Category *",
                    value = category,
                    options = categoryOptions,
                    onChange = { category = it },
                )
                Text(
                    "Use \"Production Equipment\" for a production asset; anything else lands in Others.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            SuggestField(
                label = "Floor / Sub-location *",
                value = subLocation,
                // Floors are building-specific; fall back to every known floor when the
                // chosen building has no assets yet.
                options = subLocationsByBuilding[building]
                    ?: subLocationsByBuilding.values.flatten().distinct().sorted(),
                onChange = { subLocation = it },
            )
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
                text = if (lockedCategory != null) "Add asset" else "Add machine",
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

/**
 * Editable dropdown: shows the existing values (filtered as you type) so the supervisor
 * picks a known Category/Floor, but free text still works for a genuinely new one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestField(
    label: String,
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, options) {
        val q = value.trim()
        if (q.isEmpty()) options else options.filter { it.contains(q, ignoreCase = true) }
    }
    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); expanded = true },
            label = { Text(label) },
            singleLine = true,
            supportingText = { Text("Pick an existing value or type a new one.") },
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.menuAnchor(MenuAnchorType.SecondaryEditable),
                )
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filtered.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onChange(opt); expanded = false })
            }
        }
    }
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

private val buildingOptions = listOf("W202", "A185")
