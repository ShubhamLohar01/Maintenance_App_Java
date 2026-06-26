package com.candorfoods.factoryops.presentation.checklist

import com.candorfoods.factoryops.domain.model.BreakdownF06Draft
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.QcStatus
import com.candorfoods.factoryops.util.DateTimeFormatter
import com.candorfoods.factoryops.util.pdf.BreakdownRow

/** The editable F.06 columns the technician completes (everything not already in the record). */
data class F06Fields(
    val location: String = "",
    val equipmentModel: String = "",
    val typeOfMaintenance: String = "",
    val partOfMachine: String = "",
    val reason: String = "",
    val durationStart: String = "",
    val durationEnd: String = "",
    val machineOperatorSign: String = "",
    val maintenancePersonSign: String = "",
    val qcClearanceSign: String = "",
    val verifiedBy: String = "",
)

/** A breakdown is fillable only once QC has approved it. */
fun isApproved(flag: BreakdownFlag): Boolean = flag.qcStatus == QcStatus.APPROVED

/** Short status label for the records list chip. */
fun breakdownStatusLabel(flag: BreakdownFlag): String = when {
    flag.qcStatus == QcStatus.APPROVED          -> "Approved"
    flag.status == BreakdownStatus.PENDING_QC   -> "Pending QC"
    flag.status == BreakdownStatus.ACKNOWLEDGED -> "In progress"
    flag.status == BreakdownStatus.REOPENED     -> "Reopened"
    else                                        -> "Open"
}

/** Seed the editable fields from what the system already knows. */
fun defaultF06Fields(flag: BreakdownFlag): F06Fields = F06Fields(
    location = flag.building.orEmpty(),
    equipmentModel = flag.machineId,
    maintenancePersonSign = flag.resolvedByName.orEmpty(),
    qcClearanceSign = flag.qcDecidedByName.orEmpty(),
)

fun BreakdownF06Draft.toFields(): F06Fields = F06Fields(
    location = location, equipmentModel = equipmentModel, typeOfMaintenance = typeOfMaintenance,
    partOfMachine = partOfMachine, reason = reason, durationStart = durationStart,
    durationEnd = durationEnd, machineOperatorSign = machineOperatorSign,
    maintenancePersonSign = maintenancePersonSign, qcClearanceSign = qcClearanceSign,
    verifiedBy = verifiedBy,
)

fun F06Fields.toDraft(breakdownId: String, updatedAt: Long): BreakdownF06Draft = BreakdownF06Draft(
    breakdownId = breakdownId, location = location, equipmentModel = equipmentModel,
    typeOfMaintenance = typeOfMaintenance, partOfMachine = partOfMachine, reason = reason,
    durationStart = durationStart, durationEnd = durationEnd,
    machineOperatorSign = machineOperatorSign, maintenancePersonSign = maintenancePersonSign,
    qcClearanceSign = qcClearanceSign, verifiedBy = verifiedBy, updatedAt = updatedAt,
)

/**
 * Merge the immutable breakdown record + the entered fields into one F.06 register row.
 * `srNo` is hard-coded to 1 because each download is a single-record sheet; a future
 * multi-record register must assign sequential numbers at the call site instead.
 */
fun buildF06Row(flag: BreakdownFlag, fields: F06Fields): BreakdownRow = BreakdownRow(
    srNo = 1,
    date = DateTimeFormatter.formatDate(flag.raisedAt),
    location = fields.location,
    machineName = flag.assetName?.ifBlank { null } ?: flag.machineId,
    equipmentModel = fields.equipmentModel,
    problem = flag.description,
    typeOfMaintenance = fields.typeOfMaintenance,
    partOfMachine = fields.partOfMachine,
    reason = fields.reason,
    durationStart = fields.durationStart,
    durationEnd = fields.durationEnd,
    machineOperatorSign = fields.machineOperatorSign,
    maintenancePersonSign = fields.maintenancePersonSign,
    qcClearanceSign = fields.qcClearanceSign,
)
