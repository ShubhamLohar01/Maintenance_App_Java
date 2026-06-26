package com.candorfoods.factoryops.presentation.qc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.Criticality
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.util.DateTimeFormatter

private val DarkAmberText = Color(0xFF92400E)
private val LightAmberBg  = Color(0xFFFEF3C7)
private val AmberBorder   = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QcHomeScreen(
    onRowClick: (QcInboxRow) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: QcHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FactoryOps — QC Verification",
                            style = MaterialTheme.typography.titleLarge)
                        uiState.currentUser?.let { user ->
                            val roleLabel = when (user.role) {
                                UserRole.QC_HEAD   -> "QC Head"
                                UserRole.QC_MEMBER -> "QC Member"
                                else               -> user.role.name
                            }
                            Text(
                                "${user.name} — $roleLabel — ${user.plantId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                QcTab("Awaiting QC", uiState.awaiting.size, selectedTab == 0) { selectedTab = 0 }
                QcTab("My Active",   uiState.myActive.size, selectedTab == 1) { selectedTab = 1 }
                QcTab("Done today",  uiState.doneToday.size, selectedTab == 2) { selectedTab = 2 }
            }

            val rows = when (selectedTab) {
                0    -> uiState.awaiting
                1    -> uiState.myActive
                else -> uiState.doneToday
            }
            val emptyMsg = when (selectedTab) {
                0    -> "No tickets waiting for QC."
                1    -> "You have no active QC items."
                else -> "Nothing decided today."
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (rows.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            emptyMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(rows, key = { it.id }) { row ->
                            QcInboxCard(row = row, tab = selectedTab, onClick = { onRowClick(row) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QcTab(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                if (count > 0) {
                    Spacer(Modifier.width(6.dp))
                    val badgeBg =
                        if (count > 5) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .background(badgeBg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun QcInboxCard(
    row: QcInboxRow,
    tab: Int,
    onClick: () -> Unit
) {
    FactoryCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeChip(row)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.machineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${row.machineCode} · ${row.machineLocation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                CriticalityBadge(row.criticality)
                if (row is QcInboxRow.Breakdown) {
                    Spacer(Modifier.width(8.dp))
                    SeverityChip(row.flag.severity)
                }
            }

            // Body line — different per type
            when (row) {
                is QcInboxRow.Breakdown -> Text(
                    text = row.flag.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                is QcInboxRow.Pm -> Text(
                    text = row.wo.templateName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusLine(row, tab)
                Spacer(Modifier.weight(1f))
                if (row is QcInboxRow.Breakdown && (row.flag.beforePhotoLocalPath != null ||
                    row.flag.afterPhotoLocalPath != null ||
                    row.flag.qcAfterPhotoLocalPath != null)) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Photo",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeChip(row: QcInboxRow) {
    val (label, bg, fg) = when (row) {
        is QcInboxRow.Breakdown -> Triple("BD", FactoryTheme.colors.statusStopped.copy(alpha = 0.15f), FactoryTheme.colors.statusStopped)
        is QcInboxRow.Pm        -> Triple("PM", MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.colorScheme.primary)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StatusLine(row: QcInboxRow, tab: Int) {
    val now = System.currentTimeMillis()
    val (label, color) = when (row) {
        is QcInboxRow.Breakdown -> breakdownStatusLine(row.flag, tab, now)
        is QcInboxRow.Pm        -> pmStatusLine(row.wo, tab, now)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
private fun breakdownStatusLine(
    flag: com.candorfoods.factoryops.domain.model.BreakdownFlag,
    tab: Int,
    now: Long
): Pair<String, Color> = when (tab) {
    0 -> {
        val by = flag.resolvedByName ?: "technician"
        val rel = flag.resolvedAt?.let { DateTimeFormatter.formatRelative(it, now) } ?: ""
        "Sent to QC by $by $rel" to FactoryTheme.colors.statusFlagged
    }
    1 -> {
        val rel = flag.qcAcknowledgedAt?.let { DateTimeFormatter.formatRelative(it, now) } ?: ""
        "Picked up $rel" to MaterialTheme.colorScheme.primary
    }
    else -> {
        val (verb, c) = when (flag.status) {
            BreakdownStatus.CLOSED   -> "Approved" to FactoryTheme.colors.statusRunning
            BreakdownStatus.REOPENED -> "Rejected" to MaterialTheme.colorScheme.error
            else                        -> "Decided" to MaterialTheme.colorScheme.onSurface
        }
        val rel = flag.qcDecidedAt?.let { DateTimeFormatter.formatRelative(it, now) } ?: ""
        "$verb $rel" to c
    }
}

@Composable
private fun pmStatusLine(
    wo: com.candorfoods.factoryops.domain.model.PmWorkOrder,
    tab: Int,
    now: Long
): Pair<String, Color> = when (tab) {
    0 -> {
        val rel = wo.submittedAt?.let { DateTimeFormatter.formatRelative(it, now) }
            ?: wo.supervisorApprovedAt?.let { DateTimeFormatter.formatRelative(it, now) }
            ?: ""
        "Sent to QC by ${wo.supervisorApprovedByName ?: "supervisor"} $rel" to FactoryTheme.colors.statusFlagged
    }
    1 -> {
        val rel = wo.qcAcknowledgedAt?.let { DateTimeFormatter.formatRelative(it, now) } ?: ""
        "Picked up $rel" to MaterialTheme.colorScheme.primary
    }
    else -> {
        val (verb, c) = when (wo.status) {
            PmWoStatus.QC_APPROVED -> "Approved" to FactoryTheme.colors.statusRunning
            PmWoStatus.SUBMITTED   -> "Sent back to supervisor" to MaterialTheme.colorScheme.error
            else                   -> "Decided" to MaterialTheme.colorScheme.onSurface
        }
        val rel = wo.qcDecidedAt?.let { DateTimeFormatter.formatRelative(it, now) } ?: ""
        "$verb $rel" to c
    }
}

private data class BadgeColors(val bg: Color, val text: Color, val border: Color, val label: String)
private data class ChipColors(val bg: Color, val text: Color, val border: Color)

@Composable
private fun CriticalityBadge(criticality: Criticality) {
    val primary = MaterialTheme.colorScheme.primary
    val stopped = FactoryTheme.colors.statusStopped
    val (bg, text, border, label) = when (criticality) {
        Criticality.A -> BadgeColors(stopped.copy(alpha = 0.1f), stopped, stopped.copy(alpha = 0.4f), "A")
        Criticality.B -> BadgeColors(LightAmberBg, DarkAmberText, AmberBorder, "B")
        Criticality.C -> BadgeColors(primary.copy(alpha = 0.1f), primary, primary.copy(alpha = 0.4f), "C")
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
private fun SeverityChip(severity: Severity) {
    val primary = MaterialTheme.colorScheme.primary
    val stopped = FactoryTheme.colors.statusStopped
    val (bg, text, border) = when (severity) {
        Severity.CRITICAL -> ChipColors(stopped.copy(alpha = 0.1f), stopped, stopped.copy(alpha = 0.4f))
        Severity.MAJOR    -> ChipColors(LightAmberBg, DarkAmberText, AmberBorder)
        Severity.MINOR    -> ChipColors(primary.copy(alpha = 0.1f), primary, primary.copy(alpha = 0.4f))
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
