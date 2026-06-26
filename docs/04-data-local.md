# Data — Local (Room + DataStore)

FactoryOps is an **offline-first** Android app: Room is the single source of truth for all
domain data, and every write goes to Room **first** before WorkManager picks up an enqueued
`SyncQueueEntity` and ships it to the backend. UI screens consume Room via `Flow<…>` queries,
so any successful local write — including an optimistic update from a background sync — is
reflected in the UI immediately.

Authentication tokens and a handful of small per-session scalars live separately in
`EncryptedSharedPreferences` (wrapped by [`AuthPreferences`](#authpreferences)) so they
survive DB wipes and never appear in domain entities.

Current schema is **v4** with `fallbackToDestructiveMigration` — acceptable until the
Phase 3 production seed lands. No `TypeConverters` are registered; all columns map to
primitive SQLite types (`TEXT`, `INTEGER`, `REAL`). JSON payloads are stored as raw `String`
columns (e.g. `qcChecklistJson`, `payloadJson`) and parsed at the repository layer.

## AppDatabase

Source: [`app/src/main/java/com/candorfoods/factoryops/data/local/AppDatabase.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/AppDatabase.kt)

- `@Database(version = 4, exportSchema = false)`
- v3 → v4 (Phase 2C) adds the 6 PM tables and a `machineType` column on `machines`.
- Destructive migration is the chosen strategy; no `Migration` objects are registered.
- No `@TypeConverters` are attached.
- Entities registered (11 total):
  - `MachineEntity`
  - `ProductionRunEntity`
  - `UserEntity`
  - `SyncQueueEntity`
  - `BreakdownFlagEntity`
  - `PmTemplateEntity`
  - `PmTemplateItemEntity`
  - `PmPlanEntity`
  - `PmWorkOrderEntity`
  - `PmWoTaskLogEntity`
  - `PmWoSpareEntity`
- DAO accessors exposed:
  - `machineDao()`, `productionRunDao()`, `userDao()`, `syncQueueDao()`,
    `breakdownFlagDao()`, `pmTemplateDao()`, `pmPlanDao()`, `pmWorkOrderDao()`,
    `pmWoTaskLogDao()`, `pmWoSpareDao()`

## AuthPreferences

Source: [`app/src/main/java/com/candorfoods/factoryops/data/local/AuthPreferences.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/AuthPreferences.kt)

`@Singleton`, `@Inject`-constructed wrapper around `EncryptedSharedPreferences`. Despite the
file living under `data/local`, this is **not** Jetpack DataStore — it uses
`androidx.security.crypto`:

- Master key: `MasterKey.KeyScheme.AES256_GCM`
- Key encryption: `AES256_SIV` ; value encryption: `AES256_GCM`
- Backing file name from `Constants.AUTH_PREFS`

Persisted scalars (all keys defined in `util.Constants`):

| Method | Key | Purpose |
|---|---|---|
| `saveToken(String)` / `getToken(): String?` | `KEY_JWT_TOKEN` | Bearer JWT for API calls |
| `saveUserId(String)` / `getUserId(): String?` | `KEY_USER_ID` | Logged-in user id |
| `savePlantId(String)` / `getPlantId(): String?` | `KEY_PLANT_ID` | Active plant scope |
| `saveBreakdownLastSeenAt(Long)` / `getBreakdownLastSeenAt(): Long` | `KEY_BREAKDOWN_LAST_SEEN` | High-water timestamp for new-breakdown notifications |

Helpers:

- `isLoggedIn(): Boolean` — `getToken() != null`
- `clear()` — wipes all encrypted prefs (used on logout)

## Entities

### MachineEntity

Source: [`entity/MachineEntity.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/entity/MachineEntity.kt)

Table `machines`. No indices declared; no FKs.

| Column | Type | Notes |
|---|---|---|
| `id` | `String` | `@PrimaryKey` |
| `code` | `String` | |
| `name` | `String` | |
| `location` | `String` | |
| `plantId` | `String` | Soft-FK to plant (no Room FK declared) |
| `machineType` | `String` | Added in v4 (Phase 2C); drives PM-template scoping |
| `ratedKw` | `Double` | |
| `loadFactor` | `Double` | |
| `loadFactorSource` | `String` | |
| `criticality` | `String` | |
| `expectedRunHours` | `Double` | |
| `currentStatus` | `String` | RUNNING / IDLE / PENDING_QC / DOWN etc. |
| `updatedAt` | `Long` | epoch millis |

### ProductionRunEntity

Source: [`entity/ProductionRunEntity.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/entity/ProductionRunEntity.kt)

Table `production_runs`. No FKs declared.

| Column | Type | Notes |
|---|---|---|
| `id` | `String` | `@PrimaryKey` |
| `machineId` | `String` | Soft-FK to `machines.id` |
| `operatorId` | `String` | Soft-FK to `users.id` |
| `startedAt` | `Long` | epoch millis |
| `scheduledEndAt` | `Long` | |
| `endedAt` | `Long?` | `NULL` while the run is active |
| `computedKwh` | `Double?` | Filled on stop |
| `syncStatus` | `String` | PENDING / SYNCED / FAILED |
| `flagStatus` | `String?` | Linked breakdown state, if any |
| `createdAt` | `Long` | |
| `updatedAt` | `Long` | |

### UserEntity

Source: [`entity/UserEntity.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/entity/UserEntity.kt)

Table `users`. Single-row table in practice (`SELECT * FROM users LIMIT 1`).

| Column | Type | Notes |
|---|---|---|
| `id` | `String` | `@PrimaryKey` |
| `name` | `String` | |
| `username` | `String` | |
| `role` | `String` | OPERATOR / TECHNICIAN / SUPERVISOR / QC / QC_HEAD etc. |
| `plantId` | `String` | |
| `jwtToken` | `String` | Mirror of the token in `AuthPreferences` |
| `tokenExpiresAt` | `Long` | epoch millis |

### SyncQueueEntity

Source: [`entity/SyncQueueEntity.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/entity/SyncQueueEntity.kt)

Table `sync_queue`. The central outbox consumed by WorkManager workers.

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` | `@PrimaryKey(autoGenerate = true)`, default `0` |
| `actionType` | `String` | Discriminator, e.g. `START_RUN`, `RESOLVE_FLAG`, `QC_APPROVE_WO` |
| `payloadJson` | `String` | Serialized DTO for the sync worker |
| `retryCount` | `Int` | default `0` |
| `lastErrorMessage` | `String?` | |
| `createdAt` | `Long` | FIFO ordering key |

### BreakdownFlagEntity

Source: [`entity/BreakdownFlagEntity.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/entity/BreakdownFlagEntity.kt)

Table `breakdown_flags`. Tracks the full lifecycle of a maintenance ticket through
acknowledge → resolve → QC.

| Column | Type | Notes |
|---|---|---|
| `id` | `String` | `@PrimaryKey` |
| `machineId` | `String` | Soft-FK to `machines.id` |
| `operatorId` | `String` | Raiser |
| `severity` | `String` | |
| `description` | `String` | |
| `beforePhotoLocalPath` | `String?` | Local file URI prior to upload |
| `raisedAt` | `Long` | |
| `status` | `String` | OPEN / ACKNOWLEDGED / PENDING_QC / REOPENED / QC_APPROVED |
| `acknowledgedBy` / `acknowledgedByName` / `acknowledgedAt` | `String?` / `String?` / `Long?` | Tech who picked it up |
| `resolvedBy` / `resolvedByName` / `resolvedAt` | `String?` / `String?` / `Long?` | Tech who closed it out |
| `partsUsed` | `String?` | Free-text summary of spares |
| `afterPhotoLocalPath` | `String?` | |
| `qcStatus` | `String?` | default `null` — PENDING / APPROVED / DISAPPROVED |
| `qcAcknowledgedBy` / `qcAcknowledgedByName` / `qcAcknowledgedAt` | `String?` / `String?` / `Long?` | default `null` |
| `qcDecidedBy` / `qcDecidedByName` / `qcDecidedAt` | `String?` / `String?` / `Long?` | default `null` |
| `qcDecisionNotes` | `String?` | default `null` |
| `qcAfterPhotoLocalPath` | `String?` | default `null` |
| `qcChecklistJson` | `String?` | default `null` — raw JSON, parsed in repo |
| `qcOverrideFromUserId` / `qcOverrideFromName` / `qcOverrideAt` | `String?` / `String?` / `Long?` | default `null` — QC_HEAD takeover audit |
| `syncStatus` | `String` | PENDING / SYNCED / FAILED |

### PmTemplateEntity / PmTemplateItemEntity

Source: [`entity/PmTemplateEntity.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/entity/PmTemplateEntity.kt)

Two entities in one file. No FKs declared (relationship via `templateId` column).

**`pm_templates`**

| Column | Type | Notes |
|---|---|---|
| `id` | `String` | `@PrimaryKey` |
| `name` | `String` | |
| `machineType` | `String` | Scope filter — matched against `machines.machineType` |
| `description` | `String` | |
| `estimatedDurationMinutes` | `Int` | |
| `defaultTradeType` | `String` | |
| `createdBy` | `String` | |
| `createdAt` | `Long` | |
| `updatedAt` | `Long` | |
| `isActive` | `Boolean` | |
| `syncStatus` | `String` | |

**`pm_template_items`**

| Column | Type | Notes |
|---|---|---|
| `id` | `String` | `@PrimaryKey` |
| `templateId` | `String` | Soft-FK to `pm_templates.id` |
| `orderIndex` | `Int` | UI/DAO sort key |
| `title` | `String` | |
| `description` | `String` | |
| `expectedResult` | `String` | |
| `requiresPhoto` | `Boolean` | |
| `requiresMeasurement` | `Boolean` | |
| `measurementUnit` | `String?` | |
| `measurementMin` | `Double?` | |
| `measurementMax` | `Double?` | |
| `syncStatus` | `String` | |

### PmPlanEntity

Source: [`entity/PmPlanEntity.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/entity/PmPlanEntity.kt)

Table `pm_plans`. Recurring PM schedule (one row per machine × template).

| Column | Type | Notes |
|---|---|---|
| `id` | `String` | `@PrimaryKey` |
| `machineId` | `String` | Soft-FK |
| `templateId` | `String` | Soft-FK |
| `triggerType` | `String` | TIME / METER |
| `triggerInterval` | `Int` | Days or hours, per `triggerType` |
| `lastCompletedAt` | `Long?` | |
| `lastCompletedAtHours` | `Double?` | Meter reading at last completion (meter-based) |
| `nextDueAt` | `Long` | Sort key for "Active plans" |
| `assignedTechnicianId` | `String` | |
| `isActive` | `Boolean` | |
| `createdAt` | `Long` | |
| `syncStatus` | `String` | |

### PmWorkOrderEntity

Source: [`entity/PmWorkOrderEntity.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/entity/PmWorkOrderEntity.kt)

Table `pm_work_orders`. Materialised instance of a PM plan; mirrors much of the
breakdown-flag QC vocabulary because PM WOs go through the same QC pipeline.

| Column | Type | Notes |
|---|---|---|
| `id` | `String` | `@PrimaryKey` |
| `planId` | `String` | Soft-FK to `pm_plans.id` |
| `machineId` | `String` | Soft-FK |
| `templateId` | `String` | Soft-FK |
| `templateName` | `String` | Denormalised for offline rendering |
| `machineType` | `String` | Denormalised |
| `estimatedDurationMinutes` | `Int` | |
| `tradeType` | `String` | |
| `scheduledDate` | `Long` | Primary sort key |
| `generatedAt` | `Long` | |
| `status` | `String` | SCHEDULED / ACKNOWLEDGED / IN_PROGRESS / SUBMITTED / PENDING_QC / QC_APPROVED / CLOSED / CANCELLED |
| `assignedTechnicianId` | `String` | |
| `assignedTechnicianName` | `String` | Denormalised |
| `acknowledgedAt` | `Long?` | |
| `startedAt` | `Long?` | |
| `submittedAt` | `Long?` | |
| `supervisorApprovedBy` / `supervisorApprovedByName` / `supervisorApprovedAt` | `String?` / `String?` / `Long?` | |
| `supervisorRejectedAt` | `Long?` | |
| `supervisorRejectionNotes` | `String?` | |
| `qcAcknowledgedBy` / `qcAcknowledgedByName` / `qcAcknowledgedAt` | `String?` / `String?` / `Long?` | |
| `qcDecidedBy` / `qcDecidedByName` / `qcDecidedAt` | `String?` / `String?` / `Long?` | |
| `qcDecisionNotes` | `String?` | |
| `qcAfterPhotoLocalPath` | `String?` | |
| `qcChecklistJson` | `String?` | Raw JSON |
| `closedAt` | `Long?` | |
| `finalNotes` | `String?` | |
| `escalationTier` | `Int` | 0 / 1 / 2 escalation rungs |
| `syncStatus` | `String` | |

### PmWoTaskLogEntity

Source: [`entity/PmWoTaskLogEntity.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/entity/PmWoTaskLogEntity.kt)

Table `pm_wo_task_logs`. One row per template item per work order; columns from
`PmTemplateItemEntity` are denormalised in so a WO is self-contained offline even if its
template later changes.

| Column | Type | Notes |
|---|---|---|
| `id` | `String` | `@PrimaryKey` |
| `woId` | `String` | Soft-FK to `pm_work_orders.id` |
| `templateItemId` | `String` | Soft-FK to `pm_template_items.id` |
| `orderIndex` | `Int` | Sort key |
| `title` | `String` | Denormalised |
| `description` | `String` | Denormalised |
| `expectedResult` | `String` | Denormalised |
| `requiresPhoto` | `Boolean` | Denormalised |
| `requiresMeasurement` | `Boolean` | Denormalised |
| `measurementUnit` | `String?` | Denormalised |
| `measurementMin` | `Double?` | Denormalised |
| `measurementMax` | `Double?` | Denormalised |
| `status` | `String` | PENDING / OK / NOT_OK / SKIPPED |
| `measurementValue` | `Double?` | |
| `photoLocalPath` | `String?` | |
| `notes` | `String?` | |
| `completedAt` | `Long?` | |
| `completedBy` | `String?` | |
| `syncStatus` | `String` | |

### PmWoSpareEntity

Source: [`entity/PmWoSpareEntity.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/entity/PmWoSpareEntity.kt)

Table `pm_wo_spares`. Free-form spare-parts log attached to a PM WO. The string-typed
`spareName` is intentional pre-inventory-migration; see `suggestSpareNames` for the
autocomplete that fights case-variant dupes.

| Column | Type | Notes |
|---|---|---|
| `id` | `String` | `@PrimaryKey` |
| `woId` | `String` | Soft-FK to `pm_work_orders.id` |
| `spareName` | `String` | Free text; deduped via autocomplete |
| `quantityUsed` | `Double` | |
| `unit` | `String` | |
| `notes` | `String?` | |
| `loggedAt` | `Long` | Sort key |
| `loggedBy` | `String` | |
| `syncStatus` | `String` | |

## DAOs

### MachineDao

Source: [`dao/MachineDao.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/dao/MachineDao.kt)

- **Read (Flow / observe)**
  - `getAllMachines(): Flow<List<MachineEntity>>` — `ORDER BY name ASC`
  - `getMachineById(machineId): Flow<MachineEntity?>`
- **Read (one-shot)**
  - `getMachineByIdOnce(machineId): MachineEntity?`
  - `count(): Int`
- **Write (insert)**
  - `insertAll(machines): suspend` — `OnConflictStrategy.REPLACE`
  - `insert(machine): suspend` — `OnConflictStrategy.REPLACE`
- **Write (update)**
  - `updateStatus(machineId, status, updatedAt)` — partial column update

### ProductionRunDao

Source: [`dao/ProductionRunDao.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/dao/ProductionRunDao.kt)

- **Read (Flow)**
  - `getTodaysRuns(machineId, startOfDay): Flow<List<…>>` — runs since day-start, DESC by `startedAt`
  - `getRunsInRange(machineId, fromDate, toDate): Flow<List<…>>`
  - `getActiveRunForMachine(machineId): Flow<ProductionRunEntity?>` — `endedAt IS NULL LIMIT 1`
  - `getPendingSyncCount(): Flow<Int>` — count where `syncStatus` in `PENDING` / `FAILED`
- **Read (one-shot)**
  - `getActiveRunForMachineOnce(machineId)`
  - `getRunById(runId)`
  - `getAllActiveRuns(): List<…>`
  - `getPendingRunsOnce(): List<…>` — drives sync worker
- **Write (insert)**
  - `insert(run)` — `OnConflictStrategy.REPLACE`
- **Write (update)**
  - `update(run)` — Room `@Update`
  - `stopRun(runId, endedAt, computedKwh, syncStatus, updatedAt)` — closes a run
  - `updateSyncStatus(runId, syncStatus, updatedAt)`
  - `updateFlagStatus(runId, flagStatus, updatedAt)`

### UserDao

Source: [`dao/UserDao.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/dao/UserDao.kt)

- **Read (Flow)**
  - `getCurrentUser(): Flow<UserEntity?>` — `SELECT * FROM users LIMIT 1`
- **Read (one-shot)**
  - `getCurrentUserOnce(): UserEntity?`
- **Write (insert)**
  - `insert(user)` — `OnConflictStrategy.REPLACE`
- **Delete**
  - `clearAll()` — `DELETE FROM users` (logout)

### SyncQueueDao

Source: [`dao/SyncQueueDao.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/dao/SyncQueueDao.kt)

- **Read (Flow)**
  - `getAll(): Flow<List<SyncQueueEntity>>` — `ORDER BY createdAt ASC` (FIFO)
  - `getCount(): Flow<Int>` — drives the sync-badge in the UI
- **Read (one-shot)**
  - `getAllOnce(): List<SyncQueueEntity>` — used by the WorkManager worker
- **Write (insert)**
  - `insert(item): Long` — returns auto-generated row id
- **Write (update)**
  - `update(item)` — Room `@Update`; used to bump `retryCount` / `lastErrorMessage`
- **Delete**
  - `deleteById(id)` — after a successful sync

### BreakdownFlagDao

Source: [`dao/BreakdownFlagDao.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/dao/BreakdownFlagDao.kt)

- **Read (Flow / observe)**
  - `getPendingFlags(): Flow<List<…>>` — `syncStatus != 'SYNCED'`
  - `observeOpenFlagsByPlant(plantId)` — `status = 'OPEN'` joined to `machines` by `plantId`
  - `observeAcknowledgedFlagsByUser(userId)` — Tech "My Active" tab: `status IN ('ACKNOWLEDGED','REOPENED')`
  - `observeResolvedFlagsByPlant(plantId, sinceMillis)` — Tech "Resolved" tab: `status='QC_APPROVED' AND qcDecidedAt >= :sinceMillis`
  - `observeFlagById(flagId): Flow<…?>`
  - `observeActiveFlagForMachine(machineId)` — most-recent flag not `QC_APPROVED`
  - `observeLatestFlagForMachine(machineId)` — most-recent flag regardless of status
  - `observePendingQcByPlant(plantId)` — "Awaiting QC": `status='PENDING_QC' AND qcAcknowledgedBy IS NULL`, FIFO
  - `observeQcAcknowledgedByUser(userId)` — QC "My Active"
  - `observeQcDoneByUserSince(userId, sinceMillis)` — QC "Done today"
- **Read (one-shot)**
  - `getPendingFlagsOnce(): List<…>`
  - `getFlagByIdOnce(flagId)`
  - `getNewOpenFlagsSince(plantId, sinceMillis): List<…>` — drives the breakdown-notification poller paired with `AuthPreferences.getBreakdownLastSeenAt()`
- **Write (insert)**
  - `insert(flag)` — `REPLACE`
  - `insertAllIfAbsent(flags)` — `IGNORE`, used by hydration to avoid clobbering local edits
- **Write (status transitions — all set `syncStatus`)**
  - `updateSyncStatus(flagId, syncStatus)`
  - `acknowledgeFlag(flagId, userId, userName, acknowledgedAt, syncStatus)` — sets `status='ACKNOWLEDGED'`
  - `resolveFlag(flagId, userId, userName, resolvedAt, partsUsed, afterPhotoPath, syncStatus)` — sets `status='PENDING_QC'` and **clears every `qc*` field** so REOPENED → re-resolve starts a fresh QC cycle (prior cycle audit is overwritten; backend will preserve full history in Phase 3)
  - `qcAcknowledge(flagId, userId, userName, ackedAt, syncStatus)` — sets `qcStatus='PENDING'`
  - `qcOverrideAcknowledge(flagId, newUserId, newUserName, ackedAt, syncStatus)` — QC_HEAD takeover; copies current `qcAcknowledgedBy/Name` into `qcOverrideFrom*` for audit
  - `qcApprove(flagId, userId, userName, decidedAt, notes, photoPath, checklistJson, syncStatus)` — sets `status='QC_APPROVED'`, `qcStatus='APPROVED'`
  - `qcDisapprove(flagId, userId, userName, decidedAt, reason, photoPath, checklistJson, syncStatus)` — sets `status='REOPENED'`, `qcStatus='DISAPPROVED'`

### PmTemplateDao

Source: [`dao/PmTemplateDao.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/dao/PmTemplateDao.kt)

Manages both `pm_templates` and `pm_template_items`.

- **Read (Flow)**
  - `observeActiveTemplates()` — `isActive = 1`, `ORDER BY name ASC`
  - `observeByMachineType(machineType)` — `isActive = 1`
  - `observeTemplateById(id): Flow<PmTemplateEntity?>`
  - `observeItems(templateId): Flow<List<PmTemplateItemEntity>>` — `ORDER BY orderIndex ASC`
- **Read (one-shot)**
  - `getTemplateById(id)`
  - `getItemsForTemplate(templateId)`
  - `count(): Int`
- **Write (insert)**
  - `upsertTemplate(template)`, `upsertTemplates(templates)`, `upsertItems(items)` — all `REPLACE`
- **Delete**
  - `deleteItemsForTemplate(templateId)`
- **Transactional**
  - `replaceTemplateWithItems(template, items)` — `@Transaction`: `upsertTemplate` → `deleteItemsForTemplate` → `upsertItems` so a template re-hydration is atomic and never leaves orphan items

### PmPlanDao

Source: [`dao/PmPlanDao.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/dao/PmPlanDao.kt)

- **Read (Flow)**
  - `observeActivePlans()` — `isActive = 1`, `ORDER BY nextDueAt ASC`
  - `observeAllPlans()` — `ORDER BY nextDueAt ASC`
  - `observePlanById(id): Flow<…?>`
  - `observePlansForMachine(machineId)` — `isActive = 1`
- **Read (one-shot)**
  - `getPlanById(id)`
  - `count(): Int`
- **Write (insert)**
  - `upsert(plan)`, `upsertAll(plans)` — `REPLACE`
- **Write (update)**
  - `updateLastCompleted(id, ts, hours, nextDueAt)` — bumps the schedule after a WO closes

### PmWorkOrderDao

Source: [`dao/PmWorkOrderDao.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/dao/PmWorkOrderDao.kt)

- **Read (Flow)**
  - `observeAll()` — `ORDER BY scheduledDate ASC`
  - `observeById(id): Flow<…?>`
  - `observeForTechnician(techId, statuses)` — parameterised `status IN (:statuses)`
  - `observeByStatus(status)`
  - `observePendingQc()` — QC inbox: `status='PENDING_QC' AND qcAcknowledgedBy IS NULL`
  - `observeQcAcknowledgedByUser(userId)`
  - `observeQcDoneByUserSince(userId, sinceMillis)`
- **Read (one-shot)**
  - `getById(id)`
  - `findOpenForPlan(planId, statuses)` — guards against double-generating a WO for the same plan
  - `getAllNonClosed()` — `status NOT IN ('CLOSED','CANCELLED','QC_APPROVED')`; drives escalation sweeps
  - `count(): Int`
- **Write (insert)**
  - `upsert(wo)`, `upsertAll(workOrders)` — `REPLACE`
- **Write (status transitions — all set `syncStatus = 'PENDING'`)**
  - `updateStatusAndTier(id, status, tier)`
  - `updateEscalationTier(id, tier)`
  - `markAcknowledged(id, ts)` — `status='ACKNOWLEDGED'`
  - `markStarted(id, ts)` — `status='IN_PROGRESS'`, uses `COALESCE(startedAt, :ts)` so the first start wins
  - `markSubmitted(id, ts, finalNotes)` — `status='SUBMITTED'`
  - `markSupervisorApproved(id, userId, userName, ts)` — **supervisor approval IS the QC handoff**: sets `status='PENDING_QC'` and clears every `supervisorRejected*` and `qc*` field so the WO appears fresh in the QC inbox even after a bounce
  - `markSupervisorRejected(id, ts, notes)` — reverts to `status='IN_PROGRESS'`, nulls `submittedAt`
  - `markQcAcknowledged(id, userId, userName, ts)`
  - `markQcOverrideAcknowledged(id, newUserId, newUserName, ts)` — QC_HEAD takeover (no `qcOverrideFrom*` audit columns on WO entity — only on `breakdown_flags`)
  - `markQcApproved(id, userId, userName, ts, notes, photoPath, checklistJson)` — `status='QC_APPROVED'`, also sets `closedAt = ts`
  - `markQcDisapproved(id, userId, userName, ts, reason, photoPath, checklistJson)` — `status='SUBMITTED'` and nulls `qcAcknowledgedBy/Name/At` so the rejected WO returns to the supervisor queue

### PmWoTaskLogDao

Source: [`dao/PmWoTaskLogDao.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/dao/PmWoTaskLogDao.kt)

- **Read (Flow)**
  - `observeForWo(woId): Flow<List<…>>` — `ORDER BY orderIndex ASC`
- **Read (one-shot)**
  - `getForWo(woId): List<…>`
  - `getById(id)`
- **Write (insert)**
  - `upsert(log)`, `upsertAll(logs)` — `REPLACE`
- **Write (update)**
  - `updateLogResult(id, status, measurementValue, photoLocalPath, notes, ts, userId)` — single transition that records the technician's outcome for one checklist item and hard-codes `syncStatus = 'PENDING'`
- **Delete**
  - `deleteForWo(woId)`

### PmWoSpareDao

Source: [`dao/PmWoSpareDao.kt`](../app/src/main/java/com/candorfoods/factoryops/data/local/dao/PmWoSpareDao.kt)

- **Read (Flow)**
  - `observeForWo(woId)` — `ORDER BY loggedAt ASC`
- **Read (one-shot)**
  - `getForWo(woId)`
  - `suggestSpareNames(query, limit): List<String>` — Phase 2C-2 autocomplete:

    ```sql
    SELECT spareName
    FROM pm_wo_spares
    WHERE :query = '' OR spareName LIKE '%' || :query || '%' COLLATE NOCASE
    GROUP BY spareName
    ORDER BY COUNT(*) DESC, spareName ASC
    LIMIT :limit
    ```

    The empty-`:query` short-circuit returns the global top-N; `GROUP BY spareName` collapses
    repeats and the `COUNT(*) DESC` ordering surfaces commonly-used parts first.
    `COLLATE NOCASE` on the `LIKE` matches case-insensitively to fight `"belt"` / `"Belt"` /
    `"v-belt"` duplicates that would otherwise haunt the Phase 3 inventory migration.
- **Write (insert)**
  - `upsert(spare)` — `REPLACE`
- **Delete**
  - `deleteById(id)`
  - `deleteForWo(woId)`
