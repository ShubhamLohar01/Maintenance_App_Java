package com.candorfoods.factoryops.domain.model.reports

/** One mt_machine_daily_kwh row, straight off GET /reports/machines (no aggregation). */
data class MachineReadingRow(
    val readingDate: String,
    val machineId: String,
    val assetName: String,
    val building: String?,
    val floor: String?,
    val operatorName: String?,
    val startedAt: Long?,   // epoch ms; null while a RUN row is still open
    val endedAt: Long?,     // epoch ms; null while a RUN row is still open
    val status: String?,
    val source: String?,    // RUN | SCHEDULE
    val dailyKwh: Double?,
)

/** One mt_floor_utility_readings row, straight off GET /reports/floor-readings. */
data class FloorReadingReportRow(
    val readingDate: String,
    val building: String,
    val floor: String,
    val meterReading: Double?,   // actual, technician-entered
    val dailyKwh: Double?,       // system-computed
)
