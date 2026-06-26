package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<User>
    suspend fun logout()
    fun getCurrentUser(): Flow<User?>
    fun isLoggedIn(): Boolean
}
