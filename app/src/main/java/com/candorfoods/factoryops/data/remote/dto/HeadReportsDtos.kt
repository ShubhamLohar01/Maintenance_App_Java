package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.QcStatus
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.SyncStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of GET /breakdowns/history or GET /qc/history (mt_breakdown_records), richer than
 * [OpenBreakdownDto] — it carries the resolved/QC-decided fields open breakdowns don't have yet.
 *
 * Machine-reading and floor-reading history reuse the existing `ReportsApi`/`ReportsRepository`
 * (see `data/remote/api/ReportsApi.kt`) instead of a parallel DTO here — that pipeline already
 * covers `mt_machine_daily_kwh`/`mt_floor_utility_readings` history generically.
 */
@Serializable
data class BreakdownHistoryDto(
    val id: String,
    @SerialName("asset_id") val assetId: String,
    @SerialName("asset_name") val assetName: String = "",
    val building: String? = null,
    @SerialName("reported_by") val reportedBy: String? = null,
    @SerialName("reporter_name") val reporterName: String? = null,
    val severity: String? = null,
    val description: String = "",
    val status: String,
    @SerialName("reported_at") val reportedAt: Long? = null,
    @SerialName("acknowledged_by") val acknowledgedBy: String? = null,
    @SerialName("acknowledged_by_name") val acknowledgedByName: String? = null,
    @SerialName("acknowledged_at") val acknowledgedAt: Long? = null,
    @SerialName("resolved_by") val resolvedBy: String? = null,
    @SerialName("resolved_by_name") val resolvedByName: String? = null,
    @SerialName("resolved_at") val resolvedAt: Long? = null,
    @SerialName("parts_used") val partsUsed: String? = null,
    @SerialName("qc_status") val qcStatus: String? = null,
    @SerialName("qc_decided_by") val qcDecidedBy: String? = null,
    @SerialName("qc_decided_by_name") val qcDecidedByName: String? = null,
    @SerialName("qc_decided_at") val qcDecidedAt: Long? = null,
    @SerialName("qc_decision_notes") val qcDecisionNotes: String? = null,
)

fun BreakdownHistoryDto.toDomain() = BreakdownFlag(
    id = id,
    machineId = assetId,
    assetName = assetName,
    building = building,
    operatorId = reportedBy.orEmpty(),
    severity = when (severity) {
        "CRITICAL" -> Severity.CRITICAL
        "MAJOR"    -> Severity.MAJOR
        else       -> Severity.MINOR
    },
    description = description,
    beforePhotoLocalPath = null,
    raisedAt = reportedAt ?: 0L,
    status = when (status) {
        "ACKNOWLEDGED" -> BreakdownStatus.ACKNOWLEDGED
        "PENDING_QC"   -> BreakdownStatus.PENDING_QC
        "CLOSED"       -> BreakdownStatus.CLOSED
        "REOPENED"     -> BreakdownStatus.REOPENED
        else           -> BreakdownStatus.OPEN
    },
    acknowledgedBy = acknowledgedBy,
    acknowledgedByName = acknowledgedByName,
    acknowledgedAt = acknowledgedAt,
    resolvedBy = resolvedBy,
    resolvedByName = resolvedByName,
    resolvedAt = resolvedAt,
    partsUsed = partsUsed,
    afterPhotoLocalPath = null,
    qcStatus = when (qcStatus) {
        "PENDING"                  -> QcStatus.PENDING
        "APPROVED"                 -> QcStatus.APPROVED
        "REJECTED", "DISAPPROVED"  -> QcStatus.REJECTED
        else                       -> null
    },
    qcAcknowledgedBy = null,
    qcAcknowledgedByName = null,
    qcAcknowledgedAt = null,
    qcDecidedBy = qcDecidedBy,
    qcDecidedByName = qcDecidedByName,
    qcDecidedAt = qcDecidedAt,
    qcDecisionNotes = qcDecisionNotes,
    qcAfterPhotoLocalPath = null,
    qcChecklistJson = null,
    qcOverrideFromUserId = null,
    qcOverrideFromName = null,
    qcOverrideAt = null,
    syncStatus = SyncStatus.SYNCED,
)
