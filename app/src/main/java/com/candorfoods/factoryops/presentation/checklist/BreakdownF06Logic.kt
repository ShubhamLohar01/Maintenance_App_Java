package com.candorfoods.factoryops.presentation.checklist

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownRecord
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.QcStatus
import com.candorfoods.factoryops.util.DateTimeFormatter
import com.candorfoods.factoryops.util.pdf.BreakdownRow

/** A breakdown is fillable only once QC has approved it. */
fun isApproved(flag: BreakdownFlag): Boolean = flag.qcStatus == QcStatus.APPROVED

/** Short status label for the records list chip. */
fun breakdownStatusLabel(flag: BreakdownFlag): String = when {
    flag.qcStatus == QcStatus.APPROVED          -> "Approved"
    flag.status == BreakdownStatus.PENDING_QC   -> "Pending QC"
    flag.status == BreakdownStatus.ACKNOWLEDGED -> "In progress"
    flag.status == BreakdownStatus.REOPENED     -> "Reopened"
    flag.status == BreakdownStatus.CLOSED       -> "Closed"   // closed without APPROVED = data anomaly
    else                                        -> "Open"
}

/** Map a submitted F.06 record to a single register row for PDF export.
 *  `srNo` is 1 because each download is a single-record sheet; a multi-record
 *  register must number rows at the call site. */
fun BreakdownRecord.toF06Row(): BreakdownRow = BreakdownRow(
    srNo = 1,
    date = recordDate,
    location = location,
    machineName = machineName.ifBlank { equipmentModelNo },
    equipmentModel = equipmentModelNo,
    problem = problemInBrief,
    typeOfMaintenance = typeOfMaintenance,
    partOfMachine = partOfMachine,
    reason = temporaryReason,
    durationStart = durationStart,
    durationEnd = durationEnd,
    machineOperatorSign = machineOperatorSign,
    maintenancePersonSign = maintenancePersonSign,
    qcClearanceSign = qcClearanceSign,
)

/** Pre-fill one F.06 form row from a QC-approved breakdown's lifecycle data. */
fun breakdownFlagToEntry(flag: BreakdownFlag, key: String): BreakdownEntryState = BreakdownEntryState(
    key = key,
    date = DateTimeFormatter.isoDate(flag.raisedAt),
    location = flag.building.orEmpty(),
    machineName = flag.assetName?.ifBlank { null } ?: flag.machineId,
    equipmentModel = flag.machineId,
    problem = flag.description,
    typeOfMaintenance = "",
    partOfMachine = "",
    reason = flag.partsUsed.orEmpty(),                                  // spare parts → Reason
    durationStart = DateTimeFormatter.formatTime(flag.raisedAt),
    durationEnd = flag.resolvedAt?.let { DateTimeFormatter.formatTime(it) }.orEmpty(),
    machineOperatorSign = "",                                          // record stores operator id, not name
    maintenancePersonSign = flag.resolvedByName.orEmpty(),
    qcClearanceSign = flag.qcDecidedByName.orEmpty(),
)
