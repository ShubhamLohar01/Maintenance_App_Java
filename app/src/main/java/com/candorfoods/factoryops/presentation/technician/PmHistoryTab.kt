package com.candorfoods.factoryops.presentation.technician

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.util.DateTimeFormatter

/**
 * "History" tab — every PM the technician has finished, newest first, grouped by month.
 * Includes work still awaiting supervisor/QC review (they've done their part) alongside
 * fully approved work; the status chip tells them apart. No time cutoff.
 */
@Composable
fun PmHistoryTab(
    groups: List<PmHistoryGroupUiModel>,
    onPmClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) {
        Box(
            modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No completed PMs yet. Every PM you submit shows up here with its approval status.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        groups.forEach { group ->
            item(key = "month-${group.monthLabel}") {
                Text(
                    "${group.monthLabel.uppercase()} (${group.rows.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = FactoryTheme.colors.onSurfaceMuted,
                )
            }
            items(group.rows, key = { it.wo.id }) { row ->
                PmHistoryCard(row = row, onClick = { onPmClick(row.wo.id) })
            }
        }
    }
}

/**
 * A finished PM. Deliberately lighter than [PmTaskCard]: no due-date or escalation chip,
 * since neither means anything once the work is done — what matters is when it was done
 * and where it stands in review.
 */
@Composable
private fun PmHistoryCard(row: PmTaskRowUiModel, onClick: () -> Unit) {
    val muted = FactoryTheme.colors.onSurfaceMuted
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        row.machineName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(row.machineCode, style = MaterialTheme.typography.bodySmall, color = muted)
                }
                HistoryStatusChip(row.wo.status)
            }
            Text(
                "Done ${DateTimeFormatter.formatDayMonth(row.doneAt)}" +
                    (row.wo.overallResult?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }
}

@Composable
private fun HistoryStatusChip(status: PmWoStatus) {
    val (label, color) = when (status) {
        PmWoStatus.SUBMITTED           -> "IN REVIEW"  to FactoryTheme.colors.statusFlagged
        PmWoStatus.SUPERVISOR_APPROVED -> "WITH QC"    to FactoryTheme.colors.info
        PmWoStatus.PENDING_QC          -> "WITH QC"    to FactoryTheme.colors.info
        PmWoStatus.QC_APPROVED         -> "APPROVED"   to FactoryTheme.colors.statusRunning
        PmWoStatus.CLOSED              -> "CLOSED"     to FactoryTheme.colors.statusRunning
        else                           -> status.name  to FactoryTheme.colors.onSurfaceMuted
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
