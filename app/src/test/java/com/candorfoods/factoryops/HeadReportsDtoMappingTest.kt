package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.remote.dto.BreakdownHistoryDto
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.QcStatus
import com.candorfoods.factoryops.domain.model.Severity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HeadReportsDtoMappingTest {

    @Test
    fun `BreakdownHistoryDto maps status, severity and qcStatus like the rest of the app`() {
        val dto = BreakdownHistoryDto(
            id = "bd-1", assetId = "A185-0007", assetName = "Roaster 1", building = "A-185",
            reportedBy = "op-1", reporterName = "Ravi", severity = "CRITICAL",
            description = "Belt snapped", status = "CLOSED", reportedAt = 1000L,
            acknowledgedBy = "tech-1", acknowledgedByName = "Suresh", acknowledgedAt = 1100L,
            resolvedBy = "tech-1", resolvedByName = "Suresh", resolvedAt = 1200L,
            partsUsed = "V-belt", qcStatus = "APPROVED", qcDecidedBy = "qc-1",
            qcDecidedByName = "Sam", qcDecidedAt = 1300L, qcDecisionNotes = "Looks good",
        )
        val flag = dto.toDomain()
        assertEquals(Severity.CRITICAL, flag.severity)
        assertEquals(BreakdownStatus.CLOSED, flag.status)
        assertEquals(QcStatus.APPROVED, flag.qcStatus)
        assertEquals("Sam", flag.qcDecidedByName)
        assertNull(flag.beforePhotoLocalPath) // not present in the history payload
    }

    @Test
    fun `BreakdownHistoryDto defaults unknown severity to MINOR and unknown status to OPEN`() {
        val dto = BreakdownHistoryDto(
            id = "bd-2", assetId = "A185-0008", status = "SOME_FUTURE_STATUS", severity = null,
        )
        val flag = dto.toDomain()
        assertEquals(Severity.MINOR, flag.severity)
        assertEquals(BreakdownStatus.OPEN, flag.status)
        assertNull(flag.qcStatus)
    }
}
