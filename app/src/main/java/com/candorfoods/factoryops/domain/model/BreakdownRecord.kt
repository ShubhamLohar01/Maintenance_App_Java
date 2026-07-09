package com.candorfoods.factoryops.domain.model

/** A submitted F.06 Breakdown Maintenance Record entry (one register row),
 *  read from GET /preventive-maintenance/breakdowns. */
data class BreakdownRecord(
    val id: Int,
    val docNo: String,
    val recordDate: String,
    val location: String,
    val machineName: String,
    val equipmentModelNo: String,
    val problemInBrief: String,
    val typeOfMaintenance: String,
    val partOfMachine: String,
    val temporaryReason: String,
    val durationStart: String,
    val durationEnd: String,
    val machineOperatorSign: String,
    val maintenancePersonSign: String,
    val qcClearanceSign: String,
    val verifiedBy: String,
    val createdAt: String,
)
