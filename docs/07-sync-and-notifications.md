# Sync Workers & Notifications

FactoryOps Android runs four `HiltWorker` background workers via WorkManager to keep production runs, breakdown tickets, and PM work orders flowing without manual refresh. Workers are enrolled at app launch (some as periodic, some as immediate one-time + self-rescheduling chains) and emit local notifications through a single `NotificationHelper` that owns four channels — Sync, Production Flags, Breakdown Tickets, and PM Escalation. `FcmService` is wired for future server-pushed notifications but currently only logs incoming messages and new tokens. The PM escalation channel (Phase 2C-4) drives a tier-based priority/sound/vibration cascade (T1 silent through T4 alert+vibrate) that fires whenever the PM scheduler bumps a work order to a higher overdue tier.

## Workers

### BreakdownPollWorker

- Source: [`../app/src/main/java/com/candorfoods/factoryops/sync/BreakdownPollWorker.kt`](../app/src/main/java/com/candorfoods/factoryops/sync/BreakdownPollWorker.kt)
- **Scheduling cadence:** one-time `OneTimeWorkRequest` chain. `enqueue()` schedules with `setInitialDelay(BREAKDOWN_POLL_INTERVAL_MINUTES = 2 min)`; `enqueueImmediately()` schedules with no delay. Uses `enqueueUniqueWork(WORKER_TAG_BREAKDOWN_POLL, REPLACE, ...)`. The worker re-enqueues itself via `reschedule()` at the end of every run, producing a self-perpetuating ~2-minute poll loop. No constraints.
- **Injected dependencies:** `AuthRepository`, `AuthPreferences`, `BreakdownRepository`, `AppDatabase`, `NotificationHelper`.
- **doWork() algorithm:**
  1. Fetch current user; if null or not `UserRole.TECHNICIAN`, call `reschedule()` and return `Result.success()` (stays enrolled so the next login picks it up).
  2. Read `authPreferences.getBreakdownLastSeenAt()`; fall back to `now - BREAKDOWN_POLL_INTERVAL_MINUTES*60_000` when zero.
  3. Call `breakdownRepository.getNewOpenFlagsSince(user.plantId, lastSeen)`.
  4. For each new flag, look up the machine name via `machineDao().getMachineByIdOnce(...)` (fallback `"Machine <id>"`) and fire `notificationHelper.showBreakdownNotification(flagId, machineName, severity.name, description)`.
  5. Save `authPreferences.saveBreakdownLastSeenAt(now)`.
  6. In a `finally` block, always call `reschedule(applicationContext)`.
- **Result handling:** All exceptions are swallowed (`catch (_: Exception)`) so a polling tick never crashes the chain; always returns `Result.success()`.
- **Idempotency:** `enqueueUniqueWork` with `ExistingWorkPolicy.REPLACE` under the tag `WORKER_TAG_BREAKDOWN_POLL` guarantees a single in-flight request. The `lastSeen` watermark stored in prefs guarantees flags are notified at most once across runs.

### FlagCheckerWorker

- Source: [`../app/src/main/java/com/candorfoods/factoryops/sync/FlagCheckerWorker.kt`](../app/src/main/java/com/candorfoods/factoryops/sync/FlagCheckerWorker.kt)
- **Scheduling cadence:** periodic via `schedulePeriodic(workManager)` — `PeriodicWorkRequestBuilder<FlagCheckerWorker>(SYNC_CHECK_INTERVAL_MINUTES = 5 min)`. Enrolled with `enqueueUniquePeriodicWork(WORKER_TAG_FLAG_CHECKER, KEEP, ...)`. No constraints.
- **Injected dependencies:** `AppDatabase`, `NotificationHelper`.
- **doWork() algorithm:**
  1. Read `now = System.currentTimeMillis()` and load all active runs via `productionRunDao().getAllActiveRuns()`.
  2. For each run, compute `timeSinceExpectedStop = now - run.scheduledEndAt`.
  3. If it exceeds `MISSED_STOP_THRESHOLD_MINUTES * 60_000` (15 min) **and** `run.flagStatus != "MISSED_STOP"`:
     1. Update the run's flag status: `productionRunDao().updateFlagStatus(run.id, "MISSED_STOP", now)`.
     2. Resolve machine name (fallback to the machine id).
     3. Fire `notificationHelper.showMissedStopNotification(machineId, machineName, runId)`.
     4. Mark the machine `"FLAGGED"` via `machineDao().updateStatus(...)`.
- **Result handling:** Always returns `Result.success()`. No retry/backoff.
- **Idempotency:** The `run.flagStatus != "MISSED_STOP"` guard ensures a run is flagged (and notified) at most once. `ExistingPeriodicWorkPolicy.KEEP` prevents duplicate enrollments across cold starts.

