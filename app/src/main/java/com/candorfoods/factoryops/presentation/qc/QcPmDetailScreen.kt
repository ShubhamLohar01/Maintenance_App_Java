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
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.PmTaskLogStatus
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.PmWorkOrder
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
fun QcPmDetailScreen(
    onBack: () -> Unit,
    viewModel: QcPmDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is QcPmDetailUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is QcPmDetailUiEvent.Acknowledged -> snackbarHostState.showSnackbar("Picked up for QC.")
                is QcPmDetailUiEvent.Overridden   -> snackbarHostState.showSnackbar("QC override applied.")
                is QcPmDetailUiEvent.Approved     -> {
                    snackbarHostState.showSnackbar("QC approved — machine returned to operator.")
                    onBack()
                }
                is QcPmDetailUiEvent.Disapproved  -> {
                    snackbarHostState.showSnackbar("Sent back to supervisor.")
                    onBack()
                }
            }
        }
    }

    LoadingOverlay(isLoading = uiState.isLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uiState.machine?.name ?: "PM QC") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            val wo = uiState.workOrder
            val machine = uiState.machine
            if (wo == null || machine == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    if (!uiState.isLoading) Text("Work order not found.")
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
                    HeaderCard(machine = machine, wo = wo)
                    SubmissionCard(wo = wo)
                    HorizontalDivider()
                    Text("Checklist results", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    wo.taskLogs.forEachIndexed { idx, log -> ItemRowCard(idx + 1, log) }
                    if (wo.spares.isNotEmpty()) SparesCard(wo)
                    if (!wo.finalNotes.isNullOrBlank()) FinalNotesCard(wo.finalNotes!!)
                    HorizontalDivider()
                    QcActionSection(
                        wo = wo,
                        uiState = uiState,
                        onAcknowledge = viewModel::onAcknowledgeForQc,
                        onOpenChecklist = viewModel::onOpenChecklistSheet,
                        onOverride = viewModel::onOverrideAcknowledgeAsHead
                    )
                }
            }
        }

        if (uiState.showChecklistSheet && uiState.workOrder != null && uiState.machine != null) {
            QcChecklistSheet(
                machineName = uiState.machine!!.name,
                machineCode = uiState.machine!!.code,
                ticketId = uiState.workOrder!!.id,
                draft = uiState.checklist,
                isSubmitting = uiState.isSubmitting,
                error = uiState.error,
                onChange = viewModel::onChecklistChanged,
                onApprove = viewModel::onApprove,
                onDisapprove = viewModel::onDisapprove,
                onDismiss = viewModel::onDismissChecklistSheet
            )
        }
    }
}

