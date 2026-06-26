package com.candorfoods.factoryops.presentation.machinemaster

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.MachineCategory
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
                else -> Content(state = uiState, viewModel = viewModel)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Content(state: MachineMasterUiState, viewModel: MachineMasterViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        FilterBar(
            buildings           = state.buildings,
            locations           = state.locations,
            subCategories       = state.subCategories,
            selectedBuilding    = state.selectedBuilding,
            selectedLocation    = state.selectedLocation,
            selectedSubCategory = state.selectedSubCategory,
            onBuildingSelect    = viewModel::onBuildingSelected,
            onLocationSelect    = viewModel::onLocationSelected,
            onSubCategorySelect = viewModel::onSubCategorySelected,
        )

        if (state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No assets in the register",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.base, end = Spacing.base, bottom = Spacing.base,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            state.sections.forEach { (category, rows) ->
                val expanded = state.isExpanded(category)

                stickyHeader(key = "header-${category.name}") {
                    SectionHeader(
                        category = category,
                        count = rows.size,
                        expanded = expanded,
                        onToggle = { viewModel.onToggleSection(category) },
                    )
                }

                if (expanded) {
                    if (rows.isEmpty()) {
                        item(key = "empty-${category.name}") {
                            Text(
                                "No matches in this section",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = Spacing.md),
                            )
                        }
                    } else {
                        items(rows, key = { it.assetId }) { row ->
                            MtMachineRow(machine = row)
                        }
                    }
                }
            }
        }
    }
}

// ---- Filter bar -----------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    buildings: List<String>,
    locations: List<String>,
    subCategories: List<String>,
    selectedBuilding: String?,
    selectedLocation: String?,
    selectedSubCategory: String?,
    onBuildingSelect: (String?) -> Unit,
    onLocationSelect: (String?) -> Unit,
    onSubCategorySelect: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.base, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilterDropdown(
                modifier = Modifier.weight(1f),
                label    = "Building",
                options  = listOf<String?>(null) + buildings,
                selected = selectedBuilding,
                display  = { it ?: "All buildings" },
                onSelect = onBuildingSelect,
            )
            FilterDropdown(
                modifier = Modifier.weight(1f),
                label    = "Location",
                options  = listOf<String?>(null) + locations,
                selected = selectedLocation,
                display  = { it ?: "All locations" },
                onSelect = onLocationSelect,
            )
        }

        FilterDropdown(
            modifier = Modifier.fillMaxWidth(),
            label    = "Sub-category",
            options  = listOf<String?>(null) + subCategories,
            selected = selectedSubCategory,
            display  = { it ?: "All sub-categories" },
            onSelect = onSubCategorySelect,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> FilterDropdown(
    label: String,
    options: List<T>,
    selected: T,
    display: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = display(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(display(opt)) },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ---- Section header -------------------------------------------------------

@Composable
private fun SectionHeader(
    category: MachineCategory,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val label = when (category) {
        MachineCategory.PRODUCTION -> "Production"
        MachineCategory.OTHERS     -> "Others"
    }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "SectionChevron",
    )
    // Solid background so sticky-headers don't show list rows bleeding through.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$label  ($count)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

// ---- Row card -------------------------------------------------------------

@Composable
private fun MtMachineRow(machine: MtMachine) {
    val mutedColor = FactoryTheme.colors.onSurfaceMuted
    val locationText = listOf(machine.building, machine.subLocation)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { "—" }
    val powerText = machine.ratedKw?.let { "%.2f kW".format(it) }
        ?: machine.powerLoadRaw
        ?: "—"
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    machine.assetName,
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
