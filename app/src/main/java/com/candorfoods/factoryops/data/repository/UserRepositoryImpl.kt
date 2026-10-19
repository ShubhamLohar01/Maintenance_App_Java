package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.UserApi
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.model.UserSummary
import com.candorfoods.factoryops.domain.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User directory backed by the backend's real mt_users table via GET /users?role=.
 * The returned id is mt_users.id as a string — the exact value stored in a PM
 * plan's assigned_technician_id (the backend fills assigned_technician_name from
 * that same table). Auth follows the per-call "Bearer <token>" pattern used by
 * MachineTransferRepositoryImpl / DeviceApi.
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val api: UserApi,
    private val authPreferences: AuthPreferences,
) : UserRepository {

    override suspend fun getUsersByRole(role: UserRole): List<UserSummary> = try {
        val token = "Bearer ${authPreferences.getToken().orEmpty()}"
        api.list(token, role.name).map {
            UserSummary(
                id = it.id,
                name = it.name,
                role = runCatching { UserRole.valueOf(it.role.uppercase()) }.getOrDefault(role),
                plantId = it.plantId,
            )
        }
    } catch (e: Exception) {
        emptyList()   // offline/error → empty picker; retries when back online
    }
}
