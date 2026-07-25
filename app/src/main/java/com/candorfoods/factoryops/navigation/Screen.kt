package com.candorfoods.factoryops.navigation

import com.candorfoods.factoryops.domain.model.UserRole

sealed class Screen(val route: String) {
    object Login          : Screen("login")
    object Home           : Screen("home")              // role router — picks and replaces self
    object OperatorHome   : Screen("home/operator")
    object TechnicianHome : Screen("home/technician")
    object QcHome         : Screen("home/qc")
    object SupervisorHome : Screen("home/supervisor")
    object AdminHome      : Screen("home/admin")
    object SyncStatus     : Screen("sync")
    object Settings       : Screen("settings")
    object MachineMaster  : Screen("machine_master")
    object MachineList    : Screen("machines_list")   // shared live list (operator/supervisor/head)
    object DailyReading   : Screen("daily_reading")   // technician floor meter readings
    object ScheduleElectricAssets : Screen("schedule_electric_assets")  // supervisor schedules, head read-only

    // ─── Checklist module ────────────────────────────────────────────────────
    object ChecklistHub   : Screen("checklist")                  // Preventive / Breakdown chooser
    object PreventiveList  : Screen("checklist/preventive")       // records + Create
    object PreventiveForm  : Screen("checklist/preventive/form?draftId={draftId}") {
        // No draftId → fresh form. With draftId → reopen that draft to continue.
        fun createRoute(draftId: Int? = null) =
            if (draftId == null) "checklist/preventive/form" else "checklist/preventive/form?draftId=$draftId"
        const val ARG = "draftId"
    }
    object PreventiveDetail : Screen("checklist/preventive/record/{id}") {
        fun createRoute(id: Int) = "checklist/preventive/record/$id"
        const val ARG = "id"
    }
    object BreakdownList   : Screen("checklist/breakdown")        // records + Create
    object BreakdownForm   : Screen("checklist/breakdown/new?breakdownId={breakdownId}") {
        // No id → blank F.06. With id → pre-fill from that QC-approved breakdown.
        fun createRoute(breakdownId: String? = null) =
            if (breakdownId == null) "checklist/breakdown/new"
            else "checklist/breakdown/new?breakdownId=$breakdownId"
        const val ARG = "breakdownId"
    }

    // ─── Transfer module ─────────────────────────────────────────────────────
    object TransferRecords : Screen("transfer_records")           // records list + Create + acknowledge
    object Transfer        : Screen("transfer")                   // the create/fill form

    // ─── Head oversight (read-only) ──────────────────────────────────────────
    object HeadHome             : Screen("head")
    object HeadMachines         : Screen("head/machines")          // live machine + operator
    object HeadBreakdowns       : Screen("head/breakdowns")
    object HeadQc               : Screen("head/qc")
    object HeadChecklistRecords : Screen("head/checklist-records") // read-only filled checklists

    // ─── Head Reports (history) ──────────────────────────────────────────────
    object HeadReports              : Screen("head/reports")
    object HeadReportsMachine       : Screen("head/reports/machine-readings")
    object HeadReportsFloor         : Screen("head/reports/floor-readings")
    object HeadReportsBreakdowns    : Screen("head/reports/breakdowns")
    object HeadReportsQcActivity    : Screen("head/reports/qc-activity")

    object ManageUsers          : Screen("manage_users")           // head/admin user directory

    /** Role launcher grid (technician / supervisor / head). */
    object Dashboard : Screen("dashboard/{role}") {
        fun createRoute(role: UserRole) = "dashboard/${role.name}"
        const val ARG = "role"
    }
    /** Placeholder for dashboard boxes not built yet. */
    object ComingSoon : Screen("coming-soon/{label}") {
        fun createRoute(label: String) = "coming-soon/$label"
        const val ARG = "label"
    }

    object MachineDetail : Screen("machine/{machineId}") {
        fun createRoute(machineId: String) = "machine/$machineId"
        const val ARG = "machineId"
    }
    object History : Screen("history/{machineId}") {
        fun createRoute(machineId: String) = "history/$machineId"
        const val ARG = "machineId"
    }
    object BreakdownFlag : Screen("breakdown/{machineId}") {
        fun createRoute(machineId: String) = "breakdown/$machineId"
        const val ARG = "machineId"
    }
    object TicketDetail : Screen("ticket/{ticketId}") {
        fun createRoute(ticketId: String) = "ticket/$ticketId"
        const val ARG = "ticketId"
    }
    object QcTicketDetail : Screen("qc/ticket/{ticketId}") {
        fun createRoute(ticketId: String) = "qc/ticket/$ticketId"
        const val ARG = "ticketId"
    }
    object QcPmDetail : Screen("qc/pm/{woId}") {
        fun createRoute(woId: String) = "qc/pm/$woId"
        const val ARG = "woId"
    }

    // Phase 2C-2 — technician PM screens
    object TechnicianPmDetail : Screen("tech/pm/{woId}") {
        fun createRoute(woId: String) = "tech/pm/$woId"
        const val ARG = "woId"
    }
    object TechnicianPmExecute : Screen("tech/pm/{woId}/execute") {
        fun createRoute(woId: String) = "tech/pm/$woId/execute"
        const val ARG = "woId"
    }

    // Phase 2C-2 — supervisor PM review
    object SupervisorPmReview : Screen("supervisor/pm/{woId}") {
        fun createRoute(woId: String) = "supervisor/pm/$woId"
        const val ARG = "woId"
    }

    // Phase 2C — supervisor screens. A "plan" merges the old template (checklist)
    // + plan (machine + schedule) into one editor; the standalone template editor
    // route was removed.
    object SupervisorPlanEditor : Screen("supervisor/plan?id={planId}") {
        fun createRoute(planId: String? = null) =
            if (planId == null) "supervisor/plan?id=" else "supervisor/plan?id=$planId"
        const val ARG = "planId"
    }
}
