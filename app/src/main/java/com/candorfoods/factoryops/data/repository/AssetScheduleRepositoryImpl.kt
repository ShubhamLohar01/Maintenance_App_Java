package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.AssetScheduleApi
import com.candorfoods.factoryops.data.remote.dto.AssetScheduleIntervalDto
import com.candorfoods.factoryops.data.remote.dto.AssetScheduleUpsertDto
import com.candorfoods.factoryops.data.remote.dto.daysToCodes
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.domain.model.ElectricAssetSchedule
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.ScheduleInterval
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
        intervals: List<ScheduleInterval>,
        active: Boolean,
        days: Set<Int>,
        is24h: Boolean,
    ): Result<ElectricAssetSchedule> {
        val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
        val sorted = intervals.sortedBy { it.startMin }
        if (sorted.isEmpty()) return Result.Error("A schedule needs at least one interval.")
        return try {
            val dto = api.upsert(
                "Bearer $token", machineId,
                AssetScheduleUpsertDto(
                    // The derived pair a pre-split-shift backend reads as the one window.
                    startMin = sorted.first().startMin,
                    endMin = sorted.last().endMin,
                    intervals = sorted.map { AssetScheduleIntervalDto(it.startMin, it.endMin) },
                    active = active,
                    days = daysToCodes(days),
                    is24h = is24h,
                ),
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
