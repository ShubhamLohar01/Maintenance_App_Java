# Phase 2C-4 — PM Escalation Cascade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Tasks 1–4 are independent and dispatched in parallel; Task 5 depends on 1+2.

**Goal:** Tier-driven escalation that surfaces overdue PMs through chip-color changes, local notifications to tech and (from T2) supervisor, and a "neglect counter" that resets when the user re-engages.

**Architecture:** Tier compute is a pure function (`ComputePmEscalationTierUseCase`) called by `PmSchedulerWorker` on its existing 60-minute schedule. The worker walks all non-closed WOs, computes the new tier from `scheduledDate` vs `now`, and if the tier changed, calls `pmRepo.bumpTier(woId, newTier)` then `notificationHelper.showPmEscalationNotification(...)`. UX urgency is driven by `wo.escalationTier` only; status stays semantically clean (NOTIFIED/ACKNOWLEDGED/IN_PROGRESS) — `PmWoStatus.OVERDUE` stays unused (Q7C). When a tech acts (acknowledge/start/submit), the existing repo action also resets the tier to 0.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Hilt, Room, WorkManager, AndroidX Notification, AndroidX activity-result for permission.

**Decisions locked:**
- **Q1A** — thresholds (days overdue): T1 = 1, T2 = 4, T3 = 7, T4 = 14.
- **Q2A** — reuse existing 60-minute worker.
- **Q3A** — cascade: T1 = tech only; T2 = tech + supervisor; T3 = tech + supervisor (louder); T4 = tech + supervisor (alert + vibration).
- **Q4B** — JIT permission: request `POST_NOTIFICATIONS` when first overdue PM appears.
- **Q5A** — tier resets on acknowledge / start / submit.
- **Q6A** — tier-driven chip color: T0 normal, T1 amber chip, T2 amber chip + pulse, T3 red chip, T4 red full-card-bg.
- **Q7C** — don't flip status; tier alone drives UX urgency.
- **Q8A** — tech tab only; no new supervisor or operator screens.
- **Q9A** — fire-and-forget notifications; no audit log this phase.

**Out of scope:** any backend persistence of tier transitions, supervisor-tab badges, machine-status changes, multi-plant cascading, plant-manager role.

---

## Files

```
NEW:
  domain/usecase/pm/ComputePmEscalationTierUseCase.kt  — pure tier-compute function
MODIFY:
  util/Constants.kt                                     — tier threshold constants
  domain/repository/PmWorkOrderRepository.kt            — add bumpTier(woId, newTier)
  data/repository/PmWorkOrderRepositoryImpl.kt          — implement bumpTier; reset tier in acknowledge/start/submit
  data/local/dao/PmWorkOrderDao.kt                      — add markTierUpdated and tier-clearing on existing marks
  notification/NotificationHelper.kt                    — PM_ESCALATION channel + showPmEscalationNotification
  sync/PmSchedulerWorker.kt                             — invoke tier compute + bumpTier + notify per cycle
  presentation/technician/PmTaskCard.kt                 — tier-driven chip + T2 pulse + T4 red bg
  presentation/technician/TechnicianHomeScreen.kt       — JIT request POST_NOTIFICATIONS on first overdue PM
  PROGRESS.md                                           — log Phase 2C-4 shipped
```

The `MachineRepository` is **not** touched. The `PmWorkOrder` entity / domain model is **not** changed.

---

## Task 1 — Data layer + tier compute use case

**Touches:** Constants.kt, ComputePmEscalationTierUseCase.kt (new), PmWorkOrderRepository.kt, PmWorkOrderRepositoryImpl.kt, PmWorkOrderDao.kt.

### Step 1.1 — Add tier threshold constants to `Constants.kt`

Append to `app/src/main/java/com/candorfoods/factoryops/util/Constants.kt` inside the `object Constants` block (any sensible position):

