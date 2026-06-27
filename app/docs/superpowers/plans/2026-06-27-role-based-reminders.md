# Role-Based Reminders & Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver role-aware, escalating OS reminders for the Breakdown and PM work-order handoffs, computed on-device with no new persistence.

**Architecture:** A pure `ReminderEvaluator` encodes the role×event matrix; it takes the logged-in user, the current breakdown/PM lists, and `now`, and returns the reminders that user should see. A `ReminderDispatcher` fires/updates/cancels OS notifications via the existing `NotificationHelper`, using stable notification ids and the OS notification itself (active-notifications + a tier extra) as the dedupe/escalation state — so re-runs don't spam and tier increases re-alert. The two existing workers (`BreakdownPollWorker`, `PmSchedulerWorker`) call the evaluator each cycle. A `NotificationEnvelope` + `FcmService` parse path is defined so a future backend push reuses the same dispatcher.

**Tech Stack:** Kotlin, Jetpack Compose app, Hilt DI, WorkManager, AndroidX Notifications, JUnit 5 (Jupiter) for unit tests.

---

## Conventions

- **Run unit tests from the Gradle repo root** (`d:/Maintenance module/FactoryOps`, where `gradlew` lives). Module is `:app`.
  - Single class: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.reminder.ReminderEvaluatorTest"`
- Tests use JUnit 5: `import org.junit.jupiter.api.Test` and `org.junit.jupiter.api.Assertions.*`.
- All new production code lives under `src/main/java/com/candorfoods/factoryops/...`; tests under `src/test/java/com/candorfoods/factoryops/...`.
- Commit after each task with the message shown in its final step.

## File Structure

**Create:**
- `domain/model/Reminder.kt` — `Reminder`, `ReminderType`, `ReminderCategory`, `Reminder.notificationId()`.
- `domain/usecase/reminder/ReminderEvaluator.kt` — pure matrix logic.
- `notification/ReminderDispatcher.kt` — diff desired vs active notifications; fire/update/cancel.
- `notification/NotificationEnvelope.kt` — FCM payload contract + `toReminder()`.
- `src/test/java/com/candorfoods/factoryops/reminder/ReminderEvaluatorTest.kt`
- `src/test/java/com/candorfoods/factoryops/reminder/ReminderIdTest.kt`
- `src/test/java/com/candorfoods/factoryops/reminder/NotificationEnvelopeTest.kt`

**Modify:**
- `util/Constants.kt` — reminder threshold constants + `NOTIFICATIONS_SOURCE`.
- `notification/NotificationHelper.kt` — `showReminder(...)`, `cancel(id)`, extras keys.
- `sync/BreakdownPollWorker.kt` — widen role gating; run evaluator+dispatcher.
- `sync/PmSchedulerWorker.kt` — run evaluator+dispatcher for P2–P5.
- `notification/FcmService.kt` — parse envelope → dispatcher; token contract.

**Scope note — deferred:** Informational one-shots **B5** (machine released) and **P6** (PM closed) are intentionally NOT implemented. Without storage they cannot be delivered exactly-once and would re-fire every poll cycle. They are recorded as deferred in the spec's risk section. PM **P1** (overdue technician) stays in `PmSchedulerWorker`'s existing escalation; the evaluator does not emit it.

---

## Task 1: Reminder model + stable notification id

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/domain/model/Reminder.kt`
- Test: `src/test/java/com/candorfoods/factoryops/reminder/ReminderIdTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/candorfoods/factoryops/reminder/ReminderIdTest.kt`:

```kotlin
package com.candorfoods.factoryops.reminder

