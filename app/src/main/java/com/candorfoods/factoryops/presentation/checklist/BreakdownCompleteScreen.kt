package com.candorfoods.factoryops.presentation.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.util.DateTimeFormatter
import com.candorfoods.factoryops.util.pdf.PdfShareLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownCompleteScreen(
    onBack: () -> Unit,
    viewModel: BreakdownCompleteViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.pdfToShare) {
        ui.pdfToShare?.let { file ->
            try {
                if (!PdfShareLauncher.share(context, file)) {
                    snackbar.showSnackbar("No app available to open the PDF")
                }
            } catch (e: Exception) {
                snackbar.showSnackbar("Couldn't share the PDF")
            } finally {
                // Always clear so the share is one-shot and never gets stuck on an error.
                viewModel.onPdfShared()
            }
        }
    }
    LaunchedEffect(ui.exportError) {
        ui.exportError?.let {
            // VM already supplies a user-facing message; show a fixed string (no raw cause/path).
            snackbar.showSnackbar("Couldn't create the PDF")
            viewModel.onExportErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Complete F.06") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val flag = ui.flag
        when {
            ui.isLoading -> Box(Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            flag == null -> Box(Modifier.fillMaxSize().padding(padding)) {
                Text(ui.error ?: "Not found", Modifier.align(Alignment.Center))
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState()).padding(Spacing.base),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                val f = ui.fields
                fun set(n: F06Fields) = viewModel.updateFields(n)

                FactoryCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(flag.assetName?.ifBlank { null } ?: flag.machineId,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        ReadLine("Date", DateTimeFormatter.formatDate(flag.raisedAt))
                        if (!flag.building.isNullOrBlank()) ReadLine("Location", flag.building!!)
                        ReadLine("Problem", flag.description)
                        ReadLine("Status", breakdownStatusLabel(flag))
                        if (!flag.resolvedByName.isNullOrBlank()) ReadLine("Resolved by", flag.resolvedByName!!)
                    }
                }

                if (!ui.isApproved) {
                    Text(
                        "This breakdown is not QC-approved yet, so it can't be completed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Field("Location", f.location) { set(f.copy(location = it)) }
                    Field("Equipment Model No./Sr. No.", f.equipmentModel) { set(f.copy(equipmentModel = it)) }
                    Field("Type of Maintenance", f.typeOfMaintenance) { set(f.copy(typeOfMaintenance = it)) }
                    Field("Part of machine", f.partOfMachine) { set(f.copy(partOfMachine = it)) }
                    Field("Reason (if temporary)", f.reason, singleLine = false) { set(f.copy(reason = it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.weight(1f)) { TimeField("Duration Start", f.durationStart) { set(f.copy(durationStart = it)) } }
                        Box(Modifier.weight(1f)) { TimeField("Duration End", f.durationEnd) { set(f.copy(durationEnd = it)) } }
                    }
                    Field("Machine operator sign", f.machineOperatorSign) { set(f.copy(machineOperatorSign = it)) }
                    Field("Maintenance person sign", f.maintenancePersonSign) { set(f.copy(maintenancePersonSign = it)) }
                    Field("QC clearance sign", f.qcClearanceSign) { set(f.copy(qcClearanceSign = it)) }
                    Field("Verified By", f.verifiedBy) { set(f.copy(verifiedBy = it)) }

                    PrimaryButton(
                        text = "Save",
                        onClick = viewModel::save,
                        size = PrimaryButtonSize.Hero,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (ui.isSaved) {
                        if (ui.isExporting) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                            }
                        } else {
                            OutlinedButton(onClick = viewModel::exportPdf, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(Spacing.sm))
                                Text("Download PDF")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadLine(label: String, value: String) {
    Text("$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
}

@Composable
private fun Field(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(label: String, value: String, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(label) }, placeholder = { Text("HH:mm") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { show = true })
    }
    if (show) {
        val parts = value.split(":")
        val initialH = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val initialM = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val tp = rememberTimePickerState(initialHour = initialH, initialMinute = initialM, is24Hour = true)
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = { onChange("%02d:%02d".format(tp.hour, tp.minute)); show = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancel") } },
            text = { TimePicker(state = tp) },
        )
    }
}
