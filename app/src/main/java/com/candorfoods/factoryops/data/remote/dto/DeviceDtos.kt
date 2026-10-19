package com.candorfoods.factoryops.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Registers this device's FCM push token against the signed-in user (POST /devices/token)
 * so the backend can push a breakdown/PM reminder straight to the phone instead of the app
 * having to poll for it. `platform` is fixed "android" for this app.
 */
@Serializable
data class DeviceTokenRequestDto(
    @SerialName("user_id") val userId: String,
    @SerialName("token") val token: String,
    @SerialName("platform") val platform: String = "android",
)
