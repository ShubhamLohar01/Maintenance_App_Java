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

/**
 * A plan + its checklist snapshot as fetched from the backend (mt_pm_plan) for the
 * technician's "Assigned PM plans" view — carries what the plan-detail sheet shows.
 */
data class AssignedPmPlan(
    val plan: PmPlan,
    val machineName: String,
    val description: String,
    val items: List<PmTemplateItem>,
)
