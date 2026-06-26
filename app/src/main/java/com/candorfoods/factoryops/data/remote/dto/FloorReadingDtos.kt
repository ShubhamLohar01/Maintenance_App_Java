package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.FloorReadingRow
import com.candorfoods.factoryops.domain.model.FloorReadingsSnapshot
import com.candorfoods.factoryops.domain.model.FloorReadingsSubmitResult
import com.candorfoods.factoryops.domain.model.FloorSystemReading
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── GET /floor-readings/system ──────────────────────────────────────────────

@Serializable
data class FloorReadingsResponseDto(
    @SerialName("building")     val building: String = "",
    @SerialName("reading_date") val readingDate: String = "",
    @SerialName("floors")       val floors: List<FloorSystemReadingDto> = emptyList(),
)

@Serializable
data class FloorSystemReadingDto(
    @SerialName("floor")          val floor: String,
    @SerialName("system_reading") val systemReading: Double = 0.0,   // read-only, auto-filled
    @SerialName("meter_reading")  val meterReading: Double? = null,   // prior saved value, or null
)

// ─── POST /floor-readings ────────────────────────────────────────────────────

@Serializable
data class FloorReadingsSubmitRequestDto(
    // Omitted entirely when null (encodeDefaults is off) → backend treats it as today.
    @SerialName("reading_date") val readingDate: String? = null,
    // Echo the building from the GET response. For technician/operator it matches their
    // resolved plant anyway; for an all-plant role (HEAD/SUPERVISOR) it's what lets the
    // POST succeed — the server refuses to guess the plant. Always sent.
    @SerialName("building")     val building: String,
    @SerialName("rows")         val rows: List<FloorReadingRowInDto>,
)

/** Only the floor + the typed meter reading are sent — never system_reading. */
@Serializable
data class FloorReadingRowInDto(
    @SerialName("floor")         val floor: String,
    @SerialName("meter_reading") val meterReading: Double,
)

@Serializable
data class FloorReadingsSubmitResponseDto(
    @SerialName("building")     val building: String = "",
    @SerialName("reading_date") val readingDate: String = "",
    @SerialName("saved")        val saved: Int = 0,
)

// ─── mappers ─────────────────────────────────────────────────────────────────

fun FloorReadingsResponseDto.toDomain() = FloorReadingsSnapshot(
    building = building,
    readingDate = readingDate,
    floors = floors.map { it.toDomain() },
)

fun FloorSystemReadingDto.toDomain() = FloorSystemReading(
    floor = floor,
    systemReading = systemReading,
    meterReading = meterReading,
)

fun FloorReadingsSubmitResponseDto.toDomain() = FloorReadingsSubmitResult(
    building = building,
    readingDate = readingDate,
    saved = saved,
)

fun FloorReadingRow.toDto() = FloorReadingRowInDto(
    floor = floor,
    meterReading = meterReading,
)
