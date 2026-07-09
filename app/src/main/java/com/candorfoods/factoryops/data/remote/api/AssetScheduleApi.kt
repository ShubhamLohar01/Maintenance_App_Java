package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.AssetScheduleDto
import com.candorfoods.factoryops.data.remote.dto.AssetScheduleUpsertDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * "Schedule Electric Assets" — daily consumption-recording windows on
 * mt_asset_list 'Electric Asset' rows. Reads are open to any role; writes are
 * SUPERVISOR-only (the backend returns 403 otherwise, surfaced as Result.Error).
 */
interface AssetScheduleApi {
    @GET("asset-schedules")
    suspend fun list(
        @Header("Authorization") token: String,
        @Query("plant_id") plantId: String = "both",
    ): List<AssetScheduleDto>

    /** Create or replace an asset's daily window (SUPERVISOR only). */
    @PUT("asset-schedules/{machineId}")
    suspend fun upsert(
        @Header("Authorization") token: String,
        @Path("machineId") machineId: String,
        @Body body: AssetScheduleUpsertDto,
    ): AssetScheduleDto

    /** Remove a schedule (SUPERVISOR only). Already-recorded days are kept. */
    @DELETE("asset-schedules/{machineId}")
    suspend fun clear(
        @Header("Authorization") token: String,
        @Path("machineId") machineId: String,
    ): AssetScheduleDto
}
