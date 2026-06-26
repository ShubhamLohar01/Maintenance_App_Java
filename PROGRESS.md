# FactoryOps — Master Progress Tracker

Last updated: **2026-05-10** (Phase 2C-4 shipped end-to-end; awaiting on-device verification)
Project root: `D:\Maintenance module\FactoryOps`

This is the **single source of truth** for what's done, what's in flight, and what's next across all phases of the FactoryOps Android app. Detailed per-phase notes live in their own files (linked below).

---

## Snapshot

| Phase | Title                          | Status              | Notes                                                  |
|-------|--------------------------------|---------------------|--------------------------------------------------------|
| 1     | Power Consumption              | ✅ DONE             | Scaffold + mock API; `assembleDebug` produces APK.     |
| 2A    | QC Verification                | ✅ DONE             | 7 Kotlin files under `presentation/qc/`. Mock API.     |
| 2B    | UI Polish                      | 🟡 IN PROGRESS      | Steps (a) + (c) + (f) + (g) + (h) done; step (i) final pass next. See PHASE_2B_PROGRESS.md.|
| 2C-1  | PM setup (templates + plans + scheduler + supervisor) | ✅ DONE | See section below.                            |
| 2C-2  | PM execution + supervisor approval | ✅ DONE     | Tech 4th tab, PM detail, multi-step wizard, supervisor 4th tab + review. |
| 2C-3  | PM QC gate (mixed inbox)       | ✅ DONE             | QC inbox merges BD + PM rows; PM QC detail + checklist; QC bounceback to supervisor. |
| 2C-4  | PM escalation cascade           | ✅ DONE             | All 6 tasks landed; tier-walk loop runs in `PmSchedulerWorker` and fires escalation notifications. APK rebuilt clean. |
| 3     | Real Backend (FastAPI + PG)    | ⏸ NOT STARTED       | Currently `BuildConfig.USE_MOCK_API=true`.             |

Build verification: `./gradlew.bat :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` ✅ present.

---

## Phase 1 — Power Consumption ✅

**Goal:** Operator daily flow — log machine power readings, see history, sync offline-first.

**Outcome (2026-05-05):** Full Android scaffold compiles end-to-end and produces a debug APK.

**Stack locked in:** Kotlin 2.0.21, AGP 8.6.1, KSP 2.0.21-1.0.27, Compose BOM 2024.09.03, Hilt 2.52, Retrofit 2.11.0, Room 2.6.1, WorkManager 2.9.1, Coil 2.7.0, CameraX 1.3.4, kotlinx-datetime 0.6.1.