```kotlin
    // ─── Phase 2C-4 — PM escalation tiers ────────────────────────────────────
    /**
     * Days-overdue thresholds. A PM with `now - scheduledDate >= threshold` (in days)
     * gets the corresponding tier. Below all thresholds = tier 0 (on time).
     */
    const val PM_TIER_1_DAYS = 1   // 1 day overdue → amber chip, tech notified
    const val PM_TIER_2_DAYS = 4   // 4 days overdue → amber + pulse, tech + supervisor
    const val PM_TIER_3_DAYS = 7   // 7 days overdue → red chip, tech + supervisor (louder)
    const val PM_TIER_4_DAYS = 14  // 14 days overdue → red bg, tech + supervisor (alert + vibration)

    const val NOTIFICATION_CHANNEL_PM_ESCALATION = "pm_escalation"
    const val NOTIFICATION_ID_PM_ESCALATION_BASE = 4000
```

### Step 1.2 — Create the pure tier-compute use case

Create `app/src/main/java/com/candorfoods/factoryops/domain/usecase/pm/ComputePmEscalationTierUseCase.kt`:

```kotlin
package com.candorfoods.factoryops.domain.usecase.pm

import com.candorfoods.factoryops.util.Constants
import javax.inject.Inject

/**
 * Pure function: given the WO's scheduled date and the current time, return the
 * escalation tier (0..4) the WO should have. Tier 0 means on-time (or early).
 */
class ComputePmEscalationTierUseCase @Inject constructor() {

    operator fun invoke(scheduledDate: Long, now: Long): Int {
        val daysOverdue = ((now - scheduledDate).coerceAtLeast(0L) / DAY_MS)
        return when {
            daysOverdue >= Constants.PM_TIER_4_DAYS -> 4
            daysOverdue >= Constants.PM_TIER_3_DAYS -> 3
            daysOverdue >= Constants.PM_TIER_2_DAYS -> 2
            daysOverdue >= Constants.PM_TIER_1_DAYS -> 1
            else                                     -> 0
        }
    }

    companion object {
        private const val DAY_MS = 86_400_000L
    }
}
```

### Step 1.3 — Add `bumpTier` to repo interface

In `app/src/main/java/com/candorfoods/factoryops/domain/repository/PmWorkOrderRepository.kt`, append after existing methods (anywhere before the closing brace):

```kotlin
    /** Phase 2C-4 — bump escalation tier (called by scheduler when tier changes). */
    suspend fun bumpTier(woId: String, newTier: Int): Result<Unit>
```

### Step 1.4 — Implement `bumpTier` and add tier reset

In `app/src/main/java/com/candorfoods/factoryops/data/repository/PmWorkOrderRepositoryImpl.kt`:

**Edit A — Append the new bumpTier impl** before the `mutate { }` helper:

```kotlin
    // ─── Phase 2C-4 — escalation tier ─────────────────────────────────────────

    override suspend fun bumpTier(woId: String, newTier: Int): Result<Unit> = mutate {
        woDao.updateEscalationTier(woId, newTier)
        // Q9A: fire-and-forget. No sync queue entry — tier is a UI urgency signal,
        // not an audit-worthy state change. Phase 3 backend will compute server-side.
    }
```

**Edit B — Modify `acknowledge`, `start`, and `submit` to also reset tier to 0** (Q5A — reset on positive forward action):

Replace the body of each (keeping the rest of the function shape):

```kotlin
    override suspend fun acknowledge(woId: String): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        woDao.markAcknowledged(woId, now)
        woDao.updateEscalationTier(woId, 0)
        enqueueSync(ActionType.PM_ACKNOWLEDGE, """{"wo_id":"$woId"}""")
    }

    override suspend fun start(woId: String): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        woDao.markStarted(woId, now)
        woDao.updateEscalationTier(woId, 0)
        enqueueSync(ActionType.PM_START, """{"wo_id":"$woId"}""")
    }

    override suspend fun submit(woId: String, finalNotes: String?): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        woDao.markSubmitted(woId, now, finalNotes)
        woDao.updateEscalationTier(woId, 0)
        enqueueSync(ActionType.PM_SUBMIT, """{"wo_id":"$woId"}""")
    }
```

`updateEscalationTier(id, tier)` already exists on the DAO (added in 2C-1) — no DAO change needed for the reset path.

