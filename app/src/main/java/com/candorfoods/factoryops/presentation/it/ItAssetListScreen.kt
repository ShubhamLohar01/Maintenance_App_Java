package com.candorfoods.factoryops.presentation.it

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.ItAssetCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.presentation.machinelist.AddMachineSheet

/**
 * One IT category's assets, with the same filter set as Machine Master (warehouse
 * capsule / search / floor) plus an Add FAB that pins the category. A row opens that
 * asset's detail screen, where it can be edited.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItAssetListScreen(
    category: ItAssetCategory,
    onAssetClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ItAssetListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(category) { viewModel.onCategory(category) }

    // Coming back from the detail screen, an edit made there should already be visible.
    // The first resume is skipped — the ViewModel loads in init, so refreshing here too
    // would fetch the register twice on entry.
    var isFirstResume by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (isFirstResume) isFirstResume = false else viewModel.onRetry()
    }

    if (uiState.showAddSheet) {
        AddMachineSheet(
            isSaving = uiState.isAddingAsset,
            error = uiState.addError,
            categoryOptions = emptyList(),          // unused: the category is locked
            subLocationsByBuilding = uiState.subLocationsByBuilding,
            onDismiss = viewModel::onAddDismiss,
            onSubmit = viewModel::onSubmitNewAsset,
            title = "Add ${uiState.category.label} asset",
            lockedCategory = uiState.category.backendValue,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.category.label, style = MaterialTheme.typography.titleLarge) },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::onAddClicked,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add asset") },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Failed to load asset register", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(uiState.error!!, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.base))
                    PrimaryButton(
                        text = "Retry",
                        onClick = viewModel::onRetry,
                        style = PrimaryButtonStyle.Outlined,
                    )
                }
                else -> Content(
                    state = uiState,
                    viewModel = viewModel,
                    onAssetClick = onAssetClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    state: ItAssetListUiState,
    viewModel: ItAssetListViewModel,
    onAssetClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Warehouse capsule — only worth showing when this category spans both.
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
            placeholder = { Text("Search assets…") },
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

        val items = state.items
        if (items.isEmpty()) {
            // No filters active means the category itself is empty; otherwise the
            // filters excluded everything.
            val noFilters = state.searchQuery.isBlank() &&
                state.selectedFloor == null && state.selectedBuilding == null
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (noFilters) "No assets in this category yet."
                    else "No assets match your filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.base),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(items, key = { it.assetId }) { asset ->
                    ItAssetRow(asset, onClick = { onAssetClick(asset.assetId) })
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
private fun ItAssetRow(asset: MtMachine, onClick: () -> Unit) {
    val mutedColor = FactoryTheme.colors.onSurfaceMuted
    val locationText = listOf(asset.building, asset.subLocation)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { "—" }
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    asset.assetName.ifBlank { asset.assetId },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IdChip(asset.assetId)
            }
            Text(locationText, style = MaterialTheme.typography.bodySmall, color = mutedColor)
            asset.condition?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = mutedColor)
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
