package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.reports.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── GET /reports/summary ────────────────────────────────────────────────────

@Serializable
data class CountRowDto(
    @SerialName("label") val label: String = "",
    @SerialName("count") val count: Int = 0,
)

@Serializable
data class ReadingSourceDto(
    @SerialName("source")       val source: String = "",
    @SerialName("table")        val table: String = "",
    @SerialName("entries")      val entries: Int = 0,
    @SerialName("days_covered") val daysCovered: Int = 0,
    @SerialName("days_missing") val daysMissing: Int = 0,
)

@Serializable
data class ReadingsSummaryDto(
    @SerialName("window_days")   val windowDays: Int = 0,
    @SerialName("total_entries") val totalEntries: Int = 0,
    @SerialName("sources")       val sources: List<ReadingSourceDto> = emptyList(),
)

@Serializable
data class PmOverdueRowDto(
    @SerialName("work_order_id")  val workOrderId: String = "",
    @SerialName("machine_id")     val machineId: String = "",
    @SerialName("machine_name")   val machineName: String = "",
    @SerialName("building")       val building: String? = null,
    @SerialName("scheduled_date") val scheduledDate: String = "",
    @SerialName("days_late")      val daysLate: Int = 0,
    @SerialName("status")         val status: String = "",
)

@Serializable
data class PmSummaryDto(
    @SerialName("plans_active")        val plansActive: Int = 0,
    @SerialName("plans_inactive")      val plansInactive: Int = 0,
    @SerialName("work_orders_total")   val workOrdersTotal: Int = 0,
    @SerialName("completed_in_period") val completedInPeriod: Int = 0,
    @SerialName("overdue_total")       val overdueTotal: Int = 0,
    @SerialName("by_status")           val byStatus: List<CountRowDto> = emptyList(),
    @SerialName("overdue_top")         val overdueTop: List<PmOverdueRowDto> = emptyList(),
)

@Serializable
data class BreakdownOpenRowDto(
    @SerialName("id")           val id: String = "",
    @SerialName("machine_id")   val machineId: String = "",
    @SerialName("machine_name") val machineName: String = "",
    @SerialName("building")     val building: String? = null,
    @SerialName("severity")     val severity: String = "",
    @SerialName("status")       val status: String = "",
    @SerialName("raised_by")    val raisedBy: String? = null,
    @SerialName("raised_at")    val raisedAt: String = "",
    @SerialName("days_open")    val daysOpen: Int = 0,
    @SerialName("technician")   val technician: String? = null,
)

@Serializable
data class BreakdownSummaryDto(
    @SerialName("raised_in_period")     val raisedInPeriod: Int = 0,
    @SerialName("closed_in_period")     val closedInPeriod: Int = 0,
    @SerialName("still_open")           val stillOpen: Int = 0,
    @SerialName("avg_hours_to_resolve") val avgHoursToResolve: Double? = null,
    @SerialName("avg_hours_to_qc")      val avgHoursToQc: Double? = null,
    @SerialName("by_status")            val byStatus: List<CountRowDto> = emptyList(),
    @SerialName("by_severity")          val bySeverity: List<CountRowDto> = emptyList(),
    @SerialName("open_rows")            val openRows: List<BreakdownOpenRowDto> = emptyList(),
)

@Serializable
data class PersonActivityDto(
    @SerialName("user_id")            val userId: String? = null,
    @SerialName("name")               val name: String = "",
    @SerialName("role")               val role: String = "",
    @SerialName("runs")               val runs: Int = 0,
    @SerialName("run_hours")          val runHours: Double = 0.0,
    @SerialName("run_kwh")            val runKwh: Double = 0.0,
    @SerialName("breakdowns_raised")  val breakdownsRaised: Int = 0,
    @SerialName("breakdowns_handled") val breakdownsHandled: Int = 0,
    @SerialName("pm_closed")          val pmClosed: Int = 0,
    @SerialName("qc_decisions")       val qcDecisions: Int = 0,
    @SerialName("readings_filed")     val readingsFiled: Int = 0,
    @SerialName("first_seen")         val firstSeen: String? = null,
    @SerialName("last_seen")          val lastSeen: String? = null,
)

