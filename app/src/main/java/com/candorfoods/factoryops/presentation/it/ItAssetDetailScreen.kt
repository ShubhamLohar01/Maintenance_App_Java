package com.candorfoods.factoryops.presentation.it

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.presentation.common.components.AssetEditSheet
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.util.NumberFormatter

/**
 * One IT / Electrical asset's record. Read-only; the pencil opens the shared asset
 * edit sheet for roles the backend lets write (IT technician, supervisor, head, admin).
 *
 * Asset ID and rated kW are shown but never editable — the first is the backend key,
 * the second is computed there from power load. Assigned to is display-only because
 * PUT /mt-machines/{asset_id} doesn't persist it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItAssetDetailScreen(
    assetId: String,
    onBack: () -> Unit,
    viewModel: ItAssetDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(assetId) { viewModel.onAssetId(assetId) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is ItAssetDetailUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val asset = uiState.asset
    if (uiState.showEditSheet && asset != null) {
        AssetEditSheet(
            form = uiState.editForm,
            isSaving = uiState.isSavingEdit,
            error = uiState.editError,
            onChange = viewModel::onEditFormChange,
            onSave = viewModel::onSaveEdit,
            onDismiss = viewModel::onEditDismiss,
            title = "Edit asset",
            lockedCategory = asset.category,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        asset?.assetName?.ifBlank { asset.assetId } ?: "Asset",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (asset != null && uiState.canEdit) {
                        IconButton(onClick = viewModel::onEditClicked) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit asset")
                        }
                    }
                    IconButton(onClick = viewModel::onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                asset == null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Couldn't open this asset", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        uiState.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(Spacing.base))
                    PrimaryButton(
                        text = "Retry",
                        onClick = viewModel::onRetry,
                        style = PrimaryButtonStyle.Outlined,
                    )
                }
                else -> AssetRecord(asset)
            }
        }
    }
}

@Composable
private fun AssetRecord(asset: MtMachine) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        asset.assetName.ifBlank { asset.assetId },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    DetailRow("Asset ID", asset.assetId)
                    DetailRow("Category", asset.category)
                    DetailRow("Building", asset.building)
                    DetailRow("Floor / Sub-location", asset.subLocation)
                }
            }
        }
        item {
            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Specification", style = MaterialTheme.typography.titleMedium)
                    DetailRow("Power load", asset.powerLoadRaw)
                    DetailRow("Rated kW", asset.ratedKw?.let(NumberFormatter::formatKw))
                    DetailRow("Quantity", asset.quantity?.toString())
                    DetailRow("Model No.", asset.modelNo)
                    DetailRow("Serial No.", asset.serialNo)
                }
            }
        }
        item {
            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Status", style = MaterialTheme.typography.titleMedium)
                    DetailRow("Condition", asset.condition)
                    DetailRow("Assigned to", asset.assignedTo)
                    DetailRow("Remarks", asset.remarks)
                }
            }
        }
    }
}

/** Label / value line. A blank or absent value reads as an em dash, never an empty gap. */
@Composable
private fun DetailRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = FactoryTheme.colors.onSurfaceMuted,
            modifier = Modifier.width(150.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            value?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
