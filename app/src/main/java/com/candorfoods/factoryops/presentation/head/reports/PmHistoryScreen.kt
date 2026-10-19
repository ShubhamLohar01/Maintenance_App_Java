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
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.presentation.utilities.DateField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PmHistoryScreen(
    onBack: () -> Unit,
    viewModel: PmHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The ViewModel resolves null dates to its 30-day default and writes them back into
    // state as soon as load() starts, so state.from/state.to are the source of truth
    // for what's actually being queried. These remember{} values are only a first-frame
    // placeholder before that initial load() completes.
    val from = state.from ?: remember { LocalDate.now().minusDays(30).toString() }
    val to = state.to ?: remember { LocalDate.now().toString() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PM — History", style = MaterialTheme.typography.titleLarge) },
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
                PmStatusFilterChips(selected = state.status, onSelect = viewModel::setStatus)
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
                    Text("No PM work orders in this range.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.rows, key = { it.id }) { row ->
                        PmHistoryCard(row)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PmStatusFilterChips(selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        FilterChip(selected = selected == "CLOSED", onClick = { onSelect("CLOSED") }, label = { Text("Closed") })
    }
}

private val closedDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

@Composable
private fun PmHistoryCard(row: PmWorkOrder) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    row.templateName.takeIf { it.isNotBlank() } ?: row.machineId,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(row.status.name, style = MaterialTheme.typography.bodyMedium)
            }
            Text(row.machineId, style = MaterialTheme.typography.bodySmall)
            row.assignedTechnicianName.takeIf { it.isNotBlank() }?.let { name ->
                Text(
                    "Technician: $name",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            row.closedAt?.let { closedAt ->
                val date = Instant.ofEpochMilli(closedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                Text(
                    "Closed ${date.format(closedDateFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}
