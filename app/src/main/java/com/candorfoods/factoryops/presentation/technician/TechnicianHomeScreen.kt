package com.candorfoods.factoryops.presentation.technician

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianHomeScreen(
    onTicketClick: (String) -> Unit,
    onPmClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onMachineMasterClick: () -> Unit,
    viewModel: TechnicianHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var overflowExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val hasOverduePm = uiState.pmTasks.any { it.wo.escalationTier > 0 }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — silent degrade either way */ }

    LaunchedEffect(hasOverduePm) {
        if (!hasOverduePm) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FactoryOps — Maintenance", style = MaterialTheme.typography.titleLarge)
                        uiState.currentUser?.let {
                            Text("${it.name} — ${it.plantId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    Box {
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Machine Master") },
                                onClick = {
                                    overflowExpanded = false
                                    onMachineMasterClick()
                                },
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                TabItem("Open",      uiState.openTickets.size,     selectedTab == 0) { selectedTab = 0 }
                TabItem("My Active", uiState.activeTickets.size,   selectedTab == 1) { selectedTab = 1 }
                TabItem("Resolved",  uiState.resolvedTickets.size, selectedTab == 2) { selectedTab = 2 }
                TabItem("PM tasks",  uiState.pmTasks.size,         selectedTab == 3) { selectedTab = 3 }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else when (selectedTab) {
                3 -> PmTasksList(uiState.pmTasks, onPmClick)
                else -> {
                    val rows = when (selectedTab) {
                        0    -> uiState.openTickets
                        1    -> uiState.activeTickets
                        else -> uiState.resolvedTickets
                    }
                    val emptyMsg = when (selectedTab) {
                        0    -> "No open tickets — nothing flagged in this plant."
                        1    -> "Nothing acknowledged by you. Tap a ticket on the Open tab to acknowledge it."
                        else -> "No resolved tickets in the last 30 days."
                    }
                    if (rows.isEmpty()) {
                        EmptyState(emptyMsg)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(rows, key = { it.flag.id }) { row ->
                                TicketCard(row = row, onClick = { onTicketClick(row.flag.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PmTasksList(rows: List<PmTaskRowUiModel>, onClick: (String) -> Unit) {
    if (rows.isEmpty()) {
        EmptyState("No active PM tasks. Scheduler will assign them as plans come due.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(rows, key = { it.wo.id }) { row ->
            PmTaskCard(row = row, onClick = { onClick(row.wo.id) })
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun TabItem(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Text(
                if (count > 0) "$label ($count)" else label,
                style = MaterialTheme.typography.labelLarge
            )
        }
    )
}
