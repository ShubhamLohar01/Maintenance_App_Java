package com.candorfoods.factoryops.presentation.breakdown

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.LoadingOverlay
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownFlagScreen(
    onBack: () -> Unit,
    viewModel: BreakdownFlagViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is BreakdownFlagUiEvent.FlagRaised     -> onBack()
                is BreakdownFlagUiEvent.ShowSnackbar   -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LoadingOverlay(isLoading = uiState.isLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Flag Breakdown") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Severity", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Severity.values().forEach { sev ->
                        val selected = uiState.severity == sev
                        val color = when (sev) {
                            Severity.CRITICAL -> FactoryTheme.colors.statusStopped
                            Severity.MAJOR    -> FactoryTheme.colors.statusFlagged
                            Severity.MINOR    -> MaterialTheme.colorScheme.primary
                        }
                        FilterChip(
                            selected = selected,
                            onClick  = { viewModel.onSeverityChanged(sev) },
                            label    = { Text(sev.name) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color.copy(alpha = 0.2f),
                                selectedLabelColor     = color
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value         = uiState.description,
                    onValueChange = viewModel::onDescriptionChanged,
                    label         = { Text("Description") },
                    placeholder   = { Text("Describe the breakdown — noise, smoke, vibration, etc.") },
                    minLines      = 4,
                    modifier      = Modifier.fillMaxWidth(),
                    isError       = uiState.error != null
                )

                if (uiState.error != null) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                FactoryCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Photo (Optional)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Photo capture via camera — available once permissions granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = FactoryTheme.colors.onSurfaceMuted,
                        )
                        uiState.beforePhotoLocalPath?.let {
                            Text("Photo attached: …${it.takeLast(20)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                PrimaryButton(
                    text     = "Submit Breakdown Flag",
                    onClick  = viewModel::onSubmit,
                    style    = PrimaryButtonStyle.Danger,
                    size     = PrimaryButtonSize.Primary,
                    loading  = uiState.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
