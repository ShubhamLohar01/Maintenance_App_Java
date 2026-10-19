package com.candorfoods.factoryops.presentation.w202

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
import androidx.compose.runtime.LaunchedEffect
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
 * W-202 daily meter form — one screen serving both meters, with the meter's own rules coming
 * from [W202FormState.kind]. Consumptions are shown live but computed authoritatively by the
 * backend on save; Main's power factor is an input, Solar's is displayed read-only. W-202 only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun W202ReadingFormScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: W202ReadingFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is W202FormEvent.Saved -> onSaved()
                is W202FormEvent.Error -> snackbar.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.kind.title, style = MaterialTheme.typography.titleLarge)
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
                    "Openings carried from $it — change them only if the meter was replaced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            SectionCard("Active Energy (KWH)", error = state.kwhError) {
                NumericInputField("KWH Initial", state.initialKwh, viewModel::onInitialKwh)
                NumericInputField("KWH Final", state.finalKwh, viewModel::onFinalKwh)
                ComputedField("KWH Consumption", formatUtilityNumber(state.kwhConsumption, 3))
            }

            SectionCard("Apparent Energy (KVAH)", error = state.kvahError) {
                NumericInputField("KVAH Initial", state.initialKvah, viewModel::onInitialKvah)
                NumericInputField("KVAH Final", state.finalKvah, viewModel::onFinalKvah)
                ComputedField("KVAH Consumption", formatUtilityNumber(state.kvahConsumption, 3))
            }

            // Main's PF is the technician's entry; Solar's is KWH ÷ KVAH, computed server-side.
            SectionCard("Power Factor", error = false) {
                if (state.kind.powerFactorEditable) {
                    NumericInputField("Power Factor", state.powerFactor, viewModel::onPowerFactor)
                    Text(
                        "Defaults to ${formatUtilityNumber(W202_DEFAULT_POWER_FACTOR, 2)} — change it if the meter reads otherwise.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                } else {
                    ComputedField("Power Factor", formatUtilityNumber(state.solarPowerFactor, 3))
                    Text(
                        "KWH consumption ÷ KVAH consumption.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }

            TextInputField("Remark (optional)", state.remark, viewModel::onRemark)

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            PrimaryButton(
                text = if (state.isSubmitting) "Saving…" else if (state.isEditing) "Update reading" else "Save reading",
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
