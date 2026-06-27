# Role-Based Reminders & Notifications — Design Spec

**Date:** 2026-06-27
**Status:** Approved (brainstorming) → ready for implementation plan
**Module:** FactoryOps (Android, Kotlin, Jetpack Compose)

## 1. Goal

Ensure that on every required action in the maintenance workflows, the responsible
role/user gets a reminder — and if they don't act, the reminder repeats and then
escalates up the chain. Coverage targets the two handoff-heavy workflows:
**Breakdowns** and **PM work orders**.

The classic example ("operator forgot to stop production → notified") already works
today via `FlagCheckerWorker` + `showMissedStopNotification` and is left unchanged;
it is conceptually part of the same model.

## 2. Locked decisions (from brainstorming)

1. **Delivery:** On-device reminder engine built on the existing WorkManager polling,
   working today against mock + real REST. A future FCM push contract is *defined*
   (Section 9) so the backend can push later through the same code path — but push is
   not implemented now.
2. **No persistence / no inbox:** Reminders are **OS notifications only**. No new Room
   table, no read/unread history. (Considered and explicitly dropped — the only thing
   that required storage was an in-app inbox, which is out of scope.)
3. **Escalating re-nudge:** Re-remind the responsible role on an interval until the
   action is done; after configured thresholds, also alert the next role up.
4. **Scope:** Breakdown workflow + PM work-order workflow. (Production stays as-is;
   checklists and daily readings are out of scope.)

## 3. Scope

**In scope**
- A pure-logic `ReminderEvaluator` encoding the role×event matrix (Section 5).
- Re-nudge + escalation mechanics with tunable thresholds (Section 6).
- Wiring the evaluator into the existing breakdown + PM workers; enabling those
  workers to run for all roles that receive reminders (Section 8).
- New `NotificationHelper` entry points + category→channel mapping (Section 7).
- A *defined* FCM envelope + token-upload contract for the future server-push path
  (Section 9) — interfaces/stubs only, no backend dependency.

**Out of scope**
- In-app notification center / history / read-unread state / any new DB table.
- Real server-side FCM push (only the client-side contract is defined).
- Production "forgot-to-stop" rework (kept as-is).
- Checklist and daily floor-reading reminders.
- Admin operational reminders.

## 4. Architecture (no-storage form)

Two logic pieces; everything else is reuse.

```
Worker (existing poll/interval)
  → fetch domain slice for current user's role (existing repo observers)
  → ReminderEvaluator(currentUser, polledState, now)  // pure, no Android/DB
  → returns List<Reminder>
  → NotificationDispatcher: for each reminder, fire/update OS notification
       with a STABLE id (hash(type+entityId)); cancel ids no longer present
```

**Why no table is needed**
- **No spam:** OS notifications are keyed by a stable `notificationId`. Re-firing the
  same id *updates* the existing notification instead of stacking. The OS notification
  is the dedupe state. (This is already how `BreakdownPollWorker` / `PmSchedulerWorker`
  behave.)
- **Escalation:** tier = `now − triggerTime` vs thresholds, recomputed each run — the
  same approach as the existing `ComputePmEscalationTierUseCase`
  (`domain/usecase/pm/ComputePmEscalationTierUseCase.kt`). No stored tier.
- **State source:** the domain entities the app already persists and polls
  (`BreakdownFlagEntity`, `PmWorkOrderEntity`), read via existing repository observers
  (e.g. `BreakdownRepository.observeOpenFlagsByPlant`, `observePendingQcByPlant`,
  `observeAcknowledgedFlagsByUser`, `observeResolvedFlagsByPlant`).
- **Per-role coverage:** each device runs the evaluator for *its own* logged-in user's
  role and fires locally. Escalation to a higher role works because that role's device
  independently polls the plant-wide state and derives that it is now in the audience.

**New small types**
- `Reminder` — value object: `type`, `category`, `entityType`, `entityId`,
  `recipientRole`, `tier`, `title`, `body`, `deepLink`, `priorityHint`.
