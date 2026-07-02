package com.candorfoods.factoryops.domain.usecase.reminder

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.Reminder
import com.candorfoods.factoryops.domain.model.ReminderCategory
import com.candorfoods.factoryops.domain.model.ReminderType
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.util.Constants
import javax.inject.Inject

/**
 * Pure logic: given the logged-in user, the current breakdown flags and PM work
 * orders, and `now`, return the reminders THIS user's role should see right now.
 * No Android / DB / time-source dependency — fully unit-testable.
 *
 * Audience maps express the escalation chain: a role receives a reminder once the
 * computed tier reaches its entry. A role absent from the map never receives it.
 * PM P1 (overdue technician escalation) stays in PmSchedulerWorker and is NOT emitted
 * here, to avoid double-notifying.
 */
class ReminderEvaluator @Inject constructor() {

    operator fun invoke(
        user: User,
        flags: List<BreakdownFlag>,
        workOrders: List<PmWorkOrder>,
        now: Long,
    ): List<Reminder> {
        val out = mutableListOf<Reminder>()
        flags.forEach { evaluateBreakdown(user, it, now, out) }
        workOrders.forEach { evaluatePm(user, it, now, out) }
        return out
    }

    // ── Breakdown ─────────────────────────────────────────────────────────────

    private fun evaluateBreakdown(user: User, f: BreakdownFlag, now: Long, out: MutableList<Reminder>) {
        when (f.status) {
            BreakdownStatus.OPEN -> {
                val tier = breakdownTier(now - f.raisedAt, f.severity)
                emit(out, B1_AUDIENCE, user.role, tier) {
                    Reminder(
                        type = ReminderType.B1_NEW_BREAKDOWN,
                        category = ReminderCategory.BREAKDOWN,
                        entityId = f.id, recipientRole = user.role, tier = tier,
                        title = "${f.severity.name} breakdown — ${assetLabel(f)}",
                        body = f.description.ifBlank { "Needs a technician." },
                        deepLinkExtraKey = "ticket_id",
                    )
                }
            }
            BreakdownStatus.ACKNOWLEDGED -> {
                val ackAt = f.acknowledgedAt ?: return
                val tier = breakdownTier(now - ackAt, f.severity)
                if (tier < 1) return // not stale yet
                emit(out, B2_AUDIENCE, user.role, tier) {
                    Reminder(
                        type = ReminderType.B2_ACK_STALE,
                        category = ReminderCategory.BREAKDOWN,
                        entityId = f.id, recipientRole = user.role, tier = tier,
                        title = "Repair pending — ${assetLabel(f)}",
                        body = "Acknowledged but not resolved yet. Please finish the repair.",
                        deepLinkExtraKey = "ticket_id",
                    )
                }
            }
            BreakdownStatus.PENDING_QC -> {
                if (f.qcDecidedAt != null) return
                val since = f.resolvedAt ?: f.raisedAt
                val tier = breakdownTier(now - since, f.severity)
                emit(out, B3_AUDIENCE, user.role, tier) {
                    Reminder(
                        type = ReminderType.B3_AWAITING_QC,
                        category = ReminderCategory.BREAKDOWN,
                        entityId = f.id, recipientRole = user.role, tier = tier,
                        title = "QC check needed — ${assetLabel(f)}",
                        body = "Repair done; waiting for QC verification.",
                        deepLinkExtraKey = "ticket_id",
                    )
                }
            }
            BreakdownStatus.REOPENED -> {
                val since = f.qcDecidedAt ?: f.raisedAt
                val tier = breakdownTier(now - since, f.severity)
                emit(out, B4_AUDIENCE, user.role, tier) {
                    Reminder(
                        type = ReminderType.B4_REOPENED,
                        category = ReminderCategory.BREAKDOWN,
                        entityId = f.id, recipientRole = user.role, tier = tier,
                        title = "QC rejected — rework — ${assetLabel(f)}",
                        body = "QC sent this back. Rework needed.",
                        deepLinkExtraKey = "ticket_id",
                    )
                }
            }
            BreakdownStatus.CLOSED -> Unit // B5 informational one-shot deferred (see plan)
        }
    }

    private fun breakdownTier(elapsedMs: Long, severity: Severity): Int {
        val mult = when (severity) {
            Severity.CRITICAL -> 0.5
            Severity.MAJOR -> 1.0
            Severity.MINOR -> 2.0
        }
        val minutes = elapsedMs.coerceAtLeast(0L) / 60_000.0
        val t1 = Constants.BD_TIER_1_MINUTES * mult
        val t2 = Constants.BD_TIER_2_MINUTES * mult
        val t3 = Constants.BD_TIER_3_MINUTES * mult
        return when {
            minutes >= t3 -> 3
            minutes >= t2 -> 2
            minutes >= t1 -> 1
            else -> 0
        }
    }

