package com.candorfoods.factoryops.domain.model

data class ProductionRun(
    val id: String,
    val machineId: String,
    val operatorId: String,
    val startedAt: Long,
    val scheduledEndAt: Long,
    val endedAt: Long?,
    val computedKwh: Double?,
    val syncStatus: SyncStatus,
    val flagStatus: FlagStatus?,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isRunning: Boolean get() = endedAt == null
    val runHours: Double? get() = endedAt?.let { (it - startedAt) / 3_600_000.0 }
}
