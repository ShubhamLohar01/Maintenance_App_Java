# Domain Layer

The domain layer is the pure-Kotlin heart of FactoryOps. It contains no Android dependencies — only data classes, enums, sealed result types, repository interfaces (contracts), and use cases (business rules). It encapsulates the three core flows of the app: **power consumption logging** via production runs, **breakdown flags with a QC verification workflow**, and **preventive maintenance with a four-tier escalation model**. Everything here is consumable by tests and by the JVM in isolation; framework-specific concerns (Room, Retrofit, WorkManager, Hilt wiring) live in `data/` and the presentation layers.

## Models

### BreakdownFlag

**File:** [BreakdownFlag.kt](../app/src/main/java/com/candorfoods/factoryops/domain/model/BreakdownFlag.kt)

Represents an operator-raised machine breakdown and its full lifecycle, including the Phase 2A QC verification stage.

Key fields:
- Identity / context: `id`, `machineId`, `operatorId`, `severity` (`Severity`), `description`, `beforePhotoLocalPath`, `raisedAt`, `status` (`BreakdownStatus`).
- Acknowledgement & resolution (technician): `acknowledgedBy/Name/At`, `resolvedBy/Name/At`, `partsUsed`, `afterPhotoLocalPath`.
- QC verification block: `qcStatus` (`QcStatus`), `qcAcknowledgedBy/Name/At`, `qcDecidedBy/Name/At`, `qcDecisionNotes`, `qcAfterPhotoLocalPath`, `qcChecklistJson`, plus override audit fields `qcOverrideFromUserId/Name/At` for QC-head takeover.
- `syncStatus` (`SyncStatus`) tracks offline-first replication state.

### Enums

**File:** [Enums.kt](../app/src/main/java/com/candorfoods/factoryops/domain/model/Enums.kt)

Central catalogue of vocabulary used across the domain:
- `MachineStatus` — IDLE, RUNNING, STOPPED, FLAGGED, PENDING_QC.
- `Criticality` — A/B/C tiers.
- `LoadFactorSource` — ASSUMED, SPOT_MEASURED, IOT_METERED (how `loadFactor` was sourced for energy calculations).
- `SyncStatus` — PENDING, SYNCING, SYNCED, FAILED.
- `FlagStatus` — MISSED_START, MISSED_STOP, RESOLVED (production-run anomaly flags).
- `UserRole` — OPERATOR, TECHNICIAN, QC_HEAD, QC_MEMBER, SUPERVISOR.
- `Severity` — CRITICAL, MAJOR, MINOR.
- `ActionType` — discriminator for offline-sync payloads (run start/stop, flag raise, breakdown ack/resolve, ticket QC ack/approve/disapprove, full PM lifecycle).
- `BreakdownStatus` — OPEN, ACKNOWLEDGED, PENDING_QC, QC_APPROVED, REOPENED.
- `QcStatus` — PENDING, APPROVED, DISAPPROVED.
- Phase 2C Preventive Maintenance enums: `MachineType`, `MachineCategory`, `TradeType`, `PmTriggerType` (TIME/USAGE), `PmWoStatus` (full PM WO state machine including OVERDUE/CANCELLED), `PmTaskLogStatus` (PENDING, PASS, FAIL, NOT_APPLICABLE).

### Machine

**File:** [Machine.kt](../app/src/main/java/com/candorfoods/factoryops/domain/model/Machine.kt)

Operational view of a machine used by the energy / shop-floor screens. Fields: `id`, `code`, `name`, `location`, `plantId`, `machineType`, `ratedKw`, `loadFactor`, `loadFactorSource`, `criticality`, `expectedRunHours`, `currentStatus` (`MachineStatus`), `updatedAt`. The `ratedKw * loadFactor` pair drives kWh estimation for production runs.

### MtMachine

**File:** [MtMachine.kt](../app/src/main/java/com/candorfoods/factoryops/domain/model/MtMachine.kt)

Lightweight machine-master row sourced from the maintenance MT (master) backend. Fields: `machineId`, `machineName`, `floor`, `ratedKw?`, `machineType`, `category`. Used as a filterable directory rather than for live operational state.

### PmPlan

**File:** [PmPlan.kt](../app/src/main/java/com/candorfoods/factoryops/domain/model/PmPlan.kt)

A recurring preventive-maintenance plan binding a machine to a template with a trigger rule. Fields: `id`, `machineId`, `templateId`, `triggerType` (TIME/USAGE), `triggerInterval`, `lastCompletedAt`, `lastCompletedAtHours`, `nextDueAt`, `assignedTechnicianId`, `isActive`, `createdAt`. The scheduler reads `nextDueAt` (and run hours for USAGE plans) to decide when to generate a new `PmWorkOrder`.

### PmTemplate

