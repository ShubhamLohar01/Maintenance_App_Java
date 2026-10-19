package com.candorfoods.factoryops.presentation.machinelist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.isShutDown
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.SyncIndicator
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.Spacing

private val UnderQcOrange = Color(0xFFEF6C00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineListScreen(
    onMachineClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onBack: (() -> Unit)? = null,
    onSyncClick: (() -> Unit)? = null,
    onMachineMasterClick: (() -> Unit)? = null,
    viewModel: MachineListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    if (uiState.showAddSheet) {
        AddMachineSheet(
            isSaving = uiState.isAddingMachine,
            error = uiState.addError,
            categoryOptions = uiState.categories,
            subLocationsByBuilding = uiState.subLocationsByBuilding,
            onDismiss = viewModel::onAddDismiss,
            onSubmit = viewModel::onSubmitNewMachine,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Machines", style = MaterialTheme.typography.titleLarge)
                        uiState.currentUser?.let {
                            Text(
                                "${it.name} — ${it.plantId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (onSyncClick != null) {
                        SyncIndicator(
                            pendingCount = uiState.pendingSyncCount,
                            isSyncing = uiState.isSyncing,
                            onTap = onSyncClick,
                        )
                    }
                    IconButton(onClick = viewModel::onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter by status")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MachineStatusFilter.entries.forEach { filter ->
                                DropdownMenuItem(
                                    text = { Text(filter.label) },
                                    leadingIcon = {
                                        if (uiState.statusFilter == filter) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        } else {
                                            Spacer(Modifier.width(24.dp))
                                        }
                                    },
                                    onClick = {
                                        viewModel.onStatusFilterChange(filter)
                                        menuOpen = false
                                    },
                                )
                            }
                            if (onMachineMasterClick != null) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Machine Master") },
                                    onClick = { menuOpen = false; onMachineMasterClick() },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.canAddMachine) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::onAddMachineClicked,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add machine", fontWeight = FontWeight.SemiBold)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Failed to load machines", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(uiState.error!!, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.base))
                    OutlinedButton(onClick = viewModel::onRefresh) { Text("Retry") }
                }
                else -> Column(Modifier.fillMaxSize()) {
                    // Warehouse switch — operators move between A-185 / W-202, so any role
                    // can scope the list to one warehouse (or Both). Shown when 2+ exist.
                    if (uiState.buildings.size >= 2) {
                        val options = listOf<String?>(null) + uiState.buildings
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.base, vertical = Spacing.xs),
                        ) {
                            options.forEachIndexed { index, building ->
                                SegmentedButton(
                                    selected = uiState.selectedBuilding == building,
                                    onClick = { viewModel.onBuildingSelected(building) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                ) { Text(building ?: "Both") }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchChange,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.xs),
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

                    // Floor filter — scoped to the chosen warehouse; "All" clears it.
                    if (uiState.floors.isNotEmpty()) {
                        FloorFilterDropdown(
                            value = uiState.selectedFloor,
                            options = uiState.floors,
                            onSelect = viewModel::onFloorSelected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.base, vertical = Spacing.xs),
                        )
                    }

                    if (uiState.categories.isNotEmpty()) {
                        val selectedIndex = uiState.selectedCategory
                            ?.let { uiState.categories.indexOf(it) }?.takeIf { it >= 0 }?.plus(1) ?: 0
                        ScrollableTabRow(
                            selectedTabIndex = selectedIndex,
                            edgePadding = Spacing.base,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Tab(
                                selected = selectedIndex == 0,
                                onClick = { viewModel.onCategorySelected(null) },
                                text = { Text("All") },
                            )
                            uiState.categories.forEach { category ->
                                Tab(
                                    selected = uiState.selectedCategory == category,
                                    onClick = { viewModel.onCategorySelected(category) },
                                    text = { Text(category) },
                                )
                            }
                        }
                    }

                    val items = uiState.items
                    if (items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No machines match your filters.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    } else {
                        val grouped = uiState.statusFilter == MachineStatusFilter.ALL
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Spacing.base),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            itemsIndexed(items, key = { _, it -> it.asset.assetId }) { index, item ->
                                val prev = items.getOrNull(index - 1)
                                if (grouped && (prev == null || prev.status != item.status)) {
                                    GroupHeader(item.status.label, items.count { it.status == item.status })
                                }
                                MachineRow(item, onClick = { onMachineClick(item.asset.assetId) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Read-only floor filter; the first entry "All floors" clears the filter (null). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloorFilterDropdown(
    value: String?,
    options: List<String>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value ?: "All floors",
            onValueChange = {},
            readOnly = true,
            label = { Text("Floor") },
            singleLine = true,
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All floors") }, onClick = { onSelect(null); expanded = false })
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

@Composable
private fun GroupHeader(label: String, count: Int) {
    Text(
        "${label.uppercase()} ($count)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = statusColor(label),
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
private fun MachineRow(item: MachineListItem, onClick: () -> Unit) {
    val muted = FactoryTheme.colors.onSurfaceMuted
    val shutDown = item.asset.isShutDown()
    val nameAlpha = if (shutDown) 0.5f else 1f
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.asset.assetName.ifBlank { item.asset.assetId },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = nameAlpha),
                    modifier = Modifier.weight(1f),
                )
                if (shutDown) ShutDownBadge() else StatusBadge(item.status)
            }
            if (item.status == MachineLiveStatus.RUNNING) {
                val by = item.operatorName?.let { "by $it" }
                val dur = item.startedAtMs?.let { "Running ${formatDuration(System.currentTimeMillis() - it)}" }
                val line = listOfNotNull(by, dur).joinToString(" · ")
                if (line.isNotBlank()) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = FactoryTheme.colors.statusRunning)
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                listOfNotNull(item.asset.assetId, item.asset.building.ifBlank { null }, item.asset.subLocation.ifBlank { null })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }
}

@Composable
private fun ShutDownBadge() {
    Text(
        "● SHUT DOWN",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = FactoryTheme.colors.statusStopped,
    )
}

@Composable
private fun StatusBadge(status: MachineLiveStatus) {
    Text(
        "● ${status.label.uppercase()}",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = statusColor(status.label),
    )
}

@Composable
private fun statusColor(label: String): Color = when (label.lowercase()) {
    "running"   -> FactoryTheme.colors.statusRunning
    "breakdown" -> FactoryTheme.colors.statusStopped
    "under qc"  -> UnderQcOrange
    else        -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
}

private fun formatDuration(ms: Long): String {
    val mins = ms.coerceAtLeast(0L) / 60_000L
    if (mins < 1) return "just now"
    val h = mins / 60; val m = mins % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