### Step 1.5 — DAO sanity check

`PmWorkOrderDao.kt` already has:

```kotlin
@Query("UPDATE pm_work_orders SET escalationTier = :tier WHERE id = :id")
suspend fun updateEscalationTier(id: String, tier: Int)
```

If it doesn't (verify before assuming), add it. Otherwise no change.

### Verification

```
cd "D:\Maintenance module\FactoryOps"
./gradlew.bat :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 2 — NotificationHelper PM channel + escalation notification

**Touches:** `notification/NotificationHelper.kt` only.

### Step 2.1 — Add PM channel in `createChannels()`

In `NotificationHelper.kt`, inside `createChannels()` (after the existing 3 channel registrations):

```kotlin
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_PM_ESCALATION,
                    "PM Escalation",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Escalation alerts for overdue preventive maintenance"
                    enableVibration(true)
                }
            )
```

### Step 2.2 — Add `showPmEscalationNotification`

Append to `NotificationHelper.kt` (before the closing brace of the class):

```kotlin
    /**
     * Phase 2C-4 — fires when a PM WO crosses an escalation tier.
     * Recipient is the same device's user; the cascade (Q3A) is decided by the
     * caller (worker) which only invokes this for the WO's tech and (from T2)
     * for the supervisor when the supervisor is logged in here.
     *
     * Tier drives priority + sound/vibration:
     *   T1 → DEFAULT priority, no sound
     *   T2 → DEFAULT priority, default sound
     *   T3 → HIGH priority, default sound
     *   T4 → HIGH priority, alert sound + vibration pattern
     */
    fun showPmEscalationNotification(
        woId: String,
        machineName: String,
        templateName: String,
        tier: Int,
        daysOverdue: Long
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("pm_wo_id", woId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, woId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (priority, defaultsFlag) = when (tier) {
            1    -> NotificationCompat.PRIORITY_DEFAULT to 0
            2    -> NotificationCompat.PRIORITY_DEFAULT to NotificationCompat.DEFAULT_SOUND
            3    -> NotificationCompat.PRIORITY_HIGH to NotificationCompat.DEFAULT_SOUND
            else -> NotificationCompat.PRIORITY_HIGH to NotificationCompat.DEFAULT_ALL
        }

        val title = when (tier) {
            1    -> "PM overdue — $machineName"
            2    -> "PM overdue ${daysOverdue}d — $machineName"
            3    -> "PM very overdue — $machineName"
            else -> "PM critical — $machineName"
        }

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_PM_ESCALATION)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("$templateName — ${daysOverdue}d overdue")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$templateName has been overdue for $daysOverdue days."))
            .setPriority(priority)
            .setDefaults(defaultsFlag)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(
            Constants.NOTIFICATION_ID_PM_ESCALATION_BASE + woId.hashCode(),
            notification
        )
    }
```

### Verification

`./gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL.

---

## Task 3 — PM card tier-driven chip + pulse + T4 background

**Touches:** `presentation/technician/PmTaskCard.kt` only.

### Step 3.1 — Replace the existing due-date chip with a tier-driven chip

Open `PmTaskCard.kt`. Find the existing chip that renders "due in Xd / overdue Xd / etc." It's typically a small `Box` or `Surface` with conditional color. Replace it with a tier-aware chip composable.

**Add this new private composable** at the bottom of the file:

