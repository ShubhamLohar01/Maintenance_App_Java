package com.candorfoods.factoryops.navigation

import com.candorfoods.factoryops.domain.model.ItAssetCategory
import com.candorfoods.factoryops.domain.model.UserRole
import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Login          : Screen("login")
    object Home           : Screen("home")              // role router — picks and replaces self
    object OperatorHome   : Screen("home/operator")
    object TechnicianHome : Screen("home/technician")
    object QcLanding      : Screen("home/qc")                   // two-card chooser (QC home)
    object QcHome         : Screen("qc/verification")           // the live inbox (Awaiting/Active/Done/History)
    object SupervisorHome : Screen("home/supervisor")
    object AdminHome      : Screen("home/admin")
    object SyncStatus     : Screen("sync")
    object Settings       : Screen("settings")
    object MachineMaster  : Screen("machine_master")
    object MachineList    : Screen("machines_list")   // shared live list (operator/supervisor/head)
    object DailyReadingHub : Screen("daily_reading_hub") // utility chooser (Diesel/Gas/Electricity/Water)
    object DailyReading   : Screen("daily_reading")   // (parked) legacy Electric floor meter readings

    // ─── Daily Utility Consumption ───────────────────────────────────────────
    /** History list for a utility. util ∈ diesel|gas|electricity|water. */
    object UtilityHistory : Screen("utilities/{util}") {
        fun createRoute(util: String) = "utilities/$util"
        const val ARG_UTIL = "util"
    }
    /** Add/edit form. No plant/date → new (today); with both → edit that record. */
    object UtilityForm : Screen("utilities/{util}/form?plant={plant}&date={date}") {
        fun createRoute(util: String, plant: String? = null, date: String? = null) =
            "utilities/$util/form?plant=${plant.orEmpty()}&date=${date.orEmpty()}"
        const val ARG_UTIL = "util"
        const val ARG_PLANT = "plant"
        const val ARG_DATE = "date"
    }
    object ScheduleElectricAssets : Screen("schedule_electric_assets")  // supervisor schedules, head read-only
    object UtilityRates : Screen("utility_rates")  // supervisor sets diesel/gas/water/electricity prices

    // ─── A-185 HT Panel / Cold Room / UV daily readings (A-185 users only) ────
    object A185ReadingHistory : Screen("a185_readings")
    object A185ReadingForm : Screen("a185_readings/form?date={date}") {
        /** No date → new (today, prefilled); with a date → edit that day's row. */
        fun createRoute(date: String? = null) = "a185_readings/form?date=${date.orEmpty()}"
        const val ARG_DATE = "date"
    }

    // ─── W-202 Main Meter / Solar daily readings (W-202 users only) ──────────
    // One pair of screens for both meters; `kind` is "main" or "solar".
    /** Daily AC meter readings (W-202). Rows land in mt_floor_utility_readings. */
    object W202ReadingHistory : Screen("w202_readings/{kind}") {
        fun createRoute(kind: String) = "w202_readings/$kind"
        const val ARG_KIND = "kind"
    }
    object W202ReadingForm : Screen("w202_readings/{kind}/form?date={date}") {
        /** No date → new (today, prefilled); with a date → edit that day's row. */
        fun createRoute(kind: String, date: String? = null) =
            "w202_readings/$kind/form?date=${date.orEmpty()}"
        const val ARG_KIND = "kind"
        const val ARG_DATE = "date"
    }

    // ─── Spare Parts (both warehouses) ───────────────────────────────────────
    object SparePartsMachineList : Screen("spare_parts")
    // ─── Supervisor Reports ──────────────────────────────────────────────────
    object ReportsHub : Screen("reports_hub")
    object MachinesReading : Screen("reports/machines")
    object FloorReadingsReport : Screen("reports/floor-readings")

    object SparePartsDetail : Screen("spare_parts/detail?machine={machine}") {
        // java.net.URLEncoder (not android.net.Uri) so this stays plain-JVM-testable —
        // decoded the same way on the ViewModel side (URLDecoder), so the pairing is
        // self-consistent regardless of which encoding a "real" URI would use.
        fun createRoute(machineName: String) = "spare_parts/detail?machine=${URLEncoder.encode(machineName, "UTF-8")}"
        const val ARG_MACHINE = "machine"
    }

    object SparePartHistory : Screen("spare_parts/history?partId={partId}") {
        const val ARG_PART_ID = "partId"
        fun createRoute(partId: Int) = "spare_parts/history?partId=$partId"
    }

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

    // ─── QC History (read-only archive of completed records) ─────────────────
    object QcHistoryHub       : Screen("qc/history")             // PM / Breakdown chooser
    object QcPmHistory        : Screen("qc/history/pm")          // submitted PM checklists
    object QcBreakdownHistory : Screen("qc/history/breakdown")   // submitted F.06 records

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
    object HeadReportsPm            : Screen("head/reports/pm")

    object ManageUsers          : Screen("manage_users")           // head/admin user directory

    // ─── IT technician ───────────────────────────────────────────────────────
    /** IT Assets category chooser (Electrical / IT-Communication). */
    object ItAssetCategories : Screen("it/assets")
    /** Assets of one IT category. The arg is the ItAssetCategory *enum name*. */
    object ItAssetList : Screen("it/assets/{category}") {
        fun createRoute(category: ItAssetCategory) = "it/assets/${category.name}"
        const val ARG = "category"
    }
    /** One IT asset, read-only with an edit sheet. The arg is mt_asset_list.asset_id. */
    object ItAssetDetail : Screen("it/asset/{assetId}") {
        fun createRoute(assetId: String) = "it/asset/$assetId"
        const val ARG = "assetId"
    }

    /** WhatsApp-raised maintenance complaints: Live + History. */
    object RaisedTickets : Screen("tickets/raised")
    /** One raised ticket. The arg is wa_ticket.id. */
    object RaisedTicketDetail : Screen("tickets/raised/{ticketId}") {
        fun createRoute(ticketId: Int) = "tickets/raised/$ticketId"
        const val ARG = "ticketId"
    }

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
    /** Technician's dedicated PM tasks list (reached from the home dashboard tile). */
    object TechnicianPmList : Screen("tech/pm")
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
