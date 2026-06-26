package com.candorfoods.factoryops.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RunStartRequestDto(
    @SerialName("machine_id") val machineId: String,
    @SerialName("client_run_id") val clientRunId: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("scheduled_end_at") val scheduledEndAt: Long,
)

@Serializable
data class RunStartResponseDto(
    @SerialName("run_id") val runId: String,
    @SerialName("client_run_id") val clientRunId: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("scheduled_end_at") val scheduledEndAt: Long,
)

@Serializable
data class RunStopRequestDto(
    @SerialName("ended_at") val endedAt: Long
)

@Serializable
data class RunStopResponseDto(
    @SerialName("run_id") val runId: String,
    @SerialName("ended_at") val endedAt: Long,
    @SerialName("computed_kwh") val computedKwh: Double
)

@Serializable
data class ProductionRunDto(
    val id: String,
    @SerialName("machine_id") val machineId: String,
    @SerialName("operator_id") val operatorId: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("scheduled_end_at") val scheduledEndAt: Long,
    @SerialName("ended_at") val endedAt: Long?,
    @SerialName("computed_kwh") val computedKwh: Double?,
    @SerialName("sync_status") val syncStatus: String,
    @SerialName("flag_status") val flagStatus: String?
)

@Serializable
data class DailyRunDto(
    val id: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("ended_at") val endedAt: Long?,
    @SerialName("duration_hours") val durationHours: Double,
    val kwh: Double,
)

@Serializable
data class DailyHistoryDto(
    val date: String,
    @SerialName("total_run_hours") val totalRunHours: Double,
    @SerialName("total_kwh") val totalKwh: Double,
    @SerialName("estimated_cost") val estimatedCost: Double,
    val runs: List<DailyRunDto>,
)

/**
 * One currently-active production run from GET /energy/runs/active — a run that
 * has started and not yet stopped, for any operator on any device. The Json
 * reader uses ignoreUnknownKeys, and optional fields default so a lean backend
 * payload (only asset_id + started_at) still deserializes.
 */
@Serializable
data class ActiveRunDto(
    @SerialName("asset_id")       val assetId: String,
    @SerialName("run_id")         val runId: String? = null,
    @SerialName("operator_id")    val operatorId: String? = null,
    @SerialName("operator_name")  val operatorName: String? = null,
    @SerialName("started_at")     val startedAt: Long,   // epoch millis (UTC)
    @SerialName("building")       val building: String? = null,
)

fun ActiveRunDto.toDomain(): com.candorfoods.factoryops.domain.model.ActiveRun =
    com.candorfoods.factoryops.domain.model.ActiveRun(
        assetId = assetId,
        runId = runId?.takeIf { it.isNotBlank() },
        operatorId = operatorId?.takeIf { it.isNotBlank() },
        operatorName = operatorName?.takeIf { it.isNotBlank() },
        startedAtEpochMs = startedAt,
        building = building?.takeIf { it.isNotBlank() },
    )
