package com.candorfoods.factoryops.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A user-directory row served by GET /users?role= (reads the real mt_users table).
 * `id` is mt_users.id as a string — the exact value stored in a PM plan's
 * assigned_technician_id.
 */
@Serializable
data class UserRosterDto(
    val id: String,
    val name: String,
    val role: String = "",
    @SerialName("plant_id") val plantId: String = "",
)
