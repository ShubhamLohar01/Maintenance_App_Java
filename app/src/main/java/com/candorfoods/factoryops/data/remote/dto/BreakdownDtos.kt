package com.candorfoods.factoryops.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// NOTE: POST /breakdowns/flag and POST /breakdowns/{id}/work-done are multipart/form-data
// now (the operator before-photo / technician after-photo are uploaded to S3 backend-side),
// so their request bodies are built as multipart parts in BreakdownRepositoryImpl rather
// than serialized DTOs. See BreakdownApi.raiseFlag / workDone.

@Serializable
data class BreakdownFlagResponseDto(
    val id: String,
    @SerialName("sync_status") val syncStatus: String
)

@Serializable
data class QcAcknowledgeRequestDto(
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String,
    /** Login username of the QC user — backend writes this to
     *  mt_breakdown_records.qc_checked_by. Null on the technician-side acknowledge
     *  path (which reuses this DTO but doesn't drive the QC column). */
    @SerialName("qc_checked_by") val qcCheckedBy: String? = null,
    @SerialName("acknowledged_at") val acknowledgedAt: Long,
    @SerialName("override") val override: Boolean = false
)

@Serializable
data class QcDecisionRequestDto(
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String,
    @SerialName("decided_at") val decidedAt: Long,
    @SerialName("checklist_json") val checklistJson: String,
    @SerialName("after_photo_path") val afterPhotoPath: String?,
    val notes: String? = null,
    val reason: String? = null
)

@Serializable
data class QcUpdateResponseDto(
    val id: String,
    @SerialName("ticket_status") val ticketStatus: String,
    @SerialName("machine_status") val machineStatus: String,
    @SerialName("qc_status") val qcStatus: String?,
    @SerialName("sync_status") val syncStatus: String
)

/** One open breakdown from GET /breakdowns/open (mt_breakdown_records, the live
 * breakdown table, not yet CLOSED). `id`/`reportedAt` mirror the server row so the
 * app can key Room on the same id and resolve detail by tap. */
@Serializable
data class OpenBreakdownDto(
    val id: String,
    @SerialName("asset_id") val assetId: String,
    @SerialName("asset_name") val assetName: String = "",
    @SerialName("reported_by") val reportedBy: String? = null,
    @SerialName("reporter_name") val reporterName: String? = null,
    val severity: String? = null,
    val description: String = "",
    val status: String,
    @SerialName("reported_at") val reportedAt: Long? = null,
    /** Acknowledging technician, populated once status=ACKNOWLEDGED. Required so the
     *  technician's "My Active" list resolves on ANY device, not just the one that
     *  performed the acknowledge. `acknowledgedBy` = the user id the app matches
     *  against the logged-in user; `acknowledgedByName` = display name. Null while OPEN. */
    @SerialName("acknowledged_by") val acknowledgedBy: String? = null,
    @SerialName("acknowledged_by_name") val acknowledgedByName: String? = null,
    @SerialName("acknowledged_at") val acknowledgedAt: Long? = null,
    val building: String? = null,
    /** Populated by the server when this row is currently OPEN because QC
     *  disapproved a prior repair attempt. Null otherwise. */
    @SerialName("qc_reject_reason") val qcRejectReason: String? = null,
    /** "APPROVED" / "DISAPPROVED" / "PENDING" / null — needed so a re-opened
     *  ticket (status=OPEN + qcStatus=DISAPPROVED) can show the rejection card
     *  on other devices, not just the one that did the disapprove. */
    @SerialName("qc_status") val qcStatus: String? = null
)
