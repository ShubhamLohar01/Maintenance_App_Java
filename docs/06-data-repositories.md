# Data — Repository Implementations

FactoryOps Android follows a strict offline-first repository pattern: every user action is committed to Room first (so the UI stays reactive and snappy even on a flaky shop-floor network), and only then is a `SyncQueueEntity` enqueued with a typed `ActionType` for `WorkManager` to drain in the background. Read paths are almost always Room-backed `Flow`s mapped through entity-to-domain converters, with a few one-shot fetch helpers for use cases that need a point-in-time snapshot (e.g. resolving a flag's `machineId` before mutating it).

Repositories are also the single owner of cross-entity business rules. Beyond writing their own tables they reach into `machineDao` to flip `MachineStatus` at the correct lifecycle moments (`FLAGGED` when a breakdown is raised, `PENDING_QC` on technician resolve or supervisor approve, `IDLE` when QC approves), reset `escalationTier` to 0 on every technician touch, and refuse to overwrite QC pickups by a different user. Each Hilt-injected repository implementation is described below.

## AuthRepositoryImpl

- **File:** [`../app/src/main/java/com/candorfoods/factoryops/data/repository/AuthRepositoryImpl.kt`](../app/src/main/java/com/candorfoods/factoryops/data/repository/AuthRepositoryImpl.kt)
- **Responsibilities:** Performs online login against `AuthApi`, persists the user + JWT to Room and `AuthPreferences`, and exposes the current user as a `Flow`. Owns logout (Room clear + prefs clear) and the `isLoggedIn` check.
- **Dependencies:** `AuthApi`, `AppDatabase` (uses `userDao`), `AuthPreferences`.
- **Key methods:**
  - **Reads:** `getCurrentUser()` observes `userDao().getCurrentUser()` mapped to domain `User`; `isLoggedIn()` is a synchronous prefs lookup.
  - **Writes:**
    - `login(username, password)` — calls `authApi.login(...)`, computes a 7-day token expiry, persists a `UserEntity` via `userDao().insert(...)`, and stores token / userId / plantId in `AuthPreferences`. No sync queue entry — login is inherently online.
    - `logout()` — `userDao().clearAll()` + `authPreferences.clear()`.
- **Notable rules:** Token expiry is locally computed as `now + 7 * 24h`. Role string is decoded to a `UserRole` enum with `OPERATOR` as the fallback for unknown roles.

## BreakdownRepositoryImpl

- **File:** [`../app/src/main/java/com/candorfoods/factoryops/data/repository/BreakdownRepositoryImpl.kt`](../app/src/main/java/com/candorfoods/factoryops/data/repository/BreakdownRepositoryImpl.kt)
- **Responsibilities:** Owns the full breakdown-flag lifecycle (operator raise → tech ack/resolve → QC pickup/approve/disapprove) and the corresponding `MachineStatus` flips. Also exposes mock-seed bootstrapping and a generic sync-queue observer.
- **Dependencies:** `BreakdownApi`, `AppDatabase` (uses `breakdownFlagDao`, `machineDao`, `syncQueueDao`), `AuthPreferences`.
- **Key methods:**
  - **Reads (observers):** `observeOpenFlagsByPlant`, `observeAcknowledgedFlagsByUser`, `observeResolvedFlagsByPlant(plantId, since)`, `observeFlagById`, `observeActiveFlagForMachine`, `observeLatestFlagForMachine`, `observePendingQcByPlant`, `observeQcAcknowledgedByUser`, `observeQcDoneByUserSince`, `getPendingBreakdowns`, `getSyncQueue` (maps `SyncQueueEntity` → `SyncQueueItem`).
  - **Reads (one-shot):** `getNewOpenFlagsSince(plantId, since)` — returns a list snapshot for notification deltas.
  - **Writes:**
    - `raiseBreakdownFlag(...)` — `breakdownFlagDao.insert(...)` of a new `BreakdownFlagEntity` (status `OPEN`, sync `PENDING`), flips the machine to `FLAGGED` via `machineDao().updateStatus(...)`, enqueues `ActionType.FLAG_RAISE`.
    - `acknowledgeFlag(flagId, userId, userName)` — `breakdownFlagDao.acknowledgeFlag(...)` (atomic DAO), enqueues `ActionType.BREAKDOWN_ACK`. No machine flip.
    - `resolveFlag(...)` — reads `getFlagByIdOnce(flagId)` for `machineId`, calls `breakdownFlagDao.resolveFlag(...)`, flips machine to `PENDING_QC` (tech-resolve hands the machine to QC, **not** back to operator), enqueues `ActionType.BREAKDOWN_RESOLVE`.
    - `qcAcknowledge(...)` — verifies the flag is not already picked up by another QC user, calls `breakdownFlagDao.qcAcknowledge(...)`, enqueues `ActionType.TICKET_QC_ACKNOWLEDGE` with `"override":false`.
    - `qcOverrideAcknowledge(...)` — `breakdownFlagDao.qcOverrideAcknowledge(...)` unconditionally, enqueues `ActionType.TICKET_QC_ACKNOWLEDGE` with `"override":true`.
    - `qcApprove(...)` — `breakdownFlagDao.qcApprove(...)`, flips machine back to `IDLE`, enqueues `ActionType.TICKET_QC_APPROVE`.
    - `qcDisapprove(...)` — `breakdownFlagDao.qcDisapprove(...)`, flips machine back to `FLAGGED` so the operator stays blocked and the technician sees a hot ticket again, enqueues `ActionType.TICKET_QC_DISAPPROVE`.
  - **Other:** `seedMockOpenFlagsIfEmpty()` — only runs when `BuildConfig.USE_MOCK_API` is true; bulk-inserts mock flags and forces every referenced machine to `FLAGGED`.
- **Notable rules:**
  - QC pickup is **single-owner**: `qcAcknowledge` returns `Result.Error("Already picked up by …")` if a different user already owns the flag; an override is a separate explicit call (`qcOverrideAcknowledge`) that gets logged with `override:true`.
  - Machine status flips form a strict ladder: `IDLE → FLAGGED` (raise), unchanged on ack, `→ PENDING_QC` (tech resolve), `→ IDLE` (QC approve) or `→ FLAGGED` (QC disapprove).
  - All mutations write `syncStatus = PENDING` so the sync worker re-picks them.

## EnergyRepositoryImpl

- **File:** [`../app/src/main/java/com/candorfoods/factoryops/data/repository/EnergyRepositoryImpl.kt`](../app/src/main/java/com/candorfoods/factoryops/data/repository/EnergyRepositoryImpl.kt)
- **Responsibilities:** Tracks operator production runs (`ProductionRunEntity`), computes kWh on stop, flips machine status across `RUNNING`/`STOPPED`, and runs its own inline sync loop (does not go through `SyncQueueEntity`).
- **Dependencies:** `EnergyApi`, `AppDatabase` (uses `productionRunDao`, `machineDao`), `AuthPreferences`.
- **Key methods:**
  - **Reads:** `getTodaysRuns(machineId)` (since start-of-day via `DateTimeFormatter`), `getActiveRunForMachine`, `getMachineHistory(machineId, from, to)` (wrapped in `Result` + `catch`), `getPendingSyncCount`.
  - **Writes:**
    - `startProductionRun(machineId, scheduledEndAt)` — inserts a `ProductionRunEntity` (sync `PENDING`), flips machine to `"RUNNING"`. **No `SyncQueueEntity`** — this repo syncs by polling its own `PENDING` rows.
    - `stopProductionRun(runId, endedAt)` — looks up the run + machine, computes `kWh = ratedKw * runHours * loadFactor` (defaults `15.0` kW and `0.75` LF if machine missing), calls `productionRunDao.stopRun(...)`, flips machine to `"STOPPED"`.
    - `syncPendingRuns()` — pulls all pending rows via `getPendingRunsOnce()`, marks each `SYNCING`, posts either `energyApi.startRun(...)` (if `endedAt == null`) or `energyApi.stopRun(...)`, then transitions each row to `SYNCED` or `FAILED` per-row.
- **Notable rules:**
  - kWh is computed client-side at stop time using the machine's rated power and load factor — `(endedAt - startedAt) / 3_600_000.0` gives hours.
  - This is the only repo here that bypasses `SyncQueueEntity` and instead has its own `syncPendingRuns()` driver against the run table's own `syncStatus` column.

## MachineRepositoryImpl

- **File:** [`../app/src/main/java/com/candorfoods/factoryops/data/repository/MachineRepositoryImpl.kt`](../app/src/main/java/com/candorfoods/factoryops/data/repository/MachineRepositoryImpl.kt)
- **Responsibilities:** Read-mostly cache of operator-assigned machines. Pulls from `MachineApi` on first observation and exposes Room-backed flows of `Machine` domain objects. Also offers a generic status setter used by other layers.
- **Dependencies:** `MachineApi`, `AppDatabase` (uses `machineDao`), `AuthPreferences`.
- **Key methods:**
  - **Reads:**
    - `getAssignedMachines()` — observes `machineDao().getAllMachines()`. If the cache is empty it triggers `syncMachines()` and emits `Result.Loading`; otherwise emits `Result.Success(...)`. Starts with `Result.Loading` and catches mapping/DB errors into `Result.Error`.
    - `getMachineById(machineId)` and `observeAllMachines()` — straight `Flow` mappers.
  - **Writes:**
    - `syncMachines()` — calls `machineApi.getAssignedMachines(token)` and bulk-inserts via `machineDao().insertAll(...)`. No sync queue (it's a pull, not a push).
    - `updateMachineStatus(machineId, status)` — passthrough to `machineDao().updateStatus(...)` with `now`. No sync queue here either; this is used internally as a primitive by other repositories.
- **Notable rules:** `MachineType` and `MachineStatus` decoding both have catch-all defaults (`OTHER` and `IDLE` respectively) so unknown enum strings never crash the UI.

## MtMachineRepositoryImpl

- **File:** [`../app/src/main/java/com/candorfoods/factoryops/data/repository/MtMachineRepositoryImpl.kt`](../app/src/main/java/com/candorfoods/factoryops/data/repository/MtMachineRepositoryImpl.kt)
- **Responsibilities:** Thin read-through wrapper around the Maintenance machine-master endpoint. Has **no Room layer** — the master list is fetched on demand.
- **Dependencies:** `MtMachineApi`, `AuthPreferences`.
- **Key methods:**
  - **Reads:**
    - `list(floor, type, category)` — requires a bearer token (returns `Result.Error("Not logged in")` if missing), calls `mtMachineApi.list(...)`, maps DTOs to domain `MtMachine`.
  - **Writes:** none.
- **Notable rules:** No offline cache and no sync queue — this is the only repository in this set that is purely online. Filter args (`floor`, `type`, `category`) are passed through as the API's query parameters.

## PmPlanRepositoryImpl

- **File:** [`../app/src/main/java/com/candorfoods/factoryops/data/repository/PmPlanRepositoryImpl.kt`](../app/src/main/java/com/candorfoods/factoryops/data/repository/PmPlanRepositoryImpl.kt)
- **Responsibilities:** CRUD around `PmPlanEntity` (the schedule/cadence side of preventive maintenance). Owns the "last completed / next due" bookkeeping after a WO closes. No remote API and no sync queue at this layer.
- **Dependencies:** `AppDatabase` (uses `pmPlanDao`).
- **Key methods:**
  - **Reads (observers):** `observeAllPlans`, `observeActivePlans`, `observePlanById(id)`, `observePlansForMachine(machineId)`.
  - **Reads (one-shot):** `getPlanById(id)`, `getActivePlans()` (first emission of the active-plans flow).
  - **Writes:**
    - `savePlan(plan)` — `pmPlanDao.upsert(...)` with `syncStatus = PENDING`. No `SyncQueueEntity`.
    - `updateLastCompleted(planId, ts, hours, nextDueAt)` — direct atomic DAO call after a WO closes.
    - `seedMockIfEmpty(seedPlans)` — `dao.upsertAll(...)` only when `count() == 0`.
- **Notable rules:** Every entity write stamps `syncStatus = PENDING`; the entity's own sync flag is the source of truth (no separate queue row). `triggerType` defaults to `PmTriggerType.TIME` on unknown enum string.

## PmTemplateRepositoryImpl

- **File:** [`../app/src/main/java/com/candorfoods/factoryops/data/repository/PmTemplateRepositoryImpl.kt`](../app/src/main/java/com/candorfoods/factoryops/data/repository/PmTemplateRepositoryImpl.kt)
- **Responsibilities:** Stores PM templates and their ordered checklist items. Provides observers used by the template picker UI and a transactional save that replaces the entire item list atomically.
- **Dependencies:** `AppDatabase` (uses `pmTemplateDao`).
- **Key methods:**
  - **Reads (observers):**
    - `observeActiveTemplates()` and `observeByMachineType(machineType)` — emit templates with an empty items list (the picker shows headers only).
    - `observeTemplateById(id)` — `combine` of template + items, hydrating the full domain `PmTemplate.items`.
  - **Reads (one-shot):** `getTemplateById(id)` — loads template + items.
  - **Writes:**
    - `saveTemplate(template, items)` — calls `dao.replaceTemplateWithItems(...)` (a single DAO transaction) and re-indexes the items via `mapIndexed { idx, item -> item.copy(orderIndex = idx) }` so the persisted order matches the supplied list. No `SyncQueueEntity`.
    - `seedMockIfEmpty(seedTemplates)` — only when `dao.count() == 0`; calls `replaceTemplateWithItems` per pair.
- **Notable rules:** Item ordering is repository-enforced on save (not trusted from the caller). `MachineType` and `TradeType` decoders default to `OTHER` / `MECHANICAL`. Every entity stamps `syncStatus = PENDING`.

## PmWorkOrderRepositoryImpl

- **File:** [`../app/src/main/java/com/candorfoods/factoryops/data/repository/PmWorkOrderRepositoryImpl.kt`](../app/src/main/java/com/candorfoods/factoryops/data/repository/PmWorkOrderRepositoryImpl.kt)
- **Responsibilities:** Owns the entire preventive-maintenance work-order lifecycle — technician acknowledge / start / log items / log spares / submit, supervisor approve/reject, QC ack/override/approve/disapprove, and the escalation tier. Coordinates machine-status flips across the handoff boundaries and writes every mutation through a shared `mutate { … }` helper that wraps the body in try/catch and enqueues a typed `SyncQueueEntity`.
- **Dependencies:** `AppDatabase` (uses `pmWorkOrderDao`, `pmWoTaskLogDao`, `pmWoSpareDao`, `syncQueueDao`, `machineDao`).
- **Key methods:**
  - **Reads (observers):** `observeAll`, `observeForTechnician(techId, statuses)`, `observeByStatus(status)`, `observeById(id)` (combines WO + logs + spares), `observePendingQc`, `observeQcAcknowledgedByUser`, `observeQcDoneByUserSince`.
  - **Reads (one-shot):** `getById(id)` (hydrates logs + spares), `findOpenForPlan(planId)` (uses a fixed list of "open" statuses: `DRAFT, NOTIFIED, ACKNOWLEDGED, IN_PROGRESS, SUBMITTED, SUPERVISOR_APPROVED, PENDING_QC`), `getAllNonClosed()`, `suggestSpareNames(query, limit)`.
  - **Writes (technician path):**
    - `insertGeneratedWo(wo)` — `woDao.upsert(...)` + `logDao.upsertAll(...)`. No `SyncQueueEntity` (generation is local-only at this phase).
    - `updateStatusAndTier(id, status, tier)` / `updateEscalationTier(id, tier)` — primitives used by the escalation worker.
    - `acknowledge(woId)` — `woDao.markAcknowledged(woId, now)`, **resets escalation tier to 0**, enqueues `ActionType.PM_ACKNOWLEDGE`.
    - `start(woId)` — `woDao.markStarted(woId, now)`, **resets tier to 0**, enqueues `ActionType.PM_START`.
    - `logTaskResult(...)` — `logDao.updateLogResult(...)`, also calls `woDao.markStarted(woId, now)` so the parent WO transitions out of `NOTIFIED`/`ACKNOWLEDGED` on first item completion, enqueues `ActionType.PM_LOG_ITEM`.
    - `logSpare(...)` — generates `spareId = "spare-{uuid}"`, `spareDao.upsert(...)`, enqueues `ActionType.PM_LOG_SPARE`.
    - `deleteSpare(spareId, woId)` — `spareDao.deleteById(...)` only. **No sync queue entry** (server will reconcile by snapshot in Phase 3).
    - `submit(woId, finalNotes)` — `woDao.markSubmitted(woId, now, finalNotes)`, **resets tier to 0**, enqueues `ActionType.PM_SUBMIT`.
  - **Writes (supervisor path):**
    - `supervisorApprove(woId, supervisorId, supervisorName)` — reads the WO for `machineId`, `woDao.markSupervisorApproved(...)`, **flips machine to `PENDING_QC`** so it shows as "in QC review" on the operator/machine list, enqueues `ActionType.PM_SUPERVISOR_APPROVE`.
    - `supervisorReject(woId, …, notes)` — `woDao.markSupervisorRejected(...)`, **flips machine back to `IDLE`** (because a prior approve cycle may have left it on `PENDING_QC` and the WO is now back in `IN_PROGRESS` for tech rework), enqueues `ActionType.PM_SUPERVISOR_REJECT` with the JSON-escaped reason.
  - **Writes (QC path):**
    - `qcAcknowledge(woId, userId, userName)` — refuses pickup if already owned by another user (`Already picked up by …`), `woDao.markQcAcknowledged(...)`, enqueues `ActionType.PM_QC_ACKNOWLEDGE` with `"override":false`.
    - `qcOverrideAcknowledge(...)` — `woDao.markQcOverrideAcknowledged(...)` unconditionally, enqueues `ActionType.PM_QC_ACKNOWLEDGE` with `"override":true`.
    - `qcApprove(woId, …, checklistJson, photo, notes)` — `woDao.markQcApproved(...)`, **flips machine to `IDLE`** (machine returns to operator-controllable state), enqueues `ActionType.PM_QC_APPROVE`.
    - `qcDisapprove(woId, …, checklistJson, photo, reason)` — `woDao.markQcDisapproved(...)`, **does NOT flip machine status** (it stays on `PENDING_QC` because the supervisor still has it under review), enqueues `ActionType.PM_QC_DISAPPROVE` with the JSON-escaped reason.
  - **Writes (escalation):**
    - `bumpTier(woId, newTier)` — `woDao.updateEscalationTier(...)`. **No `SyncQueueEntity`** — tier is treated as a UI urgency signal, not an audit-worthy state change (Phase 3 backend will compute server-side).
- **Notable rules:**
  - **Tier-reset rule:** every technician touch (`acknowledge`, `start`, `submit`) resets `escalationTier` to 0. `logTaskResult` does not reset directly but its implicit `markStarted` call does the same transition for fresh items.
  - **Single-owner QC:** `qcAcknowledge` throws `IllegalStateException` when another QC user already holds the WO; override is a separate explicit method that's still logged as `override:true` in the payload.
  - **Machine-status ladder for PM:** supervisor approve `→ PENDING_QC`, supervisor reject `→ IDLE`, QC approve `→ IDLE`, QC disapprove leaves the machine unchanged (still `PENDING_QC` under supervisor review).
  - **Reason/notes are JSON-escaped** before being inlined into the sync-queue payload (`replace("\"", "\\\"")`).
  - **`mutate { … }` helper** standardises error handling: any thrown exception becomes `Result.Error(e.message ?: "Action failed", e)`. `enqueueSync(...)` writes a `SyncQueueEntity` with `now` and a null error message.
  - Every entity write stamps `syncStatus = SyncStatus.PENDING.name`.