**File:** [PmTemplate.kt](../app/src/main/java/com/candorfoods/factoryops/domain/model/PmTemplate.kt)

Reusable checklist definition curated by supervisors.
- `PmTemplate`: `id`, `name`, `machineType`, `description`, `estimatedDurationMinutes`, `defaultTradeType` (`TradeType`), `createdBy`, `createdAt`, `updatedAt`, `isActive`, `items: List<PmTemplateItem>`.
- `PmTemplateItem`: ordered checklist step — `id`, `templateId`, `orderIndex`, `title`, `description`, `expectedResult`, `requiresPhoto`, `requiresMeasurement`, optional `measurementUnit`/`measurementMin`/`measurementMax` for numeric inputs with pass-band validation.

### PmWorkOrder

**File:** [PmWorkOrder.kt](../app/src/main/java/com/candorfoods/factoryops/domain/model/PmWorkOrder.kt)

A concrete instance of a plan, snapshotted at generation time so template edits or deletions cannot retroactively alter a WO.
- `PmWorkOrder` fields include identity (`id`, `planId`, `machineId`, `templateId`), snapshots (`templateName`, `machineType`, `estimatedDurationMinutes`, `tradeType`), scheduling (`scheduledDate`, `generatedAt`, `status` `PmWoStatus`, `assignedTechnicianId/Name`), execution timestamps (`acknowledgedAt`, `startedAt`, `submittedAt`), supervisor review (`supervisorApprovedBy/Name/At`, `supervisorRejectedAt`, `supervisorRejectionNotes` — Phase 2C-2), QC review block (`qcAcknowledgedBy/Name/At`, `qcDecidedBy/Name/At`, `qcDecisionNotes`, `qcAfterPhotoLocalPath`, `qcChecklistJson` — Phase 2C-3), closure (`closedAt`, `finalNotes`), and `escalationTier: Int` (0..4, Phase 2C-4). It carries nested `taskLogs` and `spares` lists.
- `PmWoTaskLog`: a snapshotted-then-executed checklist row. Holds template-item snapshot fields plus execution data (`status` `PmTaskLogStatus`, `measurementValue`, `photoLocalPath`, `notes`, `completedAt`, `completedBy`).
- `PmWoSpare`: a spare-part consumption record (`id`, `woId`, `spareName`, `quantityUsed`, `unit`, `notes`, `loggedAt`, `loggedBy`).

### ProductionRun

**File:** [ProductionRun.kt](../app/src/main/java/com/candorfoods/factoryops/domain/model/ProductionRun.kt)

A single start-to-stop machine run used as the unit of power-consumption logging. Fields: `id`, `machineId`, `operatorId`, `startedAt`, `scheduledEndAt`, `endedAt?`, `computedKwh?`, `syncStatus`, `flagStatus?`, `createdAt`, `updatedAt`. Convenience derivations: `isRunning` (no `endedAt`) and `runHours` (computed from start/end timestamps, in hours).

### Result

**File:** [Result.kt](../app/src/main/java/com/candorfoods/factoryops/domain/model/Result.kt)

Sealed `Result<out T>` with `Success<T>(data)`, `Error(message, exception?)`, and `Loading`. Exists so the domain layer can model success/failure/in-flight uniformly without leaking exceptions across boundaries — call sites pattern-match instead of try/catch. Two inline helpers `onSuccess` and `onError` enable fluent chaining.

### SyncQueueItem

**File:** [SyncQueueItem.kt](../app/src/main/java/com/candorfoods/factoryops/domain/model/SyncQueueItem.kt)

A queued offline action waiting to be replayed against the backend. Fields: `id`, `actionType` (`ActionType` — the dispatcher key), `payloadJson` (opaque serialized body), `retryCount`, `lastErrorMessage?`, `createdAt`.

### User

**File:** [User.kt](../app/src/main/java/com/candorfoods/factoryops/domain/model/User.kt)

Authenticated principal: `id`, `name`, `username`, `role` (`UserRole`), `plantId`, `jwtToken`, `tokenExpiresAt`. Plant-scoping for all flows reads from `plantId`; role gates which screens and actions are available.

## Repository Interfaces

### AuthRepository

**File:** [AuthRepository.kt](../app/src/main/java/com/candorfoods/factoryops/domain/repository/AuthRepository.kt)

Authentication contract.
- `suspend login(username, password): Result<User>`
- `suspend logout()`
- `getCurrentUser(): Flow<User?>` — reactive session.
- `isLoggedIn(): Boolean` — synchronous probe (used by splash/router).

### BreakdownRepository

**File:** [BreakdownRepository.kt](../app/src/main/java/com/candorfoods/factoryops/domain/repository/BreakdownRepository.kt)

