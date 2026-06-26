package com.candorfoods.factoryops.presentation.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.SyncQueueItem
import com.candorfoods.factoryops.util.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(
    onBack: () -> Unit,
    viewModel: SyncStatusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Status") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = viewModel::onRetrySync) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry sync")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SyncSummaryCard(
                    pendingCount = uiState.pendingCount,
                    isSyncing    = uiState.isSyncing,
                    onRetry      = viewModel::onRetrySync
                )
            }

            if (uiState.syncQueue.isNotEmpty()) {
                item { Text("Pending Actions", style = MaterialTheme.typography.titleMedium) }
                items(uiState.syncQueue, key = { it.id }) { item ->
                    SyncQueueCard(item = item)
                }
            }

            if (uiState.pendingBreakdowns.isNotEmpty()) {
                item { Text("Pending Breakdown Flags", style = MaterialTheme.typography.titleMedium) }
                items(uiState.pendingBreakdowns, key = { it.id }) { flag ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(flag.severity.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(flag.syncStatus.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (uiState.pendingCount == 0 && uiState.syncQueue.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("All synced", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncSummaryCard(pendingCount: Int, isSyncing: Boolean, onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(if (isSyncing) "Syncing…" else "$pendingCount pending actions",
                    style = MaterialTheme.typography.titleMedium)
                Text(if (pendingCount == 0) "Everything is up to date" else "Will sync when connected",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (pendingCount > 0 && !isSyncing) {
                Button(onClick = onRetry) { Text("Retry") }
            } else if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun SyncQueueCard(item: SyncQueueItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.actionType.name, style = MaterialTheme.typography.labelLarge)
                Text("Retry ${item.retryCount}", style = MaterialTheme.typography.bodySmall)
            }
            Text(DateTimeFormatter.formatDateTime(item.createdAt), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            item.lastErrorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
