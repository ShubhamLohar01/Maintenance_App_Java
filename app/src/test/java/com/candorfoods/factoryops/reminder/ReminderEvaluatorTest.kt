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

    @Test fun `OPEN flag reaches head only at tier 3`() {
        assertTrue(types(UserRole.HEAD, flag(raisedAt = 0L), 90 * MIN).isEmpty())
        assertEquals(listOf(ReminderType.B1_NEW_BREAKDOWN), types(UserRole.HEAD, flag(raisedAt = 0L), 130 * MIN))
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
        closedAt = null, finalNotes = null, escalationTier = 0,
    )

    private fun pmTypes(role: UserRole, w: PmWorkOrder, now: Long) =
        eval(user(role), emptyList(), listOf(w), now).map { it.type }

    @Test fun `submitted PM nudges supervisor, escalates to head at 48h`() {
        val w = wo(status = PmWoStatus.SUBMITTED, submittedAt = 0L, qcDecidedAt = null)
        assertEquals(listOf(ReminderType.P2_AWAITING_SUPERVISOR), pmTypes(UserRole.SUPERVISOR, w, 1 * HOUR))
        assertTrue(pmTypes(UserRole.HEAD, w, 10 * HOUR).isEmpty())
        assertEquals(listOf(ReminderType.P2_AWAITING_SUPERVISOR), pmTypes(UserRole.HEAD, w, 49 * HOUR))
    }

    @Test fun `supervisor-rejected PM nudges technician`() {
        val w = wo(status = PmWoStatus.IN_PROGRESS, submittedAt = null, supervisorRejectedAt = 0L)
        assertEquals(listOf(ReminderType.P3_SUPERVISOR_REJECTED), pmTypes(UserRole.TECHNICIAN, w, 1 * HOUR))
    }

    @Test fun `plain in-progress PM (never rejected) produces no reminder`() {
        val w = wo(status = PmWoStatus.IN_PROGRESS, submittedAt = null, supervisorRejectedAt = null)
        assertTrue(pmTypes(UserRole.TECHNICIAN, w, 100 * HOUR).isEmpty())
    }

    @Test fun `pending-qc PM nudges qc member, qc head at 24h`() {
        val w = wo(status = PmWoStatus.PENDING_QC, supervisorApprovedAt = 0L, qcDecidedAt = null)
        assertEquals(listOf(ReminderType.P4_AWAITING_QC), pmTypes(UserRole.QC_MEMBER, w, 1 * HOUR))
        assertTrue(pmTypes(UserRole.QC_HEAD, w, 10 * HOUR).isEmpty())
        assertEquals(listOf(ReminderType.P4_AWAITING_QC), pmTypes(UserRole.QC_HEAD, w, 25 * HOUR))
    }

    @Test fun `qc-rejected PM (submitted with decision) nudges supervisor as P5`() {
        val w = wo(status = PmWoStatus.SUBMITTED, submittedAt = 0L, qcDecidedAt = 0L)
        assertEquals(listOf(ReminderType.P5_QC_REJECTED), pmTypes(UserRole.SUPERVISOR, w, 1 * HOUR))
    }
}
