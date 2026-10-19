package com.candorfoods.factoryops.presentation.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.R

/** Text-backed editable fields of an asset (mt_asset_list) for [AssetEditSheet]. */
data class AssetEditForm(
    val building: String = "",
    val assetName: String = "",
    val category: String = "",
    val subLocation: String = "",
    val quantity: String = "",
    val modelNo: String = "",
    val serialNo: String = "",
    val powerLoad: String = "",
    val condition: String = "",
    val remarks: String = "",
)

/**
 * Edit sheet over the editable mt_asset_list columns, shared by the supervisor's
 * Machine Detail screen and the IT technician's asset detail screen. asset_id is the
 * immutable backend key and rated_kw is backend-computed, so neither appears here.
 *
 * [lockedCategory] pins the category: the caller drilled in by category (the IT
 * screens do), so it renders read-only and an edit can't move the asset out of the
 * list it was opened from. Left null, Category is a normal editable field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetEditSheet(
    form: AssetEditForm,
    isSaving: Boolean,
    error: String?,
    onChange: ((AssetEditForm) -> AssetEditForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.machine_edit_title),
    lockedCategory: String? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            EditField(stringResource(R.string.machine_field_asset_name), form.assetName) { v -> onChange { it.copy(assetName = v) } }
            EditField(stringResource(R.string.machine_field_building), form.building) { v -> onChange { it.copy(building = v) } }
            if (lockedCategory != null) {
                OutlinedTextField(
                    value = lockedCategory,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(R.string.machine_field_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                EditField(stringResource(R.string.machine_field_category), form.category) { v -> onChange { it.copy(category = v) } }
            }
            EditField(stringResource(R.string.machine_field_sub_location), form.subLocation) { v -> onChange { it.copy(subLocation = v) } }
            EditField(stringResource(R.string.machine_field_quantity), form.quantity, keyboardType = KeyboardType.Number) { v ->
                onChange { it.copy(quantity = v.filter(Char::isDigit)) }
            }
            EditField(stringResource(R.string.machine_field_model_no), form.modelNo) { v -> onChange { it.copy(modelNo = v) } }
            EditField(stringResource(R.string.machine_field_serial_no), form.serialNo) { v -> onChange { it.copy(serialNo = v) } }
            EditField(stringResource(R.string.machine_field_power_load), form.powerLoad) { v -> onChange { it.copy(powerLoad = v) } }
            EditField(stringResource(R.string.machine_field_condition), form.condition) { v -> onChange { it.copy(condition = v) } }
            EditField(stringResource(R.string.machine_field_remarks), form.remarks, singleLine = false) { v -> onChange { it.copy(remarks = v) } }

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            PrimaryButton(
                text = if (isSaving) stringResource(R.string.machine_action_saving)
                       else stringResource(R.string.machine_action_save_changes),
                onClick = onSave,
                enabled = !isSaving,
                style = PrimaryButtonStyle.Success,
                size = PrimaryButtonSize.Hero,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EditField(
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
