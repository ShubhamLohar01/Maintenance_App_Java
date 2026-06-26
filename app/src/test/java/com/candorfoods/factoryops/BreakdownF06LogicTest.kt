package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.BreakdownF06Draft
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.QcStatus
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.SyncStatus
import com.candorfoods.factoryops.presentation.checklist.breakdownStatusLabel
import com.candorfoods.factoryops.presentation.checklist.buildF06Row
import com.candorfoods.factoryops.presentation.checklist.defaultF06Fields
import com.candorfoods.factoryops.presentation.checklist.isApproved
import com.candorfoods.factoryops.presentation.checklist.toDraft
import com.candorfoods.factoryops.presentation.checklist.toFields
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
    }

    @Test fun `defaultF06Fields seeds location, equipment and signer suggestions from the flag`() {
        val f = defaultF06Fields(flag())
        assertEquals("W-202", f.location)
        assertEquals("W202-0007", f.equipmentModel)
        assertEquals("Tech Ravi", f.maintenancePersonSign)
        assertEquals("QC Sam", f.qcClearanceSign)
        assertEquals("", f.typeOfMaintenance)
    }

    @Test fun `defaultF06Fields tolerates null building and names`() {
        val f = defaultF06Fields(flag(building = null, resolvedByName = null, qcDecidedByName = null))
        assertEquals("", f.location)
        assertEquals("", f.maintenancePersonSign)
        assertEquals("", f.qcClearanceSign)
    }

    @Test fun `buildF06Row maps flag plus entered fields and falls back to machineId for name`() {
        val fields = defaultF06Fields(flag()).copy(typeOfMaintenance = "Mechanical", partOfMachine = "Belt")
        val row = buildF06Row(flag(), fields)
        assertEquals(1, row.srNo)
        assertEquals("Band Sealer", row.machineName)
        assertEquals("W202-0007", row.equipmentModel)
        assertEquals("Belt snapped", row.problem)
        assertEquals("Mechanical", row.typeOfMaintenance)
        assertEquals("Belt", row.partOfMachine)
        // name falls back to machineId when assetName is blank/null
        assertEquals("W202-0007", buildF06Row(flag(assetName = null), fields).machineName)
    }

    @Test fun `fields round-trip through a draft`() {
        val fields = defaultF06Fields(flag()).copy(reason = "temp fix", verifiedBy = "Head")
        val draft = fields.toDraft(breakdownId = "bd1", updatedAt = 123L)
        assertEquals("bd1", draft.breakdownId)
        assertEquals(123L, draft.updatedAt)
        assertEquals(fields, draft.toFields())
    }
}
