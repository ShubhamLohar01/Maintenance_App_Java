package com.candorfoods.factoryops.presentation.supervisor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.presentation.common.RequestNotificationPermission
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.util.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorHomeScreen(
    onCreatePlan: () -> Unit,
    onEditPlan: (String) -> Unit,
    onReviewPm: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: SupervisorHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Prompt for the Android 13+ notification permission so escalations reach the supervisor.
    RequestNotificationPermission()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FactoryOps — Supervisor", style = MaterialTheme.typography.titleLarge)
                        uiState.currentUser?.let {
                            Text("${it.name} — ${it.plantId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(onClick = onCreatePlan) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("New plan")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            androidx.compose.material3.ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                SupervisorTab("Plan",       uiState.plans.size,            selectedTab == 0) { selectedTab = 0 }
                SupervisorTab("Active WOs", uiState.workOrders.size,       selectedTab == 1) { selectedTab = 1 }
                SupervisorTab("Approvals",  uiState.pendingApprovals.size, selectedTab == 2) { selectedTab = 2 }
                SupervisorTab("History",    uiState.history.size,          selectedTab == 3) { selectedTab = 3 }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else when (selectedTab) {
                0 -> PlansList(uiState.plans, onEditPlan)
                1 -> WorkOrdersList(uiState.workOrders)
                2 -> PendingApprovalsList(uiState.pendingApprovals, onReviewPm)
                3 -> HistoryList(uiState.history)
            }
        }
    }
}

@Composable
private fun PendingApprovalsList(
    rows: List<SupervisorWoRow>,
    onReview: (String) -> Unit
) {
    if (rows.isEmpty()) {
        EmptyState("Nothing waiting for approval. Submitted PM work orders show up here.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(rows, key = { it.wo.id }) { row ->
            FactoryCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onReview(row.wo.id) }
            ) {
                Column {
                    Text(row.machineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${row.machineCode} · ${row.wo.templateName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FactoryTheme.colors.onSurfaceMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    val submittedRel = row.wo.submittedAt?.let {
                        DateTimeFormatter.formatRelative(it)
                    } ?: ""
                    Text(
                        "Submitted by ${row.wo.assignedTechnicianName} $submittedRel · ${row.wo.taskLogs.size} items",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/** Completed PM work orders (status = CLOSED in mt_pm_work_order), newest first. */
@Composable
private fun HistoryList(rows: List<SupervisorWoRow>) {
    if (rows.isEmpty()) {
        EmptyState("No completed PM tasks yet. QC-approved work orders show up here.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(rows, key = { it.wo.id }) { row ->
            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.machineName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "COMPLETED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = FactoryTheme.colors.statusRunning,
                        )
                    }
                    Text(
                        "${row.machineCode} · ${row.wo.templateName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FactoryTheme.colors.onSurfaceMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buildString {
                            append("Done by ${row.wo.assignedTechnicianName ?: "technician"}")
                            row.wo.supervisorApprovedByName?.let { append(" · approved by $it") }
                            row.wo.qcAcknowledgedByName?.let { append(" · QC $it") }
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    row.wo.closedAt?.let {
                        Text(
                            "Closed ${DateTimeFormatter.formatDateTime(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = FactoryTheme.colors.onSurfaceMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SupervisorTab(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Text(
                if (count > 0) "$label ($count)" else label,
                style = MaterialTheme.typography.labelLarge
            )
        }
    )
}

@Composable
private fun PlansList(
    rows: List<SupervisorPlanRow>,
    onEdit: (String) -> Unit
) {
    if (rows.isEmpty()) {
        EmptyState("No plans yet. Tap + to create one.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(rows, key = { it.plan.id }) { row ->
            FactoryCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onEdit(row.plan.id) }
            ) {
                Column {
                    Text(row.machineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${row.machineCode} · ${row.templateMachineType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FactoryTheme.colors.onSurfaceMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(row.templateName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    val intervalLabel = when (row.plan.triggerType) {
                        com.candorfoods.factoryops.domain.model.PmTriggerType.TIME ->
                            "Every ${row.plan.triggerInterval} days"
                        com.candorfoods.factoryops.domain.model.PmTriggerType.USAGE ->
                            "Every ${row.plan.triggerInterval} running hours"
                    }
                    val nextDue = DateTimeFormatter.formatDate(row.plan.nextDueAt)
                    Text(
                        "$intervalLabel · next due $nextDue",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkOrdersList(rows: List<SupervisorWoRow>) {
    if (rows.isEmpty()) {
        EmptyState("No active work orders. Scheduler will generate them as plans come due.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(rows, key = { it.wo.id }) { row ->
            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.machineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${row.machineCode} · ${row.wo.templateName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = FactoryTheme.colors.onSurfaceMuted
                            )
                        }
                        WoStatusChip(row.wo.status)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Scheduled ${DateTimeFormatter.formatDate(row.wo.scheduledDate)} · ${row.wo.assignedTechnicianName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun WoStatusChip(status: PmWoStatus) {
    val (label, color) = when (status) {
        PmWoStatus.NOTIFIED            -> "NOTIFIED"            to FactoryTheme.colors.statusFlagged
        PmWoStatus.ACKNOWLEDGED        -> "ACKNOWLEDGED"        to FactoryTheme.colors.statusFlagged
        PmWoStatus.IN_PROGRESS         -> "IN PROGRESS"         to FactoryTheme.colors.info
        PmWoStatus.SUBMITTED           -> "AWAITING SUPERVISOR" to FactoryTheme.colors.info
        PmWoStatus.SUPERVISOR_APPROVED -> "SUP APPROVED"        to FactoryTheme.colors.statusRunning
        PmWoStatus.PENDING_QC          -> "PENDING QC"          to FactoryTheme.colors.statusFlagged
        PmWoStatus.QC_APPROVED         -> "QC APPROVED"         to FactoryTheme.colors.statusRunning
        PmWoStatus.CLOSED              -> "CLOSED"              to FactoryTheme.colors.statusRunning
        PmWoStatus.OVERDUE             -> "OVERDUE"             to FactoryTheme.colors.statusStopped
        PmWoStatus.CANCELLED           -> "CANCELLED"           to FactoryTheme.colors.onSurfaceMuted
        PmWoStatus.DRAFT               -> "DRAFT"               to FactoryTheme.colors.onSurfaceMuted
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(percent = 50))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}
