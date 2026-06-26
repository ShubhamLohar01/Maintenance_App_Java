package com.candorfoods.factoryops.presentation.qc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.candorfoods.factoryops.util.DateTimeFormatter
import java.io.File

private val DarkAmberText = Color(0xFF92400E)
private val LightAmberBg  = Color(0xFFFEF3C7)
private val AmberBorder   = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QcTicketDetailScreen(
    onBack: () -> Unit,
    viewModel: QcTicketDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is QcTicketDetailUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is QcTicketDetailUiEvent.Acknowledged -> snackbarHostState.showSnackbar("Picked up for QC.")
                is QcTicketDetailUiEvent.Overridden   -> snackbarHostState.showSnackbar("QC override applied.")
                is QcTicketDetailUiEvent.Approved     -> {
                    snackbarHostState.showSnackbar("QC approved — machine returned to operator.")
                    onBack()
                }
                is QcTicketDetailUiEvent.Disapproved  -> {
                    snackbarHostState.showSnackbar("Sent back to technician.")
                    onBack()
                }
            }
        }
    }

    LoadingOverlay(isLoading = uiState.isLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uiState.machine?.name ?: "QC Ticket") },
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
            // Gate on the flag only — `machine` (legacy machines table) is often null for
            // asset-register ids, but the ticket must still render.
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
                    if (machine != null) {
                        MachineCard(machine = machine, severity = flag.severity)
                    } else {
                        // Asset-register breakdowns have no legacy `machines` row — render
                        // the same header card from the breakdown's snapshot fields.
                        AssetSummaryCard(flag = flag)
                    }

                    OperatorReportCard(flag = flag)

                    HorizontalDivider()

                    TechnicianRepairCard(flag = flag)

                    HorizontalDivider()

                    QcActionSection(
                        flag = flag,
                        uiState = uiState,
                        onAcknowledge = viewModel::onAcknowledgeForQc,
                        onRequestApprove = viewModel::onRequestApprove,
                        onRequestDisapprove = viewModel::onRequestDisapprove,
                        onOverride = viewModel::onOverrideAcknowledgeAsHead
                    )
                }
            }
        }

        when (uiState.pendingConfirm) {
            QcConfirmKind.APPROVE -> ConfirmApproveDialog(
                isSubmitting = uiState.isSubmitting,
                onConfirm = viewModel::onConfirmApprove,
                onDismiss = viewModel::onCancelConfirm
            )
            QcConfirmKind.DISAPPROVE -> ConfirmDisapproveDialog(
                isSubmitting = uiState.isSubmitting,
                onConfirm = viewModel::onConfirmDisapprove,
                onDismiss = viewModel::onCancelConfirm
            )
            null -> Unit
        }
    }
}

@Composable
private fun MachineCard(
    machine: com.candorfoods.factoryops.domain.model.Machine,
    severity: Severity
) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(machine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${machine.code} · ${machine.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            CritBadge(machine.criticality)
            Spacer(Modifier.width(8.dp))
            SevChip(severity)
        }
    }
}

@Composable
private fun OperatorReportCard(flag: com.candorfoods.factoryops.domain.model.BreakdownFlag) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Operator report",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Reported by ${flag.operatorId} · " +
                "${DateTimeFormatter.formatRelative(flag.raisedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(DateTimeFormatter.formatDateTime(flag.raisedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
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
}

