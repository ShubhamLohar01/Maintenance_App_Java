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
    val description: String,
    val expectedResult: String,
    val requiresPhoto: Boolean,
    val requiresMeasurement: Boolean,
    val measurementUnit: String?,
    val measurementMin: Double?,
    val measurementMax: Double?,
    val status: String,
    val measurementValue: Double?,
    val photoLocalPath: String?,
    val notes: String?,
    val completedAt: Long?,
    val completedBy: String?,
    val syncStatus: String
)
