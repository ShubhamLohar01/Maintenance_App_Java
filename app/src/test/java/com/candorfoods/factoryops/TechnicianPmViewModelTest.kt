package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.Criticality
import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.domain.model.PmTriggerType
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.TradeType
import com.candorfoods.factoryops.presentation.technician.PmPlanRowUiModel
import com.candorfoods.factoryops.presentation.technician.PmTaskRowUiModel
import com.candorfoods.factoryops.presentation.technician.TechnicianPmViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The three derivations behind the technician PM screen's tabs: which plans land on
 * Today, which work order a Today row opens, and how finished work is grouped on History.
 */
class TechnicianPmViewModelTest {

    private val ist = ZoneId.of("Asia/Kolkata")
    private val dayMs = 86_400_000L

    private fun daysFromNow(days: Int): Long = System.currentTimeMillis() + days * dayMs

    private fun epochOf(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(ist).toInstant().toEpochMilli()

    private fun plan(
        id: String,
        machineName: String,
        daysFromToday: Int,
        woId: String? = null,
    ) = PmPlanRowUiModel(
        planId = id,
        machineName = machineName,
        machineCode = "W202-$id",
        // daysToDue floors both sides into the same day buckets, so an exact N-day
        // offset from now always reads back as N regardless of the time of day.
        nextDueAt = System.currentTimeMillis() + daysFromToday * dayMs,
        triggerType = PmTriggerType.TIME,
        triggerInterval = 7,
        woId = woId,
    )

    private fun wo(
        id: String,
        status: PmWoStatus,
        planId: String = "plan-1",
        scheduledDate: Long = epochOf(2026, 8, 1),
        submittedAt: Long? = null,
        closedAt: Long? = null,
    ) = PmWorkOrder(
        id = id,
        planId = planId,
        machineId = "M-1",
        templateId = "tpl-1",
        templateName = "Roaster PM",
        machineType = MachineType.ROASTER,
        estimatedDurationMinutes = 30,
        tradeType = TradeType.MECHANICAL,
        scheduledDate = scheduledDate,
        generatedAt = scheduledDate,
        status = status,
        assignedTechnicianId = "tech-1",
        assignedTechnicianName = "Tech One",
        acknowledgedAt = null,
        startedAt = null,
        submittedAt = submittedAt,
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
        closedAt = closedAt,
        finalNotes = null,
        overallResult = null,
        escalationTier = 0,
    )

    private fun row(wo: PmWorkOrder) = PmTaskRowUiModel(
        wo = wo,
        machineName = "Roaster",
        machineCode = "W202-0001",
        machineLocation = "Line 1",
        criticality = Criticality.C,
    )

    // --- Today ---------------------------------------------------------------

    @Test
    fun `today lists active work orders due today or overdue, most overdue first`() {
        val rows = TechnicianPmViewModel.todayWorkOrders(
            listOf(
                row(wo("today", PmWoStatus.NOTIFIED, scheduledDate = daysFromNow(0))),
                row(wo("slipped", PmWoStatus.ACKNOWLEDGED, scheduledDate = daysFromNow(-11))),
                row(wo("future", PmWoStatus.NOTIFIED, scheduledDate = daysFromNow(3))),
            )
        )

        assertEquals(listOf("slipped", "today"), rows.map { it.wo.id })
    }

    /** The reported bug: a plan due today whose work is already submitted was shown as
     *  pending-but-unstartable. Submitted work is done — it belongs on History. */
    @Test
    fun `today drops work the technician has already submitted`() {
        val rows = TechnicianPmViewModel.todayWorkOrders(
            listOf(
                row(wo("submitted", PmWoStatus.SUBMITTED, scheduledDate = daysFromNow(0))),
                row(wo("with-qc", PmWoStatus.PENDING_QC, scheduledDate = daysFromNow(-2))),
                row(wo("still-mine", PmWoStatus.IN_PROGRESS, scheduledDate = daysFromNow(0))),
            )
        )

        assertEquals(listOf("still-mine"), rows.map { it.wo.id })
    }

    /** The hidden-work half: a work order due today counts even when its plan's next due
     *  date has already rolled forward, which is why plan.nextDueAt is the wrong key. */
    @Test
    fun `today includes a due work order whose plan reads a future due date`() {
        val rows = TechnicianPmViewModel.todayWorkOrders(
            listOf(row(wo("due-now", PmWoStatus.NOTIFIED, scheduledDate = daysFromNow(0))))
        )

        assertEquals(listOf("due-now"), rows.map { it.wo.id })
    }

    @Test
    fun `a due plan with no work order at all still surfaces on today`() {
        val plans = listOf(
            plan("1", "Never generated", daysFromToday = -3),
            plan("2", "Has a task", daysFromToday = 0),
            plan("3", "Not due yet", daysFromToday = 5),
        )
        val wos = listOf(wo("wo-1", PmWoStatus.SUBMITTED, planId = "2"))

        val rows = TechnicianPmViewModel.ungeneratedDuePlans(plans, wos)

        assertEquals(listOf("Never generated"), rows.map { it.machineName })
    }

    @Test
    fun `today is empty when nothing is due`() {
        assertTrue(
            TechnicianPmViewModel.todayWorkOrders(
                listOf(row(wo("future", PmWoStatus.NOTIFIED, scheduledDate = daysFromNow(3))))
            ).isEmpty()
        )
    }

    // --- plan to work-order link ---------------------------------------------

    /** Plan rows must show the truth about their cycle, so the link spans every status —
     *  linking only active ones is what made a submitted task read "Not generated yet". */
    @Test
    fun `a plan links to its work order even once submitted`() {
        val map = TechnicianPmViewModel.latestWorkOrderByPlan(
            listOf(wo("wo-1", PmWoStatus.SUBMITTED, planId = "plan-1"))
        )

        assertEquals("wo-1", map["plan-1"]?.id)
        assertEquals(PmWoStatus.SUBMITTED, map["plan-1"]?.status)
    }

    @Test
    fun `a plan links to its newest cycle, not a closed older one`() {
        val map = TechnicianPmViewModel.latestWorkOrderByPlan(
            listOf(
                wo("old", PmWoStatus.CLOSED, scheduledDate = daysFromNow(-30)),
                wo("current", PmWoStatus.NOTIFIED, scheduledDate = daysFromNow(0)),
            )
        )

        assertEquals("current", map["plan-1"]?.id)
    }

    // --- what a plan row says about its cycle --------------------------------

    private fun planWith(status: PmWoStatus?) =
        plan("1", "Blender", daysFromToday = -1, woId = status?.let { "wo-1" })
            .copy(woStatus = status)

    /** The reported bug: an overdue plan whose work is already submitted read exactly like
     *  one still needing work, so the technician kept tapping it expecting to perform it. */
    @Test
    fun `a plan whose work is submitted says so instead of looking startable`() {
        val row = planWith(PmWoStatus.SUBMITTED)
        assertEquals("Submitted — in review", row.actionHint)
        assertFalse(row.isStartable)
        assertEquals("wo-1", row.woId)   // still opens the work order on tap
    }

    @Test
    fun `a plan with work still to do reads as startable`() {
        assertTrue(planWith(PmWoStatus.NOTIFIED).isStartable)
        assertEquals("Ready to start →", planWith(PmWoStatus.NOTIFIED).actionHint)
        assertEquals("In progress →", planWith(PmWoStatus.IN_PROGRESS).actionHint)
    }

    @Test
    fun `a plan with no work order at all says so`() {
        val row = planWith(null)
        assertEquals("Not generated yet", row.actionHint)
        assertFalse(row.isStartable)
        assertNull(row.woId)
    }

    @Test
    fun `a closed cycle is not offered for work`() {
        val row = planWith(PmWoStatus.CLOSED)
        assertEquals("Completed", row.actionHint)
        assertFalse(row.isStartable)
    }

    // --- History -------------------------------------------------------------

    @Test
    fun `history excludes work that is still active`() {
        val groups = TechnicianPmViewModel.historyGroups(
            listOf(
                row(wo("wo-1", PmWoStatus.NOTIFIED)),
                row(wo("wo-2", PmWoStatus.IN_PROGRESS)),
                row(wo("wo-3", PmWoStatus.SUBMITTED, submittedAt = epochOf(2026, 8, 19))),
            )
        )

        assertEquals(listOf("wo-3"), groups.flatMap { g -> g.rows.map { it.wo.id } })
    }

    @Test
    fun `history groups by month, newest month and row first`() {
        val groups = TechnicianPmViewModel.historyGroups(
            listOf(
                row(wo("jul", PmWoStatus.CLOSED, closedAt = epochOf(2026, 7, 30))),
                row(wo("aug-early", PmWoStatus.QC_APPROVED, closedAt = epochOf(2026, 8, 4))),
                row(wo("aug-late", PmWoStatus.SUBMITTED, submittedAt = epochOf(2026, 8, 22))),
            )
        )

        assertEquals(listOf("August 2026", "July 2026"), groups.map { it.monthLabel })
        assertEquals(listOf("aug-late", "aug-early"), groups[0].rows.map { it.wo.id })
        assertEquals(listOf("jul"), groups[1].rows.map { it.wo.id })
    }

    @Test
    fun `history dates an unclosed row by when it was submitted`() {
        val submitted = epochOf(2026, 8, 22)
        val groups = TechnicianPmViewModel.historyGroups(
            listOf(
                row(
                    wo(
                        "wo-1",
                        PmWoStatus.PENDING_QC,
                        scheduledDate = epochOf(2026, 7, 1),
                        submittedAt = submitted,
                    )
                )
            )
        )

        assertEquals("August 2026", groups.single().monthLabel)
        assertEquals(submitted, groups.single().rows.single().doneAt)
    }

    @Test
    fun `history is empty when nothing has been finished`() {
        assertTrue(TechnicianPmViewModel.historyGroups(emptyList()).isEmpty())
    }
}
