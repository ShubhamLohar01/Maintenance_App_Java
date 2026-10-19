package com.candorfoods.factoryops.presentation.w202

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.w202.W202MeterReading
import com.candorfoods.factoryops.presentation.common.components.DownloadAction
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.presentation.utilities.formatUtilityNumber

/** History of one W-202 meter's readings, newest first. Tap a day to edit; FAB adds today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun W202ReadingHistoryScreen(
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,   // reading_date
    onBack: () -> Unit,
    viewModel: W202ReadingHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.downloadMessage) {
        state.downloadMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onDownloadMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.kind.title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    DownloadAction(
                        isDownloading = state.isDownloading,
                        enabled = state.readings.isNotEmpty(),
                        onClick = viewModel::onDownload,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add reading") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    state.error ?: "",
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                    color = MaterialTheme.colorScheme.error,
                )
                state.readings.isEmpty() -> Text(
                    "No readings yet. Tap Add to record today's ${state.kind.title.lowercase()}.",
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.readings, key = { it.readingDate }) { r ->
                        ReadingRow(
                            r,
                            onClick = { onEdit(r.readingDate) },
                            onDelete = if (state.canDelete) {
                                { viewModel.askDelete(r.readingDate) }
                            } else null,
                        )
                    }
                }
            }
        }
    }

    // Deleting a day wipes the whole sheet and can't be undone — always confirm.
    state.pendingDelete?.let { date ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete $date?") },
            text = { Text("This removes the ${state.kind.title.lowercase()} for $date. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") } },
        )
    }

    state.deleteError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearDeleteError,
            title = { Text("Could not delete") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearDeleteError) { Text("OK") } },
        )
    }
}

/** @param onDelete null for every role except HEAD — no icon, no delete path. */
@Composable
private fun ReadingRow(r: W202MeterReading, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.readingDate,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete ${r.readingDate}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric("KWH", r.kwhConsumption, 3, muted)
                Metric("KVAH", r.kvahConsumption, 3, muted)
                Metric("PF", r.powerFactor, 3, muted)
            }
            if (!r.remark.isNullOrBlank()) {
                Text(r.remark!!, style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}

@Composable
private fun Metric(
    label: String,
    value: Double?,
    decimals: Int,
    muted: androidx.compose.ui.graphics.Color,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = muted)
        Text(
            formatUtilityNumber(value, decimals).ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
