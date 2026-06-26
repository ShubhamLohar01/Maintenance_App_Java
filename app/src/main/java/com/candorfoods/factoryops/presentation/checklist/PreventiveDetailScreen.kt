package com.candorfoods.factoryops.presentation.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.platform.LocalContext
import com.candorfoods.factoryops.util.pdf.PdfShareLauncher
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.ChecklistRecordDetail
import com.candorfoods.factoryops.domain.model.ChecklistRecordItem
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
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
            if (!PdfShareLauncher.share(context, file)) {
                snackbarHostState.showSnackbar("No app available to open the PDF")
            }
            viewModel.onPdfShared()
        }
    }
    LaunchedEffect(uiState.exportError) {
        uiState.exportError?.let {
            snackbarHostState.showSnackbar("Couldn't create PDF — $it")
            viewModel.onExportErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checklist Record") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            Icon(Icons.Default.Download, contentDescription = "Download PDF")
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
                    "Couldn't load — ${uiState.error}",
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
        "MONTHLY"   -> "Monthly (50a)"
        "QUARTERLY" -> "Quarterly (50b)"
        else        -> record.formType
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
                    InfoLine("Date", record.checklistDate)
                    InfoLine("Done by", record.doneBy)
                    InfoLine("Checked by", record.checkedBy)
                    InfoLine("Verified by", record.verifiedBy)
                    if (record.remarks.isNotBlank()) InfoLine("Remarks", record.remarks)
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
                    if (item.equipmentDate.isNotBlank()) append("  ·  ${item.equipmentDate}")
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

@Composable
private fun ItemRow(item: ChecklistRecordItem) {
    val ok = item.status.uppercase() == "OK"
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.checkpoint.ifBlank { "General check" },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (item.remarks.isNotBlank()) {
                    Text(
                        item.remarks,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            Text(
                if (ok) "OK" else "NOT OK",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(
        "$label: ${value.ifBlank { "—" }}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
    )
}
