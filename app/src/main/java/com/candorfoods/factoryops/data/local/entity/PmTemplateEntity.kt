package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pm_templates")
data class PmTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val machineType: String,
    val description: String,
    val estimatedDurationMinutes: Int,
    val defaultTradeType: String,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isActive: Boolean,
    val syncStatus: String
)

@Entity(tableName = "pm_template_items")
data class PmTemplateItemEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val orderIndex: Int,
    val title: String,
    val description: String,
    val expectedResult: String,
    val requiresPhoto: Boolean,
    val requiresMeasurement: Boolean,
    val measurementUnit: String?,
    val measurementMin: Double?,
    val measurementMax: Double?,
    val syncStatus: String
)