- `ReminderType` — enum of the matrix rows (B1..B5, P1..P6 below) plus production
  carry-overs if folded in later.
- `ReminderEvaluator` — `operator fun invoke(user, snapshot, now): List<Reminder>`.
- `ReminderSnapshot` — the polled slices the evaluator reads (open flags, pending-QC
  flags, acknowledged-not-resolved flags, recently-closed flags; PM WOs by status).
- `NotificationDispatcher` — maps `Reminder` → `NotificationHelper` calls, owns stable
  id derivation and cancellation of resolved reminders.

## 5. Role × Event reminder matrix

Recipient = the role whose *device* derives and shows the reminder. Conditions are
expressed against existing enums (`BreakdownStatus`, `QcStatus`, `PmWoStatus`) and
timestamps.

### 5.1 Breakdown (`OPEN → ACKNOWLEDGED → PENDING_QC → CLOSED / REOPENED`)

| ID | Fires when (derivable condition) | Recipient | Re-nudge until | Escalates to |
|----|----------------------------------|-----------|----------------|--------------|
| B1 | `status = OPEN` (no technician yet) | Technician | acknowledged | Supervisor → Head |
| B2 | `status = ACKNOWLEDGED` and stale since `acknowledgedAt` | Technician (finish/resolve) | resolved (`PENDING_QC`) | Supervisor |
| B3 | `status = PENDING_QC` and `qcDecidedAt = null` | QC member | QC decides | QC Head → Head |
| B4 | `status = REOPENED` (QC rejected) | Technician (rework) | re-resolved | Supervisor |
| B5 | `status = CLOSED` recently *(best-effort)* | Operator (`raisedBy`) + Technician | one-shot | — |

### 5.2 PM work order (`NOTIFIED → ACKNOWLEDGED → IN_PROGRESS → SUBMITTED → SUPERVISOR_APPROVED → PENDING_QC → QC_APPROVED → CLOSED`)

| ID | Fires when | Recipient | Re-nudge until | Escalates to |
|----|------------|-----------|----------------|--------------|
| P1 | `NOTIFIED`, or `scheduledDate` passed and status before `SUBMITTED` | Technician | submitted | Supervisor (T3) → Head (T4) |
| P2 | `SUBMITTED` and no supervisor decision | Supervisor | decided | Head |
| P3 | supervisor rejected → bounced back | Technician (rework) | resubmitted | Supervisor |
| P4 | `PENDING_QC` and `qcDecidedAt = null` | QC member | QC decides | QC Head → Head |
| P5 | QC rejected → bounced back | Supervisor | re-decided | Head |
| P6 | `QC_APPROVED` / `CLOSED` recently *(best-effort)* | Technician + Supervisor | one-shot | — |

**Reliability note.** Action-pending reminders (B1–B4, P1–P5) are the reliable core:
the item is still in an active state, so any role's device can poll and derive the
nudge. Informational one-shots (B5, P6) are **best-effort** — without storage we cannot
guarantee exactly-once delivery; they depend on the closed item remaining within the
poll window (e.g. `observeResolvedFlagsByPlant(plantId, sinceMillis)`). They are
nice-to-have, not the backbone, and are gated by a "seen recently" time window rather
than a stored flag.

**Role notes.** Head sits at the top of every escalation chain (read-only oversight).
Admin receives no operational reminders. `QC_HEAD` is treated as both a QC recipient
and the QC escalation target above `QC_MEMBER`.

## 6. Escalation & re-nudge mechanics

`ReminderEvaluator` recomputes tiers each run from elapsed time. All thresholds live in
one `ReminderConfig` object (constants in `util/Constants.kt`) so they are tunable
without touching logic.

**Breakdown** (urgent; evaluated every `BREAKDOWN_POLL_INTERVAL_MINUTES` = 2 min):

