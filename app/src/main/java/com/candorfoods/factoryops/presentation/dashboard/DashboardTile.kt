package com.candorfoods.factoryops.presentation.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.ReportProblem
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
    /** Which live count (if any) shows as a red corner badge on this tile. */
    val badge: TileBadge? = null,
)

/** Live alert counts a tile can display — resolved by DashboardViewModel. */
enum class TileBadge {
    /** Breakdowns needing a technician (OPEN + REOPENED). */
    OPEN_BREAKDOWNS,
    /** PM work orders assigned to the signed-in technician, not yet submitted. */
    MY_PM_TASKS,
    /** Submitted PM work orders awaiting the supervisor's approval. */
    PENDING_APPROVALS,
    /** Breakdowns + PM work orders sitting in the QC inbox. */
    PENDING_QC,
}

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
        DashboardTile("Breakdowns",     Icons.Filled.Warning,          DashboardAction.Open(Screen.TechnicianHome.route), badge = TileBadge.OPEN_BREAKDOWNS),
        DashboardTile("PM Tasks",       Icons.Filled.Schedule,         DashboardAction.Open(Screen.TechnicianPmList.route), badge = TileBadge.MY_PM_TASKS),
        DashboardTile("Transfer",       Icons.AutoMirrored.Filled.Send, DashboardAction.Open(Screen.TransferRecords.route)),
        DashboardTile("Daily Reading",  Icons.Filled.Speed,            DashboardAction.Open(Screen.DailyReadingHub.route)),
        DashboardTile("Spare Parts",    Icons.Filled.Inventory,        DashboardAction.Open(Screen.SparePartsMachineList.route)),
        DashboardTile("Reports",        Icons.Filled.DateRange,        DashboardAction.ComingSoon),
    )
    // Supervisor gets the Reports tile wired up (Machines Reading / Warehouse-Floor
    // Readings); Head keeps the placeholder for now — everything else is shared.
    UserRole.SUPERVISOR -> supervisorHeadTiles(reportsAction = DashboardAction.Open(Screen.ReportsHub.route))
    // Head additionally gets Daily Reading — they're the only role that can delete a
    // day's A-185 HT/Cold/UV readings, and the hub is the only way in. Supervisor is
    // deliberately left out: no delete power, so no reason to add the tile.
    UserRole.HEAD -> supervisorHeadTiles(reportsAction = DashboardAction.ComingSoon) +
        DashboardTile("Daily Reading", Icons.Filled.Speed, DashboardAction.Open(Screen.DailyReadingHub.route))
    // IT technician owns the Electric Asset + IT/Communication slices of the asset
    // register (browse + add), and works the maintenance complaints raised over
    // WhatsApp — Raised Tickets is where those land.
    UserRole.IT_TECHNICIAN -> listOf(
        DashboardTile("Raised Tickets", Icons.Filled.ReportProblem, DashboardAction.Open(Screen.RaisedTickets.route)),
        DashboardTile("IT Assets", Icons.Filled.Devices, DashboardAction.Open(Screen.ItAssetCategories.route)),
    )
    else -> emptyList()
}

private fun supervisorHeadTiles(reportsAction: DashboardAction): List<DashboardTile> = listOf(
    DashboardTile("Machine List",    Icons.Filled.Build,            DashboardAction.Open(Screen.MachineList.route)),
    DashboardTile("PM Manager",      Icons.Filled.List,             DashboardAction.Open(Screen.SupervisorHome.route), badge = TileBadge.PENDING_APPROVALS),
    DashboardTile("QC Status",       Icons.Filled.CheckCircle,      DashboardAction.Open(Screen.QcHome.route), badge = TileBadge.PENDING_QC),
    DashboardTile("Transfer",        Icons.AutoMirrored.Filled.Send, DashboardAction.Open(Screen.TransferRecords.route)),
    DashboardTile("Schedule Electric Assets", Icons.Filled.Schedule, DashboardAction.Open(Screen.ScheduleElectricAssets.route)),
    DashboardTile("Utility Rates",   Icons.Filled.Paid,             DashboardAction.Open(Screen.UtilityRates.route)),
    DashboardTile("Spare Parts",     Icons.Filled.Inventory,        DashboardAction.Open(Screen.SparePartsMachineList.route)),
    DashboardTile("Checklist Forms", Icons.Filled.Edit,             DashboardAction.ComingSoon),
    DashboardTile("Reports",         Icons.Filled.DateRange,        reportsAction),
)
