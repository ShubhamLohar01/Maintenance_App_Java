package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.local.LanguagePreferences
import com.candorfoods.factoryops.data.local.entity.UserEntity
import com.candorfoods.factoryops.data.remote.api.AuthApi
import com.candorfoods.factoryops.data.remote.dto.LoginRequestDto
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.AuthRepository
import com.candorfoods.factoryops.notification.FcmTokenRegistrar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val db: AppDatabase,
    private val authPreferences: AuthPreferences,
    private val languagePreferences: LanguagePreferences,
    private val fcmTokenRegistrar: FcmTokenRegistrar,
) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<User> {
        return try {
            val response = authApi.login(LoginRequestDto(username, password))
            // Session window: honour the backend's expires_at when present; otherwise keep
            // the user signed in for 2 weeks locally. (For the 2-week session to hold end
            // to end, the backend must issue a token with a matching ~14-day TTL.)
            val tokenExpiresAt = parseExpiresAt(response.expiresAt)
                ?: System.currentTimeMillis() + 14 * 24 * 3_600_000L
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
            authPreferences.saveTokenExpiresAt(tokenExpiresAt)
            authPreferences.saveUserId(response.userId)
            authPreferences.savePlantId(response.plantId)
            languagePreferences.loadForUser(response.userId)
            // Now that we know who is signed in, tell the backend where to push to.
            fcmTokenRegistrar.registerCurrentToken()
            Result.Success(entity.toDomain())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Login failed", e)
        }
    }

    override suspend fun logout() {
        db.userDao().clearAll()
        authPreferences.clear()
        languagePreferences.resetToDefault()
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
        role = parseUserRole(role),
        plantId = plantId,
        jwtToken = jwtToken,
        tokenExpiresAt = tokenExpiresAt
    )
}

/**
 * Backend `mt_users.role` string -> [UserRole]. Trim + uppercase so casing or stray
 * whitespace from the API can't silently drop a user to OPERATOR. Unknown roles fall
 * through to OPERATOR (the safe, least-privileged screen) rather than crashing.
 *
 * Backend currently sends OPERATOR / TECHNICIAN / HEAD / QC / IT_TECHNICIAN; the rest
 * are legacy/local roles still used by some screens.
 */
fun parseUserRole(raw: String): UserRole = when (raw.trim().uppercase()) {
    "TECHNICIAN" -> UserRole.TECHNICIAN
    "HEAD"       -> UserRole.HEAD
    // Backend now sends a single "QC" role; treat it as QC Head so the user gets
    // the full QC inbox + actions (incl. the head-only override). Legacy
    // QC_HEAD/QC_MEMBER still map explicitly. Change "QC" -> UserRole.QC_MEMBER
    // here if QC should be the restricted (no-override) level instead.
    "QC", "QC_HEAD" -> UserRole.QC_HEAD
    "QC_MEMBER"  -> UserRole.QC_MEMBER
    // Plant-scoped QC accounts name their plant in the role itself ('qc-a185' ->
    // "QC-A185"). They get the ordinary QC_MEMBER interface; WHICH plant they see is
    // NOT read from here — it comes from User.plantId (mt_users.location), the same
    // source every other scoped screen uses. Without these cases the role fell
    // through to OPERATOR and the account never reached QC at all.
    "QC-A185", "QC_A185", "QCA185",
    "QC-W202", "QC_W202", "QCW202" -> UserRole.QC_MEMBER
    "SUPERVISOR" -> UserRole.SUPERVISOR
    "ADMIN"      -> UserRole.ADMIN
    // mt_users stores 'it_technician'; the spaced spelling is tolerated in case it's
    // typed that way in pgAdmin, and bare "IT" because that's the shortest plausible value.
    "IT_TECHNICIAN", "IT TECHNICIAN", "IT" -> UserRole.IT_TECHNICIAN
    else         -> UserRole.OPERATOR
}
