package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.MtUserDto
import com.candorfoods.factoryops.data.remote.dto.MtUserWriteDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** HEAD-only user directory. Auth is passed per-call as a "Bearer <token>" header. */
interface MtUserApi {
    @GET("mt-users")
    suspend fun list(@Header("Authorization") token: String): List<MtUserDto>

    @POST("mt-users")
    suspend fun add(
        @Header("Authorization") token: String,
        @Body dto: MtUserWriteDto,
    ): MtUserDto

    @PUT("mt-users/{id}")
    suspend fun update(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body dto: MtUserWriteDto,
    ): MtUserDto

    @DELETE("mt-users/{id}")
    suspend fun delete(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
    ): MtUserDto
}
