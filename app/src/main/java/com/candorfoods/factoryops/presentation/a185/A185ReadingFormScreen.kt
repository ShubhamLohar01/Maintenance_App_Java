package com.candorfoods.factoryops.presentation.a185

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.presentation.utilities.ComputedField
import com.candorfoods.factoryops.presentation.utilities.DateField
import com.candorfoods.factoryops.presentation.utilities.NumericInputField
import com.candorfoods.factoryops.presentation.utilities.TextInputField
import com.candorfoods.factoryops.presentation.utilities.formatUtilityNumber

/**
 * A-185 daily readings form: one screen, three sections (HT Panel / Cold Room / UV),
 * sharing a single date. Initials pre-fill from the previous day; consumptions are shown
 * live but are computed authoritatively by the backend on save. A-185 only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A185ReadingFormScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: A185ReadingFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is A185FormEvent.Saved -> onSaved()
                is A185FormEvent.Error -> snackbar.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("HT / Cold Room / UV", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (state.isEditing) "Edit reading" else "New reading",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            DateField(value = state.readingDate, onChange = viewModel::setDate)
            state.sourceDate?.let {
                Text(
                    "Openings carried from $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            // HT Panel — includes the read-only power factor (never editable).
            SectionCard("HT Panel", error = state.htError) {
                NumericInputField("KWH Initial", state.htInitial, viewModel::onHtInitial)
                NumericInputField("KWH Final", state.htFinal, viewModel::onHtFinal)
                ComputedField("PF (fixed)", formatUtilityNumber(state.htPowerFactor, 2))
                ComputedField("KWH Consumption", formatUtilityNumber(state.htConsumption, 3))
            }

            SectionCard("Cold Room", error = state.coldError) {
                NumericInputField("Initial Reading", state.coldInitial, viewModel::onColdInitial)
                NumericInputField("Final Reading", state.coldFinal, viewModel::onColdFinal)
                ComputedField("Cold Consumption", formatUtilityNumber(state.coldConsumption, 3))
            }

            SectionCard("UV", error = state.uvError) {
                NumericInputField("Initial Reading", state.uvInitial, viewModel::onUvInitial)
                NumericInputField("Final Reading", state.uvFinal, viewModel::onUvFinal)
                ComputedField("UV Consumption", formatUtilityNumber(state.uvConsumption, 3))
            }

            TextInputField("Remark (optional)", state.remark, viewModel::onRemark)

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            PrimaryButton(
                text = if (state.isSubmitting) "Saving…" else if (state.isEditing) "Update readings" else "Save readings",
                onClick = viewModel::submit,
                enabled = state.canSubmit && !state.isSubmitting,
                size = PrimaryButtonSize.Hero,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isSubmitting) {
                CircularProgressIndicator(Modifier.padding(top = Spacing.sm))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    error: Boolean,
    content: @Composable () -> Unit,
) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            content()
            if (error) {
                Text(
                    "Final reading must be greater than or equal to Initial.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
