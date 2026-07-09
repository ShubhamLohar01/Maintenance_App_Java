package com.candorfoods.factoryops.presentation.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.BreakdownRecord
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.util.DateTimeFormatter
import com.candorfoods.factoryops.util.pdf.PdfDownloader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: BreakdownListViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.pdfToShare) {
        ui.pdfToShare?.let { file ->
            try {
                snackbar.showSnackbar(PdfDownloader.downloadAndOpen(context, file))
            } finally {
                viewModel.onPdfShared()
            }
        }
    }
    LaunchedEffect(ui.exportError) {
        ui.exportError?.let { snackbar.showSnackbar("Couldn't create the PDF"); viewModel.onExportErrorShown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Breakdown Checklist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Create") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = onCreate,
            )
        },
    ) { padding ->
        // Both sources empty AND settled. A submitted-records error is NOT "empty" — it
        // falls through to the LazyColumn so the soft error note renders in that section.
        val allDataEmpty = ui.items.isEmpty() && ui.submitted.isEmpty() &&
            !ui.isLoading && !ui.submittedLoading
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (ui.isLoading && ui.submittedLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (allDataEmpty && ui.submittedError == null) {
                Text(
                    "No breakdown records yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    item { SectionHeader("Submitted F.06 records") }
                    if (ui.submittedError != null) {
                        item { SoftNote("Couldn't load submitted records — tap Create to add one, or retry once the server is ready.") }
                    } else if (ui.submitted.isEmpty()) {
                        item { SoftNote(if (ui.submittedLoading) "Loading…" else "None yet.") }
                    } else {
                        items(ui.submitted, key = { "sub-${it.id}" }) { rec ->
                            SubmittedRecordCard(rec, onDownload = { viewModel.exportSubmitted(rec) })
                        }
                    }

                    item { SectionHeader("Operator breakdowns (QC-approved can be completed)") }
                    if (ui.items.isEmpty()) {
                        item { SoftNote("No operator breakdowns yet.") }
                    } else {
                        items(ui.items, key = { "flag-${it.flag.id}" }) { item ->
                            BreakdownFlagCard(item, onOpen = { onOpen(item.flag.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = Spacing.sm))
}

@Composable
private fun SoftNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
}

@Composable
private fun SubmittedRecordCard(record: BreakdownRecord, onDownload: () -> Unit) {
    val name = record.machineName.ifBlank { record.equipmentModelNo }
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (record.location.isNotBlank()) {
                Text(record.location, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            Text("${record.recordDate} · ${record.problemInBrief}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            OutlinedButton(onClick = onDownload, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Download")
            }
        }
    }
}

@Composable
private fun BreakdownFlagCard(item: BreakdownListItem, onOpen: () -> Unit) {
    val flag = item.flag
    val name = flag.assetName?.ifBlank { null } ?: flag.machineId
    val enabled = item.isApproved
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.7f else 0.45f)

    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = if (enabled) onOpen else null) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else muted,
                    modifier = Modifier.weight(1f))
                StatusChip(breakdownStatusLabel(flag), enabled)
            }
            if (!flag.building.isNullOrBlank()) {
                Text(flag.building!!, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Text("${DateTimeFormatter.formatDate(flag.raisedAt)} · ${flag.description}",
                style = MaterialTheme.typography.bodyMedium, color = muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (enabled) {
                    Text("Tap to complete F.06",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Default.Lock, contentDescription = null,
                        modifier = Modifier.size(16.dp), tint = muted)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Awaiting QC approval", style = MaterialTheme.typography.labelMedium, color = muted)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, approved: Boolean) {
    val container = if (approved) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
    AssistChip(
        onClick = {}, enabled = false, label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = container,
            disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        ),
    )
}
