package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.FloorReadingRow
import com.candorfoods.factoryops.domain.model.FloorReadingsSnapshot
import com.candorfoods.factoryops.domain.model.FloorReadingsSubmitResult
import com.candorfoods.factoryops.domain.model.Result

/** Daily Reading: system-computed kWh per floor (GET) and the technician's actual readings (POST). */
interface FloorReadingRepository {

    /**
     * GET /floor-readings/system — system kWh + any saved meter reading per floor.
     * [building] is null for technician/operator (resolved from JWT); set for HEAD/SUPERVISOR.
     */
    suspend fun fetchSystemReadings(
        date: String? = null,
        building: String? = null,
    ): Result<FloorReadingsSnapshot>

    /**
     * POST /floor-readings — upsert the typed meter readings. [building] must echo the
     * GET response's building. Send only the floors the technician actually filled.
     */
    suspend fun submitReadings(
        date: String?,
        building: String,
        rows: List<FloorReadingRow>,
    ): Result<FloorReadingsSubmitResult>
}
