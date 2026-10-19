package com.candorfoods.factoryops.presentation.supervisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.PmTriggerType
import com.candorfoods.factoryops.domain.model.UserSummary
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: PlanEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlanEditorEvent.Saved -> onSaved()
                is PlanEditorEvent.ShowError ->
                    snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Edit plan" else "New plan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Plan details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    // The plan is named after its asset — one searchable machine field
                    // sourced from the asset register (mt_asset_list.asset_name).
                    MachineSearchPicker(
                        assets = uiState.assets,
                        selectedId = uiState.selectedAssetId,
                        onSelect = viewModel::onAssetSelected,
                        isLoading = uiState.isLoadingAssets,
                        errorMessage = uiState.assetsError,
                        onRetry = viewModel::retryLoadAssets
                    )

                    OutlinedTextField(
                        value = uiState.description,
                        onValueChange = viewModel::onDescriptionChange,
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    HorizontalDivider()
                    Text("Schedule", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

                    EnumDropdown(
                        label = "Trigger type",
                        options = PmTriggerType.values().toList(),
                        selected = uiState.triggerType,
                        onSelect = viewModel::onTriggerTypeChange,
                        labelOf = { if (it == PmTriggerType.TIME) "TIME (calendar days)" else "USAGE (running hours)" }
                    )

                    OutlinedTextField(
                        value = uiState.triggerInterval,
                        onValueChange = viewModel::onIntervalChange,
                        label = {
                            Text(
                                if (uiState.triggerType == PmTriggerType.TIME) "Interval (days)"
                                else "Interval (running hours)"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = uiState.firstDueDate,
                        onValueChange = viewModel::onFirstDueDateChange,
                        label = { Text("First due date (YYYY-MM-DD) — optional") },
                        singleLine = true,
                        isError = !uiState.firstDueDateValid,
                        supportingText = {
                            Text(
                                if (!uiState.firstDueDateValid) "Format: YYYY-MM-DD, e.g. 2026-07-20"
                                else "Blank = due ${uiState.triggerInterval.ifBlank { "…" }} days from today. " +
                                    "The technician's task appears 7 days before the due date."
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    TechnicianDropdown(
                        technicians = uiState.technicians,
                        selectedId = uiState.assignedTechnicianId,
                        onSelect = viewModel::onTechnicianSelected
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Checklist items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${uiState.items.size}", style = MaterialTheme.typography.labelMedium)
            }

            uiState.items.forEachIndexed { index, item ->
                ItemEditorCard(
                    index = index,
                    total = uiState.items.size,
                    item = item,
                    onChange = { transform -> viewModel.onItemChange(index, transform) },
                    onMoveUp = { viewModel.onMoveUp(index) },
                    onMoveDown = { viewModel.onMoveDown(index) },
                    onRemove = { viewModel.onRemoveItem(index) }
                )
            }

            PrimaryButton(
                text = "Add item",
                onClick = viewModel::onAddItem,
                style = PrimaryButtonStyle.Outlined,
                size = PrimaryButtonSize.Min,
                icon = Icons.Default.Add,
                modifier = Modifier.fillMaxWidth()
            )

            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            PrimaryButton(
                text = if (uiState.isEditMode) "Save changes" else "Create plan",
                onClick = viewModel::onSave,
                enabled = uiState.canSave && !uiState.isSaving,
                loading = uiState.isSaving,
                style = PrimaryButtonStyle.Success,
                size = PrimaryButtonSize.Primary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Type-to-search machine field, sourced from the asset register (mt_asset_list).
 *  Editable combobox: the field value is the raw search text, so typing/deleting
 *  work and the filter matches asset name OR id. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MachineSearchPicker(
    assets: List<MtMachine>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    val selectedName = remember(selectedId, assets) {
        assets.firstOrNull { it.assetId == selectedId }?.assetName ?: ""
    }
    // Seed the query with the current selection; re-seed if the selection changes.
    var query by rememberSaveable(selectedId) { mutableStateOf(selectedName) }
    var expanded by remember { mutableStateOf(false) }

    val filtered = remember(query, assets) {
        val q = query.trim()
        if (q.isEmpty()) assets
        else assets.filter {
            it.assetName.contains(q, ignoreCase = true) || it.assetId.contains(q, ignoreCase = true)
        }
    }

    // Distinguish "still loading / load failed" from a genuine "no match" so a cold
    // backend never reads as an empty asset register.
    val support: (@Composable () -> Unit)? = when {
        isLoading            -> ({ Text("Loading machines…") })
        errorMessage != null -> ({ Text("Couldn't load machines. Tap the field and hit Retry.") })
        else                 -> null
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; expanded = true },
            label = { Text("Machine") },
            placeholder = { Text("Search assets by name or id…") },
            singleLine = true,
            isError = errorMessage != null,
            supportingText = support,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                    modifier = Modifier.menuAnchor(MenuAnchorType.SecondaryEditable)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            when {
                isLoading -> DropdownMenuItem(
                    text = { Text("Loading machines…") },
                    onClick = {},
                    enabled = false
                )
                errorMessage != null -> DropdownMenuItem(
                    text = { Text("⟳  Retry loading machines") },
                    onClick = { onRetry() } // keep open; list refreshes in place
                )
                filtered.isEmpty() -> DropdownMenuItem(
                    text = { Text("No assets match \"${query.trim()}\"") },
                    onClick = {},
                    enabled = false
                )
                else -> filtered.forEach { a ->
                    DropdownMenuItem(
                        text = { Text("${a.assetName} — ${a.assetId} · ${a.building}") },
                        onClick = {
                            onSelect(a.assetId)
                            query = a.assetName
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/** Assign the plan to a TECHNICIAN-role user. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TechnicianDropdown(
    technicians: List<UserSummary>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = technicians.firstOrNull { it.id == selectedId }
    val label = selected?.name ?: selectedId?.let { "Assigned: $it" } ?: "Select a technician"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Assign to technician") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (technicians.isEmpty()) {
                DropdownMenuItem(text = { Text("No technicians found") }, onClick = {}, enabled = false)
            } else {
                technicians.forEach { t ->
                    DropdownMenuItem(
                        text = { Text("${t.name} · ${t.plantId}") },
                        onClick = { onSelect(t.id); expanded = false }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelOf: (T) -> String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ItemEditorCard(
    index: Int,
    total: Int,
    item: ItemDraft,
    onChange: ((ItemDraft) -> ItemDraft) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Item ${index + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }

            OutlinedTextField(
                value = item.title,
                onValueChange = { v -> onChange { it.copy(title = v) } },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = item.description,
                onValueChange = { v -> onChange { it.copy(description = v) } },
                label = { Text("Remarks") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }
    }
}
