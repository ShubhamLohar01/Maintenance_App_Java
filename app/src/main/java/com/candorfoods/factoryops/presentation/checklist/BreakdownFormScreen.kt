package com.candorfoods.factoryops.presentation.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.util.pdf.PdfDownloader
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.theme.Spacing

/**
 * Breakdown Maintenance Record (CFPLA.C4.F.06) — a register: add one card per
 * breakdown, with one shared "Verified By". Submit posts the whole sheet
 * (one row per entry) to /preventive-maintenance/breakdowns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownFormScreen(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    viewModel: BreakdownFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.submitted) {
        if (state.submitted) {
            snackbarHostState.showSnackbar("Breakdown record saved.")
            viewModel.onSubmittedHandled()
            onSubmitted()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar("Save failed — $it") }
    }
    LaunchedEffect(state.pdfToShare) {
        state.pdfToShare?.let { file ->
            snackbarHostState.showSnackbar(PdfDownloader.downloadAndOpen(context, file))
            viewModel.onPdfShared()
        }
    }
    LaunchedEffect(state.exportError) {
        state.exportError?.let {
            snackbarHostState.showSnackbar("Couldn't create PDF — $it")
            viewModel.onExportErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Breakdown Record", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Doc CFPLA.C4.F.06 · ${state.entries.size} " +
                                if (state.entries.size == 1) "entry" else "entries",
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
                actions = {
                    if (state.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = viewModel::exportPdf, enabled = state.canDownload) {
                            Icon(Icons.Default.Download, contentDescription = "Download PDF")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            state.entries.forEachIndexed { index, entry ->
                EntryCard(
                    srNo = index + 1,
                    entry = entry,
                    canRemove = state.entries.size > 1,
                    machineOptions = state.machineOptions,
                    onChange = viewModel::updateEntry,
                    onMachineSelected = { option -> viewModel.onMachineSelected(entry.key, option) },
                    onRemove = { viewModel.removeEntry(entry.key) },
                )
            }
            if (state.machinesError != null && state.machineOptions.isEmpty()) {
                Text(
                    "Couldn't load machine list — you can still type the name. (${state.machinesError})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedButton(onClick = viewModel::addEntry, modifier = Modifier.fillMaxWidth()) {
                Text("+ Add breakdown")
            }

            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Field("Verified By", state.verifiedBy) { viewModel.onVerifiedByChange(it) }
                    Text(
                        "Prepared By: FST   ·   Approved By: FSTL",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }

            if (!state.canSubmit && !state.isSubmitting) {
                Text(
                    "Each entry needs a Date, Machine Name and Problem.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            PrimaryButton(
                text = if (state.isSubmitting) "Saving…" else "Submit record",
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                loading = state.isSubmitting,
                size = PrimaryButtonSize.Hero,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EntryCard(
    srNo: Int,
    entry: BreakdownEntryState,
    canRemove: Boolean,
    machineOptions: List<MachineOption>,
    onChange: (BreakdownEntryState) -> Unit,
    onMachineSelected: (MachineOption) -> Unit,
    onRemove: () -> Unit,
) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "#$srNo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (canRemove) TextButton(onClick = onRemove) { Text("Remove") }
            }
            Field("Date (YYYY-MM-DD) *", entry.date) { onChange(entry.copy(date = it)) }
            Field("Location", entry.location) { onChange(entry.copy(location = it)) }
            MachineNameField(
                value = entry.machineName,
                options = machineOptions,
                onType = { onChange(entry.copy(machineName = it)) },
                onSelect = onMachineSelected,
            )
            Field("Equipment Model No./Sr. No. (auto)", entry.equipmentModel) { onChange(entry.copy(equipmentModel = it)) }
            Field("Problem in Brief *", entry.problem, singleLine = false) { onChange(entry.copy(problem = it)) }
            Field("Type of Maintenance", entry.typeOfMaintenance) { onChange(entry.copy(typeOfMaintenance = it)) }
            Field("Part of machine", entry.partOfMachine) { onChange(entry.copy(partOfMachine = it)) }
            Field("Reason (if temporary)", entry.reason, singleLine = false) { onChange(entry.copy(reason = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { TimeField("Duration Start", entry.durationStart) { onChange(entry.copy(durationStart = it)) } }
                Box(Modifier.weight(1f)) { TimeField("Duration End", entry.durationEnd) { onChange(entry.copy(durationEnd = it)) } }
            }
            Field("Machine operator sign", entry.machineOperatorSign) { onChange(entry.copy(machineOperatorSign = it)) }
            Field("Maintenance person sign", entry.maintenancePersonSign) { onChange(entry.copy(maintenancePersonSign = it)) }
            Field("QC clearance sign", entry.qcClearanceSign) { onChange(entry.copy(qcClearanceSign = it)) }
        }
    }
}

/** Machine Name — search the asset register or type manually. Selecting fills the Sr. No. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MachineNameField(
    value: String,
    options: List<MachineOption>,
    onType: (String) -> Unit,
    onSelect: (MachineOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, options) {
        if (value.isBlank()) options
        else options.filter { it.name.contains(value, ignoreCase = true) && it.name != value }
    }
    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onType(it); expanded = true },
            label = { Text("Machine Name * — search or type") },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        if (filtered.isNotEmpty()) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                filtered.take(8).forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.name)
                                Text(
                                    option.srNo,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        },
                        onClick = { onSelect(option); expanded = false },
                    )
                }
            }
        }
    }
}

/** Read-only field that opens a clock TimePicker; stores "HH:mm". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(label: String, value: String, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("HH:mm") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { show = true })
    }
    if (show) {
        val parts = value.split(":")
        val initialH = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val initialM = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val tpState = rememberTimePickerState(initialHour = initialH, initialMinute = initialM, is24Hour = true)
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange("%02d:%02d".format(tpState.hour, tpState.minute))
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancel") } },
            text = { TimePicker(state = tpState) },
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        modifier = Modifier.fillMaxWidth(),
    )
}
