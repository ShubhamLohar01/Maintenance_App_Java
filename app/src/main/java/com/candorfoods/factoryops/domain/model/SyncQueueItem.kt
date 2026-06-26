package com.candorfoods.factoryops.domain.model

data class SyncQueueItem(
    val id: Long,
    val actionType: ActionType,
    val payloadJson: String,
    val retryCount: Int,
    val lastErrorMessage: String?,
    val createdAt: Long
)
