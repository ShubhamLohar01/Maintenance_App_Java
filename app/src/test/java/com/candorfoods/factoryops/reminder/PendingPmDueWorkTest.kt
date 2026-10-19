package com.candorfoods.factoryops.reminder

import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.TradeType
import com.candorfoods.factoryops.domain.usecase.pm.PendingPmDueWorkUseCase
import com.candorfoods.factoryops.domain.usecase.pm.PmDueNoticePolicy
import com.candorfoods.factoryops.util.DateTimeFormatter
import org.junit.jupiter.api.Assertions.assertFalse
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What the technician's daily PM reminder is allowed to nag about. The reported problem
 * was a technician being notified hourly about 19 tasks he had already submitted.
 */
class PendingPmDueWorkTest {

    private val pendingDueWork = PendingPmDueWorkUseCase()
    private val dayMs = 86_400_000L
    private val now = System.currentTimeMillis()

    private fun wo(
        id: String,
        status: PmWoStatus,
        daysFromToday: Int = 0,
        techId: String = "tech-5",
    ) = PmWorkOrder(
        id = id,
        planId = "plan-$id",
        machineId = "M-1",
        templateId = "tpl-1",
        templateName = "Water pump PM",
        machineType = MachineType.ROASTER,
        estimatedDurationMinutes = 30,
        tradeType = TradeType.MECHANICAL,
        scheduledDate = now + daysFromToday * dayMs,
        generatedAt = now,
        status = status,
        assignedTechnicianId = techId,
        assignedTechnicianName = "Anil",
        acknowledgedAt = null,
        startedAt = null,
        submittedAt = null,
        supervisorApprovedBy = null,
        supervisorApprovedByName = null,
        supervisorApprovedAt = null,
        supervisorRejectedAt = null,
        supervisorRejectionNotes = null,
        qcAcknowledgedBy = null,
        qcAcknowledgedByName = null,
        qcAcknowledgedAt = null,
        qcDecidedBy = null,
        qcDecidedByName = null,
        qcDecidedAt = null,
        qcDecisionNotes = null,
        qcAfterPhotoLocalPath = null,
        qcChecklistJson = null,
        closedAt = null,
        finalNotes = null,
        overallResult = null,
        escalationTier = 0,
    )

    /** The reported bug, in one test: submitted work must never trigger a reminder. */
    @Test
    fun `work the technician already submitted is never reminded about`() {
        val pending = pendingDueWork(
            listOf(
                wo("submitted", PmWoStatus.SUBMITTED, daysFromToday = -1),
                wo("with-qc", PmWoStatus.PENDING_QC, daysFromToday = -1),
                wo("approved", PmWoStatus.SUPERVISOR_APPROVED, daysFromToday = -1),
                wo("closed", PmWoStatus.CLOSED, daysFromToday = -1),
            ),
            "tech-5", now,
        )

        assertTrue(pending.isEmpty())
    }

    @Test
    fun `only work still to do, due today or overdue, oldest first`() {
        val pending = pendingDueWork(
            listOf(
                wo("due-today", PmWoStatus.NOTIFIED, daysFromToday = 0),
                wo("slipped", PmWoStatus.ACKNOWLEDGED, daysFromToday = -6),
                wo("started", PmWoStatus.IN_PROGRESS, daysFromToday = -2),
                wo("next-week", PmWoStatus.NOTIFIED, daysFromToday = 5),
            ),
            "tech-5", now,
        )

        assertEquals(listOf("slipped", "started", "due-today"), pending.map { it.id })
    }

    @Test
    fun `work generated ahead of its due date is not reminded about yet`() {
        val pending = pendingDueWork(
            listOf(wo("ahead", PmWoStatus.NOTIFIED, daysFromToday = 3)),
            "tech-5", now,
        )

        assertTrue(pending.isEmpty())
    }

    @Test
    fun `another technician's task is never reminded about`() {
        val pending = pendingDueWork(
            listOf(
                wo("mine", PmWoStatus.NOTIFIED, daysFromToday = -1),
                wo("theirs", PmWoStatus.NOTIFIED, daysFromToday = -1, techId = "tech-8"),
            ),
            "tech-5", now,
        )

        assertEquals(listOf("mine"), pending.map { it.id })
    }

    @Test
    fun `nothing pending means no reminder at all`() {
        assertTrue(pendingDueWork(emptyList(), "tech-5", now).isEmpty())
    }

    // --- when the one daily notice may fire ----------------------------------

    private val ist = ZoneId.of("Asia/Kolkata")

    private fun istAt(hour: Int): Long =
        LocalDate.of(2026, 8, 26).atTime(LocalTime.of(hour, 30)).atZone(ist).toInstant().toEpochMilli()

    @Test
    fun `nothing pending means the notice never fires`() {
        assertFalse(PmDueNoticePolicy.shouldNotify(0, null, istAt(9)))
    }

    /** The old behaviour let the first worker run after midnight buzz at 2am. */
    @Test
    fun `the notice is held until shift start`() {
        assertFalse(PmDueNoticePolicy.shouldNotify(3, null, istAt(2)))
        assertFalse(PmDueNoticePolicy.shouldNotify(3, null, istAt(7)))
        assertTrue(PmDueNoticePolicy.shouldNotify(3, null, istAt(8)))
    }

    @Test
    fun `only one notice per day, however often the worker runs`() {
        val morning = istAt(9)
        assertTrue(PmDueNoticePolicy.shouldNotify(3, null, morning))

        val today = DateTimeFormatter.isoDate(morning)
        assertFalse(PmDueNoticePolicy.shouldNotify(3, today, morning))
        assertFalse(PmDueNoticePolicy.shouldNotify(3, today, istAt(14)))
        assertFalse(PmDueNoticePolicy.shouldNotify(3, today, istAt(21)))
    }

    @Test
    fun `yesterday's notice does not suppress today's`() {
        val yesterday = DateTimeFormatter.isoDate(istAt(9) - 86_400_000L)
        assertTrue(PmDueNoticePolicy.shouldNotify(3, yesterday, istAt(9)))
    }
}