    private fun assetLabel(f: BreakdownFlag): String = f.assetName?.takeIf { it.isNotBlank() } ?: f.machineId

    // ── PM work-order handoffs (P1 stays in PmSchedulerWorker; not emitted here) ──

    private fun evaluatePm(user: User, w: PmWorkOrder, now: Long, out: MutableList<Reminder>) {
        when (w.status) {
            PmWoStatus.SUBMITTED -> {
                if (w.qcDecidedAt == null) {
                    // Fresh submission awaiting supervisor review.
                    val since = w.submittedAt ?: w.generatedAt
                    val tier = pmHandoffTier(now - since)
                    emit(out, P2_AUDIENCE, user.role, tier) {
                        pmReminder(ReminderType.P2_AWAITING_SUPERVISOR, w, user.role, tier,
                            "PM awaiting your review — ${w.templateName}",
                            "A technician submitted this PM for supervisor approval.")
                    }
                } else {
                    // QC bounced it back to the supervisor.
                    val since = w.qcDecidedAt!!
                    val tier = pmHandoffTier(now - since)
                    emit(out, P5_AUDIENCE, user.role, tier) {
                        pmReminder(ReminderType.P5_QC_REJECTED, w, user.role, tier,
                            "QC returned a PM — ${w.templateName}",
                            "QC sent this PM back for re-review.")
                    }
                }
            }
            PmWoStatus.IN_PROGRESS -> {
                val rejectedAt = w.supervisorRejectedAt ?: return // only rework, not normal in-progress
                val tier = pmHandoffTier(now - rejectedAt)
                emit(out, P3_AUDIENCE, user.role, tier) {
                    pmReminder(ReminderType.P3_SUPERVISOR_REJECTED, w, user.role, tier,
                        "PM sent back for rework — ${w.templateName}",
                        "Your supervisor rejected this PM. Rework and resubmit.")
                }
            }
            PmWoStatus.PENDING_QC -> {
                if (w.qcDecidedAt != null) return
                val since = w.supervisorApprovedAt ?: w.submittedAt ?: w.generatedAt
                val tier = pmHandoffTier(now - since)
                emit(out, P4_AUDIENCE, user.role, tier) {
                    pmReminder(ReminderType.P4_AWAITING_QC, w, user.role, tier,
                        "PM awaiting QC — ${w.templateName}",
                        "Supervisor approved; waiting for QC verification.")
                }
            }
            else -> Unit // DRAFT/NOTIFIED/ACKNOWLEDGED/SUPERVISOR_APPROVED/QC_APPROVED/CLOSED/OVERDUE/CANCELLED
        }
    }

    private fun pmHandoffTier(elapsedMs: Long): Int {
        val hours = elapsedMs.coerceAtLeast(0L) / 3_600_000.0
        return when {
            hours >= Constants.PM_HANDOFF_TIER_2_HOURS -> 2
            hours >= Constants.PM_HANDOFF_TIER_1_HOURS -> 1
            else -> 0
        }
    }

    private fun pmReminder(type: ReminderType, w: PmWorkOrder, role: UserRole, tier: Int, title: String, body: String) =
        Reminder(
            type = type, category = ReminderCategory.PM, entityId = w.id,
            recipientRole = role, tier = tier, title = title, body = body,
            deepLinkExtraKey = "pm_wo_id",
        )

    // ── shared helpers ──────────────────────────────────────────────────────

    private inline fun emit(
        out: MutableList<Reminder>,
        audience: Map<UserRole, Int>,
        role: UserRole,
        tier: Int,
        build: () -> Reminder,
    ) {
        val minTier = audience[role] ?: return
        if (tier >= minTier) out += build()
    }

    companion object {
        // Role -> minimum tier at which that role enters the audience.
        private val B1_AUDIENCE = mapOf(UserRole.TECHNICIAN to 0, UserRole.SUPERVISOR to 2, UserRole.HEAD to 3)
        private val B2_AUDIENCE = mapOf(UserRole.TECHNICIAN to 1, UserRole.SUPERVISOR to 2)
        private val B3_AUDIENCE = mapOf(UserRole.QC_MEMBER to 0, UserRole.QC_HEAD to 2, UserRole.HEAD to 3)
        private val B4_AUDIENCE = mapOf(UserRole.TECHNICIAN to 0, UserRole.SUPERVISOR to 2)
        private val P2_AUDIENCE = mapOf(UserRole.SUPERVISOR to 0, UserRole.HEAD to 2)
        private val P3_AUDIENCE = mapOf(UserRole.TECHNICIAN to 0, UserRole.SUPERVISOR to 2)
        private val P4_AUDIENCE = mapOf(UserRole.QC_MEMBER to 0, UserRole.QC_HEAD to 1, UserRole.HEAD to 2)
        private val P5_AUDIENCE = mapOf(UserRole.SUPERVISOR to 0, UserRole.HEAD to 2)
    }
}
