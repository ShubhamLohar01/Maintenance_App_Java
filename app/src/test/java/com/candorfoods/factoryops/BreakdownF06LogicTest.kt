package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.remote.dto.BreakdownRecordDto
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownRecord
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.QcStatus
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.SyncStatus
import com.candorfoods.factoryops.presentation.checklist.breakdownFlagToEntry
import com.candorfoods.factoryops.presentation.checklist.breakdownStatusLabel
import com.candorfoods.factoryops.presentation.checklist.isApproved
import com.candorfoods.factoryops.presentation.checklist.toF06Row
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BreakdownF06LogicTest {

    private fun flag(
        id: String = "bd1",
        machineId: String = "W202-0007",
        assetName: String? = "Band Sealer",
        building: String? = "W-202",
        description: String = "Belt snapped",
        raisedAt: Long = 0L,
        status: BreakdownStatus = BreakdownStatus.CLOSED,
        qcStatus: QcStatus? = QcStatus.APPROVED,
        resolvedByName: String? = "Tech Ravi",
        qcDecidedByName: String? = "QC Sam",
    ) = BreakdownFlag(
        id = id, machineId = machineId, assetName = assetName, building = building,
        operatorId = "op1", severity = Severity.MAJOR, description = description,
        beforePhotoLocalPath = null, raisedAt = raisedAt, status = status,
        acknowledgedBy = null, acknowledgedByName = null, acknowledgedAt = null,
        resolvedBy = null, resolvedByName = resolvedByName, resolvedAt = null,
        partsUsed = null, afterPhotoLocalPath = null, qcStatus = qcStatus,
        qcAcknowledgedBy = null, qcAcknowledgedByName = null, qcAcknowledgedAt = null,
        qcDecidedBy = null, qcDecidedByName = qcDecidedByName, qcDecidedAt = null,
        qcDecisionNotes = null, qcAfterPhotoLocalPath = null, qcChecklistJson = null,
        qcOverrideFromUserId = null, qcOverrideFromName = null, qcOverrideAt = null,
        syncStatus = SyncStatus.SYNCED,
    )

    @Test fun `isApproved is true only when qcStatus is APPROVED`() {
        assertTrue(isApproved(flag(qcStatus = QcStatus.APPROVED)))
        assertFalse(isApproved(flag(qcStatus = null, status = BreakdownStatus.PENDING_QC)))
        assertFalse(isApproved(flag(qcStatus = QcStatus.REJECTED, status = BreakdownStatus.REOPENED)))
    }

    @Test fun `status label reflects approval and stage`() {
        assertEquals("Approved", breakdownStatusLabel(flag(qcStatus = QcStatus.APPROVED)))
        assertEquals("Pending QC", breakdownStatusLabel(flag(qcStatus = null, status = BreakdownStatus.PENDING_QC)))
        assertEquals("In progress", breakdownStatusLabel(flag(qcStatus = null, status = BreakdownStatus.ACKNOWLEDGED)))
        assertEquals("Reopened", breakdownStatusLabel(flag(qcStatus = QcStatus.REJECTED, status = BreakdownStatus.REOPENED)))
        assertEquals("Open", breakdownStatusLabel(flag(qcStatus = null, status = BreakdownStatus.OPEN)))
        // CLOSED without APPROVED (data anomaly) must not read as "Open".
        assertEquals("Closed", breakdownStatusLabel(flag(qcStatus = null, status = BreakdownStatus.CLOSED)))
    }

    private fun record(
        id: Int = 12,
        machineName: String = "Band Sealer",
        equipmentModelNo: String = "W202-0007",
    ) = BreakdownRecord(
        id = id, docNo = "CFPLA.C4.F.06", recordDate = "2026-06-27", location = "W-202",
        machineName = machineName, equipmentModelNo = equipmentModelNo,
        problemInBrief = "Belt snapped", typeOfMaintenance = "Mechanical", partOfMachine = "Belt",
        temporaryReason = "", durationStart = "10:00", durationEnd = "11:30",
        machineOperatorSign = "Ravi", maintenancePersonSign = "Suresh", qcClearanceSign = "QC Sam",
        verifiedBy = "Head", createdAt = "2026-06-27T11:35:00Z",
    )

    @Test fun `toF06Row copies fields and uses srNo 1`() {
        val row = record().toF06Row()
        assertEquals(1, row.srNo)
        assertEquals("2026-06-27", row.date)
        assertEquals("W-202", row.location)
        assertEquals("Band Sealer", row.machineName)
        assertEquals("W202-0007", row.equipmentModel)
        assertEquals("Belt snapped", row.problem)
        assertEquals("Mechanical", row.typeOfMaintenance)
        assertEquals("Belt", row.partOfMachine)
        assertEquals("10:00", row.durationStart)
        assertEquals("QC Sam", row.qcClearanceSign)
    }

    @Test fun `toF06Row falls back to equipment model when machine name is blank`() {
        assertEquals("W202-0007", record(machineName = "").toF06Row().machineName)
    }

    @Test fun `BreakdownRecordDto toDomain maps snake_case fields`() {
        val dto = BreakdownRecordDto(
            id = 7, recordDate = "2026-06-01", machineName = "Pet Sealer",
            equipmentModelNo = "A185-0003", problemInBrief = "Jam", verifiedBy = "QA",
        )
        val d = dto.toDomain()
        assertEquals(7, d.id)
        assertEquals("2026-06-01", d.recordDate)
        assertEquals("Pet Sealer", d.machineName)
        assertEquals("A185-0003", d.equipmentModelNo)
        assertEquals("Jam", d.problemInBrief)
        assertEquals("QA", d.verifiedBy)
        assertEquals("CFPLA.C4.F.06", d.docNo) // default
    }

    @Test fun `breakdownFlagToEntry pre-fills the reusable F06 columns`() {
        val e = breakdownFlagToEntry(flag(), key = "bd-0")
        assertEquals("bd-0", e.key)
        assertEquals("W-202", e.location)
        assertEquals("Band Sealer", e.machineName)
        assertEquals("W202-0007", e.equipmentModel)
        assertEquals("Belt snapped", e.problem)
        assertEquals("QC Sam", e.qcClearanceSign)
        assertEquals("Tech Ravi", e.maintenancePersonSign)
        assertEquals("", e.machineOperatorSign)   // gap: only operator id is stored
        // Date/time derive from raisedAt=0L in IST; resolvedAt is null → blank end.
        assertEquals("1970-01-01", e.date)
        assertEquals("05:30", e.durationStart)
        assertEquals("", e.durationEnd)
    }
}