Full breakdown-flag lifecycle and QC inbox.
- Raise: `raiseBreakdownFlag(machineId, severity, description, beforePhotoLocalPath)`.
- Observers: open by plant, acknowledged by user, resolved by plant since timestamp, by id, active for machine, latest for machine.
- QC inbox observers: `observePendingQcByPlant`, `observeQcAcknowledgedByUser`, `observeQcDoneByUserSince`.
- Technician actions: `acknowledgeFlag`, `resolveFlag(parts, photo)`.
- QC actions: `qcAcknowledge`, `qcOverrideAcknowledge` (QC head takeover), `qcApprove(checklist, photo, notes)`, `qcDisapprove(checklist, photo, reason)`.
- Notifications/sync helpers: `getNewOpenFlagsSince`, `getPendingBreakdowns`, `getSyncQueue`.
- `seedMockOpenFlagsIfEmpty()` — seeds demo data on first launch.

### EnergyRepository

**File:** [EnergyRepository.kt](../app/src/main/java/com/candorfoods/factoryops/domain/repository/EnergyRepository.kt)

Production-run lifecycle and offline sync.
- `startProductionRun(machineId, scheduledEndAt)`, `stopProductionRun(runId, endedAt)`.
- `getTodaysRuns(machineId)`, `getActiveRunForMachine(machineId)`, `getMachineHistory(machineId, fromDate, toDate)`.
- `getPendingSyncCount(): Flow<Int>`, `syncPendingRuns(): Result<Unit>`.

### MachineRepository

**File:** [MachineRepository.kt](../app/src/main/java/com/candorfoods/factoryops/domain/repository/MachineRepository.kt)

Operational machine catalogue.
- `getAssignedMachines(): Flow<Result<List<Machine>>>` (operator-scoped).
- `getMachineById(machineId): Flow<Machine?>`, `observeAllMachines()`.
- `syncMachines(): Result<Unit>`, `updateMachineStatus(machineId, status)`.

### MtMachineRepository

**File:** [MtMachineRepository.kt](../app/src/main/java/com/candorfoods/factoryops/domain/repository/MtMachineRepository.kt)

Machine-master directory.
- `suspend list(floor?, type?, category?): Result<List<MtMachine>>` — optional filters applied server-side or in-memory.

### PmPlanRepository

**File:** [PmPlanRepository.kt](../app/src/main/java/com/candorfoods/factoryops/domain/repository/PmPlanRepository.kt)

CRUD/observers for `PmPlan`.
- Observers: `observeAllPlans`, `observeActivePlans`, `observePlanById`, `observePlansForMachine`.
- One-shot reads: `getPlanById`, `getActivePlans`.
- Writes: `savePlan(plan)`, `updateLastCompleted(planId, ts, hours, nextDueAt)` (called when a WO closes).
- `seedMockIfEmpty(seedPlans)` for demo bootstrapping.

### PmTemplateRepository

**File:** [PmTemplateRepository.kt](../app/src/main/java/com/candorfoods/factoryops/domain/repository/PmTemplateRepository.kt)

Supervisor-managed templates with items.
- Observers: `observeActiveTemplates`, `observeByMachineType(machineType)`, `observeTemplateById`.
- `getTemplateById` for one-shot reads.
- `saveTemplate(template, items)` — creates or updates a template and **replaces all items in one transaction** (used by the supervisor editor).
- `seedMockIfEmpty(seedTemplates)`.

### PmWorkOrderRepository

**File:** [PmWorkOrderRepository.kt](../app/src/main/java/com/candorfoods/factoryops/domain/repository/PmWorkOrderRepository.kt)

The largest contract; it owns the full PM WO state machine.
- Observers/reads: `observeAll`, `observeForTechnician(techId, statuses)`, `observeByStatus`, `observeById`, `getById`, `findOpenForPlan`, `getAllNonClosed`.
- Generation: `insertGeneratedWo(wo)` — inserts a newly generated WO with snapshotted task logs (called by the scheduler when a plan trips its trigger).
- State writes: `updateStatusAndTier`, `updateEscalationTier`.
- Phase 2C-2 execution + supervisor approval: `acknowledge`, `start`, `logTaskResult(...)`, `logSpare(...)`, `deleteSpare`, `submit(finalNotes)`, `supervisorApprove`, `supervisorReject(notes)`.
- `suggestSpareNames(query, limit)` — Q3 autocomplete ordered by historical usage frequency.
- Phase 2C-3 QC inbox/actions: `observePendingQc`, `observeQcAcknowledgedByUser`, `observeQcDoneByUserSince`; `qcAcknowledge`, `qcOverrideAcknowledge` (QC head takeover). `qcApprove(...)` is terminal — flips WO to `QC_APPROVED` and machine to `IDLE`. `qcDisapprove(...)` is *not* terminal — it bounces the WO back to the supervisor's Approvals tab (status returns to `SUBMITTED`, machine stays `PENDING_QC`, supervisor sees a "QC sent this back" banner driven by `qcDecidedAt`).
- Phase 2C-4: `bumpTier(woId, newTier)` — called by the scheduler when the escalation tier changes (driven by `ComputePmEscalationTierUseCase`).

