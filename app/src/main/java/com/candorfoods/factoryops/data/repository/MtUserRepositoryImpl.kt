package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.MtUserApi
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.data.remote.dto.toWriteDto
import com.candorfoods.factoryops.domain.model.MtUser
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MtUserRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MtUserRepositoryImpl @Inject constructor(
    private val mtUserApi: MtUserApi,
    private val authPreferences: AuthPreferences,
) : MtUserRepository {

    override suspend fun list(): Result<List<MtUser>> {
        val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
        return try {
            Result.Success(mtUserApi.list("Bearer $token").map { it.toDomain() })
        } catch (e: Exception) {
            Result.Error(friendly(e, "Failed to load users"), e)
        }
    }

    override suspend fun add(user: MtUser): Result<MtUser> {
        val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
        return try {
            Result.Success(mtUserApi.add("Bearer $token", user.toWriteDto()).toDomain())
        } catch (e: Exception) {
            Result.Error(friendly(e, "Failed to add user"), e)
        }
    }

    override suspend fun update(user: MtUser): Result<MtUser> {
        val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
        return try {
            Result.Success(mtUserApi.update("Bearer $token", user.id, user.toWriteDto()).toDomain())
        } catch (e: Exception) {
            Result.Error(friendly(e, "Failed to update user"), e)
        }
    }

    override suspend fun delete(id: Int): Result<Unit> {
        val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
        return try {
            mtUserApi.delete("Bearer $token", id)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(friendly(e, "Failed to delete user"), e)
        }
    }

    /**
     * User-facing error text. Prefer the backend's own `detail` (so a self-delete 400
     * reads "You cannot delete your own account" and a duplicate 409 reads "username
     * already exists" — the same message the mock throws, keeping them consistent);
     * otherwise map by status code, else the raw message.
     */
    private fun friendly(e: Exception, fallback: String): String = when (e) {
        is HttpException -> backendDetail(e) ?: when (e.code()) {
            403 -> "You don't have permission to do that"
            404 -> "User not found"
            else -> e.message ?: fallback
        }
        else -> e.message ?: fallback
    }

    /** FastAPI's `{"detail": "..."}` from an error response, if present and a plain string. */
    private fun backendDetail(e: HttpException): String? = try {
        e.response()?.errorBody()?.string()
            ?.takeIf { it.isNotBlank() }
            ?.let { errorJson.decodeFromString<ApiError>(it).detail }
            ?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null   // non-string detail (e.g. a 422 list) or unparseable body -> fall back
    }

    @Serializable
    private data class ApiError(val detail: String? = null)

    private companion object {
        private val errorJson = Json { ignoreUnknownKeys = true }
    }
}
