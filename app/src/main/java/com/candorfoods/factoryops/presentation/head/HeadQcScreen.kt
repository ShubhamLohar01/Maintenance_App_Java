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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadQcScreen(
    onBack: () -> Unit,
    viewModel: HeadQcViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QC Activity") },
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
                uiState.awaiting.isEmpty() && uiState.decided.isEmpty() -> Text(
                    "No QC activity for ${uiState.plantId.ifBlank { "your plant" }}.",
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
                    if (uiState.awaiting.isNotEmpty()) {
                        item { SectionHeader("Awaiting QC (${uiState.awaiting.size})") }
                        items(uiState.awaiting, key = { "a-${it.id}" }) { QcCard(it, decided = false) }
                    }
                    if (uiState.decided.isNotEmpty()) {
                        item { SectionHeader("Decided · last 7 days (${uiState.decided.size})") }
                        items(uiState.decided, key = { "d-${it.id}" }) { QcCard(it, decided = true) }
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
private fun QcCard(flag: BreakdownFlag, decided: Boolean) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(flag.machineId, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                flag.qcStatus?.let {
                    AssistChip(onClick = {}, enabled = false, label = { Text(it.name.replace('_', ' ')) })
                }
            }
            if (flag.description.isNotBlank()) {
                Text(flag.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (decided) {
                flag.qcDecidedByName?.let {
                    Text("QC by $it", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                flag.qcDecisionNotes?.takeIf { it.isNotBlank() }?.let {
                    Text("Note: $it", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            } else {
                flag.resolvedByName?.let {
                    Text("Resolved by $it — awaiting QC", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
    }
}
