package com.candorfoods.factoryops.reminder

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.ReminderType
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.SyncStatus
import com.candorfoods.factoryops.domain.model.TradeType
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.usecase.reminder.ReminderEvaluator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReminderEvaluatorTest {

    private val eval = ReminderEvaluator()
    private val MIN = 60_000L
    private val HOUR = 3_600_000L

    private fun user(role: UserRole) = User(
        id = "u1", name = "U", username = "u", role = role,
        plantId = "W202", jwtToken = "t", tokenExpiresAt = 0L,
    )

    private fun flag(
        id: String = "flag-1",
        status: BreakdownStatus = BreakdownStatus.OPEN,
        severity: Severity = Severity.MAJOR,
        raisedAt: Long = 0L,
        acknowledgedAt: Long? = null,
        resolvedAt: Long? = null,
        qcDecidedAt: Long? = null,
    ) = BreakdownFlag(
        id = id, machineId = "W202-7", assetName = "Band Sealer", building = "W-202",
        operatorId = "op1", severity = severity, description = "Belt snapped",
        beforePhotoLocalPath = null, raisedAt = raisedAt, status = status,
        acknowledgedBy = null, acknowledgedByName = null, acknowledgedAt = acknowledgedAt,
        resolvedBy = null, resolvedByName = null, resolvedAt = resolvedAt,
        partsUsed = null, afterPhotoLocalPath = null, qcStatus = null,
        qcAcknowledgedBy = null, qcAcknowledgedByName = null, qcAcknowledgedAt = null,
        qcDecidedBy = null, qcDecidedByName = null, qcDecidedAt = qcDecidedAt,
        qcDecisionNotes = null, qcAfterPhotoLocalPath = null, qcChecklistJson = null,
        qcOverrideFromUserId = null, qcOverrideFromName = null, qcOverrideAt = null,
        syncStatus = SyncStatus.SYNCED,
    )

    private fun types(role: UserRole, f: BreakdownFlag, now: Long) =
        eval(user(role), listOf(f), emptyList(), now).map { it.type }

    @Test fun `OPEN flag nudges technician immediately at tier 0`() {
        val out = eval(user(UserRole.TECHNICIAN), listOf(flag(raisedAt = 0L)), emptyList(), 5 * MIN)
        assertEquals(1, out.size)
        assertEquals(ReminderType.B1_NEW_BREAKDOWN, out[0].type)
        assertEquals(0, out[0].tier)
        assertEquals(UserRole.TECHNICIAN, out[0].recipientRole)
        assertEquals("ticket_id", out[0].deepLinkExtraKey)
    }

    @Test fun `OPEN flag does not reach supervisor until tier 2`() {
        assertTrue(types(UserRole.SUPERVISOR, flag(raisedAt = 0L), 45 * MIN).isEmpty())
        assertEquals(listOf(ReminderType.B1_NEW_BREAKDOWN), types(UserRole.SUPERVISOR, flag(raisedAt = 0L), 70 * MIN))
    }

    @Test fun `OPEN flag reaches head only at tier 4 (5h)`() {
        // Head no longer wakes at 2h (tier 3) — must wait for tier 4 = 300 min = 5h.
        assertTrue(types(UserRole.HEAD, flag(raisedAt = 0L), 130 * MIN).isEmpty())
        assertTrue(types(UserRole.HEAD, flag(raisedAt = 0L), 290 * MIN).isEmpty())
        assertEquals(listOf(ReminderType.B1_NEW_BREAKDOWN), types(UserRole.HEAD, flag(raisedAt = 0L), 310 * MIN))
    }

    @Test fun `CRITICAL severity halves thresholds`() {
        val out = eval(user(UserRole.SUPERVISOR), listOf(flag(severity = Severity.CRITICAL, raisedAt = 0L)), emptyList(), 35 * MIN)
        assertEquals(listOf(ReminderType.B1_NEW_BREAKDOWN), out.map { it.type })
    }

    @Test fun `ACKNOWLEDGED flag only nudges once stale`() {
        val fresh = flag(status = BreakdownStatus.ACKNOWLEDGED, acknowledgedAt = 0L)
        assertTrue(types(UserRole.TECHNICIAN, fresh, 10 * MIN).isEmpty())
        assertEquals(listOf(ReminderType.B2_ACK_STALE), types(UserRole.TECHNICIAN, fresh, 40 * MIN))
    }

    @Test fun `PENDING_QC undecided nudges qc member`() {
        val f = flag(status = BreakdownStatus.PENDING_QC, resolvedAt = 0L, qcDecidedAt = null)
        assertEquals(listOf(ReminderType.B3_AWAITING_QC), types(UserRole.QC_MEMBER, f, 5 * MIN))
    }

    @Test fun `REOPENED nudges technician`() {
        val f = flag(status = BreakdownStatus.REOPENED, qcDecidedAt = 0L)
        assertEquals(listOf(ReminderType.B4_REOPENED), types(UserRole.TECHNICIAN, f, 5 * MIN))
    }

    @Test fun `operator and admin get no breakdown reminders`() {
        assertTrue(types(UserRole.OPERATOR, flag(), 200 * MIN).isEmpty())
        assertTrue(types(UserRole.ADMIN, flag(), 200 * MIN).isEmpty())
    }

    private fun userIn(role: UserRole, plant: String) = User(
        id = "u1", name = "U", username = "u", role = role,
        plantId = plant, jwtToken = "t", tokenExpiresAt = 0L,
    )

    @Test fun `technician does not get another warehouse's breakdown`() {
        // A-185 technician, W-202 flag -> plant-scoped, no reminder.
        val out = eval(userIn(UserRole.TECHNICIAN, "A185"), listOf(flag(raisedAt = 0L)), emptyList(), 5 * MIN)
        assertTrue(out.isEmpty())
    }

    @Test fun `technician gets own warehouse breakdown (hyphen vs compact spelling)`() {
        val out = eval(userIn(UserRole.TECHNICIAN, "W-202"), listOf(flag(raisedAt = 0L)), emptyList(), 5 * MIN)
        assertEquals(listOf(ReminderType.B1_NEW_BREAKDOWN), out.map { it.type })
    }

    @Test fun `supervisor still gets cross-plant breakdown (all-plant role)`() {
        // A-185 supervisor sees a W-202 breakdown once it reaches their tier (2).
        val out = eval(userIn(UserRole.SUPERVISOR, "A185"), listOf(flag(raisedAt = 0L)), emptyList(), 70 * MIN)
        assertEquals(listOf(ReminderType.B1_NEW_BREAKDOWN), out.map { it.type })
    }

    private fun wo(
        id: String = "wo-1",
        status: PmWoStatus = PmWoStatus.SUBMITTED,
        scheduledDate: Long = 0L,
        submittedAt: Long? = 0L,
        supervisorApprovedAt: Long? = null,
        supervisorRejectedAt: Long? = null,
        qcDecidedAt: Long? = null,
    ) = PmWorkOrder(
        id = id, planId = "p1", machineId = "W202-7", templateId = "tpl1",
        templateName = "Monthly roaster PM", machineType = MachineType.ROASTER,
        estimatedDurationMinutes = 60, tradeType = TradeType.MECHANICAL,
        scheduledDate = scheduledDate, generatedAt = 0L, status = status,
        assignedTechnicianId = "tech1", assignedTechnicianName = "Priya",
        acknowledgedAt = null, startedAt = null, submittedAt = submittedAt,
        supervisorApprovedBy = null, supervisorApprovedByName = null,
        supervisorApprovedAt = supervisorApprovedAt,
        supervisorRejectedAt = supervisorRejectedAt, supervisorRejectionNotes = null,
        qcAcknowledgedBy = null, qcAcknowledgedByName = null, qcAcknowledgedAt = null,
        qcDecidedBy = null, qcDecidedByName = null, qcDecidedAt = qcDecidedAt,
        qcDecisionNotes = null, qcAfterPhotoLocalPath = null, qcChecklistJson = null,
        closedAt = null, finalNotes = null, overallResult = null, escalationTier = 0,
    )

    private fun pmTypes(role: UserRole, w: PmWorkOrder, now: Long) =
        eval(user(role), emptyList(), listOf(w), now).map { it.type }

    @Test fun `submitted PM waits 24h before nudging supervisor, escalates to head at 48h`() {
        val w = wo(status = PmWoStatus.SUBMITTED, submittedAt = 0L, qcDecidedAt = null)
        // A PM that reached the queue an hour ago isn't worth interrupting anyone for.
        assertTrue(pmTypes(UserRole.SUPERVISOR, w, 1 * HOUR).isEmpty())
        assertEquals(listOf(ReminderType.P2_AWAITING_SUPERVISOR), pmTypes(UserRole.SUPERVISOR, w, 25 * HOUR))
        assertTrue(pmTypes(UserRole.HEAD, w, 10 * HOUR).isEmpty())
        assertEquals(listOf(ReminderType.P2_AWAITING_SUPERVISOR), pmTypes(UserRole.HEAD, w, 49 * HOUR))
    }

    /** A technician's only PM notification is the daily "pending today" notice; rework
     *  comes back as an IN_PROGRESS work order, which that notice already covers. */
    @Test fun `supervisor-rejected PM does not separately nudge the technician`() {
        val w = wo(status = PmWoStatus.IN_PROGRESS, submittedAt = null, supervisorRejectedAt = 0L)
        assertTrue(pmTypes(UserRole.TECHNICIAN, w, 1 * HOUR).isEmpty())
        assertTrue(pmTypes(UserRole.TECHNICIAN, w, 100 * HOUR).isEmpty())
        // The supervisor still hears about it once it has sat for 48h.
        assertEquals(listOf(ReminderType.P3_SUPERVISOR_REJECTED), pmTypes(UserRole.SUPERVISOR, w, 49 * HOUR))
    }

    @Test fun `plain in-progress PM (never rejected) produces no reminder`() {
        val w = wo(status = PmWoStatus.IN_PROGRESS, submittedAt = null, supervisorRejectedAt = null)
        assertTrue(pmTypes(UserRole.TECHNICIAN, w, 100 * HOUR).isEmpty())
    }

    @Test fun `pending-qc PM waits 24h before nudging qc member or qc head`() {
        val w = wo(status = PmWoStatus.PENDING_QC, supervisorApprovedAt = 0L, qcDecidedAt = null)
        assertTrue(pmTypes(UserRole.QC_MEMBER, w, 1 * HOUR).isEmpty())
        assertEquals(listOf(ReminderType.P4_AWAITING_QC), pmTypes(UserRole.QC_MEMBER, w, 25 * HOUR))
        assertTrue(pmTypes(UserRole.QC_HEAD, w, 10 * HOUR).isEmpty())
        assertEquals(listOf(ReminderType.P4_AWAITING_QC), pmTypes(UserRole.QC_HEAD, w, 25 * HOUR))
    }

    @Test fun `qc-rejected PM waits 24h before nudging supervisor as P5`() {
        val w = wo(status = PmWoStatus.SUBMITTED, submittedAt = 0L, qcDecidedAt = 0L)
        assertTrue(pmTypes(UserRole.SUPERVISOR, w, 1 * HOUR).isEmpty())
        assertEquals(listOf(ReminderType.P5_QC_REJECTED), pmTypes(UserRole.SUPERVISOR, w, 25 * HOUR))
    }

    /** Breakdowns are deliberately NOT quietened: this is how the floor learns a machine
     *  is down, so it must still reach the technician the moment it is raised. */
    @Test fun `a new breakdown still reaches the technician immediately`() {
        assertEquals(
            listOf(ReminderType.B1_NEW_BREAKDOWN),
            types(UserRole.TECHNICIAN, flag(raisedAt = 0L), 5 * MIN),
        )
    }
}