```kotlin
@Composable
private fun TierChip(tier: Int, daysOverdue: Long) {
    if (tier == 0) {
        // Tier 0 = on time. No chip; the existing "due in Xd" chip elsewhere on the card stays.
        return
    }
    val (bg, fg, label) = when (tier) {
        1    -> Triple(
            FactoryTheme.colors.statusFlagged.copy(alpha = 0.15f),
            FactoryTheme.colors.statusFlagged,
            "Overdue ${daysOverdue}d"
        )
        2    -> Triple(
            FactoryTheme.colors.statusFlagged.copy(alpha = 0.20f),
            FactoryTheme.colors.statusFlagged,
            "Overdue ${daysOverdue}d"
        )
        3    -> Triple(
            FactoryTheme.colors.statusStopped.copy(alpha = 0.15f),
            FactoryTheme.colors.statusStopped,
            "Overdue ${daysOverdue}d"
        )
        else -> Triple(
            FactoryTheme.colors.statusStopped.copy(alpha = 0.20f),
            FactoryTheme.colors.statusStopped,
            "CRITICAL ${daysOverdue}d"
        )
    }

    // Pulse alpha for tier 2 only (Q6A: T1 amber, T2 amber+pulse).
    val infiniteTransition = rememberInfiniteTransition(label = "tierPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (tier == 2) 0.55f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tierPulseAlpha"
    )

    Box(
        modifier = Modifier
            .background(bg.copy(alpha = bg.alpha * pulseAlpha), RoundedCornerShape(4.dp))
            .border(1.dp, fg.copy(alpha = 0.4f * pulseAlpha), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
```

### Step 3.2 — Use the chip + apply T4 full-card-bg

Find where the card renders the existing relative-due chip (or the "due in Xd" text) and add `TierChip(wo.escalationTier, daysOverdue)` next to it. Compute `daysOverdue` from `wo.scheduledDate` (existing field):

```kotlin
val daysOverdue = ((System.currentTimeMillis() - wo.scheduledDate)
    .coerceAtLeast(0L) / 86_400_000L)
```

Apply the **T4 red full-card background** by passing a `tinted` parameter to `FactoryCard` if the card supports it; if not, wrap the entire card body in a `Box` with `MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)` background. Inspect `FactoryCard`'s API first — if it has `containerColor` / `tintColor`, use it. Otherwise use the wrapped-Box approach.

The simplest reliable approach: pre-compute a `tintColor: Color?` and pass to `FactoryCard` if it accepts `containerColor`, else apply via `Modifier.background(...)` on the outer card before its existing modifier chain.

**Note for the implementer:** read `app/src/main/java/com/candorfoods/factoryops/presentation/common/components/FactoryCard.kt` first to learn its surface and how it supports tinting. Use whatever tinting hook already exists.

### Imports needed
```kotlin
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
```

### Verification

`./gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL.

---

## Task 4 — JIT POST_NOTIFICATIONS permission

**Touches:** `presentation/technician/TechnicianHomeScreen.kt` only.

### Step 4.1 — Add a JIT permission request

The technician's home screen has an `LaunchedEffect` or similar where new state arrives. Add a `LaunchedEffect` keyed on the presence of any overdue PM:

```kotlin
val context = LocalContext.current
val hasOverduePm = uiState.pmTasks.any { it.escalationTier > 0 }
val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
) { /* granted or denied — silent degrade either way */ }

