package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pm_work_orders")
data class PmWorkOrderEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val machineId: String,
    val templateId: String,
    val templateName: String,
    val machineType: String,
    val estimatedDurationMinutes: Int,
    val tradeType: String,
    val scheduledDate: Long,
    val generatedAt: Long,
    val status: String,
    val assignedTechnicianId: String,
    val assignedTechnicianName: String,
    val acknowledgedAt: Long?,
    val startedAt: Long?,
    val submittedAt: Long?,
    val supervisorApprovedBy: String?,
    val supervisorApprovedByName: String?,
    val supervisorApprovedAt: Long?,
    val supervisorRejectedAt: Long?,
    val supervisorRejectionNotes: String?,
    val qcAcknowledgedBy: String?,
    val qcAcknowledgedByName: String?,
    val qcAcknowledgedAt: Long?,
    val qcDecidedBy: String?,
    val qcDecidedByName: String?,
    val qcDecidedAt: Long?,
    val qcDecisionNotes: String?,
    val qcAfterPhotoLocalPath: String?,
    val qcChecklistJson: String?,
    val closedAt: Long?,
    val finalNotes: String?,
    val overallResult: String?,    // machine-level PASS/FAIL (summary screen)
    val escalationTier: Int,
    val syncStatus: String
)
