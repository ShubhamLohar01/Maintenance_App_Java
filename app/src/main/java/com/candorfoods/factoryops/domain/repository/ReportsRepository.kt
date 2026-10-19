package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.reports.FloorReadingReportRow
import com.candorfoods.factoryops.domain.model.reports.MachineReadingRow
import com.candorfoods.factoryops.domain.model.reports.SummaryReport

/** Supervisor Reports, backed by the backend "/reports" endpoints. */
interface ReportsRepository {

    /** mt_machine_daily_kwh rows, newest first. plant/from/to null = server default. */
    suspend fun machinesReading(plant: String?, from: String?, to: String?): Result<List<MachineReadingRow>>

    /** mt_floor_utility_readings rows, newest first. plant/from/to null = server default. */
    suspend fun floorReadingsReport(plant: String?, from: String?, to: String?): Result<List<FloorReadingReportRow>>

    /** The consolidated Summary Report. from/to null = server default (last 30 days). */
    suspend fun summaryReport(from: String?, to: String?, plant: String?): Result<SummaryReport>
}
