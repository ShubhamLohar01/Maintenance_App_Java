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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.isOnSummary) {
                    SummaryView(
                        wo = wo,
                        spares = wo.spares,
                        finalNotes = uiState.finalNotes,
                        suggestions = uiState.spareSuggestions,
                        isSubmitting = uiState.isSubmitting,
                        onFinalNotesChange = viewModel::onFinalNotesChange,
                        onAddSpare = viewModel::onAddSpare,
                        onDeleteSpare = viewModel::onDeleteSpare,
                        onRefreshSuggestions = viewModel::refreshSpareSuggestions,
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
                        woId = wo.id,
                        onStatusSelect = viewModel::onStatusSelect,
                        onMeasurementChange = viewModel::onMeasurementChange,
                        onPhotoCaptured = viewModel::onPhotoCaptured,
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
    woId: String,
    onStatusSelect: (PmTaskLogStatus) -> Unit,
    onMeasurementChange: (String) -> Unit,
    onPhotoCaptured: (String?) -> Unit,
    onNotesChange: (String) -> Unit
) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(log.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (log.description.isNotBlank()) {
                Text(log.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (log.expectedResult.isNotBlank()) {
                HorizontalDivider()
                Text("Expected", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(log.expectedResult, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    PassFailNaButtons(selected = draft.status, onSelect = onStatusSelect)

    if (log.requiresMeasurement) {
        MeasurementInput(
            log = log,
            value = draft.measurementText,
            onChange = onMeasurementChange
        )
    }

    if (log.requiresPhoto) {
        PhotoCaptureBlock(
            woId = woId,
            itemId = log.id,
            currentPath = draft.photoLocalPath,
            onCaptured = onPhotoCaptured
        )
    }

    OutlinedTextField(
        value = draft.notes,
        onValueChange = onNotesChange,
        label = { Text("Notes (optional)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )
}

@Composable
private fun PassFailNaButtons(selected: PmTaskLogStatus, onSelect: (PmTaskLogStatus) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusButton("Pass", PmTaskLogStatus.PASS, FactoryTheme.colors.statusRunning, selected, onSelect, Modifier.weight(1f))
        StatusButton("Fail", PmTaskLogStatus.FAIL, FactoryTheme.colors.statusStopped, selected, onSelect, Modifier.weight(1f))
        StatusButton("N/A",  PmTaskLogStatus.NOT_APPLICABLE, FactoryTheme.colors.onSurfaceMuted, selected, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun StatusButton(
    label: String,
    target: PmTaskLogStatus,
    color: Color,
    selected: PmTaskLogStatus,
    onSelect: (PmTaskLogStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = selected == target
    Button(
        onClick = { onSelect(target) },
        modifier = modifier.height(64.dp),
        colors = if (isSelected)
            ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
        else
            ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MeasurementInput(
    log: PmWoTaskLog,
    value: String,
    onChange: (String) -> Unit
) {
    val parsed = value.toDoubleOrNull()
    val outOfRange = parsed != null
        && (log.measurementMin?.let { parsed < it } == true
            || log.measurementMax?.let { parsed > it } == true)

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = {
            val unit = log.measurementUnit?.let { " ($it)" } ?: ""
            Text("Measurement$unit")
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = outOfRange,
        modifier = Modifier.fillMaxWidth(),
        supportingText = {
            val rangeStr = when {
                log.measurementMin != null && log.measurementMax != null ->
                    "Acceptable ${log.measurementMin}–${log.measurementMax}${log.measurementUnit?.let { " $it" }.orEmpty()}"
                log.measurementMin != null -> "Min ${log.measurementMin}"
                log.measurementMax != null -> "Max ${log.measurementMax}"
                else -> "Numeric value"
            }
            if (outOfRange) {
                Text("Outside acceptable range — verify before submit. $rangeStr",
                    color = FactoryTheme.colors.statusStopped)
            } else {
                Text(rangeStr)
            }
        }
    )
}

@Composable
private fun PhotoCaptureBlock(
    woId: String,
    itemId: String,
    currentPath: String?,
    onCaptured: (String?) -> Unit
) {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        if (success && file != null && file.exists() && file.length() > 0) {
            onCaptured(file.absolutePath)
        }
        pendingFile = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = PmPhotoStorage.createItemPhotoFile(context, woId, itemId)
            pendingFile = file
            cameraLauncher.launch(PmPhotoStorage.fileToShareUri(context, file))
        }
    }

    fun launch() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            val file = PmPhotoStorage.createItemPhotoFile(context, woId, itemId)
            pendingFile = file
            cameraLauncher.launch(PmPhotoStorage.fileToShareUri(context, file))
        }
    }

    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Photo (required)", fontWeight = FontWeight.SemiBold)
            if (currentPath != null) {
                AsyncImage(
                    model = File(currentPath),
                    contentDescription = "Captured photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                )
                PrimaryButton(
                    text = "Retake",
                    onClick = { launch() },
                    style = PrimaryButtonStyle.Outlined,
                    size = PrimaryButtonSize.Min,
                    icon = Icons.Default.Camera,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                PrimaryButton(
                    text = "Capture photo",
                    onClick = { launch() },
                    style = PrimaryButtonStyle.Outlined,
                    size = PrimaryButtonSize.Min,
                    icon = Icons.Default.PhotoCamera,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryView(
    wo: com.candorfoods.factoryops.domain.model.PmWorkOrder,
    spares: List<PmWoSpare>,
    finalNotes: String,
    suggestions: List<String>,
    isSubmitting: Boolean,
    onFinalNotesChange: (String) -> Unit,
    onAddSpare: (String, Double, String, String?) -> Unit,
    onDeleteSpare: (PmWoSpare) -> Unit,
    onRefreshSuggestions: (String) -> Unit,
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
                IconButton(onClick = {
                    onRefreshSuggestions("")
                    showAddSpareSheet = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add spare")
                }
            }
            if (spares.isEmpty()) {
                Text("No spares logged yet.", color = FactoryTheme.colors.onSurfaceMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                spares.forEach { spare ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(spare.spareName, fontWeight = FontWeight.SemiBold)
                            Text("${spare.quantityUsed} ${spare.unit}", style = MaterialTheme.typography.bodySmall)
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

    PrimaryButton(
        text = "Submit for supervisor approval",
        onClick = onSubmit,
        enabled = !isSubmitting,
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
        ModalBottomSheet(onDismissRequest = { showAddSpareSheet = false }) {
            AddSpareForm(
                suggestions = suggestions,
                onRefreshSuggestions = onRefreshSuggestions,
                onConfirm = { name, qty, unit, notes ->
                    onAddSpare(name, qty, unit, notes)
                    showAddSpareSheet = false
                },
                onDismiss = { showAddSpareSheet = false }
            )
        }
    }
}

@Composable
private fun AddSpareForm(
    suggestions: List<String>,
    onRefreshSuggestions: (String) -> Unit,
    onConfirm: (String, Double, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Add spare used", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                onRefreshSuggestions(it)
            },
            label = { Text("Part name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Q3 — autocomplete suggestions ordered by usage frequency
        if (suggestions.isNotEmpty() && name != suggestions.firstOrNull()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Previously used:", style = MaterialTheme.typography.labelSmall, color = FactoryTheme.colors.onSurfaceMuted)
                suggestions.take(5).forEach { suggestion ->
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(percent = 50))
                            .clickable { name = suggestion }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(suggestion, fontSize = 13.sp)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = qty,
                onValueChange = { qty = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text("Unit") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        PrimaryButton(
            text = "Add",
            onClick = {
                val q = qty.toDoubleOrNull() ?: 0.0
                if (name.isBlank() || q <= 0.0 || unit.isBlank()) return@PrimaryButton
                onConfirm(name.trim(), q, unit.trim(), notes.takeIf { it.isNotBlank() })
            },
            enabled = name.isNotBlank() && (qty.toDoubleOrNull() ?: 0.0) > 0.0 && unit.isNotBlank(),
            style = PrimaryButtonStyle.Success,
            size = PrimaryButtonSize.Primary,
            modifier = Modifier.fillMaxWidth()
        )
        PrimaryButton(
            text = "Cancel",
            onClick = onDismiss,
            style = PrimaryButtonStyle.Outlined,
            size = PrimaryButtonSize.Min,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusPill(status: PmTaskLogStatus) {
    val (label, color) = when (status) {
        PmTaskLogStatus.PASS -> "PASS" to FactoryTheme.colors.statusRunning
        PmTaskLogStatus.FAIL -> "FAIL" to FactoryTheme.colors.statusStopped
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
