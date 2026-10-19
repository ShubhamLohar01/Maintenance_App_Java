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

/**
 * Areas that appear in the asset register but have no electric meter to read — the
 * canteen, the gates and the lab. They come back from GET /floor-readings/system like any
 * other sub_location, and a technician can only ever leave them blank, so the Daily
 * Reading round hides them rather than showing rows nobody can fill.
 *
 * Matched on normalised names because sub_location is hand-typed in pgAdmin:
 * "Back Side Gate", "back-side gate" and "BACKSIDE GATE" are all the same place.
 */
private val METERLESS_FLOORS: Set<String> = setOf(
    "canteen",
    "backsidegate",
    "entrygate",
    "lab",
    "laboratory",
    "labfloor",
)

/** Lowercase, letters and digits only — "Back Side Gate" -> "backsidegate". */
private fun normaliseFloor(name: String): String =
    name.lowercase().filter { it.isLetterOrDigit() }

/** True when this floor has no meter and should not be offered for a reading. */
fun isMeterlessFloor(name: String): Boolean = normaliseFloor(name) in METERLESS_FLOORS
