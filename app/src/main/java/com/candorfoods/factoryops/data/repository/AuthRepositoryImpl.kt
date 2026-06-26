package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.local.entity.UserEntity
import com.candorfoods.factoryops.data.remote.api.AuthApi
import com.candorfoods.factoryops.data.remote.dto.LoginRequestDto
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val db: AppDatabase,
    private val authPreferences: AuthPreferences
) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<User> {
        return try {
            val response = authApi.login(LoginRequestDto(username, password))
            // Prefer the backend's expires_at when it's a clean epoch value; otherwise
            // keep the local 7-day fallback. (Format unconfirmed — see AuthDtos.)
            val tokenExpiresAt = parseExpiresAt(response.expiresAt)
                ?: System.currentTimeMillis() + 7 * 24 * 3_600_000L
            val entity = UserEntity(
                id = response.userId,
                name = response.name,
                username = username,
                role = response.role,
                plantId = response.plantId,
                jwtToken = response.token,
                tokenExpiresAt = tokenExpiresAt
            )
            db.userDao().insert(entity)
            authPreferences.saveToken(response.token)
            authPreferences.saveUserId(response.userId)
            authPreferences.savePlantId(response.plantId)
            Result.Success(entity.toDomain())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Login failed", e)
        }
    }

    override suspend fun logout() {
        db.userDao().clearAll()
        authPreferences.clear()
    }

    override fun getCurrentUser(): Flow<User?> =
        db.userDao().getCurrentUser().map { it?.toDomain() }

    override fun isLoggedIn(): Boolean = authPreferences.isLoggedIn()

    /**
     * Parse the backend's `expires_at` (epoch milliseconds, UTC — confirmed). Returns
     * null for absent/garbage so the caller can fall back. The seconds-vs-ms guard is
     * kept as cheap defence in case the backend ever changes units.
     */
    private fun parseExpiresAt(raw: String?): Long? {
        val n = raw?.trim()?.toLongOrNull() ?: return null
        // < ~year 2286 in seconds → treat as seconds; otherwise already milliseconds.
        return if (n < 10_000_000_000L) n * 1000 else n
    }

    private fun UserEntity.toDomain() = User(
        id = id,
        name = name,
        username = username,
        // Backend currently sends OPERATOR / TECHNICIAN / HEAD. The other values are
        // legacy/local roles still used by some screens. Trim+uppercase so casing or
        // stray whitespace from the API can't silently drop a user to OPERATOR.
        // Unknown roles fall through to OPERATOR (safe screen) rather than crashing.
        role = when (role.trim().uppercase()) {
            "TECHNICIAN" -> UserRole.TECHNICIAN
            "HEAD"       -> UserRole.HEAD
            // Backend now sends a single "QC" role; treat it as QC Head so the user gets
            // the full QC inbox + actions (incl. the head-only override). Legacy
            // QC_HEAD/QC_MEMBER still map explicitly. Change "QC" -> UserRole.QC_MEMBER
            // here if QC should be the restricted (no-override) level instead.
            "QC", "QC_HEAD" -> UserRole.QC_HEAD
            "QC_MEMBER"  -> UserRole.QC_MEMBER
            "SUPERVISOR" -> UserRole.SUPERVISOR
            "ADMIN"      -> UserRole.ADMIN
            else         -> UserRole.OPERATOR
        },
        plantId = plantId,
        jwtToken = jwtToken,
        tokenExpiresAt = tokenExpiresAt
    )
}