| Tier | Elapsed since pending-state start | Audience added | Notification urgency |
|------|-----------------------------------|----------------|----------------------|
| 0 | immediate | primary role only | default |
| 1 | ≥ 30 min unacted | primary role | high + sound |
| 2 | ≥ 60 min | + Supervisor | high + sound |
| 3 | ≥ 120 min | + Head | high + sound + vibration |

- "Pending-state start" = `raisedAt` (B1), `acknowledgedAt` (B2), `resolvedAt` (B3),
  `qcDecidedAt` of the rejection (B4).
- **Severity scaling:** `CRITICAL` halves all thresholds; `MINOR` may double them
  (tunable). Default base thresholds above are for `MAJOR`.
- Re-nudge: the evaluator runs each poll; the OS notification is updated silently every
  run, but a *fresh alert* (sound/vibration) is only raised when the tier increases.

**PM** (days; evaluated every `PM_SCHEDULER_INTERVAL_MINUTES` = 60 min):

- P1 reuses the existing day-based tiers from `Constants.PM_TIER_1..4_DAYS` and
  `ComputePmEscalationTierUseCase`: T1 ≥0d, T2 ≥4d, T3 ≥8d (+Supervisor), T4 ≥12d
  (+Head). Existing `showPmEscalationNotification` urgency mapping is kept.
- P2–P5 (review/QC handoffs) re-nudge on an hours-based cadence (default: re-nudge
  every 4 h, escalate to next role after 24 h, to Head after 48 h) — tunable in
  `ReminderConfig`.

**Auto-clear.** When an item leaves the pending state (e.g. flag acknowledged, WO
submitted, QC decided), the evaluator no longer emits that reminder; the dispatcher
`cancel()`s the corresponding stable notification id so stale "please act" alerts
disappear.

## 7. Delivery (OS notifications)

Reuse the existing `NotificationHelper` and channels:

| Category | Channel constant | Existing? |
|----------|------------------|-----------|
| BREAKDOWN | `NOTIFICATION_CHANNEL_BREAKDOWN` | yes |
| PM | `NOTIFICATION_CHANNEL_PM_ESCALATION` | yes |
| PRODUCTION | `NOTIFICATION_CHANNEL_FLAGS` | yes |

- **Stable notification id:** `NOTIFICATION_ID_BREAKDOWN_BASE + (type+entityId).hashCode()`
  for breakdowns; `NOTIFICATION_ID_PM_ESCALATION_BASE + (type+entityId).hashCode()` for
  PM. Distinct per reminder type so different reminders about the same entity don't
  collide.
- **Priority/sound:** driven by tier, following the existing
  `showPmEscalationNotification` pattern (tier → priority + `setDefaults`).
- **Deep link:** reuse the existing `PendingIntent` → `MainActivity` extras pattern
  (`ticket_id`, `pm_wo_id`) already used by `showBreakdownNotification` /
  `showPmEscalationNotification`. Tap opens the relevant detail screen.
- **New `NotificationHelper` methods:** generalize the existing three into reminder-aware
  entry points (e.g. `showBreakdownReminder(reminder)`, `showPmReminder(reminder)`,
  plus `cancel(notificationId)`), preserving current behavior for the existing callers.

## 8. Worker wiring & role gating

- **`BreakdownPollWorker`** (currently technician-only at lines 31–36): widen so it runs
  for every role that can receive a breakdown reminder — `TECHNICIAN`, `SUPERVISOR`,
  `QC_MEMBER`, `QC_HEAD`, `HEAD`, and `OPERATOR` (operator only for B1-raiser/B5
  best-effort). Each role pulls only its relevant slice via existing repo observers and
  feeds the evaluator. Keep the 2-min reschedule loop and the swallow-exceptions safety.
- **`PmSchedulerWorker`** (60 min): after its existing generation/escalation pass, call
  the evaluator for `TECHNICIAN`, `SUPERVISOR`, `QC_MEMBER`, `QC_HEAD`, `HEAD`. The
  existing P1 tier logic is refactored to flow through the same evaluator/dispatcher so
  there is one code path.
