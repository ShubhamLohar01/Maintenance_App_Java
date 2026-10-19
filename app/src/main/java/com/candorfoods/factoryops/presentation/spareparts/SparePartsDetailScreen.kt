package com.candorfoods.factoryops.presentation.spareparts

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.spareparts.SparePart
import com.candorfoods.factoryops.domain.model.spareparts.WorkType
import com.candorfoods.factoryops.presentation.common.components.ConfirmDialog
import com.candorfoods.factoryops.presentation.spareparts.components.WorkTypeChips

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparePartsDetailScreen(
    onBack: () -> Unit,
    onOpenHistory: (Int) -> Unit,
    viewModel: SparePartsDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var usePart by remember { mutableStateOf<SparePart?>(null) }
    var restockPart by remember { mutableStateOf<SparePart?>(null) }
    var editPart by remember { mutableStateOf<SparePart?>(null) }
    var deletePart by remember { mutableStateOf<SparePart?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is SparePartsDetailEvent.ActionSaved -> event.message
                is SparePartsDetailEvent.Error -> event.message
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.machineName, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (state.phase) {
            SparePartsDetailPhase.Loading -> Box(
                Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            SparePartsDetailPhase.Error -> Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.error.orEmpty(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            SparePartsDetailPhase.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { MachineHeaderCard(state) }

                if (state.parts.isEmpty()) {
                    item {
                        Text(
                            "No parts recorded for this machine yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                items(state.parts, key = { it.id }) { part ->
                    PartCard(
                        part = part,
                        canDelete = state.canDelete,
                        busy = state.busyPartId == part.id,
                        onUse = { usePart = part },
                        onRestock = { restockPart = part },
                        onHistory = { onOpenHistory(part.id) },
                        onEdit = { editPart = part },
                        onDelete = { deletePart = part },
                    )
                }
            }
        }
    }

    usePart?.let { part ->
        UsePartSheet(
            part = part,
            onDismiss = { usePart = null },
            onConfirm = { qty, workType, note ->
                usePart = null
                viewModel.use(part.id, qty, workType, note)
            },
        )
    }

    restockPart?.let { part ->
        RestockPartSheet(
            part = part,
            onDismiss = { restockPart = null },
            onConfirm = { qty, note ->
                restockPart = null
                viewModel.restock(part.id, qty, note)
            },
        )
    }

    editPart?.let { part ->
        SparePartFormSheet(
            editing = part,
            machineName = state.machineName,
            assetId = state.assetId,
            warehouse = state.warehouse,
            onDismiss = { editPart = null },
            onSaved = { editPart = null; viewModel.load() },
        )
    }

    deletePart?.let { part ->
        ConfirmDialog(
            title = "Delete ${part.partName}?",
            message = "The part stops showing in the list. Its usage history is kept.",
            confirmLabel = "Delete",
            onConfirm = { deletePart = null; viewModel.delete(part.id) },
            onDismiss = { deletePart = null },
        )
    }
}

@Composable
private fun MachineHeaderCard(state: SparePartsDetailUiState) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state.machineName, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(state.assetId, state.warehouse).joinToString(" · ")
                    .ifBlank { "Not linked to an asset" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PartCard(
    part: SparePart,
    canDelete: Boolean,
    busy: Boolean,
    onUse: () -> Unit,
    onRestock: () -> Unit,
    onHistory: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(part.partName, style = MaterialTheme.typography.titleMedium)
                Text(
                    part.unit.orEmpty().ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                if (part.lowStock) {
                    Text(
                        "Low stock (min ${part.minQuantity})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                part.quantity.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = if (part.lowStock) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = !busy) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Actions for ${part.partName}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Use") },
                        onClick = { menuOpen = false; onUse() })
                    DropdownMenuItem(text = { Text("Restock") },
                        onClick = { menuOpen = false; onRestock() })
                    DropdownMenuItem(text = { Text("History") },
                        onClick = { menuOpen = false; onHistory() })
                    DropdownMenuItem(text = { Text("Edit") },
                        onClick = { menuOpen = false; onEdit() })
                    // Hidden rather than disabled for a technician: the backend
                    // enforces it anyway, and a greyed-out Delete only invites taps.
                    if (canDelete) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Use sheet. The confirm button stays disabled until a work type is chosen —
 * nothing reaches mt_spareparts_log without a reason attached.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsePartSheet(
    part: SparePart,
    onDismiss: () -> Unit,
    onConfirm: (Int, WorkType, String?) -> Unit,
) {
    var quantity by remember { mutableIntStateOf(1) }
    var workType by remember { mutableStateOf<WorkType?>(null) }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Use — ${part.partName}", style = MaterialTheme.typography.titleLarge)
            Text(
                "On hand: ${part.quantity}${part.unit?.let { " $it" }.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconButton(onClick = { if (quantity > 1) quantity-- }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                }
                Text(quantity.toString(), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = { if (quantity < part.quantity) quantity++ }) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                }
            }

            Text("Used for *", style = MaterialTheme.typography.labelLarge)
            WorkTypeChips(selected = workType, onSelect = { workType = it })

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { onConfirm(quantity, workType!!, note.ifBlank { null }) },
                    enabled = workType != null && quantity in 1..part.quantity,
                ) { Text("Log Usage") }
            }
        }
    }
}

/** Restock sheet — the Use sheet minus the work-type row; /restock always logs 'Restock'. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestockPartSheet(
    part: SparePart,
    onDismiss: () -> Unit,
    onConfirm: (Int, String?) -> Unit,
) {
    var quantity by remember { mutableIntStateOf(1) }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Restock — ${part.partName}", style = MaterialTheme.typography.titleLarge)
            Text(
                "On hand: ${part.quantity}${part.unit?.let { " $it" }.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconButton(onClick = { if (quantity > 1) quantity-- }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                }
                Text(quantity.toString(), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = { quantity++ }) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { onConfirm(quantity, note.ifBlank { null }) },
                    enabled = quantity > 0,
                ) { Text("Add Stock") }
            }
        }
    }
}
