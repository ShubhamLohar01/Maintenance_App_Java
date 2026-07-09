package com.candorfoods.factoryops.presentation.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.navigation.Screen

/**
 * A single launcher box on the role dashboard. Tiles are pure config — the grid
 * renders whatever [dashboardTilesFor] returns, so adding / renaming / reordering
 * a box is a one-line edit here, no UI changes.
 */
data class DashboardTile(
    val label: String,
    val icon: ImageVector,
    val action: DashboardAction,
)

/** Where a tile goes when tapped. */
sealed interface DashboardAction {
    /** Navigate to an existing screen route. */
    data class Open(val route: String) : DashboardAction
    /** Section not built yet — routes to the shared "Coming soon" placeholder. */
    data object ComingSoon : DashboardAction
}

/**
 * The box set for a given role. HEAD shares the supervisor set. Roles without a
 * dedicated grid fall back to an empty list (the router only sends TECHNICIAN /
 * SUPERVISOR / HEAD here for now).
 */
fun dashboardTilesFor(role: UserRole): List<DashboardTile> = when (role) {
    UserRole.TECHNICIAN -> listOf(
        DashboardTile("Machine List",   Icons.Filled.Build,            DashboardAction.Open(Screen.MachineMaster.route)),
        DashboardTile("Checklist Form", Icons.Filled.Edit,             DashboardAction.Open(Screen.ChecklistHub.route)),
        DashboardTile("Breakdowns",     Icons.Filled.Warning,          DashboardAction.Open(Screen.TechnicianHome.route)),
        DashboardTile("Transfer",       Icons.AutoMirrored.Filled.Send, DashboardAction.Open(Screen.TransferRecords.route)),
        DashboardTile("Daily Reading",  Icons.Filled.Speed,            DashboardAction.Open(Screen.DailyReading.route)),
        DashboardTile("Reports",        Icons.Filled.DateRange,        DashboardAction.ComingSoon),
    )
    UserRole.SUPERVISOR,
    UserRole.HEAD -> listOf(
        DashboardTile("Machine List",    Icons.Filled.Build,            DashboardAction.Open(Screen.MachineList.route)),
        DashboardTile("PM Manager",      Icons.Filled.List,             DashboardAction.Open(Screen.SupervisorHome.route)),
        DashboardTile("QC Status",       Icons.Filled.CheckCircle,      DashboardAction.Open(Screen.QcHome.route)),
        DashboardTile("Transfer",        Icons.AutoMirrored.Filled.Send, DashboardAction.Open(Screen.TransferRecords.route)),
        DashboardTile("Schedule Electric Assets", Icons.Filled.Schedule, DashboardAction.Open(Screen.ScheduleElectricAssets.route)),
        DashboardTile("Checklist Forms", Icons.Filled.Edit,             DashboardAction.ComingSoon),
        DashboardTile("Reports",         Icons.Filled.DateRange,        DashboardAction.ComingSoon),
    )
    else -> emptyList()
}
