package com.candorfoods.factoryops.presentation.technician

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * "Daily Reading" tab body on the technician home. Owns its own load/submit state
 * (independent of TechnicianHomeViewModel). Fetch system kWh per floor, type the
 * actual meter readings, Submit them all at once.
 */
@Composable
fun DailyReadingSection(
    modifier: Modifier = Modifier,
    viewModel: DailyReadingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is DailyReadingUiEvent.Saved -> snackbarHostState.showSnackbar(
                    "Saved ${event.count} floor${if (event.count == 1) "" else "s"}"
                )
                is DailyReadingUiEvent.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        when (uiState.phase) {
            DailyReadingPhase.Idle -> IdleState(
                error = uiState.error,
                onFetch = viewModel::fetchSystemReading,
            )
            DailyReadingPhase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LoadedState(
                uiState = uiState,
                onInputChange = viewModel::onInputChange,
                onRefetch = viewModel::fetchSystemReading,
                onSubmit = viewModel::submit,
            )
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun IdleState(error: String?, onFetch: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Pull the system-computed kWh for each floor, then enter the actual meter readings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Button(onClick = onFetch, modifier = Modifier.padding(top = 20.dp)) {
            Text("Fetch system reading")
        }
    }
}

@Composable
private fun LoadedState(
    uiState: DailyReadingUiState,
    onInputChange: (String, String) -> Unit,
    onRefetch: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
            Text(
                "${uiState.building} · ${uiState.readingDate}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Enter the actual reading for each floor with a meter. Idle floors can stay blank.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            OutlinedButton(onClick = onRefetch, modifier = Modifier.padding(top = 8.dp)) {
                Text("Refresh system reading")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.rows, key = { it.floor }) { row ->
                FloorReadingCard(row = row, onInputChange = onInputChange)
            }
        }

        if (uiState.error != null) {
            Text(
                uiState.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Button(
            onClick = onSubmit,
            enabled = uiState.canSubmit,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            if (uiState.phase == DailyReadingPhase.Submitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Submit")
            }
        }
    }
}

@Composable
private fun FloorReadingCard(
    row: FloorRowUi,
    onInputChange: (String, String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(row.floor, style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "System: ${formatReading(row.systemReading)} kWh",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                OutlinedTextField(
                    value = row.input,
                    onValueChange = { onInputChange(row.floor, it) },
                    label = { Text("Meter reading") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(170.dp),
                )
            }
        }
    }
}
