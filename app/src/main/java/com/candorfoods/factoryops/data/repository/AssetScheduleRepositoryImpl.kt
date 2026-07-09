package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.AssetScheduleApi
import com.candorfoods.factoryops.data.remote.dto.AssetScheduleUpsertDto
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.domain.model.ElectricAssetSchedule
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.AssetScheduleRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online-only (like MachineTransfer): scheduling needs the server (it owns the
 * generated consumption rows), so a failure surfaces as Result.Error rather than
 * being queued. Writes require SUPERVISOR — the backend enforces it (403 -> error).
 */
@Singleton
class AssetScheduleRepositoryImpl @Inject constructor(
    private val api: AssetScheduleApi,
    private val authPreferences: AuthPreferences,
) : AssetScheduleRepository {

    override suspend fun list(plantId: String): Result<List<ElectricAssetSchedule>> {
        val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
        return try {
            Result.Success(api.list("Bearer $token", plantId).map { it.toDomain() })
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load electric-asset schedules", e)
        }
    }

    override suspend fun setSchedule(
        machineId: String,
        startMin: Int,
        endMin: Int,
        active: Boolean,
    ): Result<ElectricAssetSchedule> {
        val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
        return try {
            val dto = api.upsert(
                "Bearer $token", machineId,
                AssetScheduleUpsertDto(startMin = startMin, endMin = endMin, active = active),
            )
            Result.Success(dto.toDomain())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to save schedule", e)
        }
    }

    override suspend fun clearSchedule(machineId: String): Result<ElectricAssetSchedule> {
        val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
        return try {
            Result.Success(api.clear("Bearer $token", machineId).toDomain())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to clear schedule", e)
        }
    }
}