**Defects fixed during first build:**
1. Gradle wrapper incomplete → bootstrapped Gradle 8.9 (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`).
2. Launcher icons missing → added placeholder white-F-on-Material-blue adaptive icon.
3. Package `in.candorfoods.factoryops` invalid (`in` is a Kotlin keyword) → split:
   - Kotlin namespace: `com.candorfoods.factoryops` (folder layout matches).
   - `applicationId`: `in.candorfoods.factoryops` (Play Store / Firebase identity preserved).
   - `google-services.json` `package_name` matches `applicationId`, not namespace.
4. 5 deprecation warnings for `Icons.Filled.ArrowBack` → migrate to `Icons.AutoMirrored.Filled.ArrowBack` later (non-blocking).

**Modules on disk (`presentation/`):** `breakdown`, `common`, `history`, `home`, `login`, `machine`, `qc`, `settings`, `sync`, `technician`.

---

## Phase 2A — QC Verification ✅

**Goal:** After a technician resolves a breakdown, a QC member must acknowledge → approve or disapprove. Disapproval reopens the ticket.

**Files added** (`presentation/qc/`):
- `QcHomeScreen.kt`, `QcHomeViewModel.kt`
- `QcTicketDetailScreen.kt`, `QcTicketDetailViewModel.kt`
- `QcChecklistSheet.kt`, `QcChecklistData.kt`
- `QcTicketRowUiModel.kt`

**UI:** 3-tab QC dashboard — **Awaiting QC / My Active / Done today**.

**Domain additions:**
- `UserRole`: `QC_HEAD`, `QC_MEMBER`
- `BreakdownStatus`: `QC_APPROVED`, `REOPENED` (plus pending-QC state)
- `BreakdownFlag` fields: `resolvedByName`, `resolvedAt`, `qcAcknowledgedAt`, `qcDecidedAt`, `qcAfterPhotoLocalPath`, `beforePhotoLocalPath`, `afterPhotoLocalPath`
- Repo methods: `observePendingQcByPlant`, `observeQcAcknowledgedByUser`, `observeQcDoneByUserSince`

**Mock seed users** (`MockAuthApi.kt`, password `pass123`):
- `operator1` — Rajan Kumar — OPERATOR
- `technician1` — Priya Sharma — TECHNICIAN
- `pooja.parkar` — Pooja Parkar — QC_HEAD
- `pooja.mhalim` — Pooja Mhalim — QC_MEMBER
- `shraddha.jadhav` — Shraddha Jadhav — QC_MEMBER

5 mock machines + programmatic 30-day history seeded in `MockSeedData.kt`.

---

## Phase 2B — UI Polish 🟡 IN PROGRESS

**Scope:** "Medium polish, not too much." No new features, no nav changes, no data/API edits — purely cosmetic/ergonomic refinement.

**Detailed plan:** [PHASE_2B_PROGRESS.md](./PHASE_2B_PROGRESS.md)

| Step | Description                                                              | Status              |
|------|--------------------------------------------------------------------------|---------------------|
| a    | Theme foundations (Color, Spacing, Type, Theme)                          | ✅ DONE              |
| b    | Bundle real fonts (Plus Jakarta / Inter / JetBrains Mono)                | ⏭ SKIPPED (system fonts) |
| c    | Polish core components (PrimaryButton, StatusBadge, FactoryCard, SyncIndicator) | ✅ DONE       |
| e    | Compose nav screen transitions                                            | ⏸ pending (jumped over) |
| f    | Operator path: Login → Home → MachineDetail → History                    | ✅ DONE              |
| g    | Technician inbox + ticket detail                                          | ✅ DONE              |
| h    | QC inbox + checklist sheet                                                | ✅ DONE              |
| i    | Final pass: skeletons, empty states, haptic feedback                     | ⏳ NEXT              |

## Phase 2C-4 — PM escalation cascade ✅

**Done 2026-05-10.** All 6 tasks landed; tier-walk now runs inside the existing 60-min `PmSchedulerWorker` and fires local escalation notifications when a WO crosses a threshold.

**Plan file:** [PHASE_2C_4_PLAN.md](./PHASE_2C_4_PLAN.md) — full task-by-task spec.

**Decisions locked (this session)**
- **Q1A** thresholds (days overdue): T1 = 1, T2 = 4, T3 = 7, T4 = 14.
- **Q2A** reuse existing 60-minute `PmSchedulerWorker` (no separate worker).
- **Q3A** notification cascade: T1 = tech only; T2+ = tech + supervisor; T3 = louder; T4 = alert sound + vibration. Multi-device fan-out deferred to Phase 3 backend; mock-only stage fires on the local device.
- **Q4B** JIT request `POST_NOTIFICATIONS` on first overdue PM (Android 13+). Silent degrade if denied.
- **Q5A** tier resets to 0 on positive forward action (acknowledge / start / submit).
- **Q6A** tier-driven chip color: T0 normal, T1 amber chip, T2 amber + pulse animation (800ms reverse), T3 red chip, T4 red full-card-bg.
- **Q7C** don't flip status to OVERDUE; tier alone drives UX urgency. `PmWoStatus.OVERDUE` enum value stays unused.
- **Q8A** tech tab only — no supervisor or operator screen edits.
- **Q9A** fire-and-forget notifications; no audit log. `bumpTier` deliberately skips `enqueueSync`.

**Status of the 6 tasks**

| # | Task | Status |
|---|------|--------|
| 1 | Data layer + tier compute use case | ✅ DONE |
| 2 | NotificationHelper PM channel + escalation method | ✅ DONE |
| 3 | PmTaskCard tier-driven chip + pulse + T4 bg | ✅ DONE |
| 4 | JIT POST_NOTIFICATIONS permission in TechnicianHomeScreen | ✅ DONE |
| 5 | PmSchedulerWorker integration (compute tier + bumpTier + notify) | ✅ DONE |
| 6 | Smoke build + PROGRESS.md final update | ✅ DONE |

**What landed (Tasks 1–4)**

Task 1 — Data layer:
- New constants in `util/Constants.kt`: `PM_TIER_1/2/3/4_DAYS = 1/4/7/14`, `NOTIFICATION_CHANNEL_PM_ESCALATION = "pm_escalation"`, `NOTIFICATION_ID_PM_ESCALATION_BASE = 4000`.
- New file `domain/usecase/pm/ComputePmEscalationTierUseCase.kt` — pure function `(scheduledDate, now) -> Int 0..4`.
- New repo method `bumpTier(woId, newTier): Result<Unit>` on `PmWorkOrderRepository` + impl in `PmWorkOrderRepositoryImpl`.
- `acknowledge`, `start`, `submit` now also call `woDao.updateEscalationTier(woId, 0)` (Q5A reset on positive action).
- DAO unchanged — `updateEscalationTier(id, tier)` was already in place from 2C-1.

Task 2 — `NotificationHelper`:
- New 4th channel `PM_ESCALATION` (IMPORTANCE_HIGH, vibration enabled).
- New method `showPmEscalationNotification(woId, machineName, templateName, tier, daysOverdue)` with tier-driven priority + sound + vibration mapping (T1 silent / T2 default sound / T3 high+sound / T4 high+all).

Task 3 — `PmTaskCard`:
- New private `TierChip(tier, daysOverdue)` composable. T0 = no-op. T1/T2 amber, T3/T4 red. T2 only pulses (800ms reverse, alpha 1 ↔ 0.55). T4 label reads "CRITICAL Xd".
- T4 red full-card background routed through new `FactoryCard.containerColor` parameter (added during this session — `Modifier.background()` paints behind the Card surface and was visually invisible). `FactoryCard` now takes an optional `containerColor: Color? = null` that overrides the theme-aware default.
- The existing `DueDateChip` stays alongside `TierChip` — separate signals (absolute due-date info vs urgency).

Task 4 — `TechnicianHomeScreen`:
- JIT permission: `LaunchedEffect(hasOverduePm)` triggers `notificationPermissionLauncher.launch(POST_NOTIFICATIONS)` on Android 13+ when the user first sees a tier > 0 PM. Silent degrade on deny.

**Build:** `./gradlew.bat :app:compileDebugKotlin` → `BUILD SUCCESSFUL` after each task. No assembleDebug rebuild after Task 4 yet — that comes in Task 6.

**Task 5 — `sync/PmSchedulerWorker.kt` (landed)**
- `ComputePmEscalationTierUseCase` and `NotificationHelper` injected into the worker's constructor.
- After the existing plan-walk loop, a tier-walk loop runs:
  - `woRepo.getAllNonClosed()` → for each WO, compute new tier with `computeTier(wo.scheduledDate, now)`; if changed, `woRepo.bumpTier(wo.id, newTier)`; if **increased**, fire `notificationHelper.showPmEscalationNotification(woId, machineName, templateName, tier, daysOverdue)`.
- Q3A cascade (T1 = tech only; T2+ = tech + supervisor) is documented in-code as **mock-stage local-device-only** — the worker fires a notification on whichever device it's running on. Multi-device fan-out (FCM) lands with Phase 3 backend.
- Tier reset to 0 on `acknowledge` / `start` / `submit` is intentionally silent: the loop only notifies on tier increase, so a reset doesn't re-fire.

**Task 6 — Smoke (landed 2026-05-10)**
- Constants cleanup: removed unused `NOTIFICATION_ID_PM_BASE = 4000` and `NOTIFICATION_CHANNEL_PM = "pm_channel"` from `util/Constants.kt`. They were declared in 2C-1 as stubs, never wired up, and the `4000` value collided numerically with `NOTIFICATION_ID_PM_ESCALATION_BASE`. Deleting closes the deferred reviewer finding from this phase.
- `./gradlew.bat :app:assembleDebug` → `app-debug.apk` 24 MB rebuilt 11:32 IST. **BUILD SUCCESSFUL** in 1m 55s (incremental).

**Manual on-device verification (your turn)**
1. Install the new APK over the existing one (Room v4 schema unchanged — no DB wipe needed; if Hilt complains, uninstall first).
2. Login as `technician1` / `pass123`.
3. Within ~60s of app start (the immediate one-time worker run fires on launch), expect:
   - **Packaging plan PM** is seeded 15d overdue → notification: *"PM critical — Packaging Line"* with alert sound + vibration.
   - PM tasks tab shows the Packaging PM with a **red full-card background** + "CRITICAL 15d" tier chip (T4).
   - The first time you scroll past the overdue card, Android 13+ should prompt for `POST_NOTIFICATIONS`. Allow.
4. Tap **Acknowledge** on the Packaging PM → tier chip should clear immediately (tier reset to 0 on acknowledge).
5. Other expected tiers depending on seed timing:
   - Roaster plan (5d → tier 2) — amber chip, pulsing.
   - Cooling/Compressor plan (last completed 60d ago, every 90d → not yet overdue) — no chip.
6. Test reset behaviour: leave one PM unacknowledged, fast-forward your device clock by a day or wait until next worker run — chip should escalate (e.g. T2 → T3) and you should get a new "PM very overdue" notification, but **not** a duplicate of the old one.

**Reviewer findings closed in this phase**
- ✅ `NOTIFICATION_ID_PM_BASE` collision — resolved by deletion (constants were dead code).
- 🟡 *Deferred to a future cleanup:* Kotlin daemon multi-session warning during parallel-agent dispatch (KSP cache conflict). Workaround when it recurs: `--no-daemon --rerun-tasks`.

---

## Phase 2C-3 — PM QC gate (mixed inbox) ✅

**Done 2026-05-09.** Hands off supervisor-approved PM work orders to the QC inbox alongside breakdowns; QC member runs the same FSSAI/BRC checklist on PMs; QC disapproval bounces the PM back to the supervisor (not the technician).

**Decisions locked (in this session)**
- Q1A — supervisor approve flips PM status straight to `PENDING_QC`. `SUPERVISOR_APPROVED` enum value retained for audit only, never set on the WO.
- Q2A — supervisor approve flips machine to `PENDING_QC`; QC approve flips machine to `IDLE`.
- Q3A — reuse the existing `QcChecklistSheet` + `QcChecklistDraft` for PM QC. Same food-grade compliance, no separate checklist.
- Q4B — PM QC disapprove bounces back to **supervisor** (status → `SUBMITTED`, `qcDecidedAt/Notes/By` populated). Supervisor banner explains, supervisor decides re-approve or reject-to-tech.
- Q5A — small leading "BD" / "PM" type chip on each inbox card, color-coded (red / blue).
- Q6A — plant scoping for PM rows handled in the VM via the in-memory machine map (no DB schema change).
- Q7A — no extra seed; manual test walks the full flow each install.

**Domain layer — no changes (everything pre-stubbed in 2C-2)**
- Reuses existing `qcAcknowledgedBy/Name/At`, `qcDecidedBy/Name/At`, `qcDecisionNotes`, `qcAfterPhotoLocalPath`, `qcChecklistJson` fields on `PmWorkOrder`.
- Reuses `PmWoStatus.PENDING_QC / QC_APPROVED / SUBMITTED` and `ActionType.PM_QC_ACKNOWLEDGE / PM_QC_APPROVE / PM_QC_DISAPPROVE`.
- Database version unchanged (still v4).

**Repo + DAO additions (`PmWorkOrderRepositoryImpl`, `PmWorkOrderDao`)**
- 3 new Flow observers: `observePendingQc()` (unscoped — VM filters by plant), `observeQcAcknowledgedByUser(userId)`, `observeQcDoneByUserSince(userId, sinceMillis)`.
- 4 new actions: `qcAcknowledge`, `qcOverrideAcknowledge`, `qcApprove`, `qcDisapprove` — each enqueues the matching `ActionType` for Phase 3 sync.
- 4 new atomic `@Query UPDATE` mark methods on the DAO mirroring the breakdown side: `markQcAcknowledged`, `markQcOverrideAcknowledged`, `markQcApproved` (sets `closedAt`), `markQcDisapproved` (status → `SUBMITTED`, clears acknowledgement fields so re-pickup is clean).
- `markSupervisorApproved` query rewritten in-place: status now flips to `PENDING_QC` (not `SUPERVISOR_APPROVED`) and clears any prior QC bounce fields so the WO appears fresh on re-submission.
- `supervisorApprove` impl now also flips the machine to `MachineStatus.PENDING_QC`. `supervisorReject` now flips machine to `IDLE` (covers both initial reject and reject-after-bounce paths).
- `qcApprove` flips machine back to `IDLE`. `qcDisapprove` deliberately leaves machine on `PENDING_QC` (still in supervisor's hands).

**QC inbox — mixed BD + PM**
- New `presentation/qc/QcInboxRow.kt` — sealed `Breakdown(flag) | Pm(wo)` with shared `id`, `machineCode/Name/Location`, `criticality`, and a `sortMillis` Elvis chain (`qcDecidedAt → qcAcknowledgedAt → resolvedAt/submittedAt → raisedAt/generatedAt`). Single chain lands on the right freshness signal per tab without any per-tab branching.
- `QcHomeViewModel` rewritten to merge breakdown + PM streams across all 3 tabs (Awaiting / My Active / Done today) via 3 `combine` blocks. Plant scoping applies only on Awaiting tab (BD already scoped at repo, PM scoped via in-VM `machineMap[id]?.takeIf { plantId == user.plantId }`). Single `observerJob: Job?` cancels and re-launches the trio when the user re-emits — prevents observer leaks on user re-emission.
- `QcHomeScreen` renders a leading **BD/PM type chip** (red `BD` for breakdowns, blue `PM` for PMs), branches body line (BD description vs PM template name), gates `SeverityChip` and the photo icon to breakdowns only. Status line splits into `breakdownStatusLine` / `pmStatusLine` helpers per row type. Click handler typed as `(QcInboxRow) -> Unit`; `NavGraph` dispatches to `QcTicketDetail` or `QcPmDetail` accordingly.

**PM QC detail screen (new, mirror of `QcTicketDetailScreen`)**
- New route `qc/pm/{woId}` (`Screen.QcPmDetail`).
- New `presentation/qc/QcPmDetailViewModel` — collects `pmRepo.observeById(woId)` + machine; exposes `acknowledge / override / open-checklist / change / approve / disapprove`. Approve/disapprove encode the checklist via `kotlinx.serialization.json.Json { encodeDefaults = true }` and call the matching repo action. Disapprove validates 4..500-char reason, snackbar on length-fail.
- New `presentation/qc/QcPmDetailScreen` — same scaffold + checklist sheet integration as the breakdown side. Reuses `QcChecklistSheet` and `QcChecklistDraft` unchanged (Q3A). Shows: PM-chip header, submission card (tech + supervisor), per-`taskLog` `ItemRowCard` with measurement bounds + photo + notes, spares list, final tech notes, then a status-driven `QcActionSection`:
  - `PENDING_QC + ack=null` → amber "Acknowledge for QC" 64dp button.
  - `PENDING_QC + isHeldByMe` → "Picked up by you" + Hero green "Run QC checklist".
  - `PENDING_QC + isHeldByOther` → "Picked up by …" + (if `isQcHead`) outlined "Override — take this over as QC Head".
  - `QC_APPROVED` → green summary card with decided-by + notes + after-photo.
  - `SUBMITTED` (back-from-QC) → red summary card with decided-by + reason.
  - else → "This PM is not awaiting QC."
- Snackbars: "Picked up for QC.", "QC override applied.", "QC approved — machine returned to operator." (then `popBack`), "Sent back to supervisor." (then `popBack`).

**Supervisor — QC bounceback awareness**
- `SupervisorPmReviewScreen` now shows a red **"QC sent this back"** banner above the header card when `wo.qcDecidedAt != null && wo.status == SUBMITTED`. Banner renders QC member name, decision time (relative), reason, the QC after-photo (if captured), and a hint: "Approve to re-send to QC, or reject to send to technician for rework."
- The Approve button auto-relabels to **"Re-approve and send to QC"** in this state.
- The VM is unchanged — `onApprove` already calls `pmRepo.supervisorApprove(...)`, and Task 1's `markSupervisorApproved` query clears `qcDecidedBy/Name/At/Notes` and `qcAcknowledgedBy/Name/At` so the WO appears fresh in the QC inbox on re-submission.

**Build**
- `./gradlew.bat :app:assembleDebug` → `app-debug.apk` 24.6 MB. **BUILD SUCCESSFUL.**

**Notes / known tradeoffs**
- `supervisorReject` unconditionally flips machine to `IDLE`. Acceptable for mock-only stage; Phase 3 backend should introduce a stacked-state machine (machine PENDING_QC for breakdown vs PM vs both) so reject doesn't clobber a concurrent breakdown's hold.
- JSON sync payloads use naive `replace("\"", "\\\"")` escape (matches 2C-2 precedent). Phase 3 to switch to `kotlinx.serialization`.
- `qcOverrideAcknowledge` does not validate WO existence/status — convention-conforming with breakdown side; both should harden together in a later pass.

**Test plan — end-to-end (4 scenarios)**

**A. Happy path (PM approved by QC)**
1. Reinstall APK. Login `technician1` / `pass123`.
2. PM tasks tab → tap a Roaster PM → Acknowledge → Start → walk all items (PASS, photos where required, in-bounds measurements) → Submit.
3. Logout. Login `supervisor1` / `pass123` (Shubham Lohar).
4. Approvals tab → tap PM → Approve. Snackbar; PM disappears from supervisor inbox.
5. Logout. Login `pooja.mhalim` / `pass123` (QC_MEMBER).
6. **Awaiting QC tab — PM appears with blue PM chip alongside any breakdowns.**
7. Tap → PM detail → Acknowledge for QC → tab moves to "My Active".
8. Run QC checklist → fill all required PASS → capture after-photo → Approve.
9. Snackbar "QC approved — machine returned to operator." PM moves to "Done today".
10. Login `operator1` → machine status is IDLE again.

**B. QC bounces back to supervisor**
1. Repeat A.1–A.7.
2. Run QC checklist → Disapprove with reason "evidence of oil leak post-repair, retake".
3. Snackbar "Sent back to supervisor."
4. Logout. Login `supervisor1`.
5. Approvals tab → PM appears with red **"QC sent this back: evidence of oil leak post-repair, retake"** banner including the QC photo.
6. Tap PM → review screen shows banner. Approve button reads **"Re-approve and send to QC"**.
7. Tap Re-approve → PM goes back to PENDING_QC fresh. Login as QC member, Awaiting QC tab → PM is back, no QC history shown (cleared on re-approve).

**C. Supervisor rejects QC bounceback to technician**
1. Repeat B.1–B.5.
2. Tap PM → tap **Reject — needs rework** with reason "tech to redo item 3".
3. PM disappears from supervisor inbox.
4. Logout. Login `technician1`. PM tasks tab → PM is back as IN_PROGRESS with the supervisor rejection banner.

**D. Mixed inbox**
1. With one PM in PENDING_QC AND one breakdown in PENDING_QC simultaneously.
2. Login as QC member → Awaiting QC tab shows both, PM with blue chip and breakdown with red chip, sorted by most-recent-needs-attention.
3. Tap each — opens the correct detail screen.

---

## Phase 2C-2 — PM execution + supervisor approval ✅

**Done 2026-05-08.** Completes the technician path: pick up assigned PM → acknowledge → start → walk through checklist items → submit → supervisor approves or rejects.

**Repo + DAO additions**
- `PmWorkOrderRepository` gained 8 action methods: `acknowledge`, `start`, `logTaskResult`, `logSpare`, `deleteSpare`, `submit`, `supervisorApprove`, `supervisorReject`, plus `suggestSpareNames(query, limit)` for the autocomplete (Q3 tweak).
- `PmWorkOrderDao` — `markAcknowledged` / `markStarted` / `markSubmitted` / `markSupervisorApproved` / `markSupervisorRejected` with explicit `@Query UPDATE` statements (atomic field flips).
- `PmWoTaskLogDao` — `updateLogResult` for single-item save.
- `PmWoSpareDao` — **`suggestSpareNames` query** uses `GROUP BY spareName ORDER BY COUNT(*) DESC, spareName ASC LIMIT :limit` to surface most-frequently-used parts first. LIKE filter is case-insensitive (`COLLATE NOCASE`). Q3 reasoning: prevents `belt`/`Belt`/`v-belt` duplicate proliferation that would haunt Phase 3 inventory migration.
- All actions enqueue a `SyncQueueEntity` with the matching `ActionType` (PM_ACKNOWLEDGE, PM_START, PM_LOG_ITEM, PM_LOG_SPARE, PM_SUBMIT, PM_SUPERVISOR_APPROVE, PM_SUPERVISOR_REJECT). Network call wiring lands in Phase 3.
- New `PmPhotoStorage` helper in `util/BreakdownPhotoStorage.kt` — files at `pm_photos/<woId>_<itemId>_<ts>.jpg`.

**Technician screens (3 new + 1 updated)**
- `TechnicianHomeScreen` — switched to `ScrollableTabRow`, added 4th **PM tasks** tab. Empty state when no active PM tasks. Existing 3 ticket tabs untouched.
- `PmTaskCard` (new) — machine name+code, location, A-criticality red chip if applicable, status chip, due-date relative chip (red if overdue, amber if ≤3 days, green otherwise), template name, est. duration + trade.
- `TechnicianPmDetailScreen` — entry screen. Status-driven action button:
  - NOTIFIED → outlined "Acknowledge" (Warning semantic)
  - ACKNOWLEDGED → Hero green "Start PM"
  - IN_PROGRESS → primary "Continue execution"
  - SUBMITTED / SUPERVISOR_APPROVED → read-only summary banner
  - **Rejection banner** at top when `supervisorRejectedAt != null` and status reverted to IN_PROGRESS — shows date, reason, and prompts user to resume.
- `TechnicianPmExecuteScreen` — multi-step wizard:
  - Top progress bar (`LinearProgressIndicator`, item N of total).
  - Per-item view: title / description / expected result, **Pass/Fail/N/A** big buttons (64dp), measurement input with unit + min/max bounds (out-of-range = soft red helper text per Q1 default), photo capture with preview (CameraX intent + `PmPhotoStorage`), per-item notes.
  - Auto-resume from first PENDING item on screen open.
  - Next persists the item to Room first via `logTaskResult`, then advances. Back navigates without saving (current draft is local).
  - **Summary view** after last item: items recap with status pills (tap to jump back), **spares list with add button** (modal bottom sheet), final notes, Submit (Hero green) + Save draft & exit (outlined).
  - **AddSpareForm** in the bottom sheet shows top-5 autocomplete chips below the Part name field, refreshed on every keystroke via `refreshSpareSuggestions(query)`. Tapping a chip fills the field. Quantity restricted to digits + dot. Add disabled until name + qty>0 + unit all set.

**Supervisor screens (1 updated + 1 new)**
- `SupervisorHomeScreen` — switched to `ScrollableTabRow`, added **Approvals** as 4th tab. Empty state copy. Tap a row → review screen.
- `SupervisorPmReviewScreen` (new) — read-only review:
  - Header card: machine + template + submitted-by + relative time.
  - Per-item review card: status pill (PASS/FAIL/N/A/PENDING), measurement value with green/red color depending on whether it falls in acceptable range, photo preview if captured, item notes.
  - Spares used list (read-only, with notes shown).
  - Final tech notes (if any).
  - **Approve** button (Hero green) → calls `supervisorApprove`, status → SUPERVISOR_APPROVED.
  - **Reject — needs rework** button (raw amber Button, Warning semantic, 64dp) → opens ModalBottomSheet with 4–500 char reason field. Submit reject → calls `supervisorReject`, status reverts to IN_PROGRESS with `supervisorRejectionNotes` set. Tech sees the rejection banner on next visit to the PM detail.

**Navigation**
- New routes: `tech/pm/{woId}`, `tech/pm/{woId}/execute`, `supervisor/pm/{woId}`. Helpers `Screen.TechnicianPmDetail.createRoute(id)`, `Screen.TechnicianPmExecute.createRoute(id)`, `Screen.SupervisorPmReview.createRoute(id)`.

**Build**
- `./gradlew.bat :app:assembleDebug` → `app-debug.apk` 24.3 MB rebuilt 23:46 IST. **BUILD SUCCESSFUL.**

**Q-answer recap (locked in this session)**
- Q1 measurement out-of-range → soft warn (red supporting text, not blocking)
- Q2 photo storage → new `PmPhotoStorage` helper, mirror of `BreakdownPhotoStorage`
- **Q3 spare add → free-text + autocomplete suggestions ordered by historical usage frequency** (Q3 tweak you asked for)
- Q4 rejection visibility → banner at top of PM detail when `supervisorRejectedAt != null`
- Q5 tab order → 4th tab on the right, `ScrollableTabRow` for both tech + supervisor
- Q6 wizard nav → Hardware Back per default; Next requires Pass/Fail/N/A + photo (if required) + measurement (if required)

**Test plan — end-to-end Roaster PM**
1. Reinstall APK (DB v4 schema unchanged, no destructive migration this time — but `assembleDebug` rebuilt).
2. Launch app — scheduler runs in background, generates Roaster + Packing PM WOs (already true since 2C-1 seed).
3. Login as **`technician1` / `pass123`** (Priya Sharma).
4. **PM tasks tab** — see 1–3 PM cards depending on which plans triggered.
5. Tap a Roaster PM → Acknowledge → Start PM (goes straight into wizard).
6. Walk through 4 items: pick PASS, capture a photo where required, type a temp value (within bounds), advance.
7. Reach summary → add a spare (e.g. "V-belt", qty 1, unit "pcs") → Submit for supervisor approval.
8. Logout → login as **`supervisor1` / `pass123`** (Shubham Lohar).
9. **Approvals tab** — Roaster PM appears → tap → review → Approve.
10. To test reject path: re-submit another, hit Reject, type a reason (4+ chars), submit. Logout, login as technician1, open the PM — banner appears at top.

**Carryover for 2C-3 (next)**
- After supervisor approve, `wo.status = SUPERVISOR_APPROVED`. The handoff to QC (status → PENDING_QC, machine → PENDING_QC, mixed inbox) lands in **2C-3**.
- For now SUPERVISOR_APPROVED is a terminal-looking state for the tech (read-only summary). 2C-3 will pick this up.

---

## Phase 2C-1 — Preventive Maintenance setup ✅

**Done 2026-05-08.** Built foundation + scheduler + supervisor screens for PM. **Phase 2B step (i) deferred** — user pulled forward to PM.

**Domain layer**
- New enums: `MachineType` (12 types), `TradeType`, `PmTriggerType`, `PmWoStatus` (11 states), `PmTaskLogStatus`. Added `SUPERVISOR` to `UserRole`. Added 9 new `ActionType` values for sync queue.
- New domain models: `PmTemplate`, `PmTemplateItem`, `PmPlan`, `PmWorkOrder`, `PmWoTaskLog`, `PmWoSpare`. `Machine` now has `machineType`.

**Data layer**
- 6 new Room entities and DAOs. `AppDatabase` bumped to **v4** (still `fallbackToDestructiveMigration` per agreement). 3 new repository interfaces + impls registered in Hilt.
- `MachineDto` gained `machine_type` (defaults `OTHER` for legacy backend payloads).

**Mock seed (`MockSeedData.kt`)**
- 7 machines now: existing 5 + `mach-006` Compressor 1 (COMPRESSOR) + `mach-007` Mixer 1 (MIXER).
- All machines tagged with `MachineType` (Roaster→ROASTER, Packaging→PACKING_LINE, Grinder→OTHER, Cooling→HVAC, Conveyor→CONVEYOR, +2 new).
- 3 templates seeded: Roaster monthly (4 items), Compressor quarterly (5 items), Packing line monthly (3 items).
- 3 plans seeded: Roaster (every 30d, last 25d ago), Packaging (every 30d, last 45d ago — overdue), Cooling reused as Compressor quarterly (every 90d, last 60d ago).

**Auth**
- `MockAuthApi` adds **`supervisor1` / `pass123` / Shubham Lohar / SUPERVISOR**. Login routes to new `SupervisorHomeScreen`.

**Scheduler (`PmSchedulerWorker`)**
- HiltWorker, periodic every 60 min, also fires once immediately at app startup so demo data appears without a wait.
- Idempotent first-run seed of templates/plans.
- Walks active plans, computes `next_due_at` (TIME-based; USAGE-based stub for 2C-2). Generates a `PmWorkOrder` with status `NOTIFIED` and snapshotted task logs when due is within 7 days and no open WO exists for the plan.
- Notification firing stubbed — wired in 2C-2/2C-4.

**UI — Supervisor screens**
- `SupervisorHomeScreen` — 3-tab Scaffold: Templates / Plans / Active WOs. Each row uses `FactoryCard`. Extended FAB on Templates and Plans tabs.
- `TemplateEditorScr dropdown, description, duration, default trade type. Item list with up/down arrow reorder (Q3 choice — gloves-friendly, no extra dep). Per-item: photo / measurement toggles, unit + min/max only when measurement on. Save validates `name + items + each title`.
- `PlanEditorScreen` — machine dropdown, template dropdown filtered by machine type, trigger type, interval, technician id field. Save computes `next_due_at = now + interval days`.

**Navigation**
- New routes: `home/supervisor`, `supervisor/template?id=...`, `supervisor/plan?id=...`. Uses `Screen.SupervisorTemplateEditor.createRoute(id?)` / `SupervisorPlanEditor.createRoute(id?)` helpers.

**Notes / deferred to next sub-sessions**
- Q6 not answered → defaulted to `today − N days` for `last_completed_at` so escalation tier compute (2C-4) lights up correctly.
- Drag-to-reorder skipped per Q3 in favour of arrow buttons.
- Local notifications wired to scheduler → 2C-4.
- Running-hours-driven USAGE trigger → 2C-2 (when running-hours integration lands).
- Template editor `menuAnchor()` deprecation warning — non-blocking.

**Build**
- `./gradlew.bat :app:assembleDebug` → `app-debug.apk` 24.3 MB rebuilt 22:54 IST. **BUILD SUCCESSFUL.**

**Test plan**
1. Reinstall APK. Login `supervisor1` / `pass123`.
2. Templates tab — see 3 seeded templates. Tap one to edit. Add an item, reorder, save.
3. Plans tab — see 3 seeded plans. Tap one to edit. Try changing the technician.
4. Active WOs tab — scheduler should auto-generate a WO for the Roaster plan on app start (due in 5 days, within 7-day lead). Packaging plan is 15d overdue, will also generate.
5. Try creating a brand-new plan for `Mixer 1` — pick template (likely "Compressor quarterly" if you create a Mixer template, or none until you do).

---

**Step (h) deliverables (done 2026-05-08):**
- `presentation/qc/QcHomeScreen.kt` — `QcTicketCard` `Card` → `FactoryCard(onClick = ...)`. Migrated `StatusFlagged`/`StatusRunning`/`StatusStopped` legacy aliases to `FactoryTheme.colors.*` in `StatusLine`, `CriticalityBadge`, `SeverityChip`. Removed unused `clickable` import.
- `presentation/qc/QcTicketDetailScreen.kt` — `Icons.Default.ArrowBack` → `Icons.AutoMirrored.Filled.ArrowBack`. 3 info Cards (`MachineCard`, `OperatorReportCard`, `TechnicianRepairCard`) → `FactoryCard`. "Run QC checklist" Button → `PrimaryButton(Primary 64dp, icon=CheckCircle)`. "Override — take this over as QC Head" `OutlinedButton` → `PrimaryButton(Outlined, icon=SwapHoriz)`. "Acknowledge for QC" stays raw amber Button (Warning semantic), height bumped to 64dp. QC_APPROVED status sub-card migrated to `FactoryTheme.colors.statusRunning`. CritBadge + SevChip migrated. "Picked up by you/other" amber sub-cards kept raw (status-tinted background).
- `presentation/qc/QcChecklistSheet.kt` — `ChecklistSection` `Card` → `FactoryCard`. After-QC photo `Card` → `FactoryCard`. "Capture QC photo" `OutlinedButton` → `PrimaryButton(Outlined, icon=PhotoCamera)`. "Approve" Button → `PrimaryButton(Success, Primary 64dp, loading=isSubmitting)`. "Disapprove" stays raw amber Button (Warning), height bumped to 64dp. `PassFailRow` ResultChip Pass/Fail/N/A colors migrated to `FactoryTheme.colors.statusRunning/statusStopped/statusFlagged`. AlertDialog "Submit disapproval" TextButton color migrated.
- Build: `./gradlew.bat :app:assembleDebug` → `app-debug.apk` 24.3 MB rebuilt 21:13 IST. BUILD SUCCESSFUL.

**Note:** Legacy color aliases `StatusRunning/Stopped/Flagged/Idle` are still referenced ONLY by `presentation/breakdown/BreakdownFlagScreen.kt` after step (h). Will be folded into step (i) cleanup.

**Step (g) deliverables (done 2026-05-08):**
- `presentation/technician/TicketCard.kt` — `Card` → `FactoryCard(onClick = ...)`. Migrated `StatusRunning`/`StatusStopped` legacy aliases to `FactoryTheme.colors.*` in `StatusLine`, `CriticalityBadge`, `SeverityChip`.
- `presentation/technician/TicketDetailScreen.kt` — `Icons.Default.ArrowBack` → `Icons.AutoMirrored.Filled.ArrowBack`. Header `Card` → `FactoryCard`. "Mark Resolved" + "Re-resolve" `Button` → `PrimaryButton(style=Success, size=Primary 64dp)` with `icon=CheckCircle`. QC_APPROVED status card migrated to `FactoryTheme.colors.statusRunning`. Migrated SeverityChip stopped color. Status sub-cards (Acknowledged amber, PENDING_QC surfaceVariant, REOPENED errorContainer) kept as raw `Card` because their tinted backgrounds don't fit FactoryCard's surface contract.
- "Acknowledge — I'll fix this" Button: kept as raw Button with intentional amber styling (Warning semantic — distinct from Primary/Success/Danger/Outlined). Height bumped to `TouchTarget.primary` (64dp). Comment added explaining why.
- ResolveSheet — "Capture after-repair photo" `OutlinedButton` → `PrimaryButton(style=Outlined, icon=PhotoCamera)`. "Submit resolution" `Button` → `PrimaryButton(style=Success, size=Primary, loading=isSubmitting)` — now shows spinner inline during submit.
- `presentation/technician/TechnicianHomeScreen.kt` — no changes needed; already used Material 3 standard components and theme tokens flow through.
- Build: `./gradlew.bat :app:assembleDebug` → `app-debug.apk` 24.3 MB rebuilt 20:48 IST. BUILD SUCCESSFUL.

**Note:** Legacy color aliases `StatusRunning`/`StatusStopped`/`StatusFlagged`/`StatusIdle` still referenced by `presentation/qc/*` (cleaned up in step h) and `presentation/breakdown/BreakdownFlagScreen.kt` (operator-side flag entry — not yet polished; consider folding into step h or a follow-up).

**Step (f) deliverables (done 2026-05-08):**
- `presentation/login/LoginScreen.kt` — raw `Button` → `PrimaryButton(size=Primary, 64dp)`. Login button now teal, larger tap target.
- `presentation/home/HomeScreen.kt` — Retry error button → `PrimaryButton(style=Outlined)`.
- `presentation/common/components/MachineCard.kt` — raw `Card` → `FactoryCard` with `statusBorder = statusBorderColor(machine.currentStatus)` for left-stripe color. Migrated `StatusFlagged` literal to `FactoryTheme.colors.statusPendingQc`. All muted text now uses `FactoryTheme.colors.onSurfaceMuted`.
- `presentation/machine/MachineDetailScreen.kt` — `Icons.Default.ArrowBack` → `Icons.AutoMirrored.Filled.ArrowBack`. `HeaderCard` and `RunRowItem` migrated to `FactoryCard`. Production Start/Stop buttons → `PrimaryButton(size=Hero 88dp, style=Success/Danger)`. `LifecycleBanner` `StatusFlagged` → `FactoryTheme.colors.statusFlagged`. Banner kept as raw `Card` because its tinted container colors are status-state-specific and don't fit FactoryCard's surface contract.
- `presentation/history/HistoryScreen.kt` — `ArrowBack` AutoMirrored, `HistoryRunCard` → `FactoryCard`. `HistorySummaryCard` kept as raw `Card` for the same reason as `LifecycleBanner` (primaryContainer background).
- Build: `./gradlew.bat :app:assembleDebug` → `app-debug.apk` 24.3 MB rebuilt 20:42 IST. BUILD SUCCESSFUL.
- ⚠️ Pre-existing ArrowBack deprecation warnings now cleared from these 2 screens.

**Step (c) deliverables (done 2026-05-08):**
- `presentation/common/components/PrimaryButton.kt` NEW — variants `Primary`/`Success`/`Danger`/`Outlined`; sizes `Min` 56dp / `Primary` 64dp / `Hero` 88dp; press scale 0.97 (100ms); loading spinner replaces text without layout shift; disabled at 40% alpha; optional leading icon
- `presentation/common/components/StatusBadge.kt` REFACTORED — migrated to `FactoryTheme.colors.*`; 200ms color tween (`animateColorAsState`) on container + content when status changes; pill shape (heights 28dp / 64dp); optional leading icon param added
- `presentation/common/components/FactoryCard.kt` NEW — 16dp internal padding, 12dp corner radius, 1dp `FactoryTheme.colors.border`; light=elevation 2dp, dark=surfaceVariant background no shadow; optional `statusBorder: Color?` 4dp left stripe; press scale 0.98 (80ms) when clickable
- `presentation/common/components/SyncIndicator.kt` REFACTORED — migrated to `FactoryTheme.colors.sync*`; states Synced/Syncing/Pending/Failed with 200ms `Crossfade`; pending = white-on-amber pill with CloudOff + count; failed = red `SyncProblem`; tap target ≥ 40dp; new optional `hasFailed: Boolean = false` param
- Build: `./gradlew.bat :app:assembleDebug` → `app-debug.apk` 24.3 MB rebuilt 20:23 IST. BUILD SUCCESSFUL.
- ⚠️ User to verify on phone before step (e).

**Step (a) deliverables (done):**
- `presentation/common/theme/Color.kt` rewritten — raw tokens + `FactoryTheme.colors.*` semantic accessor + legacy aliases for backward compat
- `presentation/common/theme/Spacing.kt` new — `Spacing.xs..xxxl`, `TouchTarget.min/primary/hero/chip/chipLarge`
- `presentation/common/theme/Type.kt` rewritten — full M3 slot scale, `bodyLarge` 16→17sp, `displayHero`/`monoLarge`/`monoMedium` extras, `FactoryFonts.sans/mono` swap point
- `presentation/common/theme/Theme.kt` rewritten — light + dark `ColorScheme`, `LocalFactoryColors`, `LocalFactoryTypeExtras`
- Build: `./gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL, no warnings
- ⚠️ User has **not yet visually verified** on phone

**Open questions blocking final decisions** (see PHASE_2B_PROGRESS.md §Open questions):
- Dark-mode trigger: system-only or add user toggle?
- Haptics: primary actions only, or every press?
- Production Start/Stop confirm: keep AlertDialog or upgrade to ModalBottomSheet?
- Test device target

---

## Phase 2 — Preventive Maintenance (PM) ⏸

**Status:** Not started. Do not begin without explicit approval.

Will cover scheduled PM execution + QC closure flow (per `04_preventive_maintenance_execution_qc.png`).

---

## Phase 3 — Real Backend ⏸

**Status:** Not started. Currently mock-only.

- Backend stack: FastAPI + PostgreSQL (separate repo, not yet built).
- Switchover: flip `BuildConfig.USE_MOCK_API` to `false` in release build.
- `google-services.json` is a placeholder; replace with real Firebase config.

---

## Architectural invariants (do not violate)

- **Offline-first.** Room is the single source of truth. Every write goes to Room first, then WorkManager syncs. Every record has a `sync_status` field.
- **Stack is non-negotiable.** No Java, no XML Views, no RxJava, no Dagger-without-Hilt.
- **Indian formatting.** `Rs.1,23,456`, `dd MMM yyyy`, 24-hour time.
- **Factory-floor UX.** Wi-Fi dead zones, gloved hands, dim/dusty screens → large tap targets, high-contrast Material 3, semantic colors (green=running, red=stop, amber=flag).
- **Approval-gated build.** Build screen-by-screen; report build status; wait for confirmation before next screen.

---

## Resume checklist (next session)

1. Read this file (master view).
2. **Decide what comes next.** Two open fronts:
   - **Phase 2B step (i)** — final UI polish pass (skeletons, empty states, haptic feedback). See [PHASE_2B_PROGRESS.md](./PHASE_2B_PROGRESS.md).
   - **Phase 3** — real backend (FastAPI + Postgres). Greenfield, not yet started. Currently `BuildConfig.USE_MOCK_API=true`.
3. Run `./gradlew.bat :app:compileDebugKotlin` to confirm clean baseline (~35s) before any code changes.
4. Wait for user direction. Do not start Phase 3 without explicit approval.

---

## How to update this file

After each completed step or phase milestone:
- Move the row's status in the Snapshot table (⏸ → 🟡 → ✅).
- Add a brief deliverables bullet under the relevant phase section.
- Update **Last updated** at the top.
- Keep detail in the per-phase doc; this file stays a one-page overview.
