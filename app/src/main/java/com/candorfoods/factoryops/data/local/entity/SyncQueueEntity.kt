package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    val payloadJson: String,
    val retryCount: Int = 0,
    val lastErrorMessage: String?,
    val createdAt: Long
)