@Composable
private fun HeaderCard(machine: Machine, wo: PmWorkOrder) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("PM", color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(8.dp))
                Text(machine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("${machine.code} · ${machine.location}",
                style = MaterialTheme.typography.bodySmall,
                color = FactoryTheme.colors.onSurfaceMuted)
            Text(wo.templateName, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SubmissionCard(wo: PmWorkOrder) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Technician + supervisor", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Performed by ${wo.assignedTechnicianName}" +
                (wo.submittedAt?.let { " · submitted ${DateTimeFormatter.formatRelative(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall)
            wo.overallResult?.takeIf { it.isNotBlank() }?.let { result ->
                val color = if (result.equals("PASS", true)) FactoryTheme.colors.statusRunning
                            else FactoryTheme.colors.statusStopped
                Text("Machine result: ${result.uppercase()}",
                    color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
            wo.supervisorApprovedByName?.let { sup ->
                Text("Approved by $sup" +
                    (wo.supervisorApprovedAt?.let { " · ${DateTimeFormatter.formatRelative(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ItemRowCard(idx: Int, log: com.candorfoods.factoryops.domain.model.PmWoTaskLog) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$idx. ${log.title}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                StatusPill(log.status)
            }
            if (!log.notes.isNullOrBlank()) {
                Text("Remarks: ${log.notes}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SparesCard(wo: PmWorkOrder) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Spares used", fontWeight = FontWeight.Bold)
            wo.spares.forEach { s ->
                Text("• ${s.spareName} — ${s.quantityUsed} ${s.unit}",
                    style = MaterialTheme.typography.bodyMedium)
                if (!s.notes.isNullOrBlank()) {
                    Text("    ${s.notes}", style = MaterialTheme.typography.bodySmall, color = FactoryTheme.colors.onSurfaceMuted)
                }
            }
        }
    }
}

@Composable
private fun FinalNotesCard(notes: String) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("Final tech notes", fontWeight = FontWeight.Bold)
            Text(notes)
        }
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

@Composable
private fun QcActionSection(
    wo: PmWorkOrder,
    uiState: QcPmDetailUiState,
    onAcknowledge: () -> Unit,
    onOpenChecklist: () -> Unit,
    onOverride: () -> Unit
) {
    when (wo.status) {
        PmWoStatus.PENDING_QC -> {
            if (!uiState.isQcRole) {
                // View-only for non-QC roles (Supervisor / Head): QC status, no actions.
                Card(colors = CardDefaults.cardColors(containerColor = LightAmberBg)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Awaiting QC verification",
                            fontWeight = FontWeight.SemiBold, color = DarkAmberText)
                        wo.qcAcknowledgedByName?.let {
                            Text("Picked up by QC: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkAmberText.copy(alpha = 0.8f))
                        }
                    }
                }
            } else when {
                wo.qcAcknowledgedBy == null -> {
                    Button(
                        onClick = onAcknowledge,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(TouchTarget.primary),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberBorder, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("Acknowledge for QC", style = MaterialTheme.typography.titleMedium,
                            color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                uiState.isHeldByMe -> {
                    Card(colors = CardDefaults.cardColors(containerColor = LightAmberBg)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Picked up by you", fontWeight = FontWeight.SemiBold, color = DarkAmberText)
                            wo.qcAcknowledgedAt?.let {
                                Text("at ${DateTimeFormatter.formatDateTime(it)}",
                                    style = MaterialTheme.typography.bodySmall, color = DarkAmberText.copy(alpha = 0.8f))
                            }
                        }
                    }
                    PrimaryButton(
                        text = "Run QC checklist",
                        onClick = onOpenChecklist,
                        enabled = !uiState.isSubmitting,
                        style = PrimaryButtonStyle.Primary,
                        size = PrimaryButtonSize.Primary,
                        icon = Icons.Default.CheckCircle,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                uiState.isHeldByOther -> {
                    Card(colors = CardDefaults.cardColors(containerColor = LightAmberBg)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Picked up by ${wo.qcAcknowledgedByName ?: "another QC user"}",
                                fontWeight = FontWeight.SemiBold, color = DarkAmberText)
                            wo.qcAcknowledgedAt?.let {
                                Text("at ${DateTimeFormatter.formatDateTime(it)}",
                                    style = MaterialTheme.typography.bodySmall, color = DarkAmberText.copy(alpha = 0.8f))
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        PmWoStatus.QC_APPROVED -> {
            val approved = FactoryTheme.colors.statusRunning
            Card(colors = CardDefaults.cardColors(containerColor = approved.copy(alpha = 0.18f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("QC approved", fontWeight = FontWeight.Bold, color = approved,
                        style = MaterialTheme.typography.titleMedium)
                    Text("by ${wo.qcDecidedByName ?: "QC"}" +
                        (wo.qcDecidedAt?.let { " at ${DateTimeFormatter.formatDateTime(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall)
                    wo.qcDecisionNotes?.takeIf { it.isNotBlank() }?.let {
                        Text("Notes", fontWeight = FontWeight.SemiBold)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    wo.qcAfterPhotoLocalPath?.let { path ->
                        AsyncImage(
                            model = File(path), contentDescription = "QC photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        }
        PmWoStatus.SUBMITTED -> {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sent back to supervisor",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.titleMedium)
                    Text("by ${wo.qcDecidedByName ?: "QC"}" +
                        (wo.qcDecidedAt?.let { " on ${DateTimeFormatter.formatDateTime(it)}" } ?: ""),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall)
                    wo.qcDecisionNotes?.takeIf { it.isNotBlank() }?.let {
                        Text("Reason", fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        else -> {
            Text("This PM is not awaiting QC.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}
