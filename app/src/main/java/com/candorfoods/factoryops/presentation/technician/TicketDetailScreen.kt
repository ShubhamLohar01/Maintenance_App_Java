package com.candorfoods.factoryops.presentation.technician

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.LoadingOverlay
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.TouchTarget
import com.candorfoods.factoryops.util.BreakdownPhotoStorage
import com.candorfoods.factoryops.util.DateTimeFormatter
import java.io.File

// High-Contrast Amber Palette for Visibility
private val DarkAmberText = Color(0xFF92400E) // Deep Burnt Amber
private val LightAmberBg  = Color(0xFFFEF3C7) // Soft Amber background
private val AmberBorder   = Color(0xFFF59E0B) // Rich Amber border

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    onBack: () -> Unit,
    viewModel: TicketDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is TicketDetailUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is TicketDetailUiEvent.Acknowledged ->
                    snackbarHostState.showSnackbar("Ticket acknowledged.")
                is TicketDetailUiEvent.Resolved -> {
                    snackbarHostState.showSnackbar("Ticket resolved. Operator will verify.")
                    onBack()
                }
            }
        }
    }

    LoadingOverlay(isLoading = uiState.isLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uiState.machine?.name ?: "Ticket") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            val flag = uiState.flag
            val machine = uiState.machine
            // Gate on the flag only. `machine` resolves from the legacy machines table,
            // which doesn't contain asset-register ids (the flag's machineId is an
            // asset_id), so it's often null — the ticket must still render.
            if (flag == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    if (!uiState.isLoading) Text("Ticket not found.")
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FactoryCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(machine?.code ?: flag.machineId, fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium)
                                    Text(machine?.location ?: "", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                SeverityChip(flag.severity)
                            }
                            HorizontalDivider()
                            Text("Reported by operator ${flag.operatorId}", style = MaterialTheme.typography.bodySmall)
                            Text("Flagged ${DateTimeFormatter.formatRelative(flag.raisedAt)} (${DateTimeFormatter.formatDateTime(flag.raisedAt)})",
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Text("Operator description", fontWeight = FontWeight.SemiBold)
                            Text(flag.description, style = MaterialTheme.typography.bodyMedium)
                            flag.beforePhotoLocalPath?.let { path ->
                                AsyncImage(
                                    model = File(path),
                                    contentDescription = "Before photo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }

                    when (flag.status) {
                        BreakdownStatus.OPEN -> {
                            // Intentional one-off amber styling: signals "in-progress" state distinct
                            // from Primary/Success/Danger/Outlined. PrimaryButton's 4-style contract
                            // doesn't cover Warning, so this stays as a raw Button.
                            Button(
                                onClick = viewModel::onAcknowledge,
                                enabled = !uiState.isSubmitting,
                                modifier = Modifier.fillMaxWidth().height(TouchTarget.primary),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberBorder,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, tint = Color.Black)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Acknowledge — I'll fix this",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        BreakdownStatus.ACKNOWLEDGED -> {
                            Card(colors = CardDefaults.cardColors(containerColor = LightAmberBg)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Acknowledged by ${flag.acknowledgedByName ?: "you"}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkAmberText
                                    )
                                    flag.acknowledgedAt?.let {
                                        Text("at ${DateTimeFormatter.formatDateTime(it)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = DarkAmberText.copy(alpha = 0.8f))
                                    }
                                }
                            }
                            PrimaryButton(
                                text = "Mark Resolved",
                                onClick = viewModel::onOpenResolveSheet,
                                enabled = !uiState.isSubmitting,
                                style = PrimaryButtonStyle.Success,
                                size = PrimaryButtonSize.Primary,
                                icon = Icons.Default.CheckCircle,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        BreakdownStatus.PENDING_QC -> {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Sent to QC — awaiting verification",
                                        fontWeight = FontWeight.SemiBold)
                                    Text("Resolved by ${flag.resolvedByName ?: "technician"}",
                                        style = MaterialTheme.typography.bodyMedium)
                                    flag.resolvedAt?.let {
                                        Text("at ${DateTimeFormatter.formatDateTime(it)}",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    HorizontalDivider()
                                    Text("Parts used / work done", fontWeight = FontWeight.SemiBold)
                                    Text(flag.partsUsed.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                                    flag.afterPhotoLocalPath?.let { path ->
                                        AsyncImage(
                                            model = File(path),
                                            contentDescription = "After-repair photo",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        )
                                    }
                                    flag.qcAcknowledgedByName?.let {
                                        HorizontalDivider()
                                        Text("Picked up by QC: $it",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        BreakdownStatus.CLOSED -> {
                            val approved = FactoryTheme.colors.statusRunning
                            Card(colors = CardDefaults.cardColors(containerColor = approved.copy(alpha = 0.18f))) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("QC approved",
                                        fontWeight = FontWeight.Bold,
                                        color = approved,
                                        style = MaterialTheme.typography.titleMedium)
                                    Text("by ${flag.qcDecidedByName ?: "QC"}" +
                                        (flag.qcDecidedAt?.let { " at ${DateTimeFormatter.formatDateTime(it)}" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall)
                                    flag.qcDecisionNotes?.takeIf { it.isNotBlank() }?.let {
                                        HorizontalDivider()
                                        Text("QC notes", fontWeight = FontWeight.SemiBold)
                                        Text(it, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                        BreakdownStatus.REOPENED -> {
                            Card(colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("QC disapproved",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.titleMedium)
                                    Text("by ${flag.qcDecidedByName ?: "QC"}" +
                                        (flag.qcDecidedAt?.let { " on ${DateTimeFormatter.formatDateTime(it)}" } ?: ""),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall)
                                    HorizontalDivider()
                                    Text("Reason", fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer)
                                    Text(flag.qcDecisionNotes.orEmpty(),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            PrimaryButton(
                                text = "Rework",
                                onClick = viewModel::onOpenResolveSheet,
                                enabled = !uiState.isSubmitting,
                                style = PrimaryButtonStyle.Success,
                                size = PrimaryButtonSize.Primary,
                                icon = Icons.Default.CheckCircle,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        if (uiState.showResolveSheet && uiState.flag != null) {
            ResolveSheet(
                ticketId = uiState.flag!!.id,
                partsUsed = uiState.partsUsedDraft,
                afterPhotoPath = uiState.afterPhotoDraft,
                isSubmitting = uiState.isSubmitting,
                error = uiState.error,
                onPartsUsedChanged = viewModel::onPartsUsedChanged,
                onAfterPhotoCaptured = viewModel::onAfterPhotoCaptured,
                onSubmit = viewModel::onResolve,
                onDismiss = viewModel::onDismissResolveSheet
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResolveSheet(
    ticketId: String,
    partsUsed: String,
    afterPhotoPath: String?,
    isSubmitting: Boolean,
    error: String?,
    onPartsUsedChanged: (String) -> Unit,
    onAfterPhotoCaptured: (String?) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        if (success && file != null && file.exists() && file.length() > 0) {
            onAfterPhotoCaptured(file.absolutePath)
        } else {
            onAfterPhotoCaptured(null)
        }
        pendingFile = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = BreakdownPhotoStorage.createAfterPhotoFile(context, ticketId)
            pendingFile = file
            cameraLauncher.launch(BreakdownPhotoStorage.fileToShareUri(context, file))
        }
    }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            val file = BreakdownPhotoStorage.createAfterPhotoFile(context, ticketId)
            pendingFile = file
            cameraLauncher.launch(BreakdownPhotoStorage.fileToShareUri(context, file))
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Resolve ticket", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Document what you did. Operator must verify before machine can resume.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

            OutlinedTextField(
                value = partsUsed,
                onValueChange = onPartsUsedChanged,
                label = { Text("Parts used / work done") },
                placeholder = { Text("e.g. replaced V-belt, cleaned filter, tightened mounts") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
                isError = error != null
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("After-repair photo (required)", fontWeight = FontWeight.SemiBold)
                    if (afterPhotoPath != null) {
                        AsyncImage(
                            model = File(afterPhotoPath),
                            contentDescription = "After photo preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        )
                        TextButton(onClick = { launchCamera() }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Retake")
                        }
                    } else {
                        PrimaryButton(
                            text = "Capture after-repair photo",
                            onClick = { launchCamera() },
                            style = PrimaryButtonStyle.Outlined,
                            size = PrimaryButtonSize.Min,
                            icon = Icons.Default.PhotoCamera,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            PrimaryButton(
                text = "Submit resolution",
                onClick = onSubmit,
                enabled = !isSubmitting && partsUsed.trim().length >= 4 && afterPhotoPath != null,
                loading = isSubmitting,
                style = PrimaryButtonStyle.Success,
                size = PrimaryButtonSize.Primary,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

private data class SeverityChipColors(val bg: Color, val text: Color, val border: Color)

@Composable
private fun SeverityChip(severity: Severity) {
    val primary = MaterialTheme.colorScheme.primary
    val stopped = FactoryTheme.colors.statusStopped
    val (bg, text, border) = when (severity) {
        Severity.CRITICAL -> SeverityChipColors(stopped.copy(alpha = 0.1f), stopped, stopped.copy(alpha = 0.4f))
        Severity.MAJOR    -> SeverityChipColors(LightAmberBg, DarkAmberText, AmberBorder)
        Severity.MINOR    -> SeverityChipColors(primary.copy(alpha = 0.1f), primary, primary.copy(alpha = 0.4f))
    }

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            severity.name,
            color = text,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
