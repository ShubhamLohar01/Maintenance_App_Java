package com.candorfoods.factoryops.presentation.technician

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.candorfoods.factoryops.domain.model.PmTaskLogStatus
import com.candorfoods.factoryops.domain.model.PmWoSpare
import com.candorfoods.factoryops.domain.model.spareparts.MAX_WO_SPARES
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPick
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPickerCatalog
import com.candorfoods.factoryops.presentation.spareparts.components.SparePartPickerSheet
import com.candorfoods.factoryops.domain.model.PmWoTaskLog
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.util.PmPhotoStorage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianPmExecuteScreen(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    viewModel: TechnicianPmExecuteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TechnicianPmExecuteEvent.Submitted -> onSubmitted()
                is TechnicianPmExecuteEvent.DraftSaved -> onBack()
                is TechnicianPmExecuteEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val total = uiState.totalItems
                    val title = if (uiState.isOnSummary) "Summary" else "Item ${uiState.currentIndex + 1} of $total"
                    Text(title)
                },
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
        val wo = uiState.workOrder ?: run {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(uiState.error ?: "Work order not found.")
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ProgressBar(uiState.currentIndex, uiState.totalItems)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.isOnSummary) {
                    SummaryView(
                        wo = wo,
                        spares = wo.spares,
                        finalNotes = uiState.finalNotes,
                        overallResult = uiState.overallResult,
                        sparePicker = uiState.sparePicker,
                        isLoadingPicker = uiState.isLoadingPicker,
                        canAddSpare = uiState.canAddSpare,
                        isSubmitting = uiState.isSubmitting,
                        onFinalNotesChange = viewModel::onFinalNotesChange,
                        onOverallResultChange = viewModel::onOverallResultChange,
                        onAddSpare = viewModel::onAddSpare,
                        onDeleteSpare = viewModel::onDeleteSpare,
                        onOpenSparePicker = viewModel::refreshSparePicker,
                        onJumpToItem = viewModel::onJumpToItem,
                        onSubmit = viewModel::onSubmit,
                        onSaveDraft = viewModel::onSaveDraft
                    )
                } else {
                    val log = uiState.currentLog ?: return@Column
                    val draft = uiState.draft ?: return@Column
                    ItemExecutionView(
                        log = log,
                        draft = draft,
                        onIsGoodChange = viewModel::onIsGoodChange,
                        onNotesChange = viewModel::onNotesChange
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.currentIndex > 0) {
                            PrimaryButton(
                                text = "Back",
                                onClick = viewModel::onBack,
                                style = PrimaryButtonStyle.Outlined,
                                size = PrimaryButtonSize.Min,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        PrimaryButton(
                            text = if (uiState.currentIndex == uiState.totalItems - 1) "Finish items"
                                   else "Next",
                            onClick = viewModel::onNext,
                            enabled = !uiState.isSubmitting,
                            loading = uiState.isSubmitting,
                            style = PrimaryButtonStyle.Primary,
                            size = PrimaryButtonSize.Primary,
                            modifier = Modifier.weight(2f)
                        )
                    }

                    PrimaryButton(
                        text = "Save draft & exit",
                        onClick = viewModel::onSaveDraft,
                        style = PrimaryButtonStyle.Outlined,
                        size = PrimaryButtonSize.Min,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ProgressBar(currentIndex: Int, total: Int) {
    val displayed = (currentIndex.coerceAtMost(total)).toFloat()
    val target = total.coerceAtLeast(1).toFloat()
    LinearProgressIndicator(
        progress = { displayed / target },
        modifier = Modifier.fillMaxWidth().height(4.dp)
    )
}

@Composable
private fun ItemExecutionView(
    log: PmWoTaskLog,
    draft: ItemExecutionDraft,
    onIsGoodChange: (Boolean) -> Unit,
    onNotesChange: (String) -> Unit
) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(log.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (log.description.isNotBlank()) {
                Text(log.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    // "Is good?" — ticked = good (remark optional); un-ticked = not good (remark REQUIRED).
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onIsGoodChange(!draft.isGood) }
                .padding(vertical = 4.dp)
        ) {
            Checkbox(checked = draft.isGood, onCheckedChange = onIsGoodChange)
            Spacer(Modifier.width(8.dp))
            Text("Is good?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }

    val remarksRequired = !draft.isGood
    OutlinedTextField(
        value = draft.notes,
        onValueChange = onNotesChange,
        label = { Text(if (remarksRequired) "Remarks (required)" else "Remarks (optional)") },
        isError = remarksRequired && draft.notes.isBlank(),
        supportingText = {
            if (remarksRequired) {
                Text(
                    "Not marked good — add a remark describing the issue before continuing.",
                    color = FactoryTheme.colors.statusStopped
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )
}

/** Machine-level Pass/Fail toggle used once on the summary screen. */
@Composable
private fun OverallResultButton(
    label: String,
    target: String,
    color: Color,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = selected == target
    Button(
        onClick = { onSelect(target) },
        modifier = modifier.height(56.dp),
        colors = if (isSelected)
            ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
        else
            ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryView(
    wo: com.candorfoods.factoryops.domain.model.PmWorkOrder,
    spares: List<PmWoSpare>,
    finalNotes: String,
    overallResult: String?,
    sparePicker: SparePartPickerCatalog,
    isLoadingPicker: Boolean,
    canAddSpare: Boolean,
    isSubmitting: Boolean,
    onFinalNotesChange: (String) -> Unit,
    onOverallResultChange: (String) -> Unit,
    onAddSpare: (SparePartPick, Double, String?) -> Unit,
    onDeleteSpare: (PmWoSpare) -> Unit,
    onOpenSparePicker: () -> Unit,
    onJumpToItem: (Int) -> Unit,
    onSubmit: () -> Unit,
    onSaveDraft: () -> Unit
) {
    var showAddSpareSheet by remember { mutableStateOf(false) }

    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            wo.taskLogs.forEachIndexed { idx, log ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onJumpToItem(idx) }
                        .padding(vertical = 4.dp)
                ) {
                    Text("${idx + 1}.", modifier = Modifier.width(28.dp), fontWeight = FontWeight.Bold)
                    Text(log.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    StatusPill(log.status)
                }
            }
        }
    }

    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Spares used", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(
                    enabled = canAddSpare,
                    onClick = {
                        onOpenSparePicker()
                        showAddSpareSheet = true
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add spare")
                }
            }
            if (!canAddSpare) {
                Text(
                    "Limit of $MAX_WO_SPARES spare parts reached.",
                    color = FactoryTheme.colors.onSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (spares.isEmpty()) {
                Text("No spares logged yet.", color = FactoryTheme.colors.onSurfaceMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                spares.forEach { spare ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(spare.spareName, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    "${spare.quantityUsed} ${spare.unit}".trim(),
                                    spare.machineName?.takeIf { it.isNotBlank() },
                                ).joinToString("  -  "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { onDeleteSpare(spare) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }
        }
    }

    OutlinedTextField(
        value = finalNotes,
        onValueChange = onFinalNotesChange,
        label = { Text("Final notes (optional)") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )

    // Machine-level verdict — asked once, here at the end (not per item).
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Machine result", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Overall verdict for this machine after the PM.",
                style = MaterialTheme.typography.bodySmall,
                color = FactoryTheme.colors.onSurfaceMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverallResultButton("Pass", "PASS", FactoryTheme.colors.statusRunning, overallResult, onOverallResultChange, Modifier.weight(1f))
                OverallResultButton("Fail", "FAIL", FactoryTheme.colors.statusStopped, overallResult, onOverallResultChange, Modifier.weight(1f))
            }
        }
    }

    PrimaryButton(
        text = "Submit for supervisor approval",
        onClick = onSubmit,
        enabled = !isSubmitting && overallResult != null,
        loading = isSubmitting,
        style = PrimaryButtonStyle.Success,
        size = PrimaryButtonSize.Hero,
        modifier = Modifier.fillMaxWidth()
    )
    PrimaryButton(
        text = "Save draft & exit",
        onClick = onSaveDraft,
        style = PrimaryButtonStyle.Outlined,
        size = PrimaryButtonSize.Min,
        modifier = Modifier.fillMaxWidth()
    )

    if (showAddSpareSheet) {
        SparePartPickerSheet(
            catalog = sparePicker,
            isLoading = isLoadingPicker,
            onConfirm = { pick, qty, notes ->
                onAddSpare(pick, qty, notes)
                showAddSpareSheet = false
            },
            onDismiss = { showAddSpareSheet = false },
        )
    }
}

@Composable
private fun StatusPill(status: PmTaskLogStatus) {
    val (label, color) = when (status) {
        PmTaskLogStatus.PASS -> "GOOD" to FactoryTheme.colors.statusRunning
        PmTaskLogStatus.FAIL -> "NOT GOOD" to FactoryTheme.colors.statusStopped
        PmTaskLogStatus.NOT_APPLICABLE -> "N/A" to FactoryTheme.colors.onSurfaceMuted
        PmTaskLogStatus.PENDING -> "PENDING" to FactoryTheme.colors.statusFlagged
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
