package com.candorfoods.factoryops.presentation.spareparts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.spareparts.SparePartLogEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparePartHistoryScreen(
    onBack: () -> Unit,
    viewModel: SparePartHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usage History", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.error!!,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            state.entries.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No usage recorded yet.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.entries, key = { it.id }) { entry -> HistoryCard(entry) }
            }
        }
    }
}

/**
 * Chip colour for a log action. Covers the two labels WorkType does not model —
 * "Restock" and the legacy "Not specified" — and falls back to grey for anything
 * unrecognised, so a work type added server-side later still renders its text
 * instead of crashing this screen.
 */
@Composable
private fun actionColor(action: String): Color = when (action) {
    "Breakdown" -> MaterialTheme.colorScheme.error
    "PM Maintenance" -> MaterialTheme.colorScheme.primary
    "Replacement / Wear & Tear" -> MaterialTheme.colorScheme.tertiary
    "Restock" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
}

@Composable
private fun HistoryCard(entry: SparePartLogEntry) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(entry.action, color = actionColor(entry.action)) },
                )
                Text(
                    listOfNotNull(
                        entry.performedAt?.take(10),
                        entry.performedByName,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                entry.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                entry.signedQuantity,
                style = MaterialTheme.typography.titleLarge,
                color = if (entry.isInbound) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}
