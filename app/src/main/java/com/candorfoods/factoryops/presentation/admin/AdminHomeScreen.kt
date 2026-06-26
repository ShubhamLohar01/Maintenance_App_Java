package com.candorfoods.factoryops.presentation.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(
    onSettingsClick: () -> Unit,
    viewModel: AdminHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.lastAddedName) {
        uiState.lastAddedName?.let { name ->
            snackbarHostState.showSnackbar("Added \"$name\" to Others")
            viewModel.onSnackbarShown()
        }
    }
    LaunchedEffect(uiState.lastToggleMessage) {
        uiState.lastToggleMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onToggleMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Admin", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${uiState.taggedCount} in Others · ${uiState.rows.size} machines total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            // Secondary: add a brand-new machine entry. Main flow is the per-row
            // "Move to Others" / "Restore" toggle below.
            FloatingActionButton(onClick = viewModel::onOpenAddSheet) {
                Icon(Icons.Default.Add, contentDescription = "Add new machine")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Failed to load machines", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(uiState.error!!, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = "Retry",
                        onClick = viewModel::onRefresh,
                        style = PrimaryButtonStyle.Outlined,
                    )
                }
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                        placeholder = { Text("Search machines…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                    )

                    val filtered = uiState.filteredRows
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            val label = if (uiState.searchQuery.isBlank()) {
                                "All machines (${uiState.rows.size})"
                            } else {
                                "Showing ${filtered.size} of ${uiState.rows.size}"
                            }
                            Text(label, style = MaterialTheme.typography.titleMedium)
                        }
                        items(filtered, key = { it.machine.id }) { row ->
                            AdminMachineRowCard(row = row, onToggle = { viewModel.onToggleTag(row) })
                        }
                    }
                }
            }
        }

        if (uiState.showAddSheet) {
            AddOtherSheet(
                isSaving = uiState.isSaving,
                saveError = uiState.saveError,
                onDismiss = viewModel::onCloseAddSheet,
                onSubmit = viewModel::onAdd,
            )
        }
    }
}

@Composable
private fun AdminMachineRowCard(
    row: AdminMachineRow,
    onToggle: () -> Unit,
) {
    val mutedColor = FactoryTheme.colors.onSurfaceMuted
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.machine.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${row.machine.code} — ${row.machine.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor,
                    )
                }
                if (row.isTagged) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("In Others") },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            disabledLabelColor     = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text  = if (row.isTagged) "Restore to operator list" else "Move to Others",
                onClick = onToggle,
                style = if (row.isTagged) PrimaryButtonStyle.Outlined else PrimaryButtonStyle.Primary,
                size  = PrimaryButtonSize.Min,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
