package com.candorfoods.factoryops.presentation.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.utilities.FieldKind
import com.candorfoods.factoryops.domain.model.utilities.isRequiredAt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilityFormScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: UtilityFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val type = viewModel.type
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                UtilityFormEvent.Saved -> onSaved()
                is UtilityFormEvent.Error -> snackbar.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${type.label} · ${if (state.isEditing) "Edit" else "New"} reading",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlantSelector(selected = state.plant, onSelect = viewModel::setPlant)
            DateField(value = state.readingDate, onChange = viewModel::setDate)

            // Editable inputs. Non-required fields (remark, production units, department)
            // are marked "(optional)" so users know they can submit without them. The
            // price/rate field is supervisor-managed, so it renders read-only and is
            // filled from the backend (see UtilityFormViewModel.loadPrefill).
            //
            // What's required also depends on the plant — W-202's DG has no kWh meter,
            // so its kWh readings show as optional there (see UtilityValidation).
            type.inputs.forEach { field ->
                val value = state.inputs[field.key].orEmpty()
                val label = if (type.isRequiredAt(field, state.plant)) field.label
                    else "${field.label} (optional)"
                when {
                    field.key == type.rateKey -> LockedRateField(label = field.label, value = value)
                    field.kind == FieldKind.NUMBER -> NumericInputField(
                        label = label,
                        value = value,
                        onChange = { viewModel.onInputChange(field.key, it) },
                    )
                    field.kind == FieldKind.TEXT -> TextInputField(
                        label = label,
                        value = value,
                        onChange = { viewModel.onInputChange(field.key, it) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Calculated", style = MaterialTheme.typography.titleSmall)

            // Read-only computed fields (recompute live)
            type.computed.forEach { field ->
                ComputedField(
                    label = field.label,
                    value = formatUtilityNumber(state.computed[field.key], field.decimals),
                )
            }

            if (state.error != null) {
                Text(
                    state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = viewModel::submit,
                enabled = state.canSubmit && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Submit")
                }
            }
        }
    }
}
