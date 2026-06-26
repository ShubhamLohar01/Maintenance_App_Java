package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pm_plans")
data class PmPlanEntity(
    @PrimaryKey val id: String,
    val machineId: String,
    val templateId: String,
    val triggerType: String,
    val triggerInterval: Int,
    val lastCompletedAt: Long?,
    val lastCompletedAtHours: Double?,
    val nextDueAt: Long,
    val assignedTechnicianId: String,
    val isActive: Boolean,
    val createdAt: Long,
    val syncStatus: String
)