- No new worker is required. (If P2–P5 latency at 60 min proves too slow in practice, a
  dedicated lighter-cadence `ReminderWorker` can be added later — noted, not built now.)

## 9. FCM forward contract (define-only)

So the future server-push path reuses the exact same dispatcher/notification code:

- **Data payload `NotificationEnvelope`:**
  `{ type, category, entityType, entityId, targetRole?, targetUserId?, title, body, tier, deepLinkRoute, dedupeKey }`.
- **`FcmService.onMessageReceived`** (currently a stub): parse the envelope → build a
  `Reminder` → `NotificationDispatcher` (same path as local poll).
- **`FcmService.onNewToken`** (currently a stub): `POST /devices/token`
  `{ userId, token, platform }` — contract defined; no-op against the mock.
- **Double-notify guard:** a `NOTIFICATIONS_SOURCE` flag (`LOCAL_POLL` default,
  `SERVER_PUSH` later) lets local evaluation be disabled per-category once the backend
  pushes, with no code change.

This section is interface/stub only — no backend work in this effort.

## 10. Components

**New**
- `presentation`/`domain` notification logic:
  - `domain/usecase/reminder/ReminderEvaluator.kt` (pure)
  - `domain/model/Reminder.kt`, `ReminderType`, `ReminderSnapshot`
  - `notification/NotificationDispatcher.kt`
  - `notification/NotificationEnvelope.kt` (FCM contract)
- `util/Constants.kt`: `ReminderConfig` thresholds (breakdown tiers, PM handoff
  cadences, severity scaling), `NOTIFICATIONS_SOURCE` flag.

**Changed**
- `notification/NotificationHelper.kt`: add reminder-aware methods + `cancel`.
- `sync/BreakdownPollWorker.kt`: widen role gating; call evaluator/dispatcher.
- `sync/PmSchedulerWorker.kt`: route P1 + P2–P5 through evaluator/dispatcher.
- `notification/FcmService.kt`: implement envelope parsing + token-upload contract
  (behind the source flag).
- DI: provide `ReminderEvaluator`, `NotificationDispatcher` (Hilt).

**Reused as-is**
- `BreakdownRepository` observers, `PmWorkOrderRepository`, `ComputePmEscalationTierUseCase`
  pattern, `NotificationHelper` channels, Nav routes, `AuthPreferences`.

## 11. Testing

- **`ReminderEvaluator` unit tests (primary, TDD):** pure logic, no Android deps. For
  each matrix row (B1–B5, P1–P6): feed crafted `ReminderSnapshot` + `now`, assert the
  exact `List<Reminder>` (type, recipient role, tier). Cover every tier boundary,
  severity scaling, and **auto-clear** (e.g. acknowledged flag → B1 no longer emitted).
- **Dispatcher tests:** stable-id derivation; tier increase raises a fresh alert while
  same-tier re-runs update silently; resolved reminder triggers `cancel`.
- **Envelope test:** `NotificationEnvelope` JSON parse → `Reminder` mapping.
- **Manual/instrumented:** OS notification appears, updates without stacking, deep-links
  to the correct detail screen.

## 12. Risks & open items

- **Best-effort one-shots (B5, P6):** without storage, exactly-once is not guaranteed;
  mitigated by a "recently closed" poll window. Acceptable per the no-storage decision.
- **Role-widened polling cost:** more roles now poll plant-wide breakdown state every
  2 min. Volume is small (single plant); reuse of existing endpoints keeps it cheap.
- **PM handoff latency:** P2–P5 nudges run on the 60-min PM cadence; if faster is needed,
  add a dedicated `ReminderWorker` later.
- **Notification permission:** `POST_NOTIFICATIONS` (Android 13+) is already declared;
  ensure runtime permission is requested for all roles, not just the original ones.
