package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.ReportsApi
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.reports.FloorReadingReportRow
import com.candorfoods.factoryops.domain.model.reports.MachineReadingRow
import com.candorfoods.factoryops.domain.model.reports.SummaryReport
import com.candorfoods.factoryops.domain.repository.ReportsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportsRepositoryImpl @Inject constructor(
    private val api: ReportsApi,
    private val authPreferences: AuthPreferences,
) : ReportsRepository {

    private suspend fun bearerToken() = "Bearer ${authPreferences.getToken().orEmpty()}"

    override suspend fun machinesReading(plant: String?, from: String?, to: String?): Result<List<MachineReadingRow>> = try {
        Result.Success(api.machinesReading(bearerToken(), plant, from, to).toDomain())
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to load machines reading", e)
    }

    override suspend fun floorReadingsReport(plant: String?, from: String?, to: String?): Result<List<FloorReadingReportRow>> = try {
        Result.Success(api.floorReadingsReport(bearerToken(), plant, from, to).toDomain())
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to load floor readings", e)
    }

    override suspend fun summaryReport(from: String?, to: String?, plant: String?): Result<SummaryReport> = try {
        Result.Success(api.summaryReport(bearerToken(), from, to, plant).toDomain())
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to load the summary report", e)
    }
}