## Use Cases

### GetCurrentUserUseCase

**File:** [GetCurrentUserUseCase.kt](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/auth/GetCurrentUserUseCase.kt)

`invoke(): Flow<User?>` — passthrough to `AuthRepository.getCurrentUser()`. Used by the router/splash to observe the active session.

### LoginUseCase

**File:** [LoginUseCase.kt](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/auth/LoginUseCase.kt)

`suspend invoke(username, password): Result<User>`. Rejects blank inputs with `Result.Error` before calling `AuthRepository.login`, keeping validation in the domain rather than the VM.

### LogoutUseCase

**File:** [LogoutUseCase.kt](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/auth/LogoutUseCase.kt)

`suspend invoke() = authRepository.logout()`. Thin wrapper for DI/testability symmetry.

### AcknowledgeBreakdownUseCase

**File:** [AcknowledgeBreakdownUseCase.kt](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/breakdown/AcknowledgeBreakdownUseCase.kt)

`suspend invoke(flagId, userId, userName): Result<Unit>` — technician picks up an open flag.

### RaiseBreakdownFlagUseCase

**File:** [RaiseBreakdownFlagUseCase.kt](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/breakdown/RaiseBreakdownFlagUseCase.kt)

`suspend invoke(machineId, severity, description, beforePhotoLocalPath): Result<BreakdownFlag>`. Enforces non-blank `description` before delegating to the repository.

### ResolveBreakdownUseCase

**File:** [ResolveBreakdownUseCase.kt](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/breakdown/ResolveBreakdownUseCase.kt)

`suspend invoke(flagId, userId, userName, partsUsed, afterPhotoLocalPath): Result<Unit>`. Trims and validates: `partsUsed` must be at least 4 chars, an after-repair photo is mandatory. Enforcing these rules in the use case keeps the QC handoff trustworthy regardless of the UI surface.

### GetMachineHistoryUseCase

**File:** [GetMachineHistoryUseCase.kt](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/energy/GetMachineHistoryUseCase.kt)

`invoke(machineId, fromDate, toDate): Flow<Result<List<ProductionRun>>>` — reactive date-window history.

### GetTodaysRunsUseCase

**File:** [GetTodaysRunsUseCase.kt](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/energy/GetTodaysRunsUseCase.kt)

`invoke(machineId): Flow<List<ProductionRun>>` — today's runs for the machine dashboard.

### StartProductionRunUseCase

**File:** [StartProductionRunUseCase.kt](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/energy/StartProductionRunUseCase.kt)

`suspend invoke(machineId, expectedRunHours): Result<ProductionRun>`. Computes `scheduledEndAt = now + expectedRunHours * 3_600_000` so the repository doesn't need to know about wall-clock arithmetic.

### StopProductionRunUseCase

**File:** [StopProductionRunUseCase.kt](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/energy/StopProductionRunUseCase.kt)

`suspend invoke(runId): Result<ProductionRun>`. Stamps `System.currentTimeMillis()` as `endedAt`.

### GetAssignedMachinesUseCase

**File:** [GetAssignedMachinesUseCase.kt](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/machine/GetAssignedMachinesUseCase.kt)

`invoke(): Flow<Result<List<Machine>>>` — passthrough for operator-scoped machine list.

### GetMachineMasterUseCase

**File:** [GetMachineMasterUseCase.kt](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/mtmachine/GetMachineMasterUseCase.kt)

`suspend invoke(floor?, type?, category?): Result<List<MtMachine>>` — filterable machine-master directory.

### ComputePmEscalationTierUseCase

**File:** [ComputePmEscalationTierUseCase.kt](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/pm/ComputePmEscalationTierUseCase.kt)

Pure function `invoke(scheduledDate, now): Int` returning the escalation tier (0..4) a PM WO should currently sit at. Computes `daysOverdue` in whole days and bucket-matches against `Constants.PM_TIER_1_DAYS` … `PM_TIER_4_DAYS`; tier 0 means on-time or early. The scheduler calls this to drive `PmWorkOrderRepository.bumpTier`, decoupling the (testable) rule from the (impure) timer.

### GetPendingSyncCountUseCase

**File:** [GetPendingSyncCountUseCase.kt](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/sync/GetPendingSyncCountUseCase.kt)

`invoke(): Flow<Int>` — exposes pending offline-sync count for header/badge UI; backed by `EnergyRepository.getPendingSyncCount()`.