### PmSchedulerWorker

- Source: [`../app/src/main/java/com/candorfoods/factoryops/sync/PmSchedulerWorker.kt`](../app/src/main/java/com/candorfoods/factoryops/sync/PmSchedulerWorker.kt)
- **Scheduling cadence:**
  - Periodic via `schedulePeriodic(workManager)` — `PeriodicWorkRequestBuilder<PmSchedulerWorker>(PM_SCHEDULER_INTERVAL_MINUTES = 60 min)` enrolled with `enqueueUniquePeriodicWork(WORKER_TAG_PM_SCHEDULER, KEEP, ...)`.
  - One-time immediate via `enqueueImmediately(context)` at app launch — `OneTimeWorkRequest` enrolled with `enqueueUniqueWork("${WORKER_TAG_PM_SCHEDULER}_immediate", KEEP, ...)` so demo data appears without waiting an hour.
  - No constraints.
- **Injected dependencies:** `PmTemplateRepository`, `PmPlanRepository`, `PmWorkOrderRepository`, `MachineRepository`, `ComputePmEscalationTierUseCase`, `NotificationHelper`.
- **doWork() algorithm:**
  1. **First-run seed (idempotent):** `templateRepo.seedMockIfEmpty(MockSeedData.pmTemplatesWithItems)` and `planRepo.seedMockIfEmpty(MockSeedData.pmPlans)` — both no-op when the table already has rows.
  2. **Walk active plans** — for each plan from `planRepo.getActivePlans()`:
     1. Compute `nextDue` via `computeNextDueAt(plan)`:
        - `TIME` trigger: `(plan.lastCompletedAt ?: plan.createdAt) + plan.triggerInterval * 86_400_000L`.
        - `USAGE` trigger: same base + 30-day fallback (TODO: integrate running-hours).
     2. `daysUntilDue = (nextDue - now) / DAY_MS`; skip when `> PM_GENERATION_LEAD_DAYS` (7).
     3. Skip when `woRepo.findOpenForPlan(plan.id)` is non-empty (open WO already exists).
     4. Load template (`templateRepo.getTemplateById(plan.templateId)`) and machine (`machineRepo.observeAllMachines().first().firstOrNull { it.id == plan.machineId }`); skip if either missing.
     5. Build `woId = "wo-${plan.id}-${now}"` and snapshot every template item into a `PmWoTaskLog(status = PENDING, ...)`.
     6. Construct a `PmWorkOrder(status = NOTIFIED, escalationTier = 0, assignedTechnicianName = "Priya Sharma" (TODO 2C-2), ...)` with the snapshotted `taskLogs` and an empty `spares` list.
     7. Persist via `woRepo.insertGeneratedWo(wo)`. (Local-notification fire on generation is a 2C-2 TODO.)
  3. **Escalation tier compute (Phase 2C-4)** — for each WO from `woRepo.getAllNonClosed()`:
     1. `newTier = computeTier(wo.scheduledDate, now)`; skip if unchanged.
     2. `woRepo.bumpTier(wo.id, newTier)`.
     3. If `newTier > wo.escalationTier`, look up the machine, compute `daysOverdue = ((now - wo.scheduledDate).coerceAtLeast(0L) / DAY_MS)`, and fire `notificationHelper.showPmEscalationNotification(woId, machineName, templateName, tier = newTier, daysOverdue)`. (Q3A cascade is local-only for mock stage; full FCM fan-out lands with Phase 3 backend.)
- **Result handling:** Always returns `Result.success()`. No retry/backoff.
- **Idempotency:** Seeding is row-count guarded inside the repos. WO generation is gated by `findOpenForPlan(...)` so a plan can have only one open WO at a time. The escalation pass uses `newTier == wo.escalationTier` short-circuit and only notifies on a strict tier increase (not on resets to 0). The immediate-launch one-time work uses `ExistingWorkPolicy.KEEP` so cold-start spamming the worker is harmless.

### SyncWorker

- Source: [`../app/src/main/java/com/candorfoods/factoryops/sync/SyncWorker.kt`](../app/src/main/java/com/candorfoods/factoryops/sync/SyncWorker.kt)
- **Scheduling cadence:**
  - One-time via `enqueue(workManager)` — `OneTimeWorkRequest` with `Constraints(NetworkType.CONNECTED)` and `BackoffPolicy.EXPONENTIAL, 30 SECONDS`; enrolled with `enqueueUniqueWork(WORKER_TAG_SYNC, KEEP, ...)`.
  - Periodic via `schedulePeriodic(workManager)` — `PeriodicWorkRequestBuilder<SyncWorker>(SYNC_CHECK_INTERVAL_MINUTES = 5 min)` with the same network constraint; enrolled with `enqueueUniquePeriodicWork("${WORKER_TAG_SYNC}_periodic", KEEP, ...)`.
