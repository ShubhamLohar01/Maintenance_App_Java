package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "breakdown_flags")
data class BreakdownFlagEntity(
    @PrimaryKey val id: String,
    val machineId: String,
    val assetName: String? = null,
    val building: String? = null,
    val operatorId: String,
    val severity: String,
    val description: String,
    val beforePhotoLocalPath: String?,
    val raisedAt: Long,
    val status: String,
    val acknowledgedBy: String?,
    val acknowledgedByName: String?,
    val acknowledgedAt: Long?,
    val resolvedBy: String?,
    val resolvedByName: String?,
    val resolvedAt: Long?,
    val partsUsed: String?,
    val afterPhotoLocalPath: String?,
    // QC verification (Phase 2A)
    val qcStatus: String? = null,
    val qcAcknowledgedBy: String? = null,
    val qcAcknowledgedByName: String? = null,
    val qcAcknowledgedAt: Long? = null,
    val qcDecidedBy: String? = null,
    val qcDecidedByName: String? = null,
    val qcDecidedAt: Long? = null,
    val qcDecisionNotes: String? = null,
    val qcAfterPhotoLocalPath: String? = null,
    val qcChecklistJson: String? = null,
    val qcOverrideFromUserId: String? = null,
    val qcOverrideFromName: String? = null,
    val qcOverrideAt: Long? = null,
    val syncStatus: String
)
