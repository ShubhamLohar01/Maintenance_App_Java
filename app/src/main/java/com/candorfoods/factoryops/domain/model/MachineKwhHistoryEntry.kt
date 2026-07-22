package com.candorfoods.factoryops.domain.model

/** One row of `mt_machine_daily_kwh` — a machine's recorded consumption for one day. */
data class MachineKwhHistoryEntry(
    val machineId: String,
    val machineName: String,
    val building: String,
    val floor: String,
    val readingDate: String,   // ISO yyyy-MM-dd
    val dailyKwh: Double,
    val source: String,        // RUN | SCHEDULE | MANUAL
    val startedAt: Long?,
    val endedAt: Long?,
    val status: String?,
    val operatorName: String?,
)
