package com.candorfoods.factoryops.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponseDto(
    val token: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val role: String,
    @SerialName("plant_id") val plantId: String,
    // Token expiry as epoch MILLISECONDS, UTC (confirmed with backend; = login + 8h,
    // matches the JWT's own exp). Nullable + ignoreUnknownKeys keeps older/missing
    // responses parseable; the repository falls back to a local expiry when absent.
    @SerialName("expires_at") val expiresAt: String? = null
)
