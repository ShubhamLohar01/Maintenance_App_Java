package com.candorfoods.factoryops.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.*
import androidx.navigation.compose.*
import com.candorfoods.factoryops.domain.model.ItAssetCategory
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.presentation.it.ItAssetCategoriesScreen
import com.candorfoods.factoryops.presentation.it.ItAssetDetailScreen
import com.candorfoods.factoryops.presentation.it.ItAssetListScreen
import com.candorfoods.factoryops.presentation.tickets.RaisedTicketDetailScreen
import com.candorfoods.factoryops.presentation.tickets.RaisedTicketsScreen
import com.candorfoods.factoryops.presentation.admin.AdminHomeScreen
import com.candorfoods.factoryops.presentation.breakdown.BreakdownFlagScreen
import com.candorfoods.factoryops.presentation.checklist.BreakdownFormScreen
import com.candorfoods.factoryops.presentation.checklist.BreakdownListScreen
import com.candorfoods.factoryops.presentation.checklist.ChecklistHubScreen
import com.candorfoods.factoryops.presentation.checklist.ChecklistScreen
import com.candorfoods.factoryops.presentation.checklist.PreventiveDetailScreen
import com.candorfoods.factoryops.presentation.checklist.PreventiveListScreen
import com.candorfoods.factoryops.presentation.head.HeadBreakdownsScreen
import com.candorfoods.factoryops.presentation.head.HeadHomeScreen
import com.candorfoods.factoryops.presentation.head.HeadQcScreen
import com.candorfoods.factoryops.presentation.head.reports.BreakdownHistoryScreen
import com.candorfoods.factoryops.presentation.head.reports.HeadReportsScreen
import com.candorfoods.factoryops.presentation.head.reports.PmHistoryScreen
import com.candorfoods.factoryops.presentation.head.reports.QcActivityHistoryScreen
import com.candorfoods.factoryops.presentation.dashboard.ComingSoonScreen
import com.candorfoods.factoryops.presentation.dashboard.DashboardScreen
import com.candorfoods.factoryops.presentation.history.HistoryScreen
import com.candorfoods.factoryops.presentation.home.HomeRouterScreen
import com.candorfoods.factoryops.presentation.machinelist.MachineListScreen
import com.candorfoods.factoryops.presentation.login.LoginScreen
import com.candorfoods.factoryops.presentation.login.LoginViewModel
import com.candorfoods.factoryops.presentation.machine.MachineDetailScreen
import com.candorfoods.factoryops.presentation.schedule.ScheduleElectricAssetsScreen
import com.candorfoods.factoryops.presentation.machinemaster.MachineMasterScreen
import com.candorfoods.factoryops.presentation.settings.SettingsScreen
import com.candorfoods.factoryops.presentation.sync.SyncStatusScreen
import com.candorfoods.factoryops.presentation.qc.QcHomeScreen
import com.candorfoods.factoryops.presentation.qc.QcLandingScreen
import com.candorfoods.factoryops.presentation.qc.QcInboxRow
import com.candorfoods.factoryops.presentation.qc.QcPmDetailScreen
import com.candorfoods.factoryops.presentation.qc.QcTicketDetailScreen
import com.candorfoods.factoryops.presentation.qc.history.QcBreakdownHistoryScreen
import com.candorfoods.factoryops.presentation.qc.history.QcHistoryHubScreen
import com.candorfoods.factoryops.presentation.qc.history.QcPmHistoryScreen
import com.candorfoods.factoryops.presentation.supervisor.PlanEditorScreen
import com.candorfoods.factoryops.presentation.supervisor.SupervisorHomeScreen
import com.candorfoods.factoryops.presentation.supervisor.SupervisorPmReviewScreen
import com.candorfoods.factoryops.presentation.reports.FloorReadingsReportScreen
import com.candorfoods.factoryops.presentation.reports.MachinesReadingScreen
import com.candorfoods.factoryops.presentation.reports.ReportsHubScreen
import com.candorfoods.factoryops.presentation.spareparts.SparePartHistoryScreen
import com.candorfoods.factoryops.presentation.spareparts.SparePartsDetailScreen
import com.candorfoods.factoryops.presentation.spareparts.SparePartsMachineListScreen
import com.candorfoods.factoryops.presentation.technician.DailyReadingHubScreen
import com.candorfoods.factoryops.presentation.technician.DailyReadingScreen
import com.candorfoods.factoryops.presentation.a185.A185ReadingFormScreen
import com.candorfoods.factoryops.presentation.a185.A185ReadingHistoryScreen
import com.candorfoods.factoryops.presentation.w202.W202ReadingFormScreen
import com.candorfoods.factoryops.presentation.w202.W202ReadingHistoryScreen
import com.candorfoods.factoryops.presentation.utilities.UtilityFormScreen
import com.candorfoods.factoryops.presentation.utilities.UtilityHistoryScreen
import com.candorfoods.factoryops.presentation.utilities.UtilityRatesScreen
import com.candorfoods.factoryops.presentation.technician.TechnicianHomeScreen
import com.candorfoods.factoryops.presentation.technician.TechnicianPmDetailScreen
import com.candorfoods.factoryops.presentation.technician.TechnicianPmExecuteScreen
import com.candorfoods.factoryops.presentation.technician.TechnicianPmScreen
import com.candorfoods.factoryops.presentation.technician.TicketDetailScreen
import com.candorfoods.factoryops.presentation.transfer.TransferFormScreen
import com.candorfoods.factoryops.presentation.transfer.TransferRecordsScreen
import com.candorfoods.factoryops.presentation.user.ManageUsersScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    val loginViewModel: LoginViewModel = hiltViewModel()
    val isLoggedIn by loginViewModel.isLoggedIn.collectAsStateWithLifecycle()

    val startDestination = if (isLoggedIn) Screen.Home.route else Screen.Login.route

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Login.route) {
            LoginScreen(onNavigateToHome = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Home.route) {
            HomeRouterScreen(
                onResolved = { role ->
                    val target = when (role) {
                        UserRole.OPERATOR              -> Screen.OperatorHome.route
                        // Technician and SUPERVISOR land on the launcher grid.
                        UserRole.TECHNICIAN,
                        UserRole.SUPERVISOR            -> Screen.Dashboard.createRoute(role)
                        // IT technician gets the same launcher grid with an IT-only tile set.
                        UserRole.IT_TECHNICIAN         -> Screen.Dashboard.createRoute(role)
                        // HEAD gets a dedicated read-only oversight interface.
                        UserRole.HEAD                  -> Screen.HeadHome.route
                        UserRole.QC_HEAD,
                        UserRole.QC_MEMBER             -> Screen.QcLanding.route
                        UserRole.ADMIN                 -> Screen.AdminHome.route
                    }
                    navController.navigate(target) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onGiveUp = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Dashboard.route,
            arguments = listOf(navArgument(Screen.Dashboard.ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            val role = runCatching {
                UserRole.valueOf(backStackEntry.arguments?.getString(Screen.Dashboard.ARG).orEmpty())
            }.getOrDefault(UserRole.TECHNICIAN)
            DashboardScreen(
                role            = role,
                onOpen          = { route -> navController.navigate(route) },
                onComingSoon    = { label -> navController.navigate(Screen.ComingSoon.createRoute(label)) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(
            route = Screen.ComingSoon.route,
            arguments = listOf(navArgument(Screen.ComingSoon.ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            ComingSoonScreen(
                label  = backStackEntry.arguments?.getString(Screen.ComingSoon.ARG).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.OperatorHome.route) {
            MachineListScreen(
                onMachineClick       = { id -> navController.navigate(Screen.MachineDetail.createRoute(id)) },
                onSettingsClick      = { navController.navigate(Screen.Settings.route) },
                onSyncClick          = { navController.navigate(Screen.SyncStatus.route) },
                onMachineMasterClick = { navController.navigate(Screen.MachineMaster.route) },
            )
        }

        // Shared live machine list for Supervisor + Head (read-only via detail gating).
        composable(Screen.MachineList.route) {
            MachineListScreen(
                onMachineClick  = { id -> navController.navigate(Screen.MachineDetail.createRoute(id)) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onBack          = { navController.popBackStack() },
            )
        }

        // Schedule Electric Assets — supervisor edits schedule + asset details, head read-only.
        composable(Screen.ScheduleElectricAssets.route) {
            ScheduleElectricAssetsScreen(
                onBack = { navController.popBackStack() },
                )
        }

        // Utility Rates — supervisor sets diesel/gas/water/electricity prices (head read-only).
        composable(Screen.UtilityRates.route) {
            UtilityRatesScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // Spare Parts — every role: browse, add/edit, use/restock, history.
        composable(Screen.SparePartsMachineList.route) {
            SparePartsMachineListScreen(
                onMachineClick = { machineName -> navController.navigate(Screen.SparePartsDetail.createRoute(machineName)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.SparePartsDetail.route,
            arguments = listOf(navArgument(Screen.SparePartsDetail.ARG_MACHINE) { type = NavType.StringType; defaultValue = "" }),
        ) {
            SparePartsDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenHistory = { partId ->
                    navController.navigate(Screen.SparePartHistory.createRoute(partId))
                },
            )
        }

        composable(
            route = Screen.SparePartHistory.route,
            arguments = listOf(navArgument(Screen.SparePartHistory.ARG_PART_ID) { type = NavType.IntType; defaultValue = 0 }),
        ) {
            SparePartHistoryScreen(onBack = { navController.popBackStack() })
        }

        // Supervisor Reports — Machines Reading + Warehouse/Floor Readings.
        composable(Screen.ReportsHub.route) {
            ReportsHubScreen(
                onMachinesReading = { navController.navigate(Screen.MachinesReading.route) },
                onFloorReadings = { navController.navigate(Screen.FloorReadingsReport.route) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.MachinesReading.route) {
            MachinesReadingScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.FloorReadingsReport.route) {
            FloorReadingsReportScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.TechnicianHome.route) {
            TechnicianHomeScreen(
                onTicketClick        = { id -> navController.navigate(Screen.TicketDetail.createRoute(id)) },
                onSettingsClick      = { navController.navigate(Screen.Settings.route) },
                onMachineMasterClick = { navController.navigate(Screen.MachineMaster.route) },
            )
        }

        // Technician's dedicated PM tasks list (opened from the home dashboard tile).
        composable(Screen.TechnicianPmList.route) {
            TechnicianPmScreen(
                onBack    = { navController.popBackStack() },
                onPmClick = { id -> navController.navigate(Screen.TechnicianPmDetail.createRoute(id)) },
            )
        }

        composable(Screen.DailyReadingHub.route) {
            DailyReadingHubScreen(
                onUtility = { type -> navController.navigate(Screen.UtilityHistory.createRoute(type.slug)) },
                // Electricity reverted to the legacy floor-meter "Electric Consumption" screen (W-202).
                // New utility electricity flow parked: navController.navigate(Screen.UtilityHistory.createRoute("electricity"))
                onElectricConsumption = { navController.navigate(Screen.DailyReading.route) },
                onA185Readings = { navController.navigate(Screen.A185ReadingHistory.route) },
                onW202Reading = { kind -> navController.navigate(Screen.W202ReadingHistory.createRoute(kind.slug)) },
                onBack    = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.W202ReadingHistory.route,
            arguments = listOf(navArgument(Screen.W202ReadingHistory.ARG_KIND) { type = NavType.StringType }),
        ) { backStackEntry ->
            val kind = backStackEntry.arguments?.getString(Screen.W202ReadingHistory.ARG_KIND).orEmpty()
            W202ReadingHistoryScreen(
                onAdd  = { navController.navigate(Screen.W202ReadingForm.createRoute(kind)) },
                onEdit = { date -> navController.navigate(Screen.W202ReadingForm.createRoute(kind, date)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.W202ReadingForm.route,
            arguments = listOf(
                navArgument(Screen.W202ReadingForm.ARG_KIND) { type = NavType.StringType },
                navArgument(Screen.W202ReadingForm.ARG_DATE) { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            W202ReadingFormScreen(
                onSaved = { navController.popBackStack() },
                onBack  = { navController.popBackStack() },
            )
        }

        composable(Screen.A185ReadingHistory.route) {
            A185ReadingHistoryScreen(
                onAdd  = { navController.navigate(Screen.A185ReadingForm.createRoute()) },
                onEdit = { date -> navController.navigate(Screen.A185ReadingForm.createRoute(date)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.A185ReadingForm.route,
            arguments = listOf(
                navArgument(Screen.A185ReadingForm.ARG_DATE) { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            A185ReadingFormScreen(
                onSaved = { navController.popBackStack() },
                onBack  = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.UtilityHistory.route,
            arguments = listOf(navArgument(Screen.UtilityHistory.ARG_UTIL) { type = NavType.StringType }),
        ) { backStackEntry ->
            val util = backStackEntry.arguments?.getString(Screen.UtilityHistory.ARG_UTIL).orEmpty()
            UtilityHistoryScreen(
                onAdd  = { navController.navigate(Screen.UtilityForm.createRoute(util)) },
                onEdit = { plant, date -> navController.navigate(Screen.UtilityForm.createRoute(util, plant, date)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.UtilityForm.route,
            arguments = listOf(
                navArgument(Screen.UtilityForm.ARG_UTIL) { type = NavType.StringType },
                navArgument(Screen.UtilityForm.ARG_PLANT) { type = NavType.StringType; defaultValue = "" },
                navArgument(Screen.UtilityForm.ARG_DATE) { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            UtilityFormScreen(
                onSaved = { navController.popBackStack() },
                onBack  = { navController.popBackStack() },
            )
        }

        composable(Screen.DailyReading.route) {
            DailyReadingScreen(onBack = { navController.popBackStack() })
        }

        // QC home — two-card chooser: Verification (live inbox) vs Checklist Records (archive).
        composable(Screen.QcLanding.route) {
            QcLandingScreen(
                onQcVerification   = { navController.navigate(Screen.QcHome.route) },
                onChecklistRecords = { navController.navigate(Screen.QcHistoryHub.route) },
                onSettingsClick    = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(Screen.QcHome.route) {
            QcHomeScreen(
                onRowClick = { row ->
                    val route = when (row) {
                        is QcInboxRow.Breakdown -> Screen.QcTicketDetail.createRoute(row.id)
                        is QcInboxRow.Pm        -> Screen.QcPmDetail.createRoute(row.id)
                    }
                    navController.navigate(route)
                },
                onBack = { navController.popBackStack() },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        // QC History — read-only archive. PM rows reuse PreventiveDetailScreen, which is
        // already view-only and carries the Download PDF action.
        composable(Screen.QcHistoryHub.route) {
            QcHistoryHubScreen(
                onPmRecords        = { navController.navigate(Screen.QcPmHistory.route) },
                onBreakdownRecords = { navController.navigate(Screen.QcBreakdownHistory.route) },
                onBack             = { navController.popBackStack() },
            )
        }

        composable(Screen.QcPmHistory.route) {
            QcPmHistoryScreen(
                onOpen = { id -> navController.navigate(Screen.PreventiveDetail.createRoute(id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.QcBreakdownHistory.route) {
            QcBreakdownHistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.AdminHome.route) {
            AdminHomeScreen(
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(Screen.SupervisorHome.route) {
            SupervisorHomeScreen(
                onCreatePlan     = { navController.navigate(Screen.SupervisorPlanEditor.createRoute(null)) },
                onEditPlan       = { id -> navController.navigate(Screen.SupervisorPlanEditor.createRoute(id)) },
                onReviewPm       = { id -> navController.navigate(Screen.SupervisorPmReview.createRoute(id)) },
                onSettingsClick  = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.SupervisorPlanEditor.route,
            arguments = listOf(navArgument(Screen.SupervisorPlanEditor.ARG) {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) {
            PlanEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.TicketDetail.route,
            arguments = listOf(navArgument(Screen.TicketDetail.ARG) { type = NavType.StringType })
        ) {
            TicketDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.TechnicianPmDetail.route,
            arguments = listOf(navArgument(Screen.TechnicianPmDetail.ARG) { type = NavType.StringType })
        ) {
            TechnicianPmDetailScreen(
                onBack = { navController.popBackStack() },
                onLaunchExecution = { woId ->
                    navController.navigate(Screen.TechnicianPmExecute.createRoute(woId))
                }
            )
        }

        composable(
            route = Screen.TechnicianPmExecute.route,
            arguments = listOf(navArgument(Screen.TechnicianPmExecute.ARG) { type = NavType.StringType })
        ) {
            TechnicianPmExecuteScreen(
                onBack = { navController.popBackStack() },
                onSubmitted = {
                    // PM lives under Dashboard → PM Tasks now (not TechnicianHome), so
                    // return to the PM list; plain pop as a fallback if it isn't on
                    // the stack (e.g. a deep-link straight into execution).
                    if (!navController.popBackStack(Screen.TechnicianPmList.route, inclusive = false)) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(
            route = Screen.SupervisorPmReview.route,
            arguments = listOf(navArgument(Screen.SupervisorPmReview.ARG) { type = NavType.StringType })
        ) {
            SupervisorPmReviewScreen(
                onBack = { navController.popBackStack() },
                onDecided = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.QcTicketDetail.route,
            arguments = listOf(navArgument(Screen.QcTicketDetail.ARG) { type = NavType.StringType })
        ) {
            QcTicketDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.QcPmDetail.route,
            arguments = listOf(navArgument(Screen.QcPmDetail.ARG) { type = NavType.StringType })
        ) {
            QcPmDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.MachineDetail.route,
            arguments = listOf(navArgument(Screen.MachineDetail.ARG) { type = NavType.StringType })
        ) {
            MachineDetailScreen(
                onBack          = { navController.popBackStack() },
                onHistoryClick  = { id -> navController.navigate(Screen.History.createRoute(id)) },
                onBreakdownClick = { id -> navController.navigate(Screen.BreakdownFlag.createRoute(id)) }
            )
        }

        composable(
            route = Screen.History.route,
            arguments = listOf(navArgument(Screen.History.ARG) { type = NavType.StringType })
        ) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.BreakdownFlag.route,
            arguments = listOf(navArgument(Screen.BreakdownFlag.ARG) { type = NavType.StringType })
        ) {
            BreakdownFlagScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SyncStatus.route) {
            SyncStatusScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.MachineMaster.route) {
            MachineMasterScreen(
                onBack         = { navController.popBackStack() },
                onMachineClick = { id -> navController.navigate(Screen.MachineDetail.createRoute(id)) },
            )
        }

        // ─── IT technician: IT Assets ────────────────────────────────────────
        composable(Screen.ItAssetCategories.route) {
            ItAssetCategoriesScreen(
                onCategoryClick = { category ->
                    navController.navigate(Screen.ItAssetList.createRoute(category))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.ItAssetList.route,
            arguments = listOf(navArgument(Screen.ItAssetList.ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            // Same pattern as the Dashboard route: parse the arg here, and fall back
            // rather than crash on an unknown value.
            ItAssetListScreen(
                category = ItAssetCategory.of(
                    backStackEntry.arguments?.getString(Screen.ItAssetList.ARG)
                ),
                onAssetClick = { assetId ->
                    navController.navigate(Screen.ItAssetDetail.createRoute(assetId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.ItAssetDetail.route,
            arguments = listOf(navArgument(Screen.ItAssetDetail.ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            ItAssetDetailScreen(
                assetId = backStackEntry.arguments?.getString(Screen.ItAssetDetail.ARG).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }

        // ─── IT technician: Raised Tickets (WhatsApp complaints) ─────────────
        composable(Screen.RaisedTickets.route) {
            RaisedTicketsScreen(
                onTicketClick = { id ->
                    navController.navigate(Screen.RaisedTicketDetail.createRoute(id))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.RaisedTicketDetail.route,
            arguments = listOf(navArgument(Screen.RaisedTicketDetail.ARG) { type = NavType.IntType })
        ) { backStackEntry ->
            RaisedTicketDetailScreen(
                ticketId = backStackEntry.arguments?.getInt(Screen.RaisedTicketDetail.ARG) ?: 0,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.ChecklistHub.route) {
            ChecklistHubScreen(
                onPreventive = { navController.navigate(Screen.PreventiveList.route) },
                onBreakdown  = { navController.navigate(Screen.BreakdownList.route) },
                onBack       = { navController.popBackStack() },
            )
        }

        composable(Screen.PreventiveList.route) {
            PreventiveListScreen(
                onBack     = { navController.popBackStack() },
                onCreate   = { navController.navigate(Screen.PreventiveForm.createRoute(null)) },
                onDraft    = { id -> navController.navigate(Screen.PreventiveForm.createRoute(id)) },
                onFinal    = { id -> navController.navigate(Screen.PreventiveDetail.createRoute(id)) },
            )
        }

        composable(
            route = Screen.PreventiveForm.route,
            arguments = listOf(navArgument(Screen.PreventiveForm.ARG) {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) {
            ChecklistScreen(
                onBack      = { navController.popBackStack() },
                onSubmitted = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.PreventiveDetail.route,
            arguments = listOf(navArgument(Screen.PreventiveDetail.ARG) { type = NavType.StringType })
        ) {
            PreventiveDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.BreakdownList.route) {
            BreakdownListScreen(
                onBack   = { navController.popBackStack() },
                onCreate = { navController.navigate(Screen.BreakdownForm.createRoute()) },
                onOpen   = { id -> navController.navigate(Screen.BreakdownForm.createRoute(breakdownId = id)) },
            )
        }

        composable(
            route = Screen.BreakdownForm.route,
            arguments = listOf(navArgument(Screen.BreakdownForm.ARG) {
                type = NavType.StringType; nullable = true; defaultValue = null
            }),
        ) {
            BreakdownFormScreen(
                onBack      = { navController.popBackStack() },
                onSubmitted = { navController.popBackStack() },
            )
        }

        composable(Screen.TransferRecords.route) {
            TransferRecordsScreen(
                onBack   = { navController.popBackStack() },
                onCreate = { navController.navigate(Screen.Transfer.route) },
            )
        }

        composable(Screen.Transfer.route) {
            TransferFormScreen(
                onBack      = { navController.popBackStack() },
                onSubmitted = { navController.popBackStack() },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() }, onLogout = {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            })
        }

        // ─── Head oversight (read-only) ──────────────────────────────────────
        composable(Screen.HeadHome.route) {
            HeadHomeScreen(
                onMachines        = { navController.navigate(Screen.MachineList.route) },
                onBreakdowns      = { navController.navigate(Screen.HeadBreakdowns.route) },
                onQc              = { navController.navigate(Screen.HeadQc.route) },
                onChecklistRecords = { navController.navigate(Screen.HeadChecklistRecords.route) },
                onReports         = { navController.navigate(Screen.HeadReports.route) },
                onTransfers       = { navController.navigate(Screen.ComingSoon.createRoute("Transfer records")) },
                onSettings        = { navController.navigate(Screen.Settings.route) },
                onManageUsers     = { navController.navigate(Screen.ManageUsers.route) },
            )
        }
        composable(Screen.HeadBreakdowns.route) {
            HeadBreakdownsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.HeadQc.route) {
            HeadQcScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.HeadChecklistRecords.route) {
            PreventiveListScreen(
                onBack     = { navController.popBackStack() },
                onCreate   = {},
                onDraft    = { id -> navController.navigate(Screen.PreventiveDetail.createRoute(id)) },
                onFinal    = { id -> navController.navigate(Screen.PreventiveDetail.createRoute(id)) },
                readOnly   = true,
            )
        }

        // ─── Head Reports (history) ────────────────────────────────────────
        composable(Screen.HeadReports.route) {
            HeadReportsScreen(
                onBack               = { navController.popBackStack() },
                onMachineReadings    = { navController.navigate(Screen.HeadReportsMachine.route) },
                onFloorReadings      = { navController.navigate(Screen.HeadReportsFloor.route) },
                onBreakdownHistory   = { navController.navigate(Screen.HeadReportsBreakdowns.route) },
                onQcActivityHistory  = { navController.navigate(Screen.HeadReportsQcActivity.route) },
                onPmHistory          = { navController.navigate(Screen.HeadReportsPm.route) },
            )
        }
        composable(Screen.HeadReportsMachine.route) {
            MachinesReadingScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.HeadReportsFloor.route) {
            FloorReadingsReportScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.HeadReportsBreakdowns.route) {
            BreakdownHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.HeadReportsQcActivity.route) {
            QcActivityHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.HeadReportsPm.route) {
            PmHistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.ManageUsers.route) {
            ManageUsersScreen(onBack = { navController.popBackStack() })
        }
    }
}
