package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.FloorReadingApi
import com.candorfoods.factoryops.data.remote.dto.FloorReadingsSubmitRequestDto
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.data.remote.dto.toDto
import com.candorfoods.factoryops.domain.model.FloorReadingRow
import com.candorfoods.factoryops.domain.model.FloorReadingsSnapshot
import com.candorfoods.factoryops.domain.model.FloorReadingsSubmitResult
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.FloorReadingRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online-only: the server is the source of truth. The GET pre-fills already-saved
 * meter readings and the POST upserts (building+floor+date), so there is no local
 * Room cache or sync queue here — unlike runs/breakdowns which buffer offline.
 */
@Singleton
class FloorReadingRepositoryImpl @Inject constructor(
    private val floorReadingApi: FloorReadingApi,
    private val authPreferences: AuthPreferences,
) : FloorReadingRepository {

    override suspend fun fetchSystemReadings(
        date: String?,
        building: String?,
    ): Result<FloorReadingsSnapshot> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            Result.Success(floorReadingApi.fetchSystem(token, date, building).toDomain())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load system readings", e)
        }
    }

    override suspend fun submitReadings(
        date: String?,
        building: String,
        rows: List<FloorReadingRow>,
    ): Result<FloorReadingsSubmitResult> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            val body = FloorReadingsSubmitRequestDto(
                readingDate = date,
                building = building,
                rows = rows.map { it.toDto() },
            )
            Result.Success(floorReadingApi.submit(token, body).toDomain())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to submit readings", e)
        }
    }
}
