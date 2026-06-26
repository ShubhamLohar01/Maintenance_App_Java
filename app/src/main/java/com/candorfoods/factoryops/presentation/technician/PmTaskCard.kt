package com.candorfoods.factoryops.presentation.technician

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme

@Composable
fun PmTaskCard(
    row: PmTaskRowUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val muted = FactoryTheme.colors.onSurfaceMuted
    val daysOverdue = ((System.currentTimeMillis() - row.wo.scheduledDate)
        .coerceAtLeast(0L) / 86_400_000L)
    // T4 = critical-overdue red wash. Routed through FactoryCard.containerColor so
    // it actually paints on the card surface (Modifier.background paints behind it).
    val cardTint = if (row.wo.escalationTier == 4)
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    else
        null
    FactoryCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = cardTint
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.machineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${row.machineCode} · ${row.machineLocation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }
                if (row.isCritical) {
                    Box(
                        modifier = Modifier
                            .background(FactoryTheme.colors.statusStopped, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("A", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                StatusChip(row.wo.status)
            }

            Text(row.wo.templateName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                DueDateChip(daysToDue = row.daysToDue)
                Spacer(Modifier.width(8.dp))
                TierChip(tier = row.wo.escalationTier, daysOverdue = daysOverdue)
                if (row.wo.escalationTier > 0) {
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    "~${row.wo.estimatedDurationMinutes} min · ${row.wo.tradeType.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: PmWoStatus) {
    val (label, color) = when (status) {
        PmWoStatus.NOTIFIED        -> "NOTIFIED"        to FactoryTheme.colors.statusFlagged
        PmWoStatus.ACKNOWLEDGED    -> "ACK"             to FactoryTheme.colors.statusFlagged
        PmWoStatus.IN_PROGRESS     -> "IN PROGRESS"     to FactoryTheme.colors.info
        else                       -> status.name      to FactoryTheme.colors.onSurfaceMuted
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
private fun DueDateChip(daysToDue: Int) {
    val (label, color) = when {
        daysToDue < 0  -> "Overdue ${-daysToDue}d" to FactoryTheme.colors.statusStopped
        daysToDue == 0 -> "Due today"              to FactoryTheme.colors.statusFlagged
        daysToDue == 1 -> "Due tomorrow"           to FactoryTheme.colors.statusFlagged
        daysToDue <= 3 -> "Due in ${daysToDue}d"   to FactoryTheme.colors.statusFlagged
        else           -> "Due in ${daysToDue}d"   to FactoryTheme.colors.statusRunning
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
private fun TierChip(tier: Int, daysOverdue: Long) {
    if (tier == 0) return
    val (bg, fg, label) = when (tier) {
        1    -> Triple(
            FactoryTheme.colors.statusFlagged.copy(alpha = 0.15f),
            FactoryTheme.colors.statusFlagged,
            "Overdue ${daysOverdue}d"
        )
        2    -> Triple(
            FactoryTheme.colors.statusFlagged.copy(alpha = 0.20f),
            FactoryTheme.colors.statusFlagged,
            "Overdue ${daysOverdue}d"
        )
        3    -> Triple(
            FactoryTheme.colors.statusStopped.copy(alpha = 0.15f),
            FactoryTheme.colors.statusStopped,
            "Overdue ${daysOverdue}d"
        )
        else -> Triple(
            FactoryTheme.colors.statusStopped.copy(alpha = 0.20f),
            FactoryTheme.colors.statusStopped,
            "CRITICAL ${daysOverdue}d"
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "tierPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (tier == 2) 0.55f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tierPulseAlpha"
    )

    Box(
        modifier = Modifier
            .background(bg.copy(alpha = bg.alpha * pulseAlpha), RoundedCornerShape(4.dp))
            .border(1.dp, fg.copy(alpha = 0.4f * pulseAlpha), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
