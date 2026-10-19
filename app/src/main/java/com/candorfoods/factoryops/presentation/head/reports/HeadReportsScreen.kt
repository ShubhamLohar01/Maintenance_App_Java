package com.candorfoods.factoryops.presentation.head.reports

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.presentation.reports.SummaryReportCard
import kotlinx.coroutines.launch

/** Head's "Reports" hub: five date-filterable history views. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadReportsScreen(
    onBack: () -> Unit,
    onMachineReadings: () -> Unit,
    onFloorReadings: () -> Unit,
    onBreakdownHistory: () -> Unit,
    onQcActivityHistory: () -> Unit,
    onPmHistory: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Scrollable: the Summary Report card plus five tiles overflow a 640dp-tall
        // screen, and a plain Column clips the overflow with no way to reach it —
        // "PM History" simply could not be tapped on a small device.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SummaryReportCard(
                onMessage = { message ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                },
            )
            ReportTile(Icons.Filled.Bolt, "Machine Readings", "Per-machine kWh, date-wise", onMachineReadings)
            ReportTile(Icons.Filled.DateRange, "Floor Readings", "Per-floor meter readings, date-wise", onFloorReadings)
            ReportTile(Icons.Filled.Warning, "Breakdown History", "Every breakdown, open or closed", onBreakdownHistory)
            ReportTile(Icons.Filled.CheckCircle, "QC Activity History", "Every QC decision, date-wise", onQcActivityHistory)
            ReportTile(Icons.Filled.Build, "PM History", "Every PM work order, live or closed", onPmHistory)
        }
    }
}

@Composable
private fun ReportTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(Spacing.base))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
