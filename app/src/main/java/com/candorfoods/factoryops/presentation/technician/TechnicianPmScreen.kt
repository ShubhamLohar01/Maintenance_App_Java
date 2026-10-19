package com.candorfoods.factoryops.presentation.technician

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.util.DateTimeFormatter

/**
 * Technician's dedicated Preventive-Maintenance screen — one place for all their PM
 * work (opened from the "PM Tasks" home tile), split into three tabs:
 *  - Today   — what's due today or overdue. The daily job.
 *  - All     — every assigned plan, plus tasks generated ahead of their due date.
 *  - History — everything they've finished, all time, grouped by month.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianPmScreen(
    onBack: () -> Unit,
    onPmClick: (String) -> Unit,
    viewModel: TechnicianPmViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var sheetPlan by remember { mutableStateOf<PmPlanRowUiModel?>(null) }
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_TODAY) }

    // A plan row — on either tab — opens its work order when one exists, whatever state
    // that work order is in; the technician needs to see submitted work as submitted, not
    // be handed a checklist preview. Only a plan with no work order falls back to the sheet.
    val onPlanRowClick: (PmPlanRowUiModel) -> Unit = { plan ->
        plan.woId?.let(onPmClick) ?: run { sheetPlan = plan }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PM Tasks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                val labels = listOf(
                    "Today (${uiState.todayCount})",
                    "All (${uiState.plans.size})",
                    "History (${uiState.historyCount})",
                )
                labels.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) },
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (selectedTab) {
                        TAB_TODAY -> PmTodayTab(
                            rows = uiState.today,
                            ungenerated = uiState.todayUngenerated,
                            onPmClick = onPmClick,
                            onPlanClick = onPlanRowClick,
                        )
                        TAB_ALL -> PmAllPlansTab(
                            plans = uiState.plans,
                            active = uiState.active,
                            plansError = uiState.plansError,
                            onPlanClick = onPlanRowClick,
                            onPmClick = onPmClick,
                        )
                        else -> PmHistoryTab(
                            groups = uiState.history,
                            onPmClick = onPmClick,
                        )
                    }
                }
            }
        }
    }

    sheetPlan?.let { plan ->
        PlanDetailSheet(plan = plan, onDismiss = { sheetPlan = null })
    }
}

private const val TAB_TODAY = 0
private const val TAB_ALL = 1

/** Bottom sheet: what this recurring plan is, when its next task lands, and its checklist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDetailSheet(plan: PmPlanRowUiModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(plan.machineName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    plan.machineCode,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            item {
                Text(
                    "${plan.intervalLabel} · next due ${DateTimeFormatter.formatDate(plan.nextDueAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (plan.daysToDue < 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                )
                // This sheet only opens for a plan with no work order behind it — anything
                // with one goes straight to the work order instead. So say when the task
                // is coming, not where to look for it.
                Text(
                    if (plan.isInsideGenerationWindow)
                        "The task is due but hasn't been created yet — pull down to refresh."
                    else
                        "Its task is created on ${DateTimeFormatter.formatDate(plan.generatesAt)}, 7 days before the due date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            if (plan.description.isNotBlank()) {
                item {
                    Text(plan.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    "Checklist (${plan.items.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(plan.items, key = { it.id }) { item ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "${item.orderIndex + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium)
                        if (item.description.isNotBlank()) {
                            Text(
                                item.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}
