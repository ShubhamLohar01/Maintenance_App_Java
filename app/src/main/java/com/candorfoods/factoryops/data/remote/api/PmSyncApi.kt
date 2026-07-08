package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.PmPlanDto
import com.candorfoods.factoryops.data.remote.dto.PmWorkOrderDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
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
}
