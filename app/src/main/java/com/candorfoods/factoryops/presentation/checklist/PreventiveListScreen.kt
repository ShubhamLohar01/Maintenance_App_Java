package com.candorfoods.factoryops.presentation.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.R
import com.candorfoods.factoryops.domain.model.ChecklistRecordSummary
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.util.pdf.PdfDownloader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreventiveListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onDraft: (Int) -> Unit,
    onFinal: (Int) -> Unit,
    readOnly: Boolean = false,
    viewModel: PreventiveListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Head sees only filled (submitted) records; the creator sees drafts too.
    val records = if (readOnly) uiState.records.filterNot { it.isDraft } else uiState.records

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.pdfToShare) {
        uiState.pdfToShare?.let { file ->
            snackbarHostState.showSnackbar(PdfDownloader.downloadAndOpen(context, file))
            viewModel.onPdfShared()
        }
    }
    LaunchedEffect(uiState.exportError) {
        uiState.exportError?.let {
            snackbarHostState.showSnackbar(context.getString(R.string.preventive_pdf_create_error, it))
            viewModel.onExportErrorShown()
        }
    }

    // Reload whenever we return to this screen (e.g. after creating a record).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (readOnly) stringResource(R.string.preventive_title_records) else stringResource(R.string.preventive_title_list)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.preventive_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (!readOnly) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.preventive_create)) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = onCreate,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.error != null -> CenteredMessage(
                    stringResource(R.string.preventive_load_records_error, uiState.error!!)
                )
                records.isEmpty() -> CenteredMessage(
                    if (readOnly) stringResource(R.string.preventive_empty_filled) else stringResource(R.string.preventive_empty_editable)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(records, key = { it.id }) { record ->
                        RecordCard(
                            record,
                            isExporting = uiState.exportingId == record.id,
                            onClick = {
                                if (!readOnly && record.isDraft) onDraft(record.id) else onFinal(record.id)
                            },
                            onDownload = { viewModel.exportPdf(record.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordCard(
    record: ChecklistRecordSummary,
    isExporting: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
) {
    val typeLabel = when (record.formType.uppercase()) {
        "MONTHLY"   -> stringResource(R.string.preventive_type_monthly)
        "QUARTERLY" -> stringResource(R.string.preventive_type_quarterly)
        else        -> record.formType
    }
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(typeLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (record.isDraft) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text(stringResource(R.string.preventive_draft)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
            Text(
                stringResource(R.string.preventive_list_date_line, record.checklistDate, record.id),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            if (record.doneBy.isNotBlank()) {
                Text(
                    stringResource(R.string.preventive_done_by_value, record.doneBy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            OutlinedButton(
                onClick = onDownload,
                enabled = !isExporting,
                modifier = Modifier.align(Alignment.End),
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.preventive_download))
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CenteredMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
    )
}
