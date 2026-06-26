package com.candorfoods.factoryops.presentation.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    viewModel: ChecklistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.submittedId) {
        uiState.submittedId?.let { id ->
            snackbarHostState.showSnackbar("Submitted (id #$id). Thank you.")
            viewModel.onSubmittedAcknowledged()
            onSubmitted()
        }
    }
    LaunchedEffect(uiState.submitError) {
        uiState.submitError?.let { snackbarHostState.showSnackbar("Submit failed — $it") }
    }
    LaunchedEffect(uiState.draftSavedTick) {
        if (uiState.draftSavedTick > 0) {
            snackbarHostState.showSnackbar("Draft saved. You can keep filling and submit later.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Checklist Form", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Doc ${uiState.type.docNo} · ${uiState.filledRows}/${uiState.totalRows} filled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { TypeToggle(uiState.type, viewModel::onTypeChange) }
            item { HeaderCard(uiState, viewModel) }

            itemsIndexed(uiState.rows, key = { i, _ -> "${uiState.type}-$i" }) { index, row ->
                val prev = uiState.rows.getOrNull(index - 1)
                val newSection = prev == null || prev.section != row.section
                val newEquipment = newSection || prev?.equipment != row.equipment || prev?.srNo != row.srNo

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (newSection) SectionPill(row.section)
                    if (newEquipment) {
                        EquipmentHeader(
                            row = row,
                            onDateChange = { viewModel.onEquipmentDateChange(row.equipmentKey, it) },
                        )
                    }
                    CheckpointRow(
                        row       = row,
                        onChecked = { viewModel.onRowCheckedChange(index, it) },
                        onRemarks = { viewModel.onRowRemarksChange(index, it) },
                    )
                }
            }

            item { FooterCard(uiState, viewModel) }

            item {
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick  = viewModel::onSaveDraft,
                    enabled  = uiState.canSaveDraft,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.isSubmitting) "Saving…" else "Save draft")
                }
                Spacer(Modifier.height(Spacing.sm))
                if (!uiState.canSubmit && !uiState.isSubmitting) {
                    Text(
                        submitHint(uiState),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
                PrimaryButton(
                    text     = if (uiState.isSubmitting) "Submitting…" else "Submit checklist",
                    onClick  = viewModel::onSubmit,
                    enabled  = uiState.canSubmit,
                    loading  = uiState.isSubmitting,
                    style    = PrimaryButtonStyle.Success,
                    size     = PrimaryButtonSize.Hero,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TypeToggle(selected: ChecklistType, onSelect: (ChecklistType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        ChecklistType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(type.label) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeaderCard(state: ChecklistUiState, vm: ChecklistViewModel) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                "Set a date on each equipment below; tick or remark every checkpoint.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                "${state.filledRows} / ${state.totalRows} checkpoints · ${state.datedEquipment} / ${state.totalEquipment} equipment dated",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.allFilled && state.allEquipmentDated) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun EquipmentHeader(row: ChecklistRow, onDateChange: (String) -> Unit) {
    Text(
        buildString {
            if (row.srNo != null) append("${row.srNo}. ")
            append(row.equipment)
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    OutlinedTextField(
        value = row.equipmentDate,
        onValueChange = onDateChange,
        label = { Text("Date (YYYY-MM-DD) *") },
        singleLine = true,
        isError = row.equipmentDate.isNotBlank() && !DATE_OK.matches(row.equipmentDate),
        supportingText = {
            if (row.equipmentDate.isNotBlank() && !DATE_OK.matches(row.equipmentDate)) {
                Text("Format: YYYY-MM-DD, e.g. 2026-06-22")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionPill(section: String) {
    Text(
        section.uppercase(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
private fun CheckpointRow(
    row: ChecklistRow,
    onChecked: (Boolean) -> Unit,
    onRemarks: (String) -> Unit,
) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = row.checked, onCheckedChange = onChecked)
            Column(
                modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (row.checkpoint.isNotBlank()) {
                    Text(row.checkpoint, style = MaterialTheme.typography.bodyLarge)
                }
                StatusLabel(row)
                OutlinedTextField(
                    value = row.remarks,
                    onValueChange = onRemarks,
                    label = { Text("Remarks (if not OK)") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatusLabel(row: ChecklistRow) {
    val (text, color) = when {
        row.checked                 -> "OK" to MaterialTheme.colorScheme.primary
        row.remarks.isNotBlank()    -> "NOT OK" to MaterialTheme.colorScheme.error
        else                        -> "Tick if OK, or add a remark" to
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
private fun FooterCard(state: ChecklistUiState, vm: ChecklistViewModel) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = state.doneBy, onValueChange = vm::onDoneByChange,
                label = { Text("Done By *") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.checkedBy, onValueChange = vm::onCheckedByChange,
                label = { Text("Checked By *") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.verifiedBy, onValueChange = vm::onVerifiedByChange,
                label = { Text("Verified By *") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.remarks, onValueChange = vm::onRemarksChange,
                label = { Text("Remarks (If Any)") }, singleLine = false, maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun submitHint(state: ChecklistUiState): String {
    val missing = state.totalRows - state.filledRows
    val undated = state.totalEquipment - state.datedEquipment
    return when {
        missing > 0 -> "$missing checkpoint(s) still need a tick or a remark."
        undated > 0 -> "$undated equipment still need a valid date."
        state.doneBy.isBlank() || state.checkedBy.isBlank() || state.verifiedBy.isBlank() ->
            "Fill Done By, Checked By and Verified By to submit."
        else -> "Complete all fields to submit."
    }
}

private val DATE_OK = Regex("""^\d{4}-\d{2}-\d{2}$""")
