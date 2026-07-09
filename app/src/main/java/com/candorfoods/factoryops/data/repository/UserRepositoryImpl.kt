package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.model.UserSummary
import com.candorfoods.factoryops.domain.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device user directory. There is no users-list endpoint yet (login is the
 * only user-facing API), so the roster is seeded here — mirroring the mt_users
 * rows in [com.candorfoods.factoryops.data.remote.mock.MockAuthApi]. Ids match
 * the login `userId` so an assigned technician resolves consistently downstream.
 *
 * Phase 3: replace with a backend `GET /users?role=` call.
 */
@Singleton
class UserRepositoryImpl @Inject constructor() : UserRepository {

    private val seed = listOf(
        UserSummary(id = "4", name = "Anil Yadav",   role = UserRole.TECHNICIAN, plantId = "185-Koparkhairnae"),
        UserSummary(id = "5", name = "Manish Kumar", role = UserRole.TECHNICIAN, plantId = "W-202-Koparkhairne"),
    )

    override suspend fun getUsersByRole(role: UserRole): List<UserSummary> =
        seed.filter { it.role == role }
}
