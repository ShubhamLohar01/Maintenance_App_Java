package com.candorfoods.factoryops.presentation.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.util.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: BreakdownListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Breakdown Checklist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Create") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = onCreate,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.items.isEmpty() -> Text(
                    "No breakdown records yet.\nApproved breakdowns will appear here to complete the F.06.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(uiState.items, key = { it.flag.id }) { item ->
                        BreakdownRecordCard(item, onOpen = { onOpen(item.flag.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownRecordCard(item: BreakdownListItem, onOpen: () -> Unit) {
    val flag = item.flag
    val name = flag.assetName?.ifBlank { null } ?: flag.machineId
    val enabled = item.isApproved
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.7f else 0.45f)

    FactoryCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (enabled) onOpen else null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else muted,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(breakdownStatusLabel(flag), enabled)
            }
            if (!flag.building.isNullOrBlank()) {
                Text(flag.building!!, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Text(
                "${DateTimeFormatter.formatDate(flag.raisedAt)} · ${flag.description}",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (enabled) {
                    Text(
                        if (item.isCompleted) "Completed — tap to view / download" else "Tap to complete F.06",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Default.Lock, contentDescription = null,
                        modifier = Modifier.size(16.dp), tint = muted)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Awaiting QC approval", style = MaterialTheme.typography.labelMedium, color = muted)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, approved: Boolean) {
    val container =
        if (approved) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = container,
            disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        ),
    )
}
