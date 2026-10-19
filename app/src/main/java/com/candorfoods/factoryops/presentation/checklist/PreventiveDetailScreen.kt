package com.candorfoods.factoryops.presentation.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.candorfoods.factoryops.util.pdf.PdfDownloader
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.R
import com.candorfoods.factoryops.domain.model.ChecklistRecordDetail
import com.candorfoods.factoryops.domain.model.ChecklistRecordItem
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.util.PeriodLabel
import com.candorfoods.factoryops.presentation.common.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreventiveDetailScreen(
    onBack: () -> Unit,
    viewModel: PreventiveDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preventive_title_detail)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.preventive_back))
                    }
                },
                actions = {
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = viewModel::exportPdf,
                            enabled = uiState.record != null,
                        ) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.preventive_download_pdf))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.error != null -> Text(
                    stringResource(R.string.preventive_load_error, uiState.error!!),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                )
                uiState.record != null -> RecordBody(uiState.record!!)
            }
        }
    }
}

@Composable
private fun RecordBody(record: ChecklistRecordDetail) {
    val typeLabel = when (record.formType.uppercase()) {
        "MONTHLY"        -> stringResource(R.string.preventive_type_monthly)
        "QUARTERLY"      -> stringResource(R.string.preventive_type_quarterly)
        // A-185's own form. Without these its records rendered the raw wire value,
        // e.g. "A185_MONTHLY".
        "A185_MONTHLY"   -> stringResource(R.string.preventive_type_a185_monthly)
        "A185_QUARTERLY" -> stringResource(R.string.preventive_type_a185_quarterly)
        else             -> record.formType
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("$typeLabel · ${record.docNo}", style = MaterialTheme.typography.titleMedium)
                    // The period this sheet covers, named the way the register
                    // names it. Leads, because an auto-generated record has no
                    // signature date and this is what identifies it.
                    InfoLine(
                        stringResource(R.string.preventive_label_period),
                        PeriodLabel.of(
                            record.checklistDateFrom, record.checklistDateTo, record.checklistDate,
                        ),
                    )
                    // The exact span, under the readable label above — an auditor
                    // wants the dates, a technician wants "Jul–Sep 2026". Shown
                    // only when it really is a span: a monthly sheet stores
                    // from == to, and "2026-07-09 to 2026-07-09" is noise.
                    if (record.hasDateRange) {
                        InfoLine(
                            stringResource(R.string.preventive_label_dates),
                            "${record.checklistDateFrom} to ${record.checklistDateTo}",
                        )
                    }
                    // The signature date, present only once a person has signed the
                    // sheet. Auto-generated drafts leave it blank by design.
                    if (record.checklistDate.isNotBlank()) {
                        InfoLine(stringResource(R.string.preventive_label_date), record.checklistDate)
                    }
                    // Computed here rather than fetched: the detail response already
                    // carries every item, so a count is free.
                    val total = record.items.size
                    val filled = record.items.count {
                        it.status.trim().uppercase() !in setOf("", "UNSET")
                    }
                    if (total > 0) {
                        val remaining = (total - filled).coerceAtLeast(0)
                        Text(
                            if (remaining == 0) {
                                stringResource(R.string.preventive_detail_progress_done, filled, total)
                            } else {
                                stringResource(R.string.preventive_detail_progress, filled, total, remaining)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (remaining == 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                    InfoLine(stringResource(R.string.preventive_label_done_by), record.doneBy)
                    InfoLine(stringResource(R.string.preventive_label_checked_by), record.checkedBy)
                    InfoLine(stringResource(R.string.preventive_label_verified_by), record.verifiedBy)
                    if (record.remarks.isNotBlank()) InfoLine(stringResource(R.string.preventive_label_remarks), record.remarks)
                }
            }
        }

        itemsIndexed(record.items, key = { i, _ -> i }) { index, item ->
            val prev = record.items.getOrNull(index - 1)
            val newSection = prev == null || prev.section != item.section
            val newEquipment = newSection || prev?.equipment != item.equipment || prev?.srNo != item.srNo
            if (newSection) {
                Text(
                    item.section.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            if (newEquipment) {
                val name = buildString {
                    if (item.srNo != null) append("${item.srNo}. ")
                    append(item.equipment)
                    // Quarterly machines carry a range; monthly ones only a single
                    // date, and "X to X" on every row would be noise.
                    if (item.equipmentDate.isNotBlank()) {
                        append("  ·  ${item.equipmentDate}")
                        if (item.equipmentDateTo.isNotBlank() &&
                            item.equipmentDateTo != item.equipmentDate
                        ) append(" to ${item.equipmentDateTo}")
                    }
                }
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            ItemRow(item)
        }
    }
}

/**
 * One checkpoint, laid out as a form row rather than its own card, so a sheet
 * reads like the printed table instead of a stack of tiles.
 *
 * THREE states, not two. This used to be `status == "OK"` with everything else
 * rendered "NOT OK", which meant an untouched UNSET cell was reported as a
 * failure — an auto-generated draft, whose cells are UNSET until the work is
 * done, looked entirely failed.
 */
@Composable
private fun ItemRow(item: ChecklistRecordItem) {
    val status = item.status.trim().uppercase()
    val (label, tint) = when (status) {
        "OK", "PASS" ->
            stringResource(R.string.preventive_status_ok) to MaterialTheme.colorScheme.primary
        "NOT_OK", "FAIL" ->
            stringResource(R.string.preventive_status_not_ok) to MaterialTheme.colorScheme.error
        else ->
            stringResource(R.string.preventive_status_pending) to
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs, horizontal = Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.checkpoint.ifBlank { stringResource(R.string.preventive_general_check) },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (item.remarks.isNotBlank()) {
                Text(
                    item.remarks,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        // A word, not a bare colour: a red/green dot alone is unreadable for a
        // colour-blind technician and invisible in a pasted screenshot.
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(
        "$label: ${value.ifBlank { "—" }}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
    )
}
