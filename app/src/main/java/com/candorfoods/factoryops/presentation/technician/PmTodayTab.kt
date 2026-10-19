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
 * "Today" tab — the daily job: work orders due today or overdue that are still the
 * technician's to do, overdue first. Work they've already submitted is deliberately
 * absent (it's finished, and lives on History); what's left is what they can act on,
 * so every row here opens straight into its work order.
 */
@Composable
fun PmTodayTab(
    rows: List<PmTaskRowUiModel>,
    ungenerated: List<PmPlanRowUiModel>,
    onPmClick: (String) -> Unit,
    onPlanClick: (PmPlanRowUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty() && ungenerated.isEmpty()) {
        Box(
            modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Nothing left to do today. Anything you've already submitted is on History; what's coming up is on All.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        return
    }

    val overdue = rows.filter { it.daysToDue < 0 }
    val dueToday = rows.filter { it.daysToDue == 0 }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        todaySection("Overdue", overdue, onPmClick)
        todaySection("Due today", dueToday, onPmClick)

        // Normally empty: the backend generates a work order for every due plan. A row
        // here means generation hasn't happened, so show it rather than lose the work.
        if (ungenerated.isNotEmpty()) {
            item(key = "today-header-ungenerated") {
                Text(
                    "Awaiting task creation (${ungenerated.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(ungenerated, key = { it.planId }) { row ->
                PmPlanCard(row = row, onClick = { onPlanClick(row) }, showActionHint = true)
            }
        }
    }
}

private fun LazyListScope.todaySection(
    title: String,
    rows: List<PmTaskRowUiModel>,
    onPmClick: (String) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "today-header-$title") {
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
