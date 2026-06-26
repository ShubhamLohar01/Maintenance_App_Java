package com.candorfoods.factoryops.presentation.technician

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.TouchTarget
import com.candorfoods.factoryops.util.DateTimeFormatter

private val DarkAmberText = Color(0xFF92400E)
private val LightAmberBg  = Color(0xFFFEF3C7)
private val AmberBorder   = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianPmDetailScreen(
    onBack: () -> Unit,
    onLaunchExecution: (String) -> Unit,
    viewModel: TechnicianPmDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TechnicianPmDetailEvent.Acknowledged -> snackbarHostState.showSnackbar("Acknowledged.")
                is TechnicianPmDetailEvent.Started -> {
                    val woId = uiState.workOrder?.id
                    if (woId != null) onLaunchExecution(woId)
                }
                is TechnicianPmDetailEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.machine?.name ?: "PM work order") },
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
                Text(uiState.error ?: "PM work order not found.")
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
            if (wo.supervisorRejectedAt != null && wo.status == PmWoStatus.IN_PROGRESS) {
                RejectionBanner(
                    rejectedAt = wo.supervisorRejectedAt,
                    notes = wo.supervisorRejectionNotes.orEmpty()
                )
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
                        "Estimated ~${wo.estimatedDurationMinutes} min · ${wo.tradeType.name}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Scheduled ${DateTimeFormatter.formatDate(wo.scheduledDate)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (wo.taskLogs.isNotEmpty()) {
                        Text(
                            "${wo.taskLogs.size} checklist items · ~${wo.taskLogs.count { it.requiresPhoto }} require photos",
                            style = MaterialTheme.typography.bodySmall,
                            color = FactoryTheme.colors.onSurfaceMuted
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            when (wo.status) {
                PmWoStatus.NOTIFIED -> {
                    PrimaryButton(
                        text = "Acknowledge",
                        onClick = viewModel::onAcknowledge,
                        enabled = !uiState.isSubmitting,
                        loading = uiState.isSubmitting,
                        // Warning semantic — same exception used for breakdown ack button.
                        style = PrimaryButtonStyle.Outlined,
                        size = PrimaryButtonSize.Primary,
                        icon = Icons.Default.Warning,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                PmWoStatus.ACKNOWLEDGED -> {
                    PrimaryButton(
                        text = "Start PM",
                        onClick = viewModel::onStart,
                        enabled = !uiState.isSubmitting,
                        loading = uiState.isSubmitting,
                        style = PrimaryButtonStyle.Success,
                        size = PrimaryButtonSize.Hero,
                        icon = Icons.Default.PlayArrow,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                PmWoStatus.IN_PROGRESS -> {
                    PrimaryButton(
                        text = "Continue execution",
                        onClick = { onLaunchExecution(wo.id) },
                        style = PrimaryButtonStyle.Primary,
                        size = PrimaryButtonSize.Primary,
                        icon = Icons.Default.PlayArrow,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                PmWoStatus.SUBMITTED -> ReadOnlySummaryBanner(
                    title = "Submitted for supervisor approval",
                    body = "Submitted ${wo.submittedAt?.let { DateTimeFormatter.formatRelative(it) } ?: ""}. " +
                        "You'll get a notification when the supervisor decides."
                )
                PmWoStatus.SUPERVISOR_APPROVED -> ReadOnlySummaryBanner(
                    title = "Supervisor approved",
                    body = "Approved by ${wo.supervisorApprovedByName ?: "supervisor"}. Awaiting QC verification next (Phase 2C-3)."
                )
                else -> ReadOnlySummaryBanner(
                    title = wo.status.name,
                    body = "This PM is in a final or non-actionable state."
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RejectionBanner(rejectedAt: Long, notes: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Supervisor sent this back ${DateTimeFormatter.formatRelative(rejectedAt)}",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider()
            Text("Reason", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
            Text(notes.ifBlank { "(no reason given)" }, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "Tap Continue execution below to address feedback and re-submit.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ReadOnlySummaryBanner(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
