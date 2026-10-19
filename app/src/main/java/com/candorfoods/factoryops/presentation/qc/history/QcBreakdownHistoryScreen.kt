package com.candorfoods.factoryops.presentation.qc.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.BreakdownRecord
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.util.pdf.PdfDownloader

/**
 * QC read-only list of submitted F.06 breakdown records. Each row expands nothing and
 * navigates nowhere — the whole record is on the card, with a per-row download that
 * renders the F.06 sheet as a PDF on the device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QcBreakdownHistoryScreen(
    onBack: () -> Unit,
    viewModel: QcBreakdownHistoryViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.pdfToShare) {
        ui.pdfToShare?.let { file ->
            snackbar.showSnackbar(PdfDownloader.downloadAndOpen(context, file))
            viewModel.onPdfShared()
        }
    }
    LaunchedEffect(ui.exportError) {
        ui.exportError?.let {
            snackbar.showSnackbar(it)
            viewModel.onExportErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Breakdown Records", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "View only",
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.error != null -> Text(
                    ui.error ?: "",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                )
                ui.records.isEmpty() -> Text(
                    "No breakdown records yet.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(ui.records, key = { it.id }) { r ->
                        BreakdownRow(
                            r,
                            isExporting = ui.isExporting,
                            onDownload = { viewModel.exportPdf(r) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(r: BreakdownRecord, isExporting: Boolean, onDownload: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        r.machineName.ifBlank { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        listOf(r.recordDate, r.location).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
                if (isExporting) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Filled.Download, contentDescription = "Download ${r.docNo}")
                    }
                }
            }
            if (r.problemInBrief.isNotBlank()) {
                Text(r.problemInBrief, style = MaterialTheme.typography.bodyMedium)
            }
            InfoLine("Type", r.typeOfMaintenance, muted)
            InfoLine("QC clearance", r.qcClearanceSign, muted)
            InfoLine("Verified by", r.verifiedBy, muted)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, muted: androidx.compose.ui.graphics.Color) {
    if (value.isBlank()) return
    Text("$label: $value", style = MaterialTheme.typography.bodySmall, color = muted)
}