@Composable
private fun TechnicianRepairCard(flag: com.candorfoods.factoryops.domain.model.BreakdownFlag) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Technician repair",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Repaired by ${flag.resolvedByName ?: "—"}" +
                (flag.resolvedAt?.let { " · ${DateTimeFormatter.formatRelative(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            flag.resolvedAt?.let {
                Text(DateTimeFormatter.formatDateTime(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Text("Parts used / work done", fontWeight = FontWeight.SemiBold)
            Text(flag.partsUsed.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            flag.afterPhotoLocalPath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = "After-repair photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
private fun QcActionSection(
    flag: com.candorfoods.factoryops.domain.model.BreakdownFlag,
    uiState: QcTicketDetailUiState,
    onAcknowledge: () -> Unit,
    onRequestApprove: () -> Unit,
    onRequestDisapprove: () -> Unit,
    onOverride: () -> Unit
) {
    when {
        flag.status == BreakdownStatus.PENDING_QC -> {
            when {
                flag.qcAcknowledgedBy == null -> {
                    // Intentional one-off amber styling (Warning semantic) — see TicketDetailScreen.
                    Button(
                        onClick = onAcknowledge,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(TouchTarget.primary),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberBorder,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("Acknowledge for QC",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold)
                    }
                }
                uiState.isHeldByMe -> {
                    Card(colors = CardDefaults.cardColors(containerColor = LightAmberBg)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Picked up by you",
                                fontWeight = FontWeight.SemiBold, color = DarkAmberText)
                            flag.qcAcknowledgedAt?.let {
                                Text("at ${DateTimeFormatter.formatDateTime(it)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DarkAmberText.copy(alpha = 0.8f))
                            }
                        }
                    }
                    Button(
                        onClick = onRequestApprove,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(TouchTarget.primary),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FactoryTheme.colors.statusRunning,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Approve", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onRequestDisapprove,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(TouchTarget.primary),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Disapprove", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                }
                uiState.isHeldByOther -> {
                    Card(colors = CardDefaults.cardColors(containerColor = LightAmberBg)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Picked up by ${flag.qcAcknowledgedByName ?: "another QC user"}",
                                fontWeight = FontWeight.SemiBold, color = DarkAmberText)
                            flag.qcAcknowledgedAt?.let {
                                Text("at ${DateTimeFormatter.formatDateTime(it)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DarkAmberText.copy(alpha = 0.8f))
                            }
                        }
                    }
                    if (uiState.isQcHead) {
                        PrimaryButton(
                            text = "Override — take this over as QC Head",
                            onClick = onOverride,
                            enabled = !uiState.isSubmitting,
                            style = PrimaryButtonStyle.Outlined,
                            size = PrimaryButtonSize.Min,
                            icon = Icons.Default.SwapHoriz,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        flag.status == BreakdownStatus.CLOSED -> {
            val approved = FactoryTheme.colors.statusRunning
            Card(colors = CardDefaults.cardColors(containerColor = approved.copy(alpha = 0.18f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("QC approved",
                        fontWeight = FontWeight.Bold,
                        color = approved,
                        style = MaterialTheme.typography.titleMedium)
                    Text("by ${flag.qcDecidedByName ?: "QC"}" +
                        (flag.qcDecidedAt?.let { " at ${DateTimeFormatter.formatDateTime(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall)
                    flag.qcDecisionNotes?.takeIf { it.isNotBlank() }?.let {
                        Text("Notes", fontWeight = FontWeight.SemiBold)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    flag.qcAfterPhotoLocalPath?.let { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "QC photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        }
        // Disapproved view: legacy REOPENED status OR backend's new flow
        // (status=OPEN + a stored qc_reject_reason).
        flag.status == BreakdownStatus.REOPENED ||
            (flag.status == BreakdownStatus.OPEN && !flag.qcDecisionNotes.isNullOrBlank()) -> {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Disapproved — sent back to technician",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.titleMedium)
                    Text("by ${flag.qcDecidedByName ?: "QC"}" +
                        (flag.qcDecidedAt?.let { " on ${DateTimeFormatter.formatDateTime(it)}" } ?: ""),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall)
                    Text("Reason", fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(flag.qcDecisionNotes.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium)
                    flag.qcAfterPhotoLocalPath?.let { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "QC photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        }
        else -> {
            Text(
                "This ticket is not awaiting QC.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

private data class CritBadgeColors(val bg: Color, val text: Color, val border: Color, val label: String)
private data class SevChipColors(val bg: Color, val text: Color, val border: Color)

@Composable
private fun CritBadge(criticality: com.candorfoods.factoryops.domain.model.Criticality) {
    val primary = MaterialTheme.colorScheme.primary
    val stopped = FactoryTheme.colors.statusStopped
    val (bg, text, border, label) = when (criticality) {
        com.candorfoods.factoryops.domain.model.Criticality.A ->
            CritBadgeColors(stopped.copy(alpha = 0.1f), stopped, stopped.copy(alpha = 0.4f), "A")
        com.candorfoods.factoryops.domain.model.Criticality.B ->
            CritBadgeColors(LightAmberBg, DarkAmberText, AmberBorder, "B")
        com.candorfoods.factoryops.domain.model.Criticality.C ->
            CritBadgeColors(primary.copy(alpha = 0.1f), primary, primary.copy(alpha = 0.4f), "C")
    }
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, border, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SevChip(severity: Severity) {
    val primary = MaterialTheme.colorScheme.primary
    val stopped = FactoryTheme.colors.statusStopped
    val (bg, text, border) = when (severity) {
        Severity.CRITICAL -> SevChipColors(stopped.copy(alpha = 0.1f), stopped, stopped.copy(alpha = 0.4f))
        Severity.MAJOR    -> SevChipColors(LightAmberBg, DarkAmberText, AmberBorder)
        Severity.MINOR    -> SevChipColors(primary.copy(alpha = 0.1f), primary, primary.copy(alpha = 0.4f))
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(severity.name, color = text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AssetSummaryCard(flag: com.candorfoods.factoryops.domain.model.BreakdownFlag) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    flag.assetName ?: flag.machineId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                val sub = listOfNotNull(
                    flag.machineId.takeIf { it.isNotBlank() && it != flag.assetName },
                    flag.building?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            SevChip(flag.severity)
        }
    }
}

@Composable
private fun ConfirmApproveDialog(
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
        title = { Text("Approve QC?") },
        text = {
            Text("This closes the breakdown and returns the machine to the operator. You can't undo this.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSubmitting) {
                Text("Approve", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ConfirmDisapproveDialog(
    isSubmitting: Boolean,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    val trimmedLen = reason.trim().length
    val isValid = trimmedLen in 4..500

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        icon = { Icon(Icons.Default.Cancel, contentDescription = null) },
        title = { Text("Disapprove QC?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The breakdown will be sent back to the same technician for rework.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (4–500 chars)") },
                    minLines = 3,
                    maxLines = 6,
                    isError = reason.isNotEmpty() && !isValid,
                    supportingText = {
                        Text(
                            "$trimmedLen / 500",
                            color = if (isValid || reason.isEmpty()) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            } else MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason) },
                enabled = !isSubmitting && isValid,
            ) {
                Text("Disapprove", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        },
    )
}
