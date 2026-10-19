package com.candorfoods.factoryops.presentation.schedule

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                        Text("Schedule Assets", style = MaterialTheme.typography.titleLarge)
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Machine-name search + warehouse/floor filters (floor options follow the chosen warehouse).
            if (!uiState.isLoading && uiState.error == null && uiState.schedules.isNotEmpty()) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchChange,
                    placeholder = { Text("Search machine name…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilterDropdown(
                        label = "Warehouse",
                        value = uiState.selectedBuilding,
                        options = uiState.buildings,
                        onSelect = viewModel::onBuildingFilter,
                        modifier = Modifier.weight(1f),
                    )
                    FilterDropdown(
                        label = "Floor",
                        value = uiState.selectedFloor,
                        options = uiState.floors,
                        onSelect = viewModel::onFloorFilter,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Box(Modifier.fillMaxSize()) {
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
                    uiState.filteredSchedules.isEmpty() -> Text(
                        "No assets match the current search or warehouse/floor filters.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.filteredSchedules, key = { it.assetId }) { item ->
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
    }

    uiState.editor?.let { editor ->
        ScheduleEditorSheet(
            editor = editor,
            isSubmitting = uiState.isSubmitting,
            onStart = viewModel::onStartChange,
            onEnd = viewModel::onEndChange,
            onAddInterval = viewModel::onAddInterval,
            onRemoveInterval = viewModel::onRemoveInterval,
            onActive = viewModel::onActiveChange,
            on24h = viewModel::on24hChange,
            onToggleDay = viewModel::onToggleDay,
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

/** Read-only filter dropdown; the first entry "All" clears the filter (null). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    value: String?,
    options: List<String>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value ?: "All",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onSelect(null); expanded = false })
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
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
            // Spelling every window out gets unreadable past a couple, so a longer
            // split shift is summarised by its count and total hours instead.
            val windows =
                if (item.is24h) "24 h"
                else if (item.intervals.size <= 2) {
                    item.intervals.joinToString(", ") { "${fmtMin(it.startMin)}–${fmtMin(it.endMin)}" }
                } else {
                    "${item.intervals.size} intervals · %.1f h/day".format(item.hours)
                }
            "$windows · $kwh · " + if (item.active) "Active" else "Paused"
        }
    }
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Text(item.assetName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(
                    item.assetId.takeIf { it.isNotBlank() },
                    item.category.takeIf { it.isNotBlank() },
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
    onStart: (Int, Int) -> Unit,
    onEnd: (Int, Int) -> Unit,
    onAddInterval: () -> Unit,
    onRemoveInterval: (Int) -> Unit,
    onActive: (Boolean) -> Unit,
    on24h: (Boolean) -> Unit,
    onToggleDay: (Int) -> Unit,
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
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(editor.assetName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Daily recording windows (factory time)", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

            // 24-hour run — when on, the start/end window is irrelevant (records all day).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = editor.is24h, onCheckedChange = on24h, enabled = !isSubmitting)
                Text("  24 hours run", style = MaterialTheme.typography.bodyMedium)
            }

            if (editor.is24h) {
                Text("Records the full day (24 h) on each selected day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            } else {
                // One row per window. A machine on a split shift runs 10:00-13:00 and
                // 15:00-22:30; the gap between windows draws nothing and isn't recorded.
                editor.intervals.forEachIndexed { index, interval ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(context, { _, h, m -> onStart(index, h * 60 + m) },
                                    interval.startMin / 60, interval.startMin % 60, true).show()
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.weight(1f),
                        ) { Text(fmtMin(interval.startMin)) }
                        Text("to", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(context, { _, h, m -> onEnd(index, h * 60 + m) },
                                    interval.endMin / 60, interval.endMin % 60, true).show()
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.weight(1f),
                        ) { Text(fmtMin(interval.endMin)) }
                        IconButton(
                            onClick = { onRemoveInterval(index) },
                            // The last window can't go: a schedule with none is no schedule.
                            enabled = !isSubmitting && editor.canRemoveInterval,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove interval ${index + 1}",
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onAddInterval,
                    enabled = !isSubmitting && editor.intervals.size < MAX_EDITOR_INTERVALS,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Add interval")
                }

                Text("Total: %.1f h/day".format(editor.totalHours),
                    style = MaterialTheme.typography.bodySmall)

                editor.validationError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Day-of-week picker — days left off are skipped (no reading fired that day).
            Text("Repeat on", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            DayToggleRow(selected = editor.days, enabled = !isSubmitting, onToggle = onToggleDay)

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
                enabled = !isSubmitting &&
                    editor.validationError == null &&
                    editor.days.isNotEmpty(),
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
/** Keep in step with ScheduleElectricAssetsViewModel.MAX_INTERVALS. */
private const val MAX_EDITOR_INTERVALS = 6

/** Sunday-first weekday labels; index matches the 0=Sunday … 6=Saturday convention. */
private val DAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

/** Row of tappable weekday chips. A selected chip = the schedule fires that day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayToggleRow(
    selected: Set<Int>,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DAY_LABELS.forEachIndexed { index, label ->
            FilterChip(
                selected = index in selected,
                onClick = { onToggle(index) },
                enabled = enabled,
                label = { Text(label) },
            )
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
                .imePadding()
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
