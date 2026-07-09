package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.MtMachineDto
import com.candorfoods.factoryops.data.remote.dto.MtMachineUpdateDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MtMachineApi {
    /**
     * Asset register from `mt_asset_list`. Path kept as /mt-machines after the
     * backend table migration. Filters are optional query params keyed on the
     * new columns; the app fetches the full register and filters client-side,
     * so these default to null (and are omitted) in the normal flow.
     */
    @GET("mt-machines")
    suspend fun list(
        @Header("Authorization") token: String,
        @Query("building") building: String? = null,
        @Query("sub_location") subLocation: String? = null,
        @Query("category") subCategory: String? = null,
    ): List<MtMachineDto>

    /**
     * Admin-only: append a new asset row. `assetId` on the incoming DTO is
     * ignored — backend assigns one.
     */
    @POST("mt-machines")
    suspend fun add(
        @Header("Authorization") token: String,
        @Body dto: MtMachineDto,
    ): MtMachineDto

    /**
     * Update an existing asset's editable columns in `mt_asset_list`. `asset_id` is
     * the path key (immutable); the body carries the editable fields. Returns the
     * updated row (with backend-recomputed `rated_kw`).
     */
    @PUT("mt-machines/{asset_id}")
    suspend fun update(
        @Header("Authorization") token: String,
        @Path("asset_id") assetId: String,
        @Body dto: MtMachineUpdateDto,
    ): MtMachineDto
}
