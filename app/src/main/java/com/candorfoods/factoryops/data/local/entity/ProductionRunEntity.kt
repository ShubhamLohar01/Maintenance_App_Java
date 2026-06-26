package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "production_runs")
data class ProductionRunEntity(
    @PrimaryKey val id: String,          // = clientRunId (device UUID)
    val machineId: String,               // = assetId
    val operatorId: String,
    val startedAt: Long,
    val scheduledEndAt: Long,
    val endedAt: Long?,
    val computedKwh: Double?,
    val syncStatus: String,
    val flagStatus: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val assetName: String? = null,       // snapshot at Start
    val ratedKw: Double? = null,         // snapshot — for offline kWh compute
    val serverRunId: String? = null,     // server run_id from start, used for stop sync
)
