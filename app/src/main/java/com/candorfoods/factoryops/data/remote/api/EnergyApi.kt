package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.*
import retrofit2.http.*

interface EnergyApi {
    @POST("energy/runs/start")
    suspend fun startRun(
        @Header("Authorization") token: String,
        @Body request: RunStartRequestDto
    ): RunStartResponseDto

    @POST("energy/runs/{runId}/stop")
    suspend fun stopRun(
        @Header("Authorization") token: String,
        @Path("runId") runId: String,
        @Body request: RunStopRequestDto
    ): RunStopResponseDto

    @GET("energy/machines/{machineId}/history")
    suspend fun getMachineHistory(
        @Header("Authorization") token: String,
        @Path("machineId") machineId: String,
        @Query("from") from: Long,
        @Query("to") to: Long
    ): List<DailyHistoryDto>

    /**
     * Production runs that are started and not yet stopped, across all operators
     * and devices for the caller's plant scope. Drives the Head's live machine
     * list. Returns an empty array when nobody is running.
     */
    @GET("energy/runs/active")
    suspend fun getActiveRuns(
        @Header("Authorization") token: String
    ): List<ActiveRunDto>
}
