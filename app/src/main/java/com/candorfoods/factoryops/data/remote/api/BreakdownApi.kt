package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.BreakdownFlagRequestDto
import com.candorfoods.factoryops.data.remote.dto.BreakdownFlagResponseDto
import com.candorfoods.factoryops.data.remote.dto.BreakdownWorkDoneRequestDto
import com.candorfoods.factoryops.data.remote.dto.OpenBreakdownDto
import com.candorfoods.factoryops.data.remote.dto.QcAcknowledgeRequestDto
import com.candorfoods.factoryops.data.remote.dto.QcDecisionRequestDto
import com.candorfoods.factoryops.data.remote.dto.QcUpdateResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BreakdownApi {
    /** Cross-device read: open breakdowns (not yet CLOSED) for a plant.
     * plant_id accepts "W202" / "A185" / "both" (any spelling). */
    @GET("breakdowns/open")
    suspend fun listOpen(
        @Header("Authorization") token: String,
        @Query("plant_id") plantId: String = "both"
    ): List<OpenBreakdownDto>

    @POST("breakdowns/flag")
    suspend fun raiseFlag(
        @Header("Authorization") token: String,
        @Body request: BreakdownFlagRequestDto
    ): BreakdownFlagResponseDto

    @POST("breakdowns/{id}/qc/acknowledge")
    suspend fun qcAcknowledge(
        @Header("Authorization") token: String,
        @Path("id") flagId: String,
        @Body request: QcAcknowledgeRequestDto
    ): QcUpdateResponseDto

    /** Technician submits the completed repair -> PENDING_QC (awaiting QC). */
    @POST("breakdowns/{id}/work-done")
    suspend fun workDone(
        @Header("Authorization") token: String,
        @Path("id") flagId: String,
        @Body request: BreakdownWorkDoneRequestDto
    ): QcUpdateResponseDto

    @POST("breakdowns/{id}/qc/approve")
    suspend fun qcApprove(
        @Header("Authorization") token: String,
        @Path("id") flagId: String,
        @Body request: QcDecisionRequestDto
    ): QcUpdateResponseDto

    @POST("breakdowns/{id}/qc/disapprove")
    suspend fun qcDisapprove(
        @Header("Authorization") token: String,
        @Path("id") flagId: String,
        @Body request: QcDecisionRequestDto
    ): QcUpdateResponseDto
}
