package com.candorfoods.factoryops.presentation.head

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadBreakdownsScreen(
    onBack: () -> Unit,
    viewModel: HeadBreakdownsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Breakdowns") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.open.isEmpty() && uiState.resolved.isEmpty() -> Text(
                    "No breakdowns for ${uiState.plantId.ifBlank { "your plant" }}.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (uiState.open.isNotEmpty()) {
                        item { SectionHeader("Active (${uiState.open.size})") }
                        items(uiState.open, key = { it.id }) { FlagCard(it) }
                    }
                    if (uiState.resolved.isNotEmpty()) {
                        item { SectionHeader("Resolved · last 7 days (${uiState.resolved.size})") }
                        items(uiState.resolved, key = { "r-${it.id}" }) { FlagCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
private fun FlagCard(flag: BreakdownFlag) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(flag.machineId, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = {}, enabled = false,
                    label = { Text(flag.status.name.replace('_', ' ')) },
                )
            }
            Text("Severity ${flag.severity.name} · raised ${fmt(flag.raisedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            if (flag.description.isNotBlank()) {
                Text(flag.description, style = MaterialTheme.typography.bodyMedium)
            }
            flag.acknowledgedByName?.let { Text("Acknowledged by $it", style = MaterialTheme.typography.bodySmall) }
            flag.resolvedByName?.let { Text("Resolved by $it", style = MaterialTheme.typography.bodySmall) }
            flag.qcStatus?.let {
                Text("QC: ${it.name.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

private val DATE_FMT = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
private fun fmt(ts: Long): String = DATE_FMT.format(Date(ts))
