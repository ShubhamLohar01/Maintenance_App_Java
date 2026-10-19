package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "machines")
data class MachineEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val location: String,
    val building: String = "W202",
    val plantId: String,
    val machineType: String,
    val category: String? = null,
    val ratedKw: Double,
    val loadFactor: Double,
    val loadFactorSource: String,
    val criticality: String,
    val expectedRunHours: Double,
    val currentStatus: String,
    val updatedAt: Long
)
