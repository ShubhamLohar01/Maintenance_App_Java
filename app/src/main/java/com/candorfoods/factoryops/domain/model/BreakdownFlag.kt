package com.candorfoods.factoryops.domain.model

/**
 * One spare the technician picked while repairing a breakdown. [partId] is the
 * mt_spareparts row it decrements when the repair is submitted — always set,
 * because breakdown spares can only be chosen from the picker.
 *
 * The breakdown twin of [PmWoSpare]; kept separate because a breakdown is keyed by
 * flag id and these rows never enter the offline sync queue.
 */
data class BreakdownSpare(
    val id: String,
    val flagId: String,
    val partId: Int,
    val spareName: String,
    val machineName: String?,
    val quantityUsed: Double,
    val unit: String,
    val notes: String?,
    val loggedAt: Long,
)

data class BreakdownFlag(
    val id: String,
    val machineId: String,
    val assetName: String? = null,
    val building: String? = null,
    val operatorId: String,
    val severity: Severity,
    val description: String,
    val beforePhotoLocalPath: String?,
    val raisedAt: Long,
    val status: BreakdownStatus,
    val acknowledgedBy: String?,
    val acknowledgedByName: String?,
    val acknowledgedAt: Long?,
    val resolvedBy: String?,
    val resolvedByName: String?,
    val resolvedAt: Long?,
    val partsUsed: String?,
    val afterPhotoLocalPath: String?,
    // QC verification fields (Phase 2A)
    val qcStatus: QcStatus?,
    val qcAcknowledgedBy: String?,
    val qcAcknowledgedByName: String?,
    val qcAcknowledgedAt: Long?,
    val qcDecidedBy: String?,
    val qcDecidedByName: String?,
    val qcDecidedAt: Long?,
    val qcDecisionNotes: String?,
    val qcAfterPhotoLocalPath: String?,
    val qcChecklistJson: String?,
    val qcOverrideFromUserId: String?,
    val qcOverrideFromName: String?,
    val qcOverrideAt: Long?,
    val syncStatus: SyncStatus
)
