package com.candorfoods.factoryops.domain.model

/** One floor's system-computed kWh plus any already-saved actual meter reading. */
data class FloorSystemReading(
    val floor: String,
    val systemReading: Double,
    val meterReading: Double?,
)

/**
 * The day's floor readings for the caller's building (from GET /floor-readings/system).
 * Building/date are resolved server-side from the JWT — the client sends no building param.
 */
data class FloorReadingsSnapshot(
    val building: String,
    val readingDate: String,
    val floors: List<FloorSystemReading>,
)

/** One floor's actual meter reading the technician is submitting. */
data class FloorReadingRow(
    val floor: String,
    val meterReading: Double,
)

/** Outcome of POST /floor-readings. */
data class FloorReadingsSubmitResult(
    val building: String,
    val readingDate: String,
    val saved: Int,
)
