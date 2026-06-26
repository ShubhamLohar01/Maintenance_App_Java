package com.candorfoods.factoryops.domain.model

/** Technician-entered F.06 fields for one breakdown, persisted on-device. */
data class BreakdownF06Draft(
    val breakdownId: String,
    val location: String,
    val equipmentModel: String,
    val typeOfMaintenance: String,
    val partOfMachine: String,
    val reason: String,
    val durationStart: String,
    val durationEnd: String,
    val machineOperatorSign: String,
    val maintenancePersonSign: String,
    val qcClearanceSign: String,
    val verifiedBy: String,
    val updatedAt: Long,
)
