package com.candorfoods.factoryops.data.remote.mock

import com.candorfoods.factoryops.data.remote.api.FloorReadingApi
import com.candorfoods.factoryops.data.remote.dto.FloorReadingsResponseDto
import com.candorfoods.factoryops.data.remote.dto.FloorReadingsSubmitRequestDto
import com.candorfoods.factoryops.data.remote.dto.FloorReadingsSubmitResponseDto
import com.candorfoods.factoryops.data.remote.dto.FloorSystemReadingDto
import kotlinx.coroutines.delay

/**
 * Stand-in for the backend floor-readings endpoints (mt_floor_utility_readings)
 * so the Daily Reading tab is exercisable under USE_MOCK_API=true. Returns a few
 * sample floors, one pre-filled (90.0) and one idle (system 0.0, no prior reading).
 */
class MockFloorReadingApi : FloorReadingApi {

    override suspend fun fetchSystem(
        token: String,
        date: String?,
        building: String?,
    ): FloorReadingsResponseDto {
        delay(300)
        return FloorReadingsResponseDto(
            building = building ?: "A-185",
            readingDate = date ?: "2026-06-25",
            floors = listOf(
                FloorSystemReadingDto(floor = "Ground floor", systemReading = 142.7, meterReading = null),
                FloorSystemReadingDto(floor = "1st floor", systemReading = 88.3, meterReading = 90.0),
                FloorSystemReadingDto(floor = "2nd floor", systemReading = 0.0, meterReading = null),
            ),
        )
    }

    override suspend fun submit(
        token: String,
        body: FloorReadingsSubmitRequestDto,
    ): FloorReadingsSubmitResponseDto {
        delay(400)
        return FloorReadingsSubmitResponseDto(
            building = body.building.ifBlank { "A-185" },
            readingDate = body.readingDate ?: "2026-06-25",
            saved = body.rows.size,
        )
    }
}
