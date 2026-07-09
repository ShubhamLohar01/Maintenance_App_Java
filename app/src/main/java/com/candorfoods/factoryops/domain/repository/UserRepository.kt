package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.model.UserSummary

/**
 * Directory of app users, used to populate pickers (e.g. "assign this PM plan to
 * a technician"). The maintenance app has no users-list endpoint yet — only
 * /auth/login — so the roster is seeded on-device for now. Swap the impl for a
 * backend `GET /users?role=` call when the PM backend lands (Phase 3).
 */
interface UserRepository {

    /** All known users with the given role (empty if none). */
    suspend fun getUsersByRole(role: UserRole): List<UserSummary>
}
