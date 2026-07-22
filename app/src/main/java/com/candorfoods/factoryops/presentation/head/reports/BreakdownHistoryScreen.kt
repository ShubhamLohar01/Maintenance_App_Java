package com.candorfoods.factoryops.presentation.head.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.presentation.utilities.DateField
import com.candorfoods.factoryops.presentation.utilities.PlantFilterChips
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownHistoryScreen(
    onBack: () -> Unit,
    viewModel: BreakdownHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now().toString() }
    val defaultFrom = remember { LocalDate.now().minusDays(30).toString() }
    val from = state.from ?: defaultFrom
    val to = state.to ?: today

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Breakdown — History", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlantFilterChips(selected = state.plant, onSelect = viewModel::setPlant)
                Row {
                    DateField(
                        value = from, label = "From",
                        onChange = { viewModel.setDateRange(it, to) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    DateField(
                        value = to, label = "To",
                        onChange = { viewModel.setDateRange(from, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                StatusFilterChips(selected = state.status, onSelect = viewModel::setStatus)
            }
            HorizontalDivider()

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(state.error!!, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                }
                state.rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No breakdowns in this range.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.rows, key = { it.id }) { row ->
                        BreakdownHistoryCard(row)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusFilterChips(selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        FilterChip(selected = selected == "OPEN", onClick = { onSelect("OPEN") }, label = { Text("Open") })
        FilterChip(selected = selected == "CLOSED", onClick = { onSelect("CLOSED") }, label = { Text("Closed") })
    }
}

@Composable
private fun BreakdownHistoryCard(row: BreakdownFlag) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    row.assetName?.takeIf { it.isNotBlank() } ?: row.machineId,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(row.status.name, style = MaterialTheme.typography.bodyMedium)
            }
            Text(row.description, style = MaterialTheme.typography.bodySmall)
            Text(
                "Severity: ${row.severity.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            row.resolvedByName?.takeIf { it.isNotBlank() }?.let { name ->
                Text(
                    "Resolved by $name",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}
