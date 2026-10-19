package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.SparePartActionRequestDto
import com.candorfoods.factoryops.data.remote.dto.SparePartCreateDto
import com.candorfoods.factoryops.data.remote.dto.SparePartDto
import com.candorfoods.factoryops.data.remote.dto.SparePartHistoryResponseDto
import com.candorfoods.factoryops.data.remote.dto.SparePartPickerResponseDto
import com.candorfoods.factoryops.data.remote.dto.SparePartUpdateDto
import com.candorfoods.factoryops.data.remote.dto.SparePartsResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Spare Parts — machine -> its parts -> quantity on hand, plus add/edit/delete,
 * use/restock, and per-part history. Everything is open to any authenticated user
 * except [delete], which the backend gates to SUPERVISOR/ADMIN.
 */
interface SparePartsApi {

    @GET("spare-parts")
    suspend fun list(@Header("Authorization") token: String): SparePartsResponseDto

    /**
     * Stock this caller may consume on a work order — only their own warehouse,
     * with the parts fitted to [assetId] flagged so the picker can show them
     * first. Unlike [list], which is all-warehouse for the stock screen.
     */
    @GET("spare-parts/picker")
    suspend fun picker(
        @Header("Authorization") token: String,
        @Query("asset_id") assetId: String?,
    ): SparePartPickerResponseDto

    @POST("spare-parts")
    suspend fun create(
        @Header("Authorization") token: String,
        @Body body: SparePartCreateDto,
    ): SparePartDto

    @PUT("spare-parts/{id}")
    suspend fun update(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body body: SparePartUpdateDto,
    ): SparePartDto

    /** 204 with no body, so this returns Response<Unit> rather than a DTO. */
    @DELETE("spare-parts/{id}")
    suspend fun delete(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
    ): Response<Unit>

    @POST("spare-parts/{id}/use")
    suspend fun use(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body body: SparePartActionRequestDto,
    ): SparePartDto

    @POST("spare-parts/{id}/restock")
    suspend fun restock(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body body: SparePartActionRequestDto,
    ): SparePartDto

    @GET("spare-parts/{id}/history")
    suspend fun history(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
    ): SparePartHistoryResponseDto
}
