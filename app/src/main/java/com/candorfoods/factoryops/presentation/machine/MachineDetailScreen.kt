package com.candorfoods.factoryops.presentation.machine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.R
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.ProductionRun
import com.candorfoods.factoryops.presentation.common.components.ConfirmDialog
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.LoadingOverlay
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.domain.usecase.energy.cumulativeActiveMs
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.util.DateTimeFormatter
import com.candorfoods.factoryops.util.NumberFormatter
import kotlinx.coroutines.delay

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

    // Live 1-second tick so the active-time / recess timer counts visibly on screen.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uiState.inLocalSession) {
        if (uiState.inLocalSession) {
            while (true) { nowMs = System.currentTimeMillis(); delay(1_000) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is MachineDetailUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    if (uiState.showStartDialog) {
        ConfirmDialog(
            title        = stringResource(R.string.machine_dialog_start_title),
            message      = stringResource(R.string.machine_dialog_start_message, uiState.asset?.assetName ?: ""),
            confirmLabel = stringResource(R.string.machine_action_start),
            onConfirm    = viewModel::onConfirmStart,
            onDismiss    = viewModel::onDismissDialogs
        )
    }
    if (uiState.showStopDialog) {
        ConfirmDialog(
            title        = stringResource(R.string.machine_dialog_stop_title),
            message      = stringResource(R.string.machine_dialog_stop_message, uiState.asset?.assetName ?: ""),
            confirmLabel = stringResource(R.string.machine_action_stop),
            onConfirm    = viewModel::onConfirmStop,
            onDismiss    = viewModel::onDismissDialogs
        )
    }
    if (uiState.showShutDownDialog) {
        ConfirmDialog(
            title        = stringResource(R.string.machine_dialog_shutdown_title),
            message      = stringResource(R.string.machine_dialog_shutdown_message, uiState.asset?.assetName ?: ""),
            confirmLabel = stringResource(R.string.machine_action_shut_down),
            onConfirm    = viewModel::onConfirmShutDown,
            onDismiss    = viewModel::onDismissShutDownDialogs
        )
    }
    if (uiState.showRestoreDialog) {
        ConfirmDialog(
            title        = stringResource(R.string.machine_dialog_restore_title),
            message      = stringResource(R.string.machine_dialog_restore_message, uiState.asset?.assetName ?: ""),
            confirmLabel = stringResource(R.string.machine_action_restore),
            onConfirm    = viewModel::onConfirmRestore,
            onDismiss    = viewModel::onDismissShutDownDialogs
        )
    }

    if (uiState.showEditSheet) {
        EditAssetSheet(
            form     = uiState.editForm,
            isSaving = uiState.isSavingEdit,
            error    = uiState.editError,
            onChange = viewModel::onEditFormChange,
            onSave   = viewModel::onSaveEdit,
            onDismiss = viewModel::onEditDismiss,
        )
    }

    LoadingOverlay(isLoading = uiState.isLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uiState.asset?.assetName ?: stringResource(R.string.machine_asset_fallback)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.machine_cd_back)) }
                    },
                    actions = {
                        if (uiState.asset != null) {
                            if (uiState.isSupervisor) {
                                IconButton(onClick = viewModel::onEditClicked) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.machine_cd_edit))
                                }
                            }
                            if (uiState.canToggleShutDown) {
                                if (uiState.isShutDown) {
                                    IconButton(onClick = viewModel::onRestoreClicked) {
                                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.machine_cd_restore))
                                    }
                                } else {
                                    IconButton(onClick = viewModel::onShutDownClicked) {
                                        Icon(Icons.Default.Block, contentDescription = stringResource(R.string.machine_cd_shut_down))
                                    }
                                }
                            }
                        }
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

                    if (uiState.isShutDown) {
                        item { ShutDownBanner() }
                    }

                    if (uiState.underBreakdown) {
                        item { BreakdownBanner(uiState.activeBreakdown!!) }
                    }

                    when {
                        uiState.onRecess -> item {
                            RecessBanner(
                                accumulatedActiveMs = uiState.accumulatedActiveMs,
                                recessStartedAtMs = uiState.recessStartedAtMs,
                                nowMs = nowMs,
                            )
                        }
                        uiState.isLocalRunning -> item {
                            RunningSessionBanner(
                                operatorName = uiState.runOperatorName,
                                accumulatedActiveMs = uiState.accumulatedActiveMs,
                                currentSegmentStartMs = uiState.currentSegmentStartMs,
                                nowMs = nowMs,
                            )
                        }
                        uiState.hasActiveRun -> item {
                            RunningBanner(uiState.runOperatorName, uiState.runStartedAtMs)
                        }
                    }

                    // Production Start/Stop is OPERATOR-only. Supervisor/Head view read-only.
                    if (uiState.isOperator) {
                        item {
                            when {
                                // Paused → Resume the run or end the shift.
                                uiState.onRecess -> Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PrimaryButton(
                                        text = stringResource(R.string.machine_action_resume),
                                        onClick = viewModel::onResumeClicked,
                                        style = PrimaryButtonStyle.Success,
                                        size = PrimaryButtonSize.Hero,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    PrimaryButton(
                                        text = stringResource(R.string.machine_action_stop_end_shift),
                                        onClick = viewModel::onStopClicked,
                                        style = PrimaryButtonStyle.Danger,
                                        size = PrimaryButtonSize.Hero,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                // Running on this device → Recess (pause) or Stop.
                                uiState.isLocalRunning && uiState.canStop -> Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PrimaryButton(
                                        text = stringResource(R.string.machine_action_recess),
                                        onClick = viewModel::onRecessClicked,
                                        style = PrimaryButtonStyle.Outlined,
                                        size = PrimaryButtonSize.Hero,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    PrimaryButton(
                                        text = stringResource(R.string.machine_action_production_stop),
                                        onClick = viewModel::onStopClicked,
                                        style = PrimaryButtonStyle.Danger,
                                        size = PrimaryButtonSize.Hero,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                // Backend-only run started by this operator (no local session) → just Stop.
                                uiState.hasActiveRun && uiState.canStop -> PrimaryButton(
                                    text = stringResource(R.string.machine_action_production_stop),
                                    onClick = viewModel::onStopClicked,
                                    style = PrimaryButtonStyle.Danger,
                                    size = PrimaryButtonSize.Hero,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                // Running, but this user can't stop it (someone else's run,
                                // or the backend hasn't supplied a run_id yet).
                                uiState.runningButLocked -> Text(
                                    stringResource(R.string.machine_run_locked, uiState.runOperatorName ?: stringResource(R.string.machine_another_operator)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                                uiState.isShutDown -> {
                                    // Start is blocked while the machine is shut down.
                                    PrimaryButton(
                                        text = stringResource(R.string.machine_action_production_start),
                                        onClick = {},
                                        enabled = false,
                                        style = PrimaryButtonStyle.Success,
                                        size = PrimaryButtonSize.Hero,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                uiState.underBreakdown -> {
                                    // Start is blocked until a technician/QC closes the breakdown.
                                    PrimaryButton(
                                        text = stringResource(R.string.machine_action_production_start),
                                        onClick = {},
                                        enabled = false,
                                        style = PrimaryButtonStyle.Success,
                                        size = PrimaryButtonSize.Hero,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                else -> PrimaryButton(
                                    text = stringResource(R.string.machine_action_production_start),
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
                    if (uiState.isOperator && !uiState.underBreakdown && !uiState.isShutDown) {
                        item {
                            PrimaryButton(
                                text = stringResource(R.string.machine_action_report_breakdown),
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
                            Text(stringResource(R.string.machine_todays_runs), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { onHistoryClick(asset.assetId) }) {
                                Text(stringResource(R.string.machine_30_day_history))
                            }
                        }
                    }

                    if (uiState.todaysRuns.isEmpty()) {
                        item {
                            Text(stringResource(R.string.machine_no_runs_today), style = MaterialTheme.typography.bodyMedium,
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
                InfoItem(stringResource(R.string.machine_label_rated_power), power)
                InfoItem(stringResource(R.string.machine_label_category), asset.category.ifBlank { "—" })
                asset.condition?.takeIf { it.isNotBlank() }?.let { InfoItem(stringResource(R.string.machine_label_condition), it) }
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
                stringResource(R.string.machine_status_running),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = running,
            )
            Text(
                stringResource(R.string.machine_operator, operatorName ?: "—"),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (startedAtMs != null) {
                Text(
                    stringResource(R.string.machine_since, DateTimeFormatter.formatTime(startedAtMs), DateTimeFormatter.formatDuration(startedAtMs, System.currentTimeMillis())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun RunningSessionBanner(
    operatorName: String?,
    accumulatedActiveMs: Long,
    currentSegmentStartMs: Long?,
    nowMs: Long,
) {
    val running = FactoryTheme.colors.statusRunning
    val activeMs = cumulativeActiveMs(accumulatedActiveMs, currentSegmentStartMs, nowMs)
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.machine_status_running), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = running)
            Text(stringResource(R.string.machine_operator, operatorName ?: "—"), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.machine_active_time, formatHms(activeMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun RecessBanner(
    accumulatedActiveMs: Long,
    recessStartedAtMs: Long?,
    nowMs: Long,
) {
    val paused = FactoryTheme.colors.statusFlagged
    val pausedForMs = recessStartedAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.machine_status_on_recess), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = paused)
            Text(
                stringResource(R.string.machine_active_time_paused, formatHms(accumulatedActiveMs)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.machine_paused_for, formatHms(pausedForMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

/** Format a duration in ms as HH:MM:SS. */
private fun formatHms(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

@Composable
private fun ShutDownBanner() {
    val danger = FactoryTheme.colors.statusStopped
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.machine_status_shut_down),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = danger,
            )
            Text(
                stringResource(R.string.machine_shutdown_banner_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.machine_shutdown_banner_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun BreakdownBanner(flag: BreakdownFlag) {
    val danger = FactoryTheme.colors.statusStopped
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.machine_status_under_breakdown),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = danger,
            )
            Text(
                stringResource(
                    R.string.machine_severity_status,
                    when (flag.severity) {
                        Severity.CRITICAL -> stringResource(R.string.machine_severity_critical)
                        Severity.MAJOR -> stringResource(R.string.machine_severity_major)
                        Severity.MINOR -> stringResource(R.string.machine_severity_minor)
                    },
                    when (flag.status) {
                        BreakdownStatus.OPEN -> stringResource(R.string.machine_bd_status_open)
                        BreakdownStatus.ACKNOWLEDGED -> stringResource(R.string.machine_bd_status_acknowledged)
                        BreakdownStatus.PENDING_QC -> stringResource(R.string.machine_bd_status_pending_qc)
                        BreakdownStatus.CLOSED -> stringResource(R.string.machine_bd_status_closed)
                        BreakdownStatus.REOPENED -> stringResource(R.string.machine_bd_status_reopened)
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            if (flag.description.isNotBlank()) {
                Text(flag.description, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                stringResource(R.string.machine_breakdown_start_disabled),
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
                    Text(stringResource(R.string.machine_run_running), color = MaterialTheme.colorScheme.primary)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAssetSheet(
    form: AssetEditForm,
    isSaving: Boolean,
    error: String?,
    onChange: ((AssetEditForm) -> AssetEditForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.machine_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            EditField(stringResource(R.string.machine_field_asset_name), form.assetName) { v -> onChange { it.copy(assetName = v) } }
            EditField(stringResource(R.string.machine_field_building), form.building) { v -> onChange { it.copy(building = v) } }
            EditField(stringResource(R.string.machine_field_category), form.category) { v -> onChange { it.copy(category = v) } }
            EditField(stringResource(R.string.machine_field_sub_location), form.subLocation) { v -> onChange { it.copy(subLocation = v) } }
            EditField(stringResource(R.string.machine_field_quantity), form.quantity, keyboardType = KeyboardType.Number) { v ->
                onChange { it.copy(quantity = v.filter(Char::isDigit)) }
            }
            EditField(stringResource(R.string.machine_field_model_no), form.modelNo) { v -> onChange { it.copy(modelNo = v) } }
            EditField(stringResource(R.string.machine_field_serial_no), form.serialNo) { v -> onChange { it.copy(serialNo = v) } }
            EditField(stringResource(R.string.machine_field_power_load), form.powerLoad) { v -> onChange { it.copy(powerLoad = v) } }
            EditField(stringResource(R.string.machine_field_condition), form.condition) { v -> onChange { it.copy(condition = v) } }
            EditField(stringResource(R.string.machine_field_remarks), form.remarks, singleLine = false) { v -> onChange { it.copy(remarks = v) } }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            PrimaryButton(
                text = if (isSaving) stringResource(R.string.machine_action_saving) else stringResource(R.string.machine_action_save_changes),
                onClick = onSave,
                enabled = !isSaving,
                style = PrimaryButtonStyle.Success,
                size = PrimaryButtonSize.Hero,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}
