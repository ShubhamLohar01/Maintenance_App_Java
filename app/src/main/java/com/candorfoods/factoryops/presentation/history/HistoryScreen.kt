package com.candorfoods.factoryops.presentation.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.ProductionRun
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.util.DateTimeFormatter
import com.candorfoods.factoryops.util.NumberFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("30-Day History") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.error != null -> Text(
                    uiState.error!!,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                uiState.runs.isEmpty() -> Text(
                    "No history in the last 30 days",
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        HistorySummaryCard(runs = uiState.runs)
                    }
                    items(uiState.runs, key = { it.id }) { run ->
                        HistoryRunCard(run = run)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySummaryCard(runs: List<ProductionRun>) {
    val totalKwh  = runs.mapNotNull { it.computedKwh }.sum()
    val totalRuns = runs.size
    val completedRuns = runs.count { !it.isRunning }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            SummaryItem("Total Runs", "$totalRuns")
            SummaryItem("Completed",  "$completedRuns")
            SummaryItem("Total kWh",  NumberFormatter.formatKwh(totalKwh))
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HistoryRunCard(run: ProductionRun) {
    FactoryCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 12.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(DateTimeFormatter.formatDate(run.startedAt), fontWeight = FontWeight.Bold)
                Text("${DateTimeFormatter.formatTime(run.startedAt)} → ${run.endedAt?.let { DateTimeFormatter.formatTime(it) } ?: "Running"}",
                    style = MaterialTheme.typography.bodySmall)
                run.endedAt?.let {
                    Text(DateTimeFormatter.formatDuration(run.startedAt, it), style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                run.computedKwh?.let { Text(NumberFormatter.formatKwh(it), fontWeight = FontWeight.SemiBold) }
                Text(run.syncStatus.name, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}
