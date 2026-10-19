package com.candorfoods.factoryops.presentation.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import java.time.Instant
import java.time.ZoneOffset

val PLANTS = listOf("A185", "W202")

/** Two-choice plant selector for the form (a plant is always chosen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantSelector(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PLANTS.forEach { plant ->
            FilterChip(
                selected = selected == plant,
                onClick = { onSelect(plant) },
                label = { Text(plant) },
            )
        }
    }
}

/** History filter: All / A185 / W202 (null = All). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantFilterChips(selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        PLANTS.forEach { plant ->
            FilterChip(
                selected = selected == plant,
                onClick = { onSelect(plant) },
                label = { Text(plant) },
            )
        }
    }
}

/** Editable numeric input (decimal keyboard). */
@Composable
fun NumericInputField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Free-text input (remark / department). */
@Composable
fun TextInputField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Read-only computed field, greyed/disabled; shows "—" when not computable. */
@Composable
fun ComputedField(label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value.ifBlank { "—" },
        onValueChange = {},
        label = { Text(label) },
        singleLine = true,
        enabled = false,
        readOnly = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Supervisor-set price shown read-only in a reading form: a technician sees the
 * value but cannot change it (a supervisor sets it on the Utility Rates screen).
 * Shows "—" until a rate has been set.
 */
@Composable
fun LockedRateField(label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value.ifBlank { "—" },
        onValueChange = {},
        label = { Text(label) },
        singleLine = true,
        enabled = false,
        readOnly = true,
        trailingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Set by supervisor") },
        supportingText = { Text("Set by supervisor") },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Read-only date field ("YYYY-MM-DD") backed by a Material date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String = "Date") {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { show = true }) {
                Icon(Icons.Filled.DateRange, contentDescription = "Pick date")
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
    if (show) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(d.toString())
                    }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}
