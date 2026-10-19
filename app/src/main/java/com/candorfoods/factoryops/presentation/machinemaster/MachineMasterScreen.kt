package com.candorfoods.factoryops.presentation.machinemaster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineMasterScreen(
    onBack: () -> Unit,
    onMachineClick: (String) -> Unit,
    viewModel: MachineMasterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Machine Master") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                uiState.error != null -> ErrorBlock(
                    message = uiState.error!!,
                    onRetry = viewModel::onRetry,
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                )
                else -> Content(
                    state = uiState,
                    viewModel = viewModel,
                    onMachineClick = onMachineClick,
                )
            }
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Failed to load asset register", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Spacing.base))
        PrimaryButton(text = "Retry", onClick = onRetry, style = PrimaryButtonStyle.Outlined)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    state: MachineMasterUiState,
    viewModel: MachineMasterViewModel,
    onMachineClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Warehouse capsule — scope the register to one building (or Both). Shown when 2+ exist.
        if (state.buildings.size >= 2) {
            val options = listOf<String?>(null) + state.buildings
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.base, vertical = Spacing.xs),
            ) {
                options.forEachIndexed { index, building ->
                    SegmentedButton(
                        selected = state.selectedBuilding == building,
                        onClick = { viewModel.onBuildingSelected(building) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) { Text(building ?: "Both") }
                }
            }
        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::onSearchChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.xs),
            placeholder = { Text("Search machines…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
        )

        // Floor filter — scoped to the chosen warehouse; "All" clears it.
        if (state.floors.isNotEmpty()) {
            FloorFilterDropdown(
                value = state.selectedFloor,
                options = state.floors,
                onSelect = viewModel::onFloorSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.base, vertical = Spacing.xs),
            )
        }

        if (state.categories.isNotEmpty()) {
            val selectedIndex = state.selectedCategory
                ?.let { state.categories.indexOf(it) }?.takeIf { it >= 0 }?.plus(1) ?: 0
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
                state.categories.forEach { category ->
                    Tab(
                        selected = state.selectedCategory == category,
                        onClick = { viewModel.onCategorySelected(category) },
                        text = { Text(category) },
                    )
                }
            }
        }

        val items = state.items
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No machines match your filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        } else {
            // Category-wise segregation: show a header per category only when the user
            // hasn't drilled into a single category tab.
            val grouped = state.selectedCategory == null
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.base),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                itemsIndexed(items, key = { _, it -> it.assetId }) { index, machine ->
                    val prev = items.getOrNull(index - 1)
                    if (grouped && (prev == null || !prev.category.equals(machine.category, ignoreCase = true))) {
                        val label = machine.category.ifBlank { "Uncategorised" }
                        GroupHeader(label, items.count { it.category.equals(machine.category, ignoreCase = true) })
                    }
                    MtMachineRow(machine = machine, onClick = { onMachineClick(machine.assetId) })
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
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

// ---- Row card -------------------------------------------------------------

@Composable
private fun MtMachineRow(machine: MtMachine, onClick: () -> Unit) {
    val mutedColor = FactoryTheme.colors.onSurfaceMuted
    val locationText = listOf(machine.building, machine.subLocation)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { "—" }
    val powerText = machine.ratedKw?.let { "%.2f kW".format(it) }
        ?: machine.powerLoadRaw
        ?: "—"
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    machine.assetName.ifBlank { machine.assetId },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IdChip(machine.assetId)
            }
            Text(locationText, style = MaterialTheme.typography.bodySmall, color = mutedColor)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubCategoryChip(subCategory = machine.category, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                Text(
                    text = powerText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun IdChip(id: String) {
    Text(
        text = id,
        style = MaterialTheme.typography.labelSmall,
        color = FactoryTheme.colors.onSurfaceMuted,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(percent = 50),
            )
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    )
}
