package com.candorfoods.factoryops.presentation.technician

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme

/**
 * One assigned PM plan — the recurring schedule, not a work order. Used on both the
 * "Today" and "All plans" tabs; Today additionally shows [PmPlanRowUiModel.actionHint]
 * so the technician can tell at a glance whether the task is ready to start.
 */
@Composable
fun PmPlanCard(
    row: PmPlanRowUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showActionHint: Boolean = false,
) {
    val muted = FactoryTheme.colors.onSurfaceMuted
    FactoryCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.machineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(row.machineCode, style = MaterialTheme.typography.bodySmall, color = muted)

            val d = row.daysToDue
            val dueText = when {
                d < 0  -> "Overdue ${-d}d"
                d == 0 -> "Due today"
                else   -> "Due in ${d}d"
            }
            val dueColor = if (d < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Row {
                Text(dueText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = dueColor)
                Text(
                    "  ·  ${row.intervalLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
            }

            if (showActionHint) {
                Text(
                    row.actionHint,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (row.isStartable) FontWeight.Bold else FontWeight.Normal,
                    color = if (row.isStartable) MaterialTheme.colorScheme.primary else muted,
                )
            }
        }
    }
}
