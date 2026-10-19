package com.candorfoods.factoryops.presentation.supervisor

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.candorfoods.factoryops.domain.model.PmTaskLogStatus
import com.candorfoods.factoryops.domain.model.PmWoTaskLog
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.TouchTarget
import com.candorfoods.factoryops.util.DateTimeFormatter
import java.io.File

private val AmberBorder = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorPmReviewScreen(
    onBack: () -> Unit,
    onDecided: () -> Unit,
    viewModel: SupervisorPmReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRejectSheet by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SupervisorPmReviewEvent.Approved -> onDecided()
                is SupervisorPmReviewEvent.Rejected -> onDecided()
                is SupervisorPmReviewEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review PM") },
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
        val wo = uiState.workOrder
        val machine = uiState.machine
        if (wo == null || machine == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(uiState.error ?: "Work order not found.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // QC bounceback banner — shows when this PM was disapproved by QC
            // and is now back in the supervisor's inbox.
            if (wo.qcDecidedAt != null && wo.status == com.candorfoods.factoryops.domain.model.PmWoStatus.SUBMITTED) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "QC sent this back",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "by ${wo.qcDecidedByName ?: "QC"}" +
                                (wo.qcDecidedAt?.let { " · ${DateTimeFormatter.formatRelative(it)}" } ?: ""),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        wo.qcDecisionNotes?.takeIf { it.isNotBlank() }?.let {
                            Text("Reason", fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(it, color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        wo.qcAfterPhotoLocalPath?.let { path ->
                            AsyncImage(
                                model = java.io.File(path),
                                contentDescription = "QC photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            )
                        }
                        Text(
                            "Approve to re-send to QC, or reject to send to technician for rework.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(machine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${machine.code} · ${machine.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FactoryTheme.colors.onSurfaceMuted
                    )
                    HorizontalDivider()
                    Text(wo.templateName, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Submitted by ${wo.assignedTechnicianName} ${wo.submittedAt?.let { DateTimeFormatter.formatRelative(it) }.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    wo.overallResult?.takeIf { it.isNotBlank() }?.let { result ->
                        val color = if (result.equals("PASS", true)) FactoryTheme.colors.statusRunning
                                    else FactoryTheme.colors.statusStopped
                        Text(
                            "Machine result: ${result.uppercase()}",
                            color = color,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Text("Checklist results", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            wo.taskLogs.forEachIndexed { idx, log ->
                ItemReviewCard(idx + 1, log)
            }

            if (wo.spares.isNotEmpty()) {
                FactoryCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Spares used", fontWeight = FontWeight.Bold)
                        wo.spares.forEach { spare ->
                            Text("• ${spare.spareName} — ${spare.quantityUsed} ${spare.unit}",
                                style = MaterialTheme.typography.bodyMedium)
                            if (!spare.notes.isNullOrBlank()) {
                                Text("    ${spare.notes}", style = MaterialTheme.typography.bodySmall, color = FactoryTheme.colors.onSurfaceMuted)
                            }
                        }
                    }
                }
            }

            if (!wo.finalNotes.isNullOrBlank()) {
                FactoryCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Final tech notes", fontWeight = FontWeight.Bold)
                        Text(wo.finalNotes)
                    }
                }
            }

            val approveLabel = if (wo.qcDecidedAt != null && wo.status == com.candorfoods.factoryops.domain.model.PmWoStatus.SUBMITTED) {
                "Re-approve and send to QC"
            } else {
                "Approve"
            }
            PrimaryButton(
                text = approveLabel,
                onClick = viewModel::onApprove,
                enabled = !uiState.isSubmitting,
                loading = uiState.isSubmitting,
                style = PrimaryButtonStyle.Success,
                size = PrimaryButtonSize.Hero,
                modifier = Modifier.fillMaxWidth()
            )

            // Reject button — Warning semantic, raw Button (same exception as breakdown ack).
            Button(
                onClick = {
                    rejectReason = ""
                    showRejectSheet = true
                },
                enabled = !uiState.isSubmitting,
                modifier = Modifier.fillMaxWidth().height(TouchTarget.primary),
                colors = ButtonDefaults.buttonColors(containerColor = AmberBorder, contentColor = Color.Black)
            ) {
                Text("Reject — needs rework", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))
        }

        if (showRejectSheet) {
            ModalBottomSheet(onDismissRequest = { showRejectSheet = false }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Reject — reason", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { v ->
                            rejectReason = if (v.length > 500) v.substring(0, 500) else v
                        },
                        label = { Text("Why is this being sent back? (${rejectReason.length}/500)") },
                        placeholder = { Text("e.g. measurement on item 3 is outside tolerance, retake") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Required: 4–500 characters.", style = MaterialTheme.typography.bodySmall,
                        color = FactoryTheme.colors.onSurfaceMuted)
                    PrimaryButton(
                        text = "Submit rejection",
                        onClick = {
                            showRejectSheet = false
                            viewModel.onReject(rejectReason)
                        },
                        enabled = rejectReason.trim().length in 4..500 && !uiState.isSubmitting,
                        style = PrimaryButtonStyle.Danger,
                        size = PrimaryButtonSize.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PrimaryButton(
                        text = "Cancel",
                        onClick = { showRejectSheet = false },
                        style = PrimaryButtonStyle.Outlined,
                        size = PrimaryButtonSize.Min,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ItemReviewCard(idx: Int, log: PmWoTaskLog) {
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
