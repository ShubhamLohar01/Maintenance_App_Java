package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.DeviceTokenRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface DeviceApi {
    /**
     * Register (or refresh) this device's FCM push token for the signed-in user so the
     * backend can fan a push out to the right phones. Server should UPSERT by token: a
     * token is unique per install but can move between users on a shared device, so the
     * newest (user_id, token) pairing wins. Returns 2xx with an empty body on success.
     */
    @POST("devices/token")
    suspend fun registerToken(
        @Header("Authorization") token: String,
        @Body body: DeviceTokenRequestDto,
    ): Response<Unit>
}
