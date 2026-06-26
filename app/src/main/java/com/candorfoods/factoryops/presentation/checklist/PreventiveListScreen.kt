package com.candorfoods.factoryops.presentation.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.ChecklistRecordSummary
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreventiveListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onDraft: (Int) -> Unit,
    onFinal: (Int) -> Unit,
    readOnly: Boolean = false,
    viewModel: PreventiveListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Head sees only filled (submitted) records; the creator sees drafts too.
    val records = if (readOnly) uiState.records.filterNot { it.isDraft } else uiState.records

    // Reload whenever we return to this screen (e.g. after creating a record).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (readOnly) "Checklist Records" else "Preventive Checklist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!readOnly) {
                ExtendedFloatingActionButton(
                    text = { Text("Create") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = onCreate,
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.error != null -> CenteredMessage(
                    "Couldn't load records — ${uiState.error}."
                )
                records.isEmpty() -> CenteredMessage(
                    if (readOnly) "No filled checklists yet." else "No checklists yet. Tap Create to fill the first one."
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(records, key = { it.id }) { record ->
                        RecordCard(
                            record,
                            onClick = {
                                if (!readOnly && record.isDraft) onDraft(record.id) else onFinal(record.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordCard(record: ChecklistRecordSummary, onClick: () -> Unit) {
    val typeLabel = when (record.formType.uppercase()) {
        "MONTHLY"   -> "Monthly (50a)"
        "QUARTERLY" -> "Quarterly (50b)"
        else        -> record.formType
    }
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(typeLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (record.isDraft) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text("Draft") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
            Text(
                "Date ${record.checklistDate} · #${record.id}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            if (record.doneBy.isNotBlank()) {
                Text(
                    "Done by ${record.doneBy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.CenteredMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
    )
}
