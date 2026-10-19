package com.candorfoods.factoryops.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Request bodies for the PM work-order lifecycle endpoints (POST /pm/work-orders/{id}/…).
 * `at` is an optional epoch-ms the app sends for offline-correct timestamps; the backend
 * defaults to its own now when null. Responses are always a full [PmWorkOrderDto].
 */

/** acknowledge + start share this — just the (optional) client timestamp. */
@Serializable
data class PmAtRequestDto(val at: Long? = null)

/**
 * Submit replaces the WO's task_logs + spares with these arrays (the technician's results),
 * sets final_notes, and records the machine-level `overall_result` (PASS/FAIL) the
 * technician chose on the summary screen. Each task log's status carries the per-item
 * "Is good?" verdict (PASS = good, FAIL = not good) plus its remark in `notes`.
 */
@Serializable
data class PmSubmitRequestDto(
    @SerialName("final_notes") val finalNotes: String = "",
    @SerialName("overall_result") val overallResult: String = "",
    val at: Long? = null,
    @SerialName("task_logs") val taskLogs: List<PmTaskLogDto> = emptyList(),
    val spares: List<PmSpareDto> = emptyList(),
)

@Serializable
data class PmSupervisorApproveRequestDto(
    @SerialName("supervisor_id") val supervisorId: String = "",
    @SerialName("supervisor_name") val supervisorName: String = "",
    val at: Long? = null,
)

@Serializable
data class PmSupervisorRejectRequestDto(
    @SerialName("supervisor_id") val supervisorId: String = "",
    @SerialName("supervisor_name") val supervisorName: String = "",
    val notes: String = "",
    val at: Long? = null,
)

@Serializable
data class PmQcAckRequestDto(
    @SerialName("user_id") val userId: String = "",
    @SerialName("user_name") val userName: String = "",
    val at: Long? = null,
)

/**
 * QC approve / disapprove. `checklist` is the freeform sign-off blob stored verbatim in
 * qc_checklist (the app folds in its checklist JSON + any after-photo URL). `notes` is a
 * convenience merged server-side when checklist is null.
 */
@Serializable
data class PmQcDecisionRequestDto(
    @SerialName("user_id") val userId: String = "",
    @SerialName("user_name") val userName: String = "",
    val checklist: JsonElement? = null,
    val notes: String = "",
    val at: Long? = null,
)

@Serializable
data class PmPhotoUploadResponseDto(val url: String)
