package com.candorfoods.factoryops.domain.model

data class PmPlan(
    val id: String,
    val machineId: String,
    val templateId: String,
    val triggerType: PmTriggerType,
    val triggerInterval: Int,
    val lastCompletedAt: Long?,
    val lastCompletedAtHours: Double?,
    val nextDueAt: Long,
    val assignedTechnicianId: String,
    val isActive: Boolean,
    val createdAt: Long
)
