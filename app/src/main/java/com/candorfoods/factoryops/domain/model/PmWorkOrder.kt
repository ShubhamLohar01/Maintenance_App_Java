package com.candorfoods.factoryops.domain.model

data class PmWorkOrder(
    val id: String,
    val planId: String,
    val machineId: String,
    val templateId: String,
    val templateName: String,                  // snapshot, survives template edits/deletes
    val machineType: MachineType,              // snapshot
    val estimatedDurationMinutes: Int,         // snapshot
    val tradeType: TradeType,                  // snapshot
    val scheduledDate: Long,
    val generatedAt: Long,
    val status: PmWoStatus,
    val assignedTechnicianId: String,
    val assignedTechnicianName: String,
    val acknowledgedAt: Long?,
    val startedAt: Long?,
    val submittedAt: Long?,
    val supervisorApprovedBy: String?,
    val supervisorApprovedByName: String?,
    val supervisorApprovedAt: Long?,
    val supervisorRejectedAt: Long?,           // Phase 2C-2 reject path
    val supervisorRejectionNotes: String?,
    val qcAcknowledgedBy: String?,             // Phase 2C-3
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
    val escalationTier: Int,                   // 0..4 — Phase 2C-4
    val taskLogs: List<PmWoTaskLog> = emptyList(),
    val spares: List<PmWoSpare> = emptyList()
)

data class PmWoTaskLog(
    val id: String,
    val woId: String,
    val templateItemId: String,
    val orderIndex: Int,
    // snapshot of template item at WO generation time
    val title: String,
    val description: String,
    val expectedResult: String,
    val requiresPhoto: Boolean,
    val requiresMeasurement: Boolean,
    val measurementUnit: String?,
    val measurementMin: Double?,
    val measurementMax: Double?,
    // execution data
    val status: PmTaskLogStatus,
    val measurementValue: Double?,
    val photoLocalPath: String?,
    val notes: String?,
    val completedAt: Long?,
    val completedBy: String?
)

data class PmWoSpare(
    val id: String,
    val woId: String,
    val spareName: String,
    val quantityUsed: Double,
    val unit: String,
    val notes: String?,
    val loggedAt: Long,
    val loggedBy: String
)
