package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.FloorReadingsReportResponseDto
import com.candorfoods.factoryops.data.remote.dto.MachinesReadingResponseDto
import com.candorfoods.factoryops.data.remote.dto.SummaryReportDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Supervisor Reports — read-only historical listings. `plant` null = both
 * plants (server narrows to the caller's own for non-supervisor roles);
 * `from`/`to` null = server default (last 30 days).
 */
interface ReportsApi {

    @GET("reports/machines")
    suspend fun machinesReading(
        @Header("Authorization") token: String,
        @Query("plant") plant: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): MachinesReadingResponseDto

    @GET("reports/floor-readings")
    suspend fun floorReadingsReport(
        @Header("Authorization") token: String,
        @Query("plant") plant: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): FloorReadingsReportResponseDto

    @GET("reports/summary")
    suspend fun summaryReport(
        @Header("Authorization") token: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("plant") plant: String? = null,
    ): SummaryReportDto
}