- **Injected dependencies:** `EnergyRepository`, `BreakdownRepository` (`BreakdownRepository` is injected but currently unused by `doWork`).
- **doWork() algorithm:**
  1. Call `energyRepository.syncPendingRuns()`.
  2. Return `Result.success()`.
- **Result handling:** Currently no explicit failure path — always returns `Result.success()`. The one-time variant carries an exponential backoff (30 s base) for the WorkManager system to apply if the worker is rescheduled after failure.
- **Idempotency:** Both variants use `KEEP` policies and unique work names so multiple `enqueue`/`schedulePeriodic` calls coalesce into a single enrollment. Network constraint ensures the worker only runs when connected.

## FCM Service

### FcmService

- Source: [`../app/src/main/java/com/candorfoods/factoryops/notification/FcmService.kt`](../app/src/main/java/com/candorfoods/factoryops/notification/FcmService.kt)
- `@AndroidEntryPoint` `FirebaseMessagingService` with a Hilt-injected `NotificationHelper` (held for channel handoff once push payloads start arriving).
- **`onMessageReceived(message: RemoteMessage)`** — currently a TODO; intended to dispatch incoming push payloads (PM reminders, supervisor overrides) into the appropriate `NotificationHelper.showXxxNotification(...)` so server-pushed alerts use the same channels and tier semantics as locally-generated ones.
- **`onNewToken(token: String)`** — TODO; intended to upload the FCM token to the backend once it is ready.

## NotificationHelper

Source: [`../app/src/main/java/com/candorfoods/factoryops/notification/NotificationHelper.kt`](../app/src/main/java/com/candorfoods/factoryops/notification/NotificationHelper.kt)

`@Singleton`, Hilt-injected with the application context. The `init` block calls `createChannels()` which, on API ≥ 26, registers four `NotificationChannel`s.

**Channels (id, importance, name, description):**

| Channel id (`Constants`)              | Value             | Name                | Importance        | Description                                            | Extras           |
|--------------------------------------|-------------------|---------------------|-------------------|--------------------------------------------------------|------------------|
| `NOTIFICATION_CHANNEL_FLAGS`         | `flags_channel`   | Production Flags    | `IMPORTANCE_HIGH` | Missed start/stop alerts                               | —                |
| `NOTIFICATION_CHANNEL_SYNC`          | `sync_channel`    | Sync Status         | `IMPORTANCE_LOW`  | Background sync notifications                          | —                |
| `NOTIFICATION_CHANNEL_BREAKDOWN`     | `breakdown_channel` | Breakdown Tickets | `IMPORTANCE_HIGH` | New breakdown tickets that need a technician           | vibration on     |
| `NOTIFICATION_CHANNEL_PM_ESCALATION` | `pm_escalation`   | PM Escalation       | `IMPORTANCE_HIGH` | Escalation alerts for overdue preventive maintenance   | vibration on     |

**`showMissedStopNotification(machineId: String, machineName: String, runId: String)`**
- Channel: `NOTIFICATION_CHANNEL_FLAGS`.
- Builds `MainActivity` `PendingIntent` (extras: `machine_id`) keyed on `machineId.hashCode()`.
- Title `"Missed Stop — $machineName"`, text `"You forgot to stop $machineName. Tap to confirm stop time."`, `PRIORITY_HIGH`, `setAutoCancel(true)`.
- Notification id: `NOTIFICATION_ID_FLAG_BASE + machineId.hashCode()`.

**`showBreakdownNotification(flagId, machineName, severity, descriptionPreview)`**
- Channel: `NOTIFICATION_CHANNEL_BREAKDOWN`.
- `PendingIntent` extras: `ticket_id = flagId`, keyed on `flagId.hashCode()`.
- Title `"$severity breakdown — $machineName"`, collapsed text `descriptionPreview.take(80)`, `BigTextStyle(descriptionPreview)`, `PRIORITY_HIGH`, `setAutoCancel(true)`.
- Notification id: `NOTIFICATION_ID_BREAKDOWN_BASE + flagId.hashCode()`.

