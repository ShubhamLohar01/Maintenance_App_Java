package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.reports.FloorReadingReportRow
import com.candorfoods.factoryops.domain.model.reports.MachineReadingRow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── GET /reports/machines ───────────────────────────────────────────────────

@Serializable
data class MachinesReadingResponseDto(
    @SerialName("rows") val rows: List<MachineReadingRowDto> = emptyList(),
)

@Serializable
data class MachineReadingRowDto(
    @SerialName("reading_date")  val readingDate: String,
    @SerialName("machine_id")    val machineId: String,
    @SerialName("asset_name")    val assetName: String = "",
    @SerialName("building")      val building: String? = null,
    @SerialName("floor")         val floor: String? = null,
    @SerialName("operator_name") val operatorName: String? = null,
    @SerialName("started_at")    val startedAt: Long? = null,
    @SerialName("ended_at")      val endedAt: Long? = null,
    @SerialName("status")        val status: String? = null,
    @SerialName("source")        val source: String? = null,
    @SerialName("daily_kwh")     val dailyKwh: Double? = null,
)

// ─── GET /reports/floor-readings ─────────────────────────────────────────────

@Serializable
data class FloorReadingsReportResponseDto(
    @SerialName("rows") val rows: List<FloorReadingReportRowDto> = emptyList(),
)

@Serializable
data class FloorReadingReportRowDto(
    @SerialName("reading_date")  val readingDate: String,
    @SerialName("building")      val building: String,
    @SerialName("floor")         val floor: String,
    @SerialName("meter_reading") val meterReading: Double? = null,
    @SerialName("daily_kwh")     val dailyKwh: Double? = null,
)

// ─── mappers ─────────────────────────────────────────────────────────────────

fun MachinesReadingResponseDto.toDomain(): List<MachineReadingRow> = rows.map { it.toDomain() }

fun MachineReadingRowDto.toDomain() = MachineReadingRow(
    readingDate = readingDate,
    machineId = machineId,
    assetName = assetName,
    building = building,
    floor = floor,
    operatorName = operatorName,
    startedAt = startedAt,
    endedAt = endedAt,
    status = status,
    source = source,
    dailyKwh = dailyKwh,
)

fun FloorReadingsReportResponseDto.toDomain(): List<FloorReadingReportRow> = rows.map { it.toDomain() }

fun FloorReadingReportRowDto.toDomain() = FloorReadingReportRow(
    readingDate = readingDate,
    building = building,
    floor = floor,
    meterReading = meterReading,
    dailyKwh = dailyKwh,
)
