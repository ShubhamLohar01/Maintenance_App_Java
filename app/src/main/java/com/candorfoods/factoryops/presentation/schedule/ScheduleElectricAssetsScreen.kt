package com.candorfoods.factoryops.presentation.schedule

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.ElectricAssetSchedule
import com.candorfoods.factoryops.presentation.common.components.FactoryCard

private fun fmtMin(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleElectricAssetsScreen(
    onBack: () -> Unit,
    viewModel: ScheduleElectricAssetsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is ScheduleUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Schedule Electric Assets", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (uiState.canEdit) "Tap an asset to set its daily recording window"
                            else "View only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.error != null -> Text(
                    uiState.error ?: "",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                uiState.schedules.isEmpty() -> Text(
                    "No electric assets found.",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.schedules, key = { it.assetId }) { item ->
                        AssetScheduleCard(
                            item = item,
                            canEdit = uiState.canEdit,
                            onClick = { if (uiState.canEdit) viewModel.onOpenEditor(item) },
                        )
                    }
                }
            }
        }
    }

    uiState.editor?.let { editor ->
        ScheduleEditorSheet(
            editor = editor,
            isSubmitting = uiState.isSubmitting,
            onStart = viewModel::onStartChange,
            onEnd = viewModel::onEndChange,
            onActive = viewModel::onActiveChange,
            onSave = viewModel::onSaveSchedule,
            onClear = viewModel::onClearSchedule,
            onEditDetails = {
                uiState.schedules.firstOrNull { it.assetId == editor.assetId }
                    ?.let(viewModel::onOpenAssetEdit)
            },
            onDismiss = viewModel::onCloseEditor,
        )
    }

    uiState.assetEdit?.let { edit ->
        AssetEditSheet(
            edit = edit,
            isSubmitting = uiState.isSubmitting,
            onName = viewModel::onAssetNameChange,
            onPower = viewModel::onPowerLoadChange,
            onFloor = viewModel::onSubLocationChange,
            onCondition = viewModel::onConditionChange,
            onSave = viewModel::onSaveAsset,
            onDismiss = viewModel::onCloseAssetEdit,
        )
    }
}

@Composable
private fun AssetScheduleCard(
    item: ElectricAssetSchedule,
    canEdit: Boolean,
    onClick: () -> Unit,
) {
    val summary: String = when {
        !item.hasSchedule -> "No schedule set"
        else -> {
            val kwh = item.estDailyKwh?.let { "~%.1f kWh/day".format(it) } ?: "—"
            "${fmtMin(item.startMin!!)}–${fmtMin(item.endMin!!)} · $kwh · " +
                if (item.active) "Active" else "Paused"
        }
    }
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Text(item.assetName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(
                    item.assetId.takeIf { it.isNotBlank() },
                    item.building.takeIf { it.isNotBlank() },
                    item.subLocation.takeIf { it.isNotBlank() },
                    item.powerLoad,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (item.hasSchedule && item.active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            if (item.ratedKw == null || item.ratedKw <= 0.0) {
                Text(
                    "Set a power load to record consumption.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditorSheet(
    editor: ScheduleEditorState,
    isSubmitting: Boolean,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    onActive: (Boolean) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onEditDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(editor.assetName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Daily recording window (factory time)", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, h, m -> onStart(h * 60 + m) },
                            editor.startMin / 60, editor.startMin % 60, true).show()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Start  ${fmtMin(editor.startMin)}") }
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, h, m -> onEnd(h * 60 + m) },
                            editor.endMin / 60, editor.endMin % 60, true).show()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("End  ${fmtMin(editor.endMin)}") }
            }

            val hours = ((editor.endMin - editor.startMin).coerceAtLeast(0)) / 60.0
            Text("Duration: %.1f h/day".format(hours), style = MaterialTheme.typography.bodySmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = editor.active, onCheckedChange = onActive, enabled = !isSubmitting)
                Spacer(Modifier.height(0.dp))
                Text(
                    if (editor.active) "  Active — recording daily" else "  Paused",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = onSave,
                enabled = !isSubmitting && editor.endMin > editor.startMin,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (editor.hasExisting) "Update schedule" else "Save schedule") }

            if (editor.hasExisting) {
                OutlinedButton(
                    onClick = onClear,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Clear schedule") }
            }

            TextButton(
                onClick = onEditDetails,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("  Edit asset details")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetEditSheet(
    edit: AssetEditState,
    isSubmitting: Boolean,
    onName: (String) -> Unit,
    onPower: (String) -> Unit,
    onFloor: (String) -> Unit,
    onCondition: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit asset details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = edit.assetName, onValueChange = onName,
                label = { Text("Asset name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = edit.powerLoad, onValueChange = onPower,
                label = { Text("Power load (e.g. 120Watt, 1.5kW)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = edit.subLocation, onValueChange = onFloor,
                label = { Text("Floor / sub-location") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = edit.condition, onValueChange = onCondition,
                label = { Text("Condition") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSave,
                enabled = !isSubmitting && edit.assetName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save details") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
