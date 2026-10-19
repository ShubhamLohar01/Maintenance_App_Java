package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.UserRosterDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/** User directory backed by the real mt_users table. Auth is passed per-call as a "Bearer <token>" header. */
interface UserApi {
    @GET("users")
    suspend fun list(
        @Header("Authorization") token: String,
        @Query("role") role: String? = null,
    ): List<UserRosterDto>
}
