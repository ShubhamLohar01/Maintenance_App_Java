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
        // Plant scoping: own-plant roles (TECHNICIAN / QC / OPERATOR) only get reminders
        // for breakdowns in their OWN warehouse — an A-185 technician must not be nudged
        // about a W-202 machine. HEAD/SUPERVISOR span every plant (cross-plant oversight).
        // Mirrors the backend's scoped_buildings (_ALL_PLANT_ROLES = HEAD, SUPERVISOR).
        if (!breakdownVisibleToUser(user, f)) return
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
        val t4 = Constants.BD_TIER_4_MINUTES * mult
        return when {
            minutes >= t4 -> 4
            minutes >= t3 -> 3
            minutes >= t2 -> 2
            minutes >= t1 -> 1
            else -> 0
        }
    }

    private fun assetLabel(f: BreakdownFlag): String = f.assetName?.takeIf { it.isNotBlank() } ?: f.machineId

    /**
     * Whether this breakdown belongs to the user's plant. HEAD/SUPERVISOR see all
     * plants. For own-plant roles, compare the flag's warehouse to the user's. Unknown
     * plant or building fails OPEN (delivers) so a missing field never silences a real
     * alert — only a confirmed cross-plant mismatch is filtered out.
     */
    private fun breakdownVisibleToUser(user: User, f: BreakdownFlag): Boolean {
        if (user.role in ALL_PLANT_ROLES) return true
        val userBuilding = buildingCode(user.plantId) ?: return true
        val flagBuilding = buildingCode(f.building) ?: return true
        return userBuilding == flagBuilding
    }

    /**
     * Lenient plant → canonical building-code match, mirroring the backend's
     * building_for: the code ("A185"/"W202") OR just its digits ("185"/"202")
     * appearing in the normalized input is enough, so a descriptive plant string
     * ("A-185-Koparkhairne") still resolves. Returns null when nothing matches.
     */
    private fun buildingCode(raw: String?): String? {
        val n = raw?.filter { it.isLetterOrDigit() }?.uppercase().orEmpty()
        if (n.isBlank()) return null
        for (code in KNOWN_BUILDING_CODES) {
            val digits = code.filter { it.isDigit() }
            if (n.contains(code) || (digits.isNotEmpty() && n.contains(digits))) return code
        }
        return null
    }

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
        // Roles that see EVERY plant's breakdowns (cross-plant oversight). Everyone else
        // is scoped to their own warehouse. Matches the backend's _ALL_PLANT_ROLES.
        private val ALL_PLANT_ROLES = setOf(UserRole.HEAD, UserRole.SUPERVISOR)

        // Canonical warehouse codes (compact form). Mirrors the backend's ALL_BUILDINGS.
        private val KNOWN_BUILDING_CODES = listOf("A185", "W202")

        // Role -> minimum tier at which that role enters the audience.
        private val B1_AUDIENCE = mapOf(UserRole.TECHNICIAN to 0, UserRole.SUPERVISOR to 2, UserRole.HEAD to 4)
        private val B2_AUDIENCE = mapOf(UserRole.TECHNICIAN to 1, UserRole.SUPERVISOR to 2)
        private val B3_AUDIENCE = mapOf(UserRole.QC_MEMBER to 0, UserRole.QC_HEAD to 2, UserRole.HEAD to 3)
        private val B4_AUDIENCE = mapOf(UserRole.TECHNICIAN to 0, UserRole.SUPERVISOR to 2)
        // PM hand-offs start at tier 1, not 0: a PM that reached a queue minutes ago is
        // not worth a notification, one still sitting there after the 24h handoff
        // threshold is. Breakdown audiences below are deliberately untouched — those are
        // the alerts that tell the floor a machine is down.
        private val P2_AUDIENCE = mapOf(UserRole.SUPERVISOR to 1, UserRole.HEAD to 2)
        // A technician gets exactly one PM notification: the daily "pending today" notice
        // from PmSchedulerWorker. Rework lands back on them as an IN_PROGRESS work order,
        // which that notice and the Today tab already cover — so P3 would only double up.
        private val P3_AUDIENCE = mapOf(UserRole.SUPERVISOR to 2)
        private val P4_AUDIENCE = mapOf(UserRole.QC_MEMBER to 1, UserRole.QC_HEAD to 1, UserRole.HEAD to 2)
        private val P5_AUDIENCE = mapOf(UserRole.SUPERVISOR to 1, UserRole.HEAD to 2)
    }
}
