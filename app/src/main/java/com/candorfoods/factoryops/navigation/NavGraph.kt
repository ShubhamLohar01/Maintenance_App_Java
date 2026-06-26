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
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.presentation.admin.AdminHomeScreen
import com.candorfoods.factoryops.presentation.breakdown.BreakdownFlagScreen
import com.candorfoods.factoryops.presentation.checklist.BreakdownCompleteScreen
import com.candorfoods.factoryops.presentation.checklist.BreakdownFormScreen
import com.candorfoods.factoryops.presentation.checklist.BreakdownListScreen
import com.candorfoods.factoryops.presentation.checklist.ChecklistHubScreen
import com.candorfoods.factoryops.presentation.checklist.ChecklistScreen
import com.candorfoods.factoryops.presentation.checklist.PreventiveDetailScreen
import com.candorfoods.factoryops.presentation.checklist.PreventiveListScreen
import com.candorfoods.factoryops.presentation.head.HeadBreakdownsScreen
import com.candorfoods.factoryops.presentation.head.HeadHomeScreen
import com.candorfoods.factoryops.presentation.head.HeadQcScreen
import com.candorfoods.factoryops.presentation.dashboard.ComingSoonScreen
import com.candorfoods.factoryops.presentation.dashboard.DashboardScreen
import com.candorfoods.factoryops.presentation.history.HistoryScreen
import com.candorfoods.factoryops.presentation.home.HomeRouterScreen
import com.candorfoods.factoryops.presentation.machinelist.MachineListScreen
import com.candorfoods.factoryops.presentation.login.LoginScreen
import com.candorfoods.factoryops.presentation.login.LoginViewModel
import com.candorfoods.factoryops.presentation.machine.MachineDetailScreen
import com.candorfoods.factoryops.presentation.machinemaster.MachineMasterScreen
import com.candorfoods.factoryops.presentation.settings.SettingsScreen
import com.candorfoods.factoryops.presentation.sync.SyncStatusScreen
import com.candorfoods.factoryops.presentation.qc.QcHomeScreen
import com.candorfoods.factoryops.presentation.qc.QcInboxRow
import com.candorfoods.factoryops.presentation.qc.QcPmDetailScreen
import com.candorfoods.factoryops.presentation.qc.QcTicketDetailScreen
import com.candorfoods.factoryops.presentation.supervisor.PlanEditorScreen
import com.candorfoods.factoryops.presentation.supervisor.SupervisorHomeScreen
import com.candorfoods.factoryops.presentation.supervisor.SupervisorPmReviewScreen
import com.candorfoods.factoryops.presentation.supervisor.TemplateEditorScreen
import com.candorfoods.factoryops.presentation.technician.DailyReadingScreen
import com.candorfoods.factoryops.presentation.technician.TechnicianHomeScreen
import com.candorfoods.factoryops.presentation.technician.TechnicianPmDetailScreen
import com.candorfoods.factoryops.presentation.technician.TechnicianPmExecuteScreen
import com.candorfoods.factoryops.presentation.technician.TicketDetailScreen
import com.candorfoods.factoryops.presentation.transfer.TransferFormScreen

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
                        // HEAD gets a dedicated read-only oversight interface.
                        UserRole.HEAD                  -> Screen.HeadHome.route
                        UserRole.QC_HEAD,
                        UserRole.QC_MEMBER             -> Screen.QcHome.route
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

        composable(Screen.TechnicianHome.route) {
            TechnicianHomeScreen(
                onTicketClick        = { id -> navController.navigate(Screen.TicketDetail.createRoute(id)) },
                onPmClick            = { id -> navController.navigate(Screen.TechnicianPmDetail.createRoute(id)) },
                onSettingsClick      = { navController.navigate(Screen.Settings.route) },
                onMachineMasterClick = { navController.navigate(Screen.MachineMaster.route) },
            )
        }

        composable(Screen.DailyReading.route) {
            DailyReadingScreen(onBack = { navController.popBackStack() })
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
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.AdminHome.route) {
            AdminHomeScreen(
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(Screen.SupervisorHome.route) {
            SupervisorHomeScreen(
                onCreateTemplate = { navController.navigate(Screen.SupervisorTemplateEditor.createRoute(null)) },
                onEditTemplate   = { id -> navController.navigate(Screen.SupervisorTemplateEditor.createRoute(id)) },
                onCreatePlan     = { navController.navigate(Screen.SupervisorPlanEditor.createRoute(null)) },
                onEditPlan       = { id -> navController.navigate(Screen.SupervisorPlanEditor.createRoute(id)) },
                onReviewPm       = { id -> navController.navigate(Screen.SupervisorPmReview.createRoute(id)) },
                onSettingsClick  = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.SupervisorTemplateEditor.route,
            arguments = listOf(navArgument(Screen.SupervisorTemplateEditor.ARG) {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) {
            TemplateEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
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
                onSubmitted = { navController.popBackStack(Screen.TechnicianHome.route, inclusive = false) }
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
            MachineMasterScreen(onBack = { navController.popBackStack() })
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
                onCreate = { navController.navigate(Screen.BreakdownForm.route) },
                onOpen   = { id -> navController.navigate(Screen.BreakdownComplete.createRoute(id)) },
            )
        }

        composable(
            route = Screen.BreakdownComplete.route,
            arguments = listOf(navArgument(Screen.BreakdownComplete.ARG) { type = NavType.StringType }),
        ) {
            BreakdownCompleteScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.BreakdownForm.route) {
            BreakdownFormScreen(
                onBack      = { navController.popBackStack() },
                onSubmitted = { navController.popBackStack() },
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
                onReports         = { navController.navigate(Screen.ComingSoon.createRoute("Reports")) },
                onTransfers       = { navController.navigate(Screen.ComingSoon.createRoute("Transfer records")) },
                onSettings        = { navController.navigate(Screen.Settings.route) },
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
    }
}
