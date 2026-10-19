package com.candorfoods.factoryops.presentation.spareparts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.spareparts.SparePartsMachine
import com.candorfoods.factoryops.presentation.common.components.DownloadAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparePartsMachineListScreen(
    onMachineClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SparePartsMachineListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
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
                title = { Text("Spare Parts", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Downloads exactly what the filters currently show. Disabled when
                    // that is nothing — an .xlsx of bare headers reads as a failure.
                    DownloadAction(
                        isDownloading = state.isDownloading,
                        enabled = state.exportRows.isNotEmpty(),
                        onClick = viewModel::onDownload,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add spare part")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::onSearchChange,
                placeholder = { Text("Search machine or part…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.warehouse == null,
                    onClick = { viewModel.onWarehouseChange(null) },
                    label = { Text("All") },
                )
                state.warehouses.forEach { wh ->
                    FilterChip(
                        selected = state.warehouse == wh,
                        onClick = { viewModel.onWarehouseChange(wh) },
                        label = { Text(if (wh == "W202") "W-202" else "A-185") },
                    )
                }
            }

            if (state.lowStockCount > 0) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                        .clickable { viewModel.onLowStockOnlyChange(!state.lowStockOnly) },
                ) {
                    Text(
                        if (state.lowStockOnly) "Showing ${state.lowStockCount} low-stock parts — tap to clear"
                        else "⚠ ${state.lowStockCount} part${if (state.lowStockCount == 1) "" else "s"} low on stock",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(state.error!!, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                }
                state.search.isNotBlank() -> {
                    if (state.partResults.isEmpty()) {
                        EmptyMessage("No part matches “${state.search}”.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.partResults, key = { it.part.id }) { hit ->
                                PartResultCard(hit) { onMachineClick(hit.machineName) }
                            }
                        }
                    }
                }
                state.visibleMachines.isEmpty() -> EmptyMessage("No spare parts on record.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.visibleMachines, key = { it.key }) { machine ->
                        MachineCard(machine) { onMachineClick(machine.displayName) }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        SparePartFormSheet(
            editing = null,
            onDismiss = { showAddSheet = false },
            onSaved = { showAddSheet = false; viewModel.load() },
        )
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun MachineCard(machine: SparePartsMachine, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(machine.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(machine.assetId, machine.warehouse).joinToString(" · ")
                    .ifBlank { "Not linked to an asset" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${machine.parts.size} part${if (machine.parts.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                if (machine.lowStockCount > 0) {
                    Text(
                        "⚠ ${machine.lowStockCount} low",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PartResultCard(hit: SparePartSearchResult, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(hit.part.partName, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(hit.machineName, hit.warehouse).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Text(
                "${hit.part.quantity}${hit.part.unit?.let { " $it" }.orEmpty()}",
                style = MaterialTheme.typography.titleMedium,
                color = if (hit.part.lowStock) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
