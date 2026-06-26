package com.candorfoods.factoryops.presentation.machine

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.ProductionRun
import com.candorfoods.factoryops.presentation.common.components.ConfirmDialog
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.LoadingOverlay
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.util.DateTimeFormatter
import com.candorfoods.factoryops.util.NumberFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineDetailScreen(
    onBack: () -> Unit,
    onHistoryClick: (String) -> Unit,
    onBreakdownClick: (String) -> Unit,   // opens the breakdown-flag screen for this asset
    viewModel: MachineDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is MachineDetailUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    if (uiState.showStartDialog) {
        ConfirmDialog(
            title        = "Start Production",
            message      = "Start production run on ${uiState.asset?.assetName}?",
            confirmLabel = "Start",
            onConfirm    = viewModel::onConfirmStart,
            onDismiss    = viewModel::onDismissDialogs
        )
    }
    if (uiState.showStopDialog) {
        ConfirmDialog(
            title        = "Stop Production",
            message      = "Stop production run on ${uiState.asset?.assetName}?",
            confirmLabel = "Stop",
            onConfirm    = viewModel::onConfirmStop,
            onDismiss    = viewModel::onDismissDialogs
        )
    }

    LoadingOverlay(isLoading = uiState.isLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uiState.asset?.assetName ?: "Asset") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            val asset = uiState.asset
            if (asset == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { AssetHeaderCard(asset) }

                    if (uiState.underBreakdown) {
                        item { BreakdownBanner(uiState.activeBreakdown!!) }
                    }

                    if (uiState.hasActiveRun) {
                        item { RunningBanner(uiState.runOperatorName, uiState.runStartedAtMs) }
                    }

                    // Production Start/Stop is OPERATOR-only. Supervisor/Head view read-only.
                    if (uiState.isOperator) {
                        item {
                            when {
                                uiState.hasActiveRun && uiState.canStop -> PrimaryButton(
                                    text = "Production Stop",
                                    onClick = viewModel::onStopClicked,
                                    style = PrimaryButtonStyle.Danger,
                                    size = PrimaryButtonSize.Hero,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                // Running, but this user can't stop it (someone else's run,
                                // or the backend hasn't supplied a run_id yet).
                                uiState.runningButLocked -> Text(
                                    "This run was started by ${uiState.runOperatorName ?: "another operator"} — only they can stop it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                                uiState.underBreakdown -> {
                                    // Start is blocked until a technician/QC closes the breakdown.
                                    PrimaryButton(
                                        text = "Production Start",
                                        onClick = {},
                                        enabled = false,
                                        style = PrimaryButtonStyle.Success,
                                        size = PrimaryButtonSize.Hero,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                else -> PrimaryButton(
                                    text = "Production Start",
                                    onClick = viewModel::onStartClicked,
                                    style = PrimaryButtonStyle.Success,
                                    size = PrimaryButtonSize.Hero,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    // Operator-raised breakdown flag → notifies technicians. Hidden once
                    // a breakdown is already open on this machine (can't double-report).
                    if (uiState.isOperator && !uiState.underBreakdown) {
                        item {
                            PrimaryButton(
                                text = "Report Breakdown",
                                onClick = { onBreakdownClick(asset.assetId) },
                                style = PrimaryButtonStyle.Outlined,
                                size = PrimaryButtonSize.Primary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    item {
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Today's Runs", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { onHistoryClick(asset.assetId) }) {
                                Text("30-day history")
                            }
                        }
                    }

                    if (uiState.todaysRuns.isEmpty()) {
                        item {
                            Text("No runs logged today", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    } else {
                        items(uiState.todaysRuns, key = { it.id }) { run ->
                            RunRowItem(run = run)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetHeaderCard(asset: MtMachine) {
    val location = listOf(asset.building, asset.subLocation).filter { it.isNotBlank() }.joinToString(" · ")
    val power = asset.ratedKw?.let { NumberFormatter.formatKw(it) } ?: asset.powerLoadRaw ?: "—"
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(asset.assetId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (location.isNotBlank()) {
                Text(location, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                InfoItem("Rated Power", power)
                InfoItem("Category", asset.category.ifBlank { "—" })
                asset.condition?.takeIf { it.isNotBlank() }?.let { InfoItem("Condition", it) }
            }
        }
    }
}

@Composable
private fun RunningBanner(operatorName: String?, startedAtMs: Long?) {
    val running = FactoryTheme.colors.statusRunning
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "● RUNNING",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = running,
            )
            Text(
                "Operator: ${operatorName ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (startedAtMs != null) {
                Text(
                    "Since ${DateTimeFormatter.formatTime(startedAtMs)} · ${DateTimeFormatter.formatDuration(startedAtMs, System.currentTimeMillis())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun BreakdownBanner(flag: BreakdownFlag) {
    val danger = FactoryTheme.colors.statusStopped
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "● UNDER BREAKDOWN",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = danger,
            )
            Text(
                "Severity: ${flag.severity.name}  ·  Status: ${flag.status.name.replace('_', ' ')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            if (flag.description.isNotBlank()) {
                Text(flag.description, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Production Start is disabled until a technician resolves and QC clears it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RunRowItem(run: ProductionRun) {
    FactoryCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 12.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(DateTimeFormatter.formatTime(run.startedAt), fontWeight = FontWeight.Bold)
                if (run.isRunning) {
                    Text("Running…", color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("→ ${DateTimeFormatter.formatTime(run.endedAt!!)}")
                    Text(DateTimeFormatter.formatDuration(run.startedAt, run.endedAt!!),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            run.computedKwh?.let {
                Text(NumberFormatter.formatKwh(it), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
