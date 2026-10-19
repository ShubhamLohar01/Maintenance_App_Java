package com.candorfoods.factoryops.presentation.head

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.presentation.common.RequestNotificationPermission
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.presentation.dashboard.DashboardViewModel

/** Head's read-only oversight launcher — grouped into sections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadHomeScreen(
    onMachines: () -> Unit,
    onBreakdowns: () -> Unit,
    onQc: () -> Unit,
    onChecklistRecords: () -> Unit,
    onReports: () -> Unit,
    onTransfers: () -> Unit,
    onSettings: () -> Unit,
    onManageUsers: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val scope = currentUser?.plantId?.let { if (it.equals("both", true)) "A185 + W202" else it }.orEmpty()

    // Prompt for the Android 13+ notification permission so escalations reach the head.
    RequestNotificationPermission()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Head — Oversight", style = MaterialTheme.typography.titleLarge)
                        currentUser?.let {
                            Text(
                                "${it.name}${if (scope.isNotBlank()) " — $scope" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { GroupHeader("Live operations") }
            item { SectionCard(Icons.AutoMirrored.Filled.List, "Machine List", "Who's running what, in real time", onClick = onMachines) }
            item { SectionCard(Icons.Filled.Warning, "Breakdowns", "Active & recent breakdown flags", onClick = onBreakdowns) }
            item { SectionCard(Icons.Filled.CheckCircle, "QC Activity", "Pending & recently decided QC", onClick = onQc) }

            item { GroupHeader("Records") }
            item { SectionCard(Icons.Filled.Edit, "Checklist Records", "Filled preventive checklists", onClick = onChecklistRecords) }
            item { SectionCard(Icons.Filled.Build, "Transfer Records", "Machine transfers between warehouses", soon = true, onClick = onTransfers) }

            item { GroupHeader("Analytics") }
            item { SectionCard(Icons.Filled.DateRange, "Reports", "Machine, floor, breakdown & QC history", onClick = onReports) }

            item { GroupHeader("Administration") }
            item { SectionCard(Icons.Filled.Person, "Manage Users", "Add, edit or remove app users", onClick = onManageUsers) }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
    )
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    soon: Boolean = false,
    onClick: () -> Unit,
) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (soon) {
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "SOON",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
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
