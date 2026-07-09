package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.MtUser
import com.candorfoods.factoryops.domain.model.Result

interface MtUserRepository {
    /** All users in the directory (HEAD/ADMIN only) → GET /mt-users. */
    suspend fun list(): Result<List<MtUser>>

    /** Create a new user → POST /mt-users. */
    suspend fun add(user: MtUser): Result<MtUser>

    /** Full overwrite of an existing user → PUT /mt-users/{id}. */
    suspend fun update(user: MtUser): Result<MtUser>

    /** Delete a user → DELETE /mt-users/{id}. */
    suspend fun delete(id: Int): Result<Unit>
}
