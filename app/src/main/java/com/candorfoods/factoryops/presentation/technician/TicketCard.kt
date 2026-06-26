package com.candorfoods.factoryops.presentation.technician

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.Criticality
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.util.DateTimeFormatter

// High-Contrast Amber Palette for Visibility
private val DarkAmberText = Color(0xFF92400E)
private val LightAmberBg  = Color(0xFFFEF3C7)
private val AmberBorder   = Color(0xFFF59E0B)

@Composable
fun TicketCard(
    row: TicketRowUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FactoryCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.machineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(row.machineCode, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                CriticalityBadge(row.criticality)
                Spacer(Modifier.width(8.dp))
                SeverityChip(row.flag.severity)
            }

            Text(
                text = row.flag.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )

            // QC kicked-back chip (visible in technician's My Active tab).
            if (row.flag.status == BreakdownStatus.REOPENED) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        "QC kicked back",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusLine(row)
                Spacer(Modifier.weight(1f))
                if (row.flag.beforePhotoLocalPath != null || row.flag.afterPhotoLocalPath != null) {
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
private fun StatusLine(row: TicketRowUiModel) {
    val now = System.currentTimeMillis()
    val (label, color) = when (row.flag.status) {
        BreakdownStatus.OPEN -> {
            "Flagged ${DateTimeFormatter.formatRelative(row.flag.raisedAt, now)}" to
                MaterialTheme.colorScheme.error
        }
        BreakdownStatus.ACKNOWLEDGED -> {
            val by = row.flag.acknowledgedByName ?: "technician"
            val rel = row.flag.acknowledgedAt?.let { DateTimeFormatter.formatRelative(it, now) } ?: ""
            "Acknowledged by $by $rel" to DarkAmberText
        }
        BreakdownStatus.PENDING_QC -> {
            val by = row.flag.resolvedByName ?: "technician"
            val rel = row.flag.resolvedAt?.let { DateTimeFormatter.formatRelative(it, now) } ?: ""
            "Sent to QC by $by $rel" to FactoryTheme.colors.statusRunning
        }
        BreakdownStatus.CLOSED -> {
            val by = row.flag.qcDecidedByName ?: "QC"
            val rel = row.flag.qcDecidedAt?.let { DateTimeFormatter.formatRelative(it, now) } ?: ""
            "Approved by $by $rel" to FactoryTheme.colors.statusRunning
        }
        BreakdownStatus.REOPENED -> {
            val by = row.flag.qcDecidedByName ?: "QC"
            val rel = row.flag.qcDecidedAt?.let { DateTimeFormatter.formatRelative(it, now) } ?: ""
            "Disapproved by $by $rel" to MaterialTheme.colorScheme.error
        }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = FontWeight.ExtraBold
    )
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
