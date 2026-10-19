package com.candorfoods.factoryops.presentation.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import java.time.Instant
import java.time.ZoneOffset

/**
 * "Download Summary Report" card, shared by the Supervisor and Head Reports hubs (Task 13
 * mounts it on both). Lets the user pick a from/to date range and generates the
 * consolidated PDF via [SummaryReportViewModel].
 *
 * This is a fragment of someone else's screen: no [androidx.compose.material3.Scaffold] or
 * SnackbarHost here. [onMessage] is called once per status/error message so the host screen
 * can show it in its own snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryReportCard(
    viewModel: SummaryReportViewModel = hiltViewModel(),
    onMessage: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            onMessage(message)
            viewModel.messageShown()
        }
    }

    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text("Summary Report", style = MaterialTheme.typography.titleMedium)
            Text(
                "One consolidated PDF covering machines, utilities, and PM activity for the selected period.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                DateButton(
                    label = "From",
                    value = state.from,
                    modifier = Modifier.weight(1f),
                    onPicked = viewModel::setFrom,
                )
                DateButton(
                    label = "To",
                    value = state.to,
                    modifier = Modifier.weight(1f),
                    onPicked = viewModel::setTo,
                )
            }
            PrimaryButton(
                text = "Download Summary Report",
                onClick = { viewModel.download(context) },
                modifier = Modifier.fillMaxWidth(),
                loading = state.isGenerating,
                enabled = !state.isGenerating,
            )
        }
    }
}

/** A date field rendered as an outlined button; tapping it opens a [DatePickerDialog]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateButton(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onPicked: (String) -> Unit,
) {
    var show by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { show = true }, modifier = modifier) {
        Text("$label: $value")
    }

    if (show) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onPicked(d.toString())
                    }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}
