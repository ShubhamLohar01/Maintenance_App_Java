package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.model.UserSummary

/**
 * Directory of app users, used to populate pickers (e.g. "assign this PM plan to
 * a technician"). Backed by the backend's real mt_users table via
 * `GET /users?role=` — the returned ids are what get stored in a PM plan's
 * assigned_technician_id.
 */
interface UserRepository {

    /** All known users with the given role (empty if none). */
    suspend fun getUsersByRole(role: UserRole): List<UserSummary>
}
