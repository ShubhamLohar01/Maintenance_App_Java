package com.candorfoods.factoryops.presentation.technician

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * "All" tab — every PM plan assigned to the technician, plus the work orders already
 * generated but not yet due (those sit under "Active"). This is the previous screen's
 * content minus the finished work, which now lives on the History tab.
 */
@Composable
fun PmAllPlansTab(
    plans: List<PmPlanRowUiModel>,
    active: List<PmTaskRowUiModel>,
    plansError: String?,
    onPlanClick: (PmPlanRowUiModel) -> Unit,
    onPmClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (plans.isEmpty() && active.isEmpty() && plansError == null) {
        Box(
            modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No PM plans assigned to you yet. They appear here as soon as a supervisor assigns one. Pull down to refresh.",
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
        plansError?.let { err ->
            item(key = "plans-error") {
                Text(
                    "Couldn't load assigned plans: $err",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        planSection("Assigned PM plans", plans, onPlanClick)
        taskSection("Active", active, onPmClick)
    }
}

/** Assigned-plans group (from mt_pm_plan). Each row states where its current cycle stands
 *  — ready to start, in progress, already submitted — because a due date alone can't tell
 *  work that still needs doing from work that's finished and waiting on review. */
private fun LazyListScope.planSection(
    title: String,
    rows: List<PmPlanRowUiModel>,
    onPlanClick: (PmPlanRowUiModel) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "header-$title") {
        Text(
            "$title (${rows.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
    items(rows, key = { it.planId }) { row ->
        PmPlanCard(row, onClick = { onPlanClick(row) }, showActionHint = true)
    }
}

/** A titled group of PM work-order cards; renders nothing when the group is empty. */
private fun LazyListScope.taskSection(
    title: String,
    rows: List<PmTaskRowUiModel>,
    onPmClick: (String) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "header-$title") {
        Text(
            "$title (${rows.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
    items(rows, key = { it.wo.id }) { row ->
        PmTaskCard(row = row, onClick = { onPmClick(row.wo.id) })
    }
}
