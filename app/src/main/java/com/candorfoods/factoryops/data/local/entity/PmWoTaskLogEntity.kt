package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pm_wo_task_logs")
data class PmWoTaskLogEntity(
    @PrimaryKey val id: String,
    val woId: String,
    val templateItemId: String,
    val orderIndex: Int,
    val title: String,
    val description: String,        // "Remarks" guidance snapshot
    val status: String,            // PENDING | PASS (Is good) | FAIL (not good)
    val notes: String?,            // technician remark; required when FAIL
    val completedAt: Long?,
    val completedBy: String?,
    val syncStatus: String
)
