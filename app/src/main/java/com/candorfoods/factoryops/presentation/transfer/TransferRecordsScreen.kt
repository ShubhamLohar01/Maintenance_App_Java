package com.candorfoods.factoryops.presentation.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.R
import com.candorfoods.factoryops.domain.model.MachineTransfer
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme

/**
 * Transfer landing page: a "Create Transfer" button on top, then the records list from
 * mt_machine_transfer. Each row shows a PENDING/APPROVED status; rows the current user
 * may confirm (backend `canAcknowledge`) show an Acknowledge button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferRecordsScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    viewModel: TransferRecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transfer_rec_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.transfer_rec_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.transfer_rec_refresh))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.transfer_rec_create), fontWeight = FontWeight.Bold)
            }

            Text(
                stringResource(R.string.transfer_rec_records),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.records.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.transfer_rec_empty),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.records, key = { it.id }) { rec ->
                        TransferRecordCard(
                            record = rec,
                            acknowledging = state.acknowledgingId == rec.id,
                            deleting = state.busyId == rec.id,
                            onAcknowledge = { viewModel.acknowledge(rec.id) },
                            onEdit = { viewModel.startEdit(rec) },
                            onDelete = { viewModel.requestDelete(rec.id) },
                        )
                    }
                }
            }
        }
    }

    state.deleteConfirmId?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.transfer_rec_delete_title)) },
            text = { Text(stringResource(R.string.transfer_rec_delete_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.transfer_rec_delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.transfer_rec_cancel)) } },
        )
    }

    state.edit?.let { draft ->
        TransferEditSheet(
            draft = draft,
            saving = state.savingEdit,
            onChange = viewModel::updateEdit,
            onSwap = viewModel::swapEditWarehouses,
            onSave = viewModel::saveEdit,
            onDismiss = viewModel::cancelEdit,
        )
    }
}

@Composable
private fun TransferRecordCard(
    record: MachineTransfer,
    acknowledging: Boolean,
    deleting: Boolean,
    onAcknowledge: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    record.machineName.ifBlank { stringResource(R.string.transfer_rec_machine_fallback) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(record.status)
            }
            Text(
                "${record.fromWarehouse} → ${record.toWarehouse}" +
                    (record.date?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            record.condition?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.transfer_rec_condition_value, it), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }

            if (record.status == "APPROVED") {
                Text(
                    record.acknowledgedBy?.let { stringResource(R.string.transfer_rec_acknowledged_by, it) }
                        ?: stringResource(R.string.transfer_rec_acknowledged),
                    style = MaterialTheme.typography.bodySmall,
                    color = FactoryTheme.colors.statusRunning,
                    fontWeight = FontWeight.SemiBold,
                )
            } else if (record.canAcknowledge) {
                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = onAcknowledge,
                    enabled = !acknowledging,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (acknowledging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.transfer_rec_acknowledge), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    stringResource(R.string.transfer_rec_waiting_ack, record.toWarehouse),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            // The creator can edit/delete while the transfer is still PENDING.
            if (record.canEdit) {
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(onClick = onEdit, enabled = !deleting, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.transfer_rec_edit))
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = !deleting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        if (deleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.transfer_rec_delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val approved = status == "APPROVED"
    val color = if (approved) FactoryTheme.colors.statusRunning else FactoryTheme.colors.statusFlagged
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(percent = 50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            if (approved) stringResource(R.string.transfer_rec_status_approved) else stringResource(R.string.transfer_rec_status_pending),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferEditSheet(
    draft: TransferEditDraft,
    saving: Boolean,
    onChange: ((TransferEditDraft) -> TransferEditDraft) -> Unit,
    onSwap: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.transfer_rec_edit_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WarehouseBox(stringResource(R.string.transfer_rec_from), draft.fromWarehouse, Modifier.weight(1f))
                IconButton(onClick = onSwap) { Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.transfer_rec_swap)) }
                WarehouseBox(stringResource(R.string.transfer_rec_to), draft.toWarehouse, Modifier.weight(1f))
            }
            EditField(stringResource(R.string.transfer_rec_machine_name), draft.machineName) { v -> onChange { it.copy(machineName = v) } }
            EditField(stringResource(R.string.transfer_rec_date), draft.date) { v -> onChange { it.copy(date = v) } }
            EditField(stringResource(R.string.transfer_rec_condition_label), draft.condition) { v -> onChange { it.copy(condition = v) } }
            EditField(stringResource(R.string.transfer_rec_model_sr), draft.machineCode) { v -> onChange { it.copy(machineCode = v) } }
            EditField(stringResource(R.string.transfer_rec_reason), draft.reason) { v -> onChange { it.copy(reason = v) } }
            EditField(stringResource(R.string.transfer_rec_authorised_by), draft.authorisedPerson) { v -> onChange { it.copy(authorisedPerson = v) } }
            EditField(stringResource(R.string.transfer_rec_remarks), draft.remarks) { v -> onChange { it.copy(remarks = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.transfer_rec_cancel)) }
                Button(onClick = onSave, enabled = !saving, modifier = Modifier.weight(1f)) {
                    if (saving) CircularProgressIndicator(
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    ) else Text(stringResource(R.string.transfer_rec_save), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EditField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WarehouseBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
