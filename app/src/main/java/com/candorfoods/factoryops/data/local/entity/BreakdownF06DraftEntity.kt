package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Technician-entered F.06 columns for one breakdown (the date/machine/problem stay sourced
 *  from breakdown_flags and are not copied here). A row existing = "saved/completed". */
@Entity(tableName = "breakdown_f06_drafts")
data class BreakdownF06DraftEntity(
    @PrimaryKey val breakdownId: String,
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