@Serializable
data class PeopleSummaryDto(
    @SerialName("total_users")  val totalUsers: Int = 0,
    @SerialName("active_users") val activeUsers: Int = 0,
    @SerialName("people")       val people: List<PersonActivityDto> = emptyList(),
)

@Serializable
data class AssetAddedRowDto(
    @SerialName("asset_id")   val assetId: String = "",
    @SerialName("asset_name") val assetName: String = "",
    @SerialName("building")   val building: String? = null,
    @SerialName("category")   val category: String? = null,
    @SerialName("added_on")   val addedOn: String = "",
)

@Serializable
data class AssetSummaryDto(
    @SerialName("total")            val total: Int = 0,
    @SerialName("added_in_period")  val addedInPeriod: Int = 0,
    @SerialName("before_tracking")  val beforeTracking: Int = 0,
    @SerialName("by_building")      val byBuilding: List<CountRowDto> = emptyList(),
    @SerialName("by_category")      val byCategory: List<CountRowDto> = emptyList(),
    @SerialName("added_rows")       val addedRows: List<AssetAddedRowDto> = emptyList(),
)

@Serializable
data class SummaryReportDto(
    @SerialName("period_from")  val periodFrom: String = "",
    @SerialName("period_to")    val periodTo: String = "",
    @SerialName("plants")       val plants: List<String> = emptyList(),
    @SerialName("generated_at") val generatedAt: Long = 0L,
    @SerialName("generated_by") val generatedBy: String = "",
    @SerialName("readings")     val readings: ReadingsSummaryDto = ReadingsSummaryDto(),
    @SerialName("pm")           val pm: PmSummaryDto = PmSummaryDto(),
    @SerialName("breakdowns")   val breakdowns: BreakdownSummaryDto = BreakdownSummaryDto(),
    @SerialName("people")       val people: PeopleSummaryDto = PeopleSummaryDto(),
    @SerialName("assets")       val assets: AssetSummaryDto = AssetSummaryDto(),
    @SerialName("notes")        val notes: List<String> = emptyList(),
)

// ─── DTO -> domain ───────────────────────────────────────────────────────────

fun CountRowDto.toDomain() = CountRow(label, count)

fun ReadingSourceDto.toDomain() = ReadingSource(source, table, entries, daysCovered, daysMissing)

fun ReadingsSummaryDto.toDomain() =
    ReadingsSummary(windowDays, totalEntries, sources.map { it.toDomain() })

fun PmOverdueRowDto.toDomain() =
    PmOverdueRow(workOrderId, machineId, machineName, building, scheduledDate, daysLate, status)

fun PmSummaryDto.toDomain() = PmSummary(
    plansActive, plansInactive, workOrdersTotal, completedInPeriod, overdueTotal,
    byStatus.map { it.toDomain() }, overdueTop.map { it.toDomain() },
)

fun BreakdownOpenRowDto.toDomain() = BreakdownOpenRow(
    id, machineId, machineName, building, severity, status, raisedBy, raisedAt,
    daysOpen, technician,
)

fun BreakdownSummaryDto.toDomain() = BreakdownSummary(
    raisedInPeriod, closedInPeriod, stillOpen, avgHoursToResolve, avgHoursToQc,
    byStatus.map { it.toDomain() }, bySeverity.map { it.toDomain() },
    openRows.map { it.toDomain() },
)

fun PersonActivityDto.toDomain() = PersonActivity(
    userId, name, role, runs, runHours, runKwh, breakdownsRaised, breakdownsHandled,
    pmClosed, qcDecisions, readingsFiled, firstSeen, lastSeen,
)

fun PeopleSummaryDto.toDomain() =
    PeopleSummary(totalUsers, activeUsers, people.map { it.toDomain() })

fun AssetAddedRowDto.toDomain() = AssetAddedRow(assetId, assetName, building, category, addedOn)

fun AssetSummaryDto.toDomain() = AssetSummary(
    total, addedInPeriod, beforeTracking, byBuilding.map { it.toDomain() },
    byCategory.map { it.toDomain() }, addedRows.map { it.toDomain() },
)

fun SummaryReportDto.toDomain() = SummaryReport(
    periodFrom, periodTo, plants, generatedAt, generatedBy,
    readings.toDomain(), pm.toDomain(), breakdowns.toDomain(),
    people.toDomain(), assets.toDomain(), notes,
)
