package com.candorfoods.factoryops.domain.model

/** Operator-side production-session state across recesses for one machine. */
data class RecessState(
    val machineId: String,
    val operatorId: String,
    val onRecess: Boolean,
    val accumulatedActiveMs: Long,
    val recessStartedAt: Long?,
)