**`showPmEscalationNotification(woId: String, machineName: String, templateName: String, tier: Int, daysOverdue: Long)`** — Phase 2C-4
- Channel: `NOTIFICATION_CHANNEL_PM_ESCALATION`.
- `PendingIntent` extras: `pm_wo_id = woId`, keyed on `woId.hashCode()`.
- **Tier-driven priority / defaults mapping (cascading sound + vibration):**

  | Tier  | `priority`         | `setDefaults(...)`             | Effect                          |
  |-------|--------------------|--------------------------------|---------------------------------|
  | 1     | `PRIORITY_DEFAULT` | `0`                            | Silent (T1)                     |
  | 2     | `PRIORITY_DEFAULT` | `DEFAULT_SOUND`                | Default sound only              |
  | 3     | `PRIORITY_HIGH`    | `DEFAULT_SOUND`                | High-priority + default sound   |
  | 4 (else) | `PRIORITY_HIGH` | `DEFAULT_ALL`                  | Sound + vibration alert (T4)    |

- **Tier-driven title mapping:**

  | Tier  | Title                                          |
  |-------|------------------------------------------------|
  | 1     | `"PM overdue — $machineName"`                  |
  | 2     | `"PM overdue ${daysOverdue}d — $machineName"`  |
  | 3     | `"PM very overdue — $machineName"`             |
  | 4 (else) | `"PM critical — $machineName"`              |

- Content text `"$templateName — ${daysOverdue}d overdue"`, `BigTextStyle("$templateName has been overdue for $daysOverdue days.")`, `setAutoCancel(true)`.
- Notification id: `NOTIFICATION_ID_PM_ESCALATION_BASE + woId.hashCode()`.

> JIT runtime permission: on Android 13+ the app requests `POST_NOTIFICATIONS` the first time the user encounters an overdue PM, so silent (T1) escalations can still post once granted and louder tiers escalate from there.

## Constants (worker/notification-relevant)

Source: [`../app/src/main/java/com/candorfoods/factoryops/util/Constants.kt`](../app/src/main/java/com/candorfoods/factoryops/util/Constants.kt)

**Thresholds & intervals**

| Constant                              | Value | Used by                                                                 |
|---------------------------------------|-------|-------------------------------------------------------------------------|
| `MISSED_STOP_THRESHOLD_MINUTES`       | `15L` | `FlagCheckerWorker` — overdue-stop trigger                              |
| `SYNC_CHECK_INTERVAL_MINUTES`         | `5L`  | `FlagCheckerWorker` periodic; `SyncWorker` periodic                     |
| `BREAKDOWN_POLL_INTERVAL_MINUTES`     | `2L`  | `BreakdownPollWorker` initial delay & fallback last-seen window         |
| `PM_SCHEDULER_INTERVAL_MINUTES`       | `60L` | `PmSchedulerWorker` periodic                                            |
| `PM_GENERATION_LEAD_DAYS`             | `7`   | `PmSchedulerWorker` — only generate WOs whose due date is within 7 days |

**PM escalation tier thresholds (days-overdue)**

| Constant         | Value |
|------------------|-------|
| `PM_TIER_1_DAYS` | `1`   |
| `PM_TIER_2_DAYS` | `4`   |
| `PM_TIER_3_DAYS` | `7`   |
| `PM_TIER_4_DAYS` | `14`  |

**Worker tags / unique names**

| Constant                       | Value                      |
|--------------------------------|----------------------------|
| `WORKER_TAG_SYNC`              | `sync_worker`              |
| `WORKER_TAG_FLAG_CHECKER`      | `flag_checker_worker`      |
| `WORKER_TAG_BREAKDOWN_POLL`    | `breakdown_poll_worker`    |
| `WORKER_TAG_PM_SCHEDULER`      | `pm_scheduler_worker`      |

**Notification channel IDs**

| Constant                              | Value                |
|---------------------------------------|----------------------|
| `NOTIFICATION_CHANNEL_SYNC`           | `sync_channel`       |
| `NOTIFICATION_CHANNEL_FLAGS`          | `flags_channel`      |
| `NOTIFICATION_CHANNEL_BREAKDOWN`      | `breakdown_channel`  |
| `NOTIFICATION_CHANNEL_PM_ESCALATION`  | `pm_escalation`      |

**Notification ID bases**

| Constant                              | Value  | Notes                                              |
|---------------------------------------|--------|----------------------------------------------------|
| `NOTIFICATION_ID_SYNC`                | `1001` | Reserved for sync foreground/status                |
| `NOTIFICATION_ID_FLAG_BASE`           | `2000` | + `machineId.hashCode()` for missed-stop alerts    |
| `NOTIFICATION_ID_BREAKDOWN_BASE`      | `3000` | + `flagId.hashCode()` for breakdown alerts         |
| `NOTIFICATION_ID_PM_ESCALATION_BASE`  | `4000` | + `woId.hashCode()` for PM escalation alerts       |

**Preference keys**

| Constant                      | Value                       | Used by                  |
|-------------------------------|-----------------------------|--------------------------|
| `KEY_BREAKDOWN_LAST_SEEN`     | `breakdown_last_seen_at`    | `BreakdownPollWorker`    |
