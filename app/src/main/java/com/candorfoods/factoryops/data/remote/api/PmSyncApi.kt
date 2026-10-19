package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.PmAtRequestDto
import com.candorfoods.factoryops.data.remote.dto.PmPlanDto
import com.candorfoods.factoryops.data.remote.dto.PmPhotoUploadResponseDto
import com.candorfoods.factoryops.data.remote.dto.PmQcAckRequestDto
import com.candorfoods.factoryops.data.remote.dto.PmQcDecisionRequestDto
import com.candorfoods.factoryops.data.remote.dto.PmSubmitRequestDto
import com.candorfoods.factoryops.data.remote.dto.PmSupervisorApproveRequestDto
import com.candorfoods.factoryops.data.remote.dto.PmSupervisorRejectRequestDto
import com.candorfoods.factoryops.data.remote.dto.PmWorkOrderDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Backend PM sync endpoints (see docs/backend/pm-backend-integration-spec.md).
 * NOT wired into DI or any repository yet — the server side doesn't exist. When the
 * endpoints are live: add a provider in NetworkModule and call these from
 * PmPlanRepositoryImpl / PmWorkOrderRepositoryImpl, mapping with the DTO extensions.
 */
interface PmSyncApi {

    // ─── Plans ──────────────────────────────────────────────────────────────
    @GET("pm/plans")
    suspend fun listPlans(
        @Header("Authorization") token: String,
    ): List<PmPlanDto>

    @GET("pm/plans/{id}")
    suspend fun getPlan(
        @Header("Authorization") token: String,
        @Path("id") id: String,
    ): PmPlanDto

    @POST("pm/plans")
    suspend fun createPlan(
        @Header("Authorization") token: String,
        @Body plan: PmPlanDto,
    ): PmPlanDto

    @PUT("pm/plans/{id}")
    suspend fun updatePlan(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body plan: PmPlanDto,
    ): PmPlanDto

    @DELETE("pm/plans/{id}")
    suspend fun deletePlan(
        @Header("Authorization") token: String,
        @Path("id") id: String,
    )

    // ─── Work orders ────────────────────────────────────────────────────────
    @GET("pm/work-orders")
    suspend fun listWorkOrders(
        @Header("Authorization") token: String,
        @Query("technician_id") technicianId: String? = null,
        @Query("status") status: String? = null,
    ): List<PmWorkOrderDto>

    @GET("pm/work-orders/{id}")
    suspend fun getWorkOrder(
        @Header("Authorization") token: String,
        @Path("id") id: String,
    ): PmWorkOrderDto

    // ─── Work-order lifecycle (each returns the updated WO) ───────────────────
    @POST("pm/work-orders/{id}/acknowledge")
    suspend fun acknowledgeWorkOrder(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: PmAtRequestDto,
    ): PmWorkOrderDto

    @POST("pm/work-orders/{id}/start")
    suspend fun startWorkOrder(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: PmAtRequestDto,
    ): PmWorkOrderDto

    @POST("pm/work-orders/{id}/submit")
    suspend fun submitWorkOrder(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: PmSubmitRequestDto,
    ): PmWorkOrderDto

    @POST("pm/work-orders/{id}/supervisor/approve")
    suspend fun supervisorApproveWorkOrder(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: PmSupervisorApproveRequestDto,
    ): PmWorkOrderDto

    @POST("pm/work-orders/{id}/supervisor/reject")
    suspend fun supervisorRejectWorkOrder(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: PmSupervisorRejectRequestDto,
    ): PmWorkOrderDto

    @POST("pm/work-orders/{id}/qc/acknowledge")
    suspend fun qcAcknowledgeWorkOrder(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: PmQcAckRequestDto,
    ): PmWorkOrderDto

    @POST("pm/work-orders/{id}/qc/override-acknowledge")
    suspend fun qcOverrideAcknowledgeWorkOrder(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: PmQcAckRequestDto,
    ): PmWorkOrderDto

    @POST("pm/work-orders/{id}/qc/approve")
    suspend fun qcApproveWorkOrder(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: PmQcDecisionRequestDto,
    ): PmWorkOrderDto

    @POST("pm/work-orders/{id}/qc/disapprove")
    suspend fun qcDisapproveWorkOrder(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: PmQcDecisionRequestDto,
    ): PmWorkOrderDto

    /** Upload one image (jpeg/png, ≤10 MB) to S3; returns its URL. Called per task-log
     *  photo and the QC after-photo, whose URLs then go into the submit / QC payloads. */
    @Multipart
    @POST("pm/photos")
    suspend fun uploadPmPhoto(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
    ): PmPhotoUploadResponseDto
}