import com.candorfoods.factoryops.domain.model.Reminder
import com.candorfoods.factoryops.domain.model.ReminderCategory
import com.candorfoods.factoryops.domain.model.ReminderType
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.model.notificationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ReminderIdTest {

    private fun reminder(
        type: ReminderType = ReminderType.B1_NEW_BREAKDOWN,
        category: ReminderCategory = ReminderCategory.BREAKDOWN,
        entityId: String = "flag-1",
    ) = Reminder(
        type = type, category = category, entityId = entityId,
        recipientRole = UserRole.TECHNICIAN, tier = 0,
        title = "t", body = "b", deepLinkExtraKey = "ticket_id",
    )

    @Test fun `id is stable for same type and entity`() {
        assertEquals(reminder().notificationId(), reminder().notificationId())
    }

    @Test fun `different reminder types on same entity get different ids`() {
        assertNotEquals(
            reminder(type = ReminderType.B1_NEW_BREAKDOWN).notificationId(),
            reminder(type = ReminderType.B2_ACK_STALE).notificationId(),
        )
    }

    @Test fun `breakdown ids sit in the 3000 band, pm ids in the 4000 band`() {
        val bd = reminder(category = ReminderCategory.BREAKDOWN).notificationId()
        val pm = reminder(category = ReminderCategory.PM, type = ReminderType.P2_AWAITING_SUPERVISOR).notificationId()
        // Bands differ by 1000; same entity+type would otherwise hash-collide.
        assertNotEquals(bd, pm)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.reminder.ReminderIdTest"`
Expected: FAIL — unresolved references `Reminder`, `ReminderType`, `notificationId`.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/candorfoods/factoryops/domain/model/Reminder.kt`:

```kotlin
package com.candorfoods.factoryops.domain.model

import com.candorfoods.factoryops.util.Constants

/** Which workflow a reminder belongs to (selects the OS channel + id band). */
enum class ReminderCategory { BREAKDOWN, PM }

/** One row of the role×event matrix. */
enum class ReminderType {
    B1_NEW_BREAKDOWN,
    B2_ACK_STALE,
    B3_AWAITING_QC,
    B4_REOPENED,
    P2_AWAITING_SUPERVISOR,
    P3_SUPERVISOR_REJECTED,
    P4_AWAITING_QC,
    P5_QC_REJECTED,
}

/**
 * A reminder the current device's user should see right now. Pure value object —
 * built by ReminderEvaluator, rendered by ReminderDispatcher/NotificationHelper.
 */
data class Reminder(
    val type: ReminderType,
    val category: ReminderCategory,
    val entityId: String,
    val recipientRole: UserRole,
    val tier: Int,
    val title: String,
    val body: String,
    /** Intent extra key MainActivity reads to deep-link (e.g. "ticket_id", "pm_wo_id"). */
    val deepLinkExtraKey: String,
)

/**
 * Stable per-(type, entity) notification id. Re-firing the same id updates the
 * existing OS notification instead of stacking — this is the dedupe mechanism.
 * Type name is folded into the hash so different reminders about one entity don't
 * collide; the category base keeps breakdown/PM in separate bands.
 */
fun Reminder.notificationId(): Int {
    val base = when (category) {
        ReminderCategory.BREAKDOWN -> Constants.NOTIFICATION_ID_BREAKDOWN_BASE
        ReminderCategory.PM -> Constants.NOTIFICATION_ID_PM_ESCALATION_BASE
    }
    return base + (type.name + entityId).hashCode()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.reminder.ReminderIdTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/domain/model/Reminder.kt src/test/java/com/candorfoods/factoryops/reminder/ReminderIdTest.kt
git commit -m "feat: add Reminder model + stable notification id"
```

---

## Task 2: Reminder thresholds in Constants

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/util/Constants.kt`

No test (constants only); they are exercised by Task 3/4 tests.

- [ ] **Step 1: Add the constants**

Append inside the `Constants` object in `src/main/java/com/candorfoods/factoryops/util/Constants.kt`, just before the closing brace (after line 62 `NOTIFICATION_ID_PM_ESCALATION_BASE`):

```kotlin

    // ─── Role-based reminders (2026-06-27) ────────────────────────────────────
    /**
     * Breakdown escalation thresholds in MINUTES for a MAJOR flag. Tier rises as the
     * pending state ages. Severity scales these (see ReminderEvaluator):
     * CRITICAL = ×0.5 (faster), MINOR = ×2.0 (slower).
     */
    const val BD_TIER_1_MINUTES = 30L
    const val BD_TIER_2_MINUTES = 60L   // also: escalate to Supervisor
    const val BD_TIER_3_MINUTES = 120L  // also: escalate to Head

    /** PM handoff (review/QC) escalation thresholds in HOURS. */
    const val PM_HANDOFF_TIER_1_HOURS = 24L  // escalate to next role
    const val PM_HANDOFF_TIER_2_HOURS = 48L  // escalate to Head

    /**
     * Source of truth for reminder delivery. LOCAL_POLL = the on-device evaluator
     * computes + fires reminders (current). SERVER_PUSH = the backend fans out FCM
     * and the local evaluator stands down (future; see FcmService).
     */
    const val NOTIFICATIONS_SOURCE_LOCAL_POLL = "LOCAL_POLL"
    const val NOTIFICATIONS_SOURCE_SERVER_PUSH = "SERVER_PUSH"
    const val NOTIFICATIONS_SOURCE = NOTIFICATIONS_SOURCE_LOCAL_POLL
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/util/Constants.kt
git commit -m "feat: add reminder threshold + delivery-source constants"
```

---

## Task 3: ReminderEvaluator — breakdown rules (B1–B4)

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/domain/usecase/reminder/ReminderEvaluator.kt`
- Test: `src/test/java/com/candorfoods/factoryops/reminder/ReminderEvaluatorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/candorfoods/factoryops/reminder/ReminderEvaluatorTest.kt`:

```kotlin
package com.candorfoods.factoryops.reminder

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.QcStatus
import com.candorfoods.factoryops.domain.model.ReminderType
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.SyncStatus
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.usecase.reminder.ReminderEvaluator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReminderEvaluatorTest {

    private val eval = ReminderEvaluator()
    private val MIN = 60_000L

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
        // 45 min overdue = tier 1 (>=30 min). Supervisor needs tier 2 (>=60 min).
        assertTrue(types(UserRole.SUPERVISOR, flag(raisedAt = 0L), 45 * MIN).isEmpty())
        // 70 min overdue = tier 2 -> supervisor now in audience.
        assertEquals(listOf(ReminderType.B1_NEW_BREAKDOWN), types(UserRole.SUPERVISOR, flag(raisedAt = 0L), 70 * MIN))
    }

    @Test fun `OPEN flag reaches head only at tier 3`() {
        assertTrue(types(UserRole.HEAD, flag(raisedAt = 0L), 90 * MIN).isEmpty())   // tier 2
        assertEquals(listOf(ReminderType.B1_NEW_BREAKDOWN), types(UserRole.HEAD, flag(raisedAt = 0L), 130 * MIN)) // tier 3
    }

    @Test fun `CRITICAL severity halves thresholds`() {
        // 35 min on a CRITICAL flag = tier 2 (>=30 min scaled by 0.5 => >=15/30 min).
        val out = eval(user(UserRole.SUPERVISOR), listOf(flag(severity = Severity.CRITICAL, raisedAt = 0L)), emptyList(), 35 * MIN)
        assertEquals(listOf(ReminderType.B1_NEW_BREAKDOWN), out.map { it.type })
    }

    @Test fun `ACKNOWLEDGED flag only nudges once stale`() {
        val fresh = flag(status = BreakdownStatus.ACKNOWLEDGED, acknowledgedAt = 0L)
        assertTrue(types(UserRole.TECHNICIAN, fresh, 10 * MIN).isEmpty())          // tier 0, not stale
        assertEquals(listOf(ReminderType.B2_ACK_STALE), types(UserRole.TECHNICIAN, fresh, 40 * MIN)) // tier 1
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.reminder.ReminderEvaluatorTest"`
Expected: FAIL — unresolved reference `ReminderEvaluator`.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/candorfoods/factoryops/domain/usecase/reminder/ReminderEvaluator.kt`:

```kotlin
package com.candorfoods.factoryops.domain.usecase.reminder

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownStatus
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
        // PM rules added in a later task.
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
                if (f.qcDecidedAt != null) return // already decided
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
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.reminder.ReminderEvaluatorTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/domain/usecase/reminder/ReminderEvaluator.kt src/test/java/com/candorfoods/factoryops/reminder/ReminderEvaluatorTest.kt
git commit -m "feat: ReminderEvaluator breakdown rules (B1-B4) with escalation"
```

---

## Task 4: ReminderEvaluator — PM handoff rules (P2–P5)

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/domain/usecase/reminder/ReminderEvaluator.kt`
- Modify: `src/test/java/com/candorfoods/factoryops/reminder/ReminderEvaluatorTest.kt`

- [ ] **Step 1: Add the failing tests**

Append these methods inside `ReminderEvaluatorTest` (before the closing brace). Add this import near the top of the test file:

```kotlin
import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.TradeType
```

Methods:

```kotlin
    private val HOUR = 3_600_000L

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
        assertTrue(pmTypes(UserRole.HEAD, w, 10 * HOUR).isEmpty())       // tier 0
        assertEquals(listOf(ReminderType.P2_AWAITING_SUPERVISOR), pmTypes(UserRole.HEAD, w, 49 * HOUR)) // tier 2
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
        assertTrue(pmTypes(UserRole.QC_HEAD, w, 10 * HOUR).isEmpty())     // tier 0
        assertEquals(listOf(ReminderType.P4_AWAITING_QC), pmTypes(UserRole.QC_HEAD, w, 25 * HOUR)) // tier 1
    }

    @Test fun `qc-rejected PM (submitted with decision) nudges supervisor as P5`() {
        val w = wo(status = PmWoStatus.SUBMITTED, submittedAt = 0L, qcDecidedAt = 0L)
        assertEquals(listOf(ReminderType.P5_QC_REJECTED), pmTypes(UserRole.SUPERVISOR, w, 1 * HOUR))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.reminder.ReminderEvaluatorTest"`
Expected: FAIL — PM cases return empty (PM logic not implemented yet).

- [ ] **Step 3: Implement the PM rules**

In `ReminderEvaluator.kt`, replace the `invoke` body line `// PM rules added in a later task.` with a call, and add the PM logic + audience maps. Specifically:

Change `invoke` to:

```kotlin
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
```

Add these imports at the top of the file:

```kotlin
import com.candorfoods.factoryops.domain.model.PmWoStatus
```

Add this function after `evaluateBreakdown(...)`:

```kotlin
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
```

Add these audience maps inside the `companion object` (after the breakdown maps):

```kotlin
        private val P2_AUDIENCE = mapOf(UserRole.SUPERVISOR to 0, UserRole.HEAD to 2)
        private val P3_AUDIENCE = mapOf(UserRole.TECHNICIAN to 0, UserRole.SUPERVISOR to 2)
        private val P4_AUDIENCE = mapOf(UserRole.QC_MEMBER to 0, UserRole.QC_HEAD to 1, UserRole.HEAD to 2)
        private val P5_AUDIENCE = mapOf(UserRole.SUPERVISOR to 0, UserRole.HEAD to 2)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.reminder.ReminderEvaluatorTest"`
Expected: PASS (13 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/domain/usecase/reminder/ReminderEvaluator.kt src/test/java/com/candorfoods/factoryops/reminder/ReminderEvaluatorTest.kt
git commit -m "feat: ReminderEvaluator PM handoff rules (P2-P5)"
```

---

## Task 5: NotificationHelper.showReminder + cancel

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/notification/NotificationHelper.kt`

No unit test (Android `NotificationManager` dependency); verified by compile + manual run in Task 9.

- [ ] **Step 1: Add imports**

At the top of `NotificationHelper.kt`, add to the existing imports:

```kotlin
import android.os.Bundle
import com.candorfoods.factoryops.domain.model.Reminder
import com.candorfoods.factoryops.domain.model.ReminderCategory
import com.candorfoods.factoryops.domain.model.notificationId
```

- [ ] **Step 2: Add the methods + extras keys**

Add inside the `NotificationHelper` class, before the final closing brace:

```kotlin
    /**
     * Fire or update a reminder notification. The stable id means re-calling with the
     * same reminder updates the existing notification rather than stacking. `alert`
     * controls whether the user is re-buzzed: pass true on first appearance or when
     * the tier increased, false for a silent refresh at the same tier. The current
     * tier and an is-reminder marker are stored in extras so ReminderDispatcher can
     * read prior tier and identify which active notifications it owns.
     */
    fun showReminder(reminder: Reminder, alert: Boolean) {
        val channel = when (reminder.category) {
            ReminderCategory.BREAKDOWN -> Constants.NOTIFICATION_CHANNEL_BREAKDOWN
            ReminderCategory.PM -> Constants.NOTIFICATION_CHANNEL_PM_ESCALATION
        }
        val id = reminder.notificationId()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(reminder.deepLinkExtraKey, reminder.entityId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val extras = Bundle().apply {
            putInt(EXTRA_TIER, reminder.tier)
            putBoolean(EXTRA_IS_REMINDER, true)
        }
        val priority = if (reminder.tier >= 1) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(reminder.title)
            .setContentText(reminder.body.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body))
            .setPriority(priority)
            .setOnlyAlertOnce(!alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addExtras(extras)
        if (alert) {
            builder.setDefaults(
                if (reminder.tier >= 2) NotificationCompat.DEFAULT_ALL else NotificationCompat.DEFAULT_SOUND
            )
        }
        notificationManager.notify(id, builder.build())
    }

    fun cancel(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    companion object {
        const val EXTRA_TIER = "reminder_tier"
        const val EXTRA_IS_REMINDER = "is_reminder"
    }
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/notification/NotificationHelper.kt
git commit -m "feat: NotificationHelper.showReminder + cancel with tier extras"
```

---

## Task 6: ReminderDispatcher

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/notification/ReminderDispatcher.kt`

No unit test (queries live `NotificationManager.activeNotifications`); verified by compile + manual run.

- [ ] **Step 1: Create the dispatcher**

Create `src/main/java/com/candorfoods/factoryops/notification/ReminderDispatcher.kt`:

```kotlin
package com.candorfoods.factoryops.notification

import android.app.NotificationManager
import android.content.Context
import com.candorfoods.factoryops.domain.model.Reminder
import com.candorfoods.factoryops.domain.model.notificationId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconciles the desired reminder set against the OS notification shade — which IS
 * our dedupe/escalation state (no DB). For each desired reminder it fires/updates,
 * alerting only on first appearance or a tier increase. Reminders that are no longer
 * desired (the underlying action was completed) get cancelled, so stale "please act"
 * alerts auto-clear.
 *
 * Only notifications we own (marked via NotificationHelper.EXTRA_IS_REMINDER) are
 * cancelled — missed-stop and the existing PM-overdue (P1) notifications are left alone.
 */
@Singleton
class ReminderDispatcher @Inject constructor(
    @ApplicationContext context: Context,
    private val notificationHelper: NotificationHelper,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun dispatch(reminders: List<Reminder>) {
        val desired: Map<Int, Reminder> = reminders.associateBy { it.notificationId() }
        val active = notificationManager.activeNotifications

        // 1) Cancel our reminders that are no longer desired (action completed).
        for (sbn in active) {
            val isOurs = sbn.notification.extras.getBoolean(NotificationHelper.EXTRA_IS_REMINDER, false)
            if (isOurs && !desired.containsKey(sbn.id)) {
                notificationHelper.cancel(sbn.id)
            }
        }

        // 2) Fire/update desired reminders; alert only when new or escalated.
        for ((id, reminder) in desired) {
            val priorTier = active.firstOrNull { it.id == id }
                ?.notification?.extras?.getInt(NotificationHelper.EXTRA_TIER, -1)
                ?: -1
            val alert = priorTier < 0 || reminder.tier > priorTier
            notificationHelper.showReminder(reminder, alert)
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/notification/ReminderDispatcher.kt
git commit -m "feat: ReminderDispatcher reconciles reminders against OS shade"
```

---

## Task 7: Wire BreakdownPollWorker (all roles + evaluator)

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/sync/BreakdownPollWorker.kt`

No unit test (worker/Android); verified by compile + manual run.

- [ ] **Step 1: Replace the worker body**

Replace the entire contents of `src/main/java/com/candorfoods/factoryops/sync/BreakdownPollWorker.kt` with:

```kotlin
package com.candorfoods.factoryops.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.candorfoods.factoryops.domain.repository.AuthRepository
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.usecase.reminder.ReminderEvaluator
import com.candorfoods.factoryops.notification.ReminderDispatcher
import com.candorfoods.factoryops.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

/**
 * Breakdown reminder poll. Runs every BREAKDOWN_POLL_INTERVAL_MINUTES for ANY logged-in
 * user (not just technicians) so escalation can reach supervisor/QC/head on their own
 * device. Refreshes open flags from the backend, then runs the ReminderEvaluator for the
 * current user's role and dispatches the result.
 */
@HiltWorker
class BreakdownPollWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val authRepository: AuthRepository,
    private val breakdownRepository: BreakdownRepository,
    private val reminderEvaluator: ReminderEvaluator,
    private val reminderDispatcher: ReminderDispatcher,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            // Server push, when enabled, owns reminder delivery — stand down locally.
            if (Constants.NOTIFICATIONS_SOURCE != Constants.NOTIFICATIONS_SOURCE_LOCAL_POLL) {
                return Result.success()
            }
            val user = authRepository.getCurrentUser().firstOrNull()
            if (user == null) {
                return Result.success()
            }

            // Pull latest open breakdowns into Room (best-effort; offline-safe).
            runCatching { breakdownRepository.refreshOpenFlags("both") }

            val flags = breakdownRepository.observeAllFlags().first()
            val reminders = reminderEvaluator(
                user = user,
                flags = flags,
                workOrders = emptyList(),
                now = System.currentTimeMillis(),
            )
            reminderDispatcher.dispatch(reminders)
        } catch (_: Exception) {
            // Swallow — never let a polling tick crash the chain.
        } finally {
            reschedule(applicationContext)
        }
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<BreakdownPollWorker>()
                .setInitialDelay(Constants.BREAKDOWN_POLL_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .addTag(Constants.WORKER_TAG_BREAKDOWN_POLL)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                Constants.WORKER_TAG_BREAKDOWN_POLL,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueueImmediately(context: Context) {
            val request = OneTimeWorkRequestBuilder<BreakdownPollWorker>()
                .addTag(Constants.WORKER_TAG_BREAKDOWN_POLL)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                Constants.WORKER_TAG_BREAKDOWN_POLL,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private fun reschedule(context: Context) = enqueue(context)
    }
}
```

> Note: this replaces the old "new flags since last-seen → showBreakdownNotification" path with the evaluator (B1 covers new-flag-needs-technician). `AppDatabase`, `AuthPreferences`, and `NotificationHelper` are no longer needed by this worker; their imports are dropped above.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/sync/BreakdownPollWorker.kt
git commit -m "feat: BreakdownPollWorker runs reminder engine for all roles"
```

---

## Task 8: Wire PmSchedulerWorker (P2–P5)

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/sync/PmSchedulerWorker.kt`

No unit test; verified by compile + manual run.

- [ ] **Step 1: Add constructor dependencies**

In `PmSchedulerWorker.kt`, add these constructor params after `notificationHelper` (line 48), keeping the trailing structure valid:

```kotlin
    private val authRepository: com.candorfoods.factoryops.domain.repository.AuthRepository,
    private val reminderEvaluator: com.candorfoods.factoryops.domain.usecase.reminder.ReminderEvaluator,
    private val reminderDispatcher: com.candorfoods.factoryops.notification.ReminderDispatcher,
```

So the constructor reads:

```kotlin
    private val computeTier: com.candorfoods.factoryops.domain.usecase.pm.ComputePmEscalationTierUseCase,
    private val notificationHelper: com.candorfoods.factoryops.notification.NotificationHelper,
    private val authRepository: com.candorfoods.factoryops.domain.repository.AuthRepository,
    private val reminderEvaluator: com.candorfoods.factoryops.domain.usecase.reminder.ReminderEvaluator,
    private val reminderDispatcher: com.candorfoods.factoryops.notification.ReminderDispatcher,
) : CoroutineWorker(context, workerParams) {
```

- [ ] **Step 2: Add the evaluator pass at the end of doWork**

In `PmSchedulerWorker.doWork()`, replace the final `return Result.success()` (line 169) with:

```kotlin
        // 4. Role-based PM handoff reminders (P2-P5). P1 overdue escalation is handled
        //    by step 3 above; the evaluator deliberately does not emit P1.
        if (Constants.NOTIFICATIONS_SOURCE == Constants.NOTIFICATIONS_SOURCE_LOCAL_POLL) {
            val user = kotlinx.coroutines.flow.firstOrNull(authRepository.getCurrentUser())
            if (user != null) {
                val reminders = reminderEvaluator(
                    user = user,
                    flags = emptyList(),
                    workOrders = openWos,
                    now = System.currentTimeMillis(),
                )
                reminderDispatcher.dispatch(reminders)
            }
        }

        return Result.success()
```

> `openWos` is the `woRepo.getAllNonClosed()` list already fetched at step 3 (line 141). Add `import kotlinx.coroutines.flow.firstOrNull` is NOT required because it is referenced fully-qualified above.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/sync/PmSchedulerWorker.kt
git commit -m "feat: PmSchedulerWorker dispatches PM handoff reminders (P2-P5)"
```

---

## Task 9: FCM forward contract (NotificationEnvelope + FcmService)

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/notification/NotificationEnvelope.kt`
- Modify: `src/main/java/com/candorfoods/factoryops/notification/FcmService.kt`
- Test: `src/test/java/com/candorfoods/factoryops/reminder/NotificationEnvelopeTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/candorfoods/factoryops/reminder/NotificationEnvelopeTest.kt`:

```kotlin
package com.candorfoods.factoryops.reminder

import com.candorfoods.factoryops.domain.model.ReminderCategory
import com.candorfoods.factoryops.domain.model.ReminderType
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.notification.NotificationEnvelope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NotificationEnvelopeTest {

    @Test fun `parse builds a Reminder from a complete data map`() {
        val data = mapOf(
            "type" to "B1_NEW_BREAKDOWN",
            "category" to "BREAKDOWN",
            "entityId" to "flag-9",
            "targetRole" to "TECHNICIAN",
            "tier" to "2",
            "title" to "Breakdown",
            "body" to "Needs a tech",
            "deepLinkExtraKey" to "ticket_id",
        )
        val r = NotificationEnvelope.parse(data)!!
        assertEquals(ReminderType.B1_NEW_BREAKDOWN, r.type)
        assertEquals(ReminderCategory.BREAKDOWN, r.category)
        assertEquals("flag-9", r.entityId)
        assertEquals(UserRole.TECHNICIAN, r.recipientRole)
        assertEquals(2, r.tier)
        assertEquals("ticket_id", r.deepLinkExtraKey)
    }

    @Test fun `parse returns null on missing or bad fields`() {
        assertNull(NotificationEnvelope.parse(emptyMap()))
        assertNull(NotificationEnvelope.parse(mapOf("type" to "NOT_A_TYPE", "category" to "PM", "entityId" to "x")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.reminder.NotificationEnvelopeTest"`
Expected: FAIL — unresolved reference `NotificationEnvelope`.

- [ ] **Step 3: Create NotificationEnvelope**

Create `src/main/java/com/candorfoods/factoryops/notification/NotificationEnvelope.kt`:

```kotlin
package com.candorfoods.factoryops.notification

import com.candorfoods.factoryops.domain.model.Reminder
import com.candorfoods.factoryops.domain.model.ReminderCategory
import com.candorfoods.factoryops.domain.model.ReminderType
import com.candorfoods.factoryops.domain.model.UserRole

/**
 * FCM data-message contract for the future server-push path. The backend sends these
 * fields in the message `data` map; the client maps them to the same [Reminder] the
 * local evaluator produces, so push and poll converge on one dispatcher.
 *
 * Required keys: type, category, entityId, title, body, deepLinkExtraKey.
 * Optional: targetRole, targetUserId, tier (default 0).
 */
object NotificationEnvelope {

    fun parse(data: Map<String, String>): Reminder? {
        val type = data["type"]?.let { runCatching { ReminderType.valueOf(it) }.getOrNull() } ?: return null
        val category = data["category"]?.let { runCatching { ReminderCategory.valueOf(it) }.getOrNull() } ?: return null
        val entityId = data["entityId"]?.takeIf { it.isNotBlank() } ?: return null
        val role = data["targetRole"]?.let { runCatching { UserRole.valueOf(it) }.getOrNull() } ?: UserRole.TECHNICIAN
        val tier = data["tier"]?.toIntOrNull() ?: 0
        val title = data["title"] ?: return null
        val body = data["body"] ?: return null
        val deepLinkExtraKey = data["deepLinkExtraKey"]
            ?: if (category == ReminderCategory.BREAKDOWN) "ticket_id" else "pm_wo_id"
        return Reminder(
            type = type, category = category, entityId = entityId,
            recipientRole = role, tier = tier, title = title, body = body,
            deepLinkExtraKey = deepLinkExtraKey,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.reminder.NotificationEnvelopeTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Implement FcmService**

Replace the contents of `src/main/java/com/candorfoods/factoryops/notification/FcmService.kt` with:

```kotlin
package com.candorfoods.factoryops.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives server-pushed reminders. Parses the data payload via [NotificationEnvelope]
 * and routes it through the SAME [ReminderDispatcher] as the local poll engine, so the
 * delivery/dedup behaviour is identical whether a reminder came from polling or push.
 */
@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject lateinit var reminderDispatcher: ReminderDispatcher

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val reminder = NotificationEnvelope.parse(message.data) ?: return
        reminderDispatcher.dispatch(listOf(reminder))
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Contract for Phase 3 backend: POST /devices/token { userId, token, platform:"android" }.
        // No-op while NOTIFICATIONS_SOURCE = LOCAL_POLL and the endpoint does not exist yet.
    }
}
```

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/notification/NotificationEnvelope.kt src/main/java/com/candorfoods/factoryops/notification/FcmService.kt src/test/java/com/candorfoods/factoryops/reminder/NotificationEnvelopeTest.kt
git commit -m "feat: define FCM NotificationEnvelope contract + route push through dispatcher"
```

---

## Task 10: Full test run, manual verification, final commit

**Files:** none (verification only)

- [ ] **Step 1: Run the whole unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `ReminderEvaluatorTest` (13), `ReminderIdTest` (3), `NotificationEnvelopeTest` (2) and all pre-existing tests pass.

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual verification (record results in the commit body)**

On an emulator/device, sign in and confirm:
1. **Technician** — raise a breakdown from an operator login on a second device/account; within ~2 min the technician device shows a `B1` notification; leaving it unacknowledged ~30 min escalates priority; ~60 min a **Supervisor** login receives it.
2. **QC** — resolve a breakdown as technician; a **QC** login receives `B3`; deciding it clears the QC notification on the next poll (auto-cancel).
3. **PM** — submit a PM as technician; a **Supervisor** login receives `P2`; supervisor-reject → **Technician** receives `P3`; approve → **QC** receives `P4`.
4. **No spam** — leaving a reminder pending across several poll cycles updates the same notification without re-buzzing until a tier increase.

- [ ] **Step 4: Final commit**

> Stage ONLY reminder-feature files — never `git add -A` (the working tree contains unrelated warehouse-filter WIP that must stay uncommitted).

```bash
git add src/main/java/com/candorfoods/factoryops/domain/model/Reminder.kt \
        src/main/java/com/candorfoods/factoryops/domain/usecase/reminder/ReminderEvaluator.kt \
        src/main/java/com/candorfoods/factoryops/notification/ReminderDispatcher.kt \
        src/main/java/com/candorfoods/factoryops/notification/NotificationEnvelope.kt \
        src/main/java/com/candorfoods/factoryops/notification/NotificationHelper.kt \
        src/main/java/com/candorfoods/factoryops/notification/FcmService.kt \
        src/main/java/com/candorfoods/factoryops/sync/BreakdownPollWorker.kt \
        src/main/java/com/candorfoods/factoryops/sync/PmSchedulerWorker.kt \
        src/main/java/com/candorfoods/factoryops/util/Constants.kt \
        src/test/java/com/candorfoods/factoryops/reminder/
git commit -m "test: verify role-based reminders end-to-end (manual notes in body)" || echo "nothing to commit"
```

---

## Self-Review (completed during planning)

- **Spec coverage:** B1–B4 → Tasks 3; P2–P5 → Task 4; escalation/thresholds → Tasks 2–4; delivery/OS → Tasks 5–6; worker wiring/role-widening → Tasks 7–8; FCM contract → Task 9; testing → Tasks 1,3,4,9,10. B5/P6 (best-effort one-shots) and PM P1 (existing) are explicitly deferred/retained with rationale in the scope note — consistent with the spec's risk section.
- **Placeholder scan:** none — all code blocks are complete; thresholds are concrete.
- **Type consistency:** `Reminder`, `ReminderType`, `ReminderCategory`, `notificationId()`, `showReminder(reminder, alert)`, `cancel(id)`, `ReminderDispatcher.dispatch(list)`, evaluator signature `invoke(user, flags, workOrders, now)` are used identically across all tasks. Field names (`operatorId`, `qcDecidedAt`, `supervisorRejectedAt`, `supervisorApprovedAt`, `submittedAt`) match the verified domain models.
