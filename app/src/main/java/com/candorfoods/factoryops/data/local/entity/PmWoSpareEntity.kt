package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pm_wo_spares")
data class PmWoSpareEntity(
    @PrimaryKey val id: String,
    val woId: String,
    val spareName: String,
    val quantityUsed: Double,
    val unit: String,
    val notes: String?,
    val loggedAt: Long,
    val loggedBy: String,
    val syncStatus: String
)
