package com.candorfoods.factoryops.domain.model.reports

data class CountRow(val label: String, val count: Int)

data class ReadingSource(
    val source: String, val table: String,
    val entries: Int, val daysCovered: Int, val daysMissing: Int,
)

data class ReadingsSummary(
    val windowDays: Int = 0, val totalEntries: Int = 0,
    val sources: List<ReadingSource> = emptyList(),
)

data class PmOverdueRow(
    val workOrderId: String, val machineId: String, val machineName: String,
    val building: String?, val scheduledDate: String, val daysLate: Int, val status: String,
)

data class PmSummary(
    val plansActive: Int = 0, val plansInactive: Int = 0, val workOrdersTotal: Int = 0,
    val completedInPeriod: Int = 0, val overdueTotal: Int = 0,
    val byStatus: List<CountRow> = emptyList(),
    val overdueTop: List<PmOverdueRow> = emptyList(),
)

data class BreakdownOpenRow(
    val id: String, val machineId: String, val machineName: String, val building: String?,
    val severity: String, val status: String, val raisedBy: String?,
    val raisedAt: String, val daysOpen: Int, val technician: String?,
)

data class BreakdownSummary(
    val raisedInPeriod: Int = 0, val closedInPeriod: Int = 0, val stillOpen: Int = 0,
    val avgHoursToResolve: Double? = null, val avgHoursToQc: Double? = null,
    val byStatus: List<CountRow> = emptyList(),
    val bySeverity: List<CountRow> = emptyList(),
    val openRows: List<BreakdownOpenRow> = emptyList(),
)

data class PersonActivity(
    val userId: String?, val name: String, val role: String,
    val runs: Int, val runHours: Double, val runKwh: Double,
    val breakdownsRaised: Int, val breakdownsHandled: Int,
    val pmClosed: Int, val qcDecisions: Int, val readingsFiled: Int,
    val firstSeen: String?, val lastSeen: String?,
)

data class PeopleSummary(
    val totalUsers: Int = 0, val activeUsers: Int = 0,
    val people: List<PersonActivity> = emptyList(),
)

data class AssetAddedRow(
    val assetId: String, val assetName: String,
    val building: String?, val category: String?, val addedOn: String,
)

data class AssetSummary(
    val total: Int = 0, val addedInPeriod: Int = 0, val beforeTracking: Int = 0,
    val byBuilding: List<CountRow> = emptyList(),
    val byCategory: List<CountRow> = emptyList(),
    val addedRows: List<AssetAddedRow> = emptyList(),
)

data class SummaryReport(
    val periodFrom: String, val periodTo: String, val plants: List<String>,
    val generatedAt: Long, val generatedBy: String,
    val readings: ReadingsSummary, val pm: PmSummary,
    val breakdowns: BreakdownSummary, val people: PeopleSummary,
    val assets: AssetSummary, val notes: List<String>,
)