LaunchedEffect(hasOverduePm) {
    if (!hasOverduePm) return@LaunchedEffect
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
```

### Imports

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
```

(Some of these may already be imported.)

**Note for the implementer:** if `uiState.pmTasks` is named differently in this VM, use whatever field exposes the PM list. Read the screen first to find the exact field name.

### Verification

`./gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL.

---

## Task 5 — Worker integration (depends on T1 + T2)

**Touches:** `sync/PmSchedulerWorker.kt` only.

### Step 5.1 — Inject the use case and notification helper

Update the constructor signature:

```kotlin
@HiltWorker
class PmSchedulerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val templateRepo: PmTemplateRepository,
    private val planRepo: PmPlanRepository,
    private val woRepo: PmWorkOrderRepository,
    private val machineRepo: MachineRepository,
    private val computeTier: com.candorfoods.factoryops.domain.usecase.pm.ComputePmEscalationTierUseCase,
    private val notificationHelper: com.candorfoods.factoryops.notification.NotificationHelper
) : CoroutineWorker(context, workerParams) {
```

Add the corresponding imports at the top.

### Step 5.2 — After the existing plan-walk loop, add a tier-walk loop

At the end of `doWork()` (just before `return Result.success()`), add:

```kotlin
        // 3. Phase 2C-4 — escalation tier compute + notifications.
        val openWos = woRepo.getAllNonClosed()
        val nowForTier = System.currentTimeMillis()
        for (wo in openWos) {
            val newTier = computeTier(wo.scheduledDate, nowForTier)
            if (newTier == wo.escalationTier) continue

            woRepo.bumpTier(wo.id, newTier)

            // Notify only when tier increases (not on tier reset to 0).
            if (newTier > wo.escalationTier) {
                val machine = machineRepo.observeAllMachines().first()
                    .firstOrNull { it.id == wo.machineId } ?: continue
                val daysOverdue = ((nowForTier - wo.scheduledDate)
                    .coerceAtLeast(0L) / DAY_MS)
                notificationHelper.showPmEscalationNotification(
                    woId = wo.id,
                    machineName = machine.name,
                    templateName = wo.templateName,
                    tier = newTier,
                    daysOverdue = daysOverdue
                )
                // Q3A cascade: from T2, also notify supervisor. The supervisor
                // notification fires only on this device — the worker doesn't know
                // who is logged in. The notification is shown locally; if the
                // current user is the supervisor, they see it. Tech-only at T1.
                // No multi-device fan-out until Phase 3 backend.
            }
        }
```

The "supervisor cascade" comment captures the constraint honestly: in the mock-only stage, the worker fires notifications on the local device. There's no FCM fan-out yet. The notification appears for whoever is logged in here; the cascade behaviour is fully realised when Phase 3 lands the backend. **Don't add additional code for cascading** — the Q3A user experience naturally degrades to "device-local notification" until then. This is documented as a Phase 3 carryover.

### Verification

```
./gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Full APK at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Task 6 — Smoke + PROGRESS.md

After Tasks 1–5 land:
1. Final `./gradlew.bat :app:assembleDebug` to confirm clean build.
2. Update `PROGRESS.md` snapshot row for 2C-4 → ✅ DONE; bump "Last updated"; append a Phase 2C-4 section with what shipped + decisions + test plan.

### Manual test plan (user runs on device)

1. Reinstall APK. Log in as `technician1`.
2. Allow notifications when prompted (after PM tasks tab loads with at least one overdue PM seeded — the existing seed has the Packaging plan 15d overdue, so tier 4 should fire on first worker run).
3. Confirm: PM card shows red **CRITICAL 15d** chip and a soft red card background.
4. Within ~hourly worker window (or kill-restart app to trigger immediate run): a system notification appears titled "PM critical — Packaging Line 1".
5. Acknowledge the PM via the existing flow → chip clears (tier reset to 0). Card returns to normal.
6. To exercise T1/T2/T3: temporarily lower the threshold constants or wait days. Easiest: change a plan's `lastCompletedAt` in mock seed to 2/5/8 days ago, reinstall, verify each tier renders correctly.

---

## Self-review

**Spec coverage:**
- Q1A thresholds → Task 1.1 + use case in 1.2.
- Q2A reuse 60-min worker → Task 5 piggybacks on existing `PmSchedulerWorker.doWork()`.
- Q3A cascade → Task 5 + Task 2 priority/sound mapping. Multi-device fan-out deferred to Phase 3 (documented).
- Q4B JIT permission → Task 4.
- Q5A tier reset → Task 1.4 Edit B (acknowledge / start / submit).
- Q6A chip color + T2 pulse + T4 bg → Task 3.
- Q7C don't flip status → no work; explicitly no DAO update of `status` field.
- Q8A tech tab only → no supervisor screen edits.
- Q9A fire-and-forget → no sync queue entry in `bumpTier`.

**Type consistency:**
- `bumpTier(woId: String, newTier: Int): Result<Unit>` — interface + impl + worker call all match.
- `Constants.PM_TIER_*_DAYS` — referenced only by the use case.
- `Constants.NOTIFICATION_CHANNEL_PM_ESCALATION` — referenced by `NotificationHelper` create + show.
- `NotificationHelper.showPmEscalationNotification(woId, machineName, templateName, tier, daysOverdue)` — single call site (the worker).

**No placeholders.** Every step has copy-pasteable code.
