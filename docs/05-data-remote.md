# Data — Remote (Retrofit APIs, DTOs, Mocks)

The remote layer defines the network contract between the FactoryOps Android app and its (future) backend. It is split into three concerns:

- **Retrofit API interfaces** declare endpoints, paths, HTTP methods, headers, and request/response DTOs.
- **DTOs** are `kotlinx.serialization` `@Serializable` data classes; field-level `@SerialName` annotations pin the wire-format JSON keys (snake_case) independent of Kotlin property names (camelCase).
- **Mock implementations** implement the same Retrofit interfaces and are the live data source during Phases 1–2C while `BuildConfig.USE_MOCK_API=true`. Real network APIs are not wired yet — that arrives in Phase 3. Each mock uses `kotlinx.coroutines.delay(...)` to simulate network latency and returns/consumes the exact same DTOs the real Retrofit clients will use, so swap-in is a DI binding change only.

A shared in-memory fixture file, `MockSeedData`, supplies stable seed rows (machines, breakdown flags, PM templates/plans, generated production-run history) that several mock APIs read from.

---

## API Interfaces

### AuthApi
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/api/AuthApi.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/api/AuthApi.kt)

| Method | Path | Request | Response |
|---|---|---|---|
| `POST` | `auth/login` | `LoginRequestDto` (body) | `LoginResponseDto` |

### BreakdownApi
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/api/BreakdownApi.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/api/BreakdownApi.kt)

All endpoints require an `Authorization` header.

| Method | Path | Request | Response |
|---|---|---|---|
| `POST` | `breakdowns/flag` | `BreakdownFlagRequestDto` | `BreakdownFlagResponseDto` |
| `POST` | `breakdowns/{id}/qc/acknowledge` | `QcAcknowledgeRequestDto` + path `id` (flag id) | `QcUpdateResponseDto` |
| `POST` | `breakdowns/{id}/qc/approve` | `QcDecisionRequestDto` + path `id` | `QcUpdateResponseDto` |
| `POST` | `breakdowns/{id}/qc/disapprove` | `QcDecisionRequestDto` + path `id` | `QcUpdateResponseDto` |

### EnergyApi
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/api/EnergyApi.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/api/EnergyApi.kt)

All endpoints require an `Authorization` header.

| Method | Path | Request | Response |
|---|---|---|---|
| `POST` | `energy/runs/start` | `RunStartRequestDto` | `RunStartResponseDto` |
| `POST` | `energy/runs/{runId}/stop` | `RunStopRequestDto` + path `runId` | `RunStopResponseDto` |
| `GET`  | `energy/machines/{machineId}/history` | path `machineId`, query `from: Long`, query `to: Long` | `MachineHistoryDto` |

### MachineApi
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/api/MachineApi.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/api/MachineApi.kt)

| Method | Path | Request | Response |
|---|---|---|---|
| `GET` | `machines/assigned` | `Authorization` header only | `List<MachineDto>` |

### MtMachineApi
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/api/MtMachineApi.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/api/MtMachineApi.kt)

| Method | Path | Request | Response |
|---|---|---|---|
| `GET` | `mt-machines` | `Authorization` header; optional query params `floor`, `machine_type`, `category` (all `String?`) | `List<MtMachineDto>` |

---

## DTOs

All DTOs are `kotlinx.serialization` `@Serializable` data classes. The `@SerialName` annotation maps the wire key (snake_case) to the Kotlin property (camelCase).

### AuthDtos.kt
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/dto/AuthDtos.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/dto/AuthDtos.kt)

- **`LoginRequestDto`** — `username: String`, `password: String`
- **`LoginResponseDto`**
  - `token: String`
  - `userId: String` ↔ `user_id`
  - `name: String`
  - `role: String`
  - `plantId: String` ↔ `plant_id`

### BreakdownDtos.kt
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/dto/BreakdownDtos.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/dto/BreakdownDtos.kt)

- **`BreakdownFlagRequestDto`**
  - `machineId: String` ↔ `machine_id`
  - `operatorId: String` ↔ `operator_id`
  - `severity: String`
  - `description: String`
  - `raisedAt: Long` ↔ `raised_at`
- **`BreakdownFlagResponseDto`**
  - `id: String`
  - `syncStatus: String` ↔ `sync_status`
- **`QcAcknowledgeRequestDto`**
  - `userId: String` ↔ `user_id`
  - `userName: String` ↔ `user_name`
  - `acknowledgedAt: Long` ↔ `acknowledged_at`
  - `override: Boolean = false` ↔ `override`
- **`QcDecisionRequestDto`**
  - `userId: String` ↔ `user_id`
  - `userName: String` ↔ `user_name`
  - `decidedAt: Long` ↔ `decided_at`
  - `checklistJson: String` ↔ `checklist_json`
  - `afterPhotoPath: String?` ↔ `after_photo_path`
  - `notes: String? = null`
  - `reason: String? = null`
- **`QcUpdateResponseDto`**
  - `id: String`
  - `ticketStatus: String` ↔ `ticket_status`
  - `machineStatus: String` ↔ `machine_status`
  - `qcStatus: String?` ↔ `qc_status`
  - `syncStatus: String` ↔ `sync_status`

### EnergyDtos.kt
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/dto/EnergyDtos.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/dto/EnergyDtos.kt)

- **`RunStartRequestDto`**
  - `machineId: String` ↔ `machine_id`
  - `startedAt: Long` ↔ `started_at`
  - `scheduledEndAt: Long` ↔ `scheduled_end_at`
- **`RunStartResponseDto`**
  - `runId: String` ↔ `run_id`
  - `machineId: String` ↔ `machine_id`
  - `operatorId: String` ↔ `operator_id`
  - `startedAt: Long` ↔ `started_at`
  - `scheduledEndAt: Long` ↔ `scheduled_end_at`
- **`RunStopRequestDto`**
  - `endedAt: Long` ↔ `ended_at`
- **`RunStopResponseDto`**
  - `runId: String` ↔ `run_id`
  - `endedAt: Long` ↔ `ended_at`
  - `computedKwh: Double` ↔ `computed_kwh`
- **`ProductionRunDto`**
  - `id: String`
  - `machineId: String` ↔ `machine_id`
  - `operatorId: String` ↔ `operator_id`
  - `startedAt: Long` ↔ `started_at`
  - `scheduledEndAt: Long` ↔ `scheduled_end_at`
  - `endedAt: Long?` ↔ `ended_at`
  - `computedKwh: Double?` ↔ `computed_kwh`
  - `syncStatus: String` ↔ `sync_status`
  - `flagStatus: String?` ↔ `flag_status`
- **`MachineHistoryDto`**
  - `runs: List<ProductionRunDto>`

### MachineDtos.kt
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/dto/MachineDtos.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/dto/MachineDtos.kt)

- **`MachineDto`**
  - `id: String`
  - `code: String`
  - `name: String`
  - `location: String`
  - `plantId: String` ↔ `plant_id`
  - `machineType: String = "OTHER"` ↔ `machine_type`
  - `ratedKw: Double` ↔ `rated_kw`
  - `loadFactor: Double` ↔ `load_factor`
  - `loadFactorSource: String` ↔ `load_factor_source`
  - `criticality: String`
  - `expectedRunHours: Double` ↔ `expected_run_hours`
  - `currentStatus: String` ↔ `current_status`
  - `updatedAt: Long` ↔ `updated_at`

### MtMachineDto.kt
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/dto/MtMachineDto.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/dto/MtMachineDto.kt)

- **`MtMachineDto`**
  - `machineId: String` ↔ `machine_id`
  - `machineName: String` ↔ `machine_name`
  - `floor: String` ↔ `floor`
  - `ratedKw: Double? = null` ↔ `rated_kw`
  - `machineType: String` ↔ `machine_type`
  - `category: String` ↔ `category`
- **`MtMachineDto.toDomain()`** — extension that maps the DTO to `domain.model.MtMachine`. Unknown `machineType` strings fall back to `MachineType.OTHER`; unknown `category` strings fall back to `MachineCategory.PRODUCTION` (both via `runCatching { enumValueOf(...) }.getOrDefault(...)`).

---

## Mock Implementations

### MockAuthApi
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/mock/MockAuthApi.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/mock/MockAuthApi.kt)

- **Latency:** `delay(600)` on `login`.
- **Persistence:** in-class `validUsers: Map<String, SeedUser>` — read-only; no state is mutated across calls.
- **Error injection:** throws `Exception("Invalid username or password")` if the username is missing or the password does not match.
- **Token shape:** returns `mock_jwt_<username>_<epochMillis>` (a per-call generated string, not a real JWT). `plantId` is hard-coded to `"PLANT-01"`.
- **Disclaimer (in source):** "Phase 2A dev-only seed. NOT for production — production seed will use anonymized fixtures."

### MockBreakdownApi
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/mock/MockBreakdownApi.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/mock/MockBreakdownApi.kt)

- **Latency:** `raiseFlag` → 400 ms; `qcAcknowledge` / `qcApprove` / `qcDisapprove` → 200 ms each.
- **Persistence:** none. The mock is **stateless** — every call synthesises a fresh response. `raiseFlag` mints a new `UUID.randomUUID()` for the flag id; QC endpoints echo back the path `flagId`.
- **Error injection:** none — all calls succeed.
- **Deterministic status transitions:**
  - `raiseFlag` → `syncStatus = "SYNCED"`.
  - `qcAcknowledge` → `ticketStatus = "PENDING_QC"`, `machineStatus = "PENDING_QC"`, `qcStatus = "PENDING"`.
  - `qcApprove` → `ticketStatus = "QC_APPROVED"`, `machineStatus = "IDLE"`, `qcStatus = "APPROVED"`.
  - `qcDisapprove` → `ticketStatus = "REOPENED"`, `machineStatus = "FLAGGED"`, `qcStatus = "DISAPPROVED"`.

### MockEnergyApi
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/mock/MockEnergyApi.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/mock/MockEnergyApi.kt)

- **Latency:** `startRun` → 300 ms; `stopRun` → 300 ms; `getMachineHistory` → 500 ms.
- **Persistence:** none for runs (each `startRun` mints a fresh `UUID`; `operatorId` is hard-coded `"user-001"`). History is proxied to `MockSeedData.generateHistoryRuns(...)` and then filtered by the request `[from, to]` window.
- **Error injection:** none.
- **Notable:** `stopRun` always reports `computedKwh = 87.5` regardless of duration — fixed placeholder for the UI.

### MockMachineApi
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/mock/MockMachineApi.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/mock/MockMachineApi.kt)

- **Latency:** 400 ms.
- **Persistence:** read-only proxy to `MockSeedData.machines` (the same 7-machine list on every call).
- **Error injection:** none.

### MockMtMachineApi
File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/mock/MockMtMachineApi.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/mock/MockMtMachineApi.kt)

- **Latency:** 400 ms.
- **Persistence:** in-class private `rows: List<MtMachineDto>` of 6 hand-crafted rows. Filters are applied in-memory by `floor` (case-insensitive equality), `machineType` (exact equality), and `category` (exact equality); a `null` filter passes through.
- **Error injection:** none.
- **Fixture intent (from source):** 6 rows engineered to exercise the screen's color buckets, null-kW handling, and both categories: 4 `PRODUCTION` (one per color bucket — production, utility, handling, other) + 2 `OTHERS` (tube light, fly catcher).

| machineId | machineName | floor | ratedKw | machineType | category |
|---|---|---|---|---|---|
| MCH-M01 | shrink wrap packaging machine | Lower basement | 0.5 | PACKING_LINE | PRODUCTION |
| MCH-M02 | screw compressor | Service floor | 22.0 | COMPRESSOR | PRODUCTION |
| MCH-M03 | belt conveyor | 1st floor | 0.18 | CONVEYOR | PRODUCTION |
| MCH-M04 | metal detector | Lower basement | `null` | OTHER | PRODUCTION |
| MCH-M05 | tube light | Lower basement | 0.04 | OTHER | OTHERS |
| MCH-M06 | fly catcher | Service floor | 0.03 | OTHER | OTHERS |

---

## Mock Seed Data

File: [`../app/src/main/java/com/candorfoods/factoryops/data/remote/mock/MockSeedData.kt`](../app/src/main/java/com/candorfoods/factoryops/data/remote/mock/MockSeedData.kt)

`MockSeedData` is a Kotlin `object` (singleton) shared by all mocks. It also exposes seed rows consumed at DB-init time (e.g., `breakdownFlags`, `pmTemplatesWithItems`, `pmPlans` are inserted into Room on first launch).

### Seeded users (in `MockAuthApi.validUsers`)

All users have password `pass123` and belong to `plantId = "PLANT-01"`.

| Username | User ID | Name | Role |
|---|---|---|---|
| `operator1` | user-001 | Rajan Kumar | OPERATOR |
| `technician1` | user-002 | Priya Sharma | TECHNICIAN |
| `pooja.parkar` | user-qc-001 | Pooja Parkar | QC_HEAD |
| `pooja.mhalim` | user-qc-002 | Pooja Mhalim | QC_MEMBER |
| `shraddha.jadhav` | user-qc-003 | Shraddha Jadhav | QC_MEMBER |
| `supervisor1` | user-sup-001 | Shubham Lohar | SUPERVISOR |

Two named constants in `MockSeedData` reference this set: `SUPERVISOR_USER_ID = "user-sup-001"` (template authoring) and `TECHNICIAN_USER_ID = "user-002"` (PM-plan assignee).

### `machines: List<MachineDto>` — 7 machines, all `plantId = "PLANT-01"`, all `currentStatus = "IDLE"`

| ID | Code | Name | Location | Type | ratedKw | loadFactor | source | crit | expRunHrs |
|---|---|---|---|---|---|---|---|---|---|
| mach-001 | FLR1-ROAST-01 | Roaster Unit 1 | Floor 1 – Section A | ROASTER | 15.0 | 0.75 | SPOT_MEASURED | A | 8.0 |
| mach-002 | FLR1-PACK-01 | Packaging Machine 1 | Floor 1 – Section B | PACKING_LINE | 5.0 | 0.6 | ASSUMED | B | 8.0 |
| mach-003 | FLR2-GRIND-01 | Grinder Unit 1 | Floor 2 – Section A | OTHER | 22.0 | 0.8 | SPOT_MEASURED | A | 8.0 |
| mach-004 | FLR2-COOL-01 | Cooling Unit 1 | Floor 2 – Section B | HVAC | 8.0 | 0.65 | ASSUMED | B | 6.0 |
| mach-005 | WH1-CONV-01 | Conveyor Belt 1 | Warehouse 1 | CONVEYOR | 3.0 | 0.5 | ASSUMED | C | 8.0 |
| mach-006 | FLR2-COMP-01 | Compressor 1 | Floor 2 – Utility Room | COMPRESSOR | 11.0 | 0.7 | ASSUMED | A | 16.0 |
| mach-007 | FLR1-MIX-01 | Mixer 1 | Floor 1 – Section C | MIXER | 7.5 | 0.6 | ASSUMED | B | 8.0 |

`updatedAt` is set to `System.currentTimeMillis()` at object init time.

### `breakdownFlags: List<BreakdownFlagEntity>` — 2 seed flags, both `status = "OPEN"`, `syncStatus = "SYNCED"`, no acknowledger/resolver/photos

| ID | Machine | Operator | Severity | raisedAt offset | Description |
|---|---|---|---|---|---|
| seed-flag-001 | mach-002 | user-operator-1 | MAJOR | now − 12 min | Excessive vibration in packaging head; sealing inconsistent on pouches. |
| seed-flag-002 | mach-004 | user-operator-1 | CRITICAL | now − 3 min | Coolant leak under unit, smell of burnt belt. Stopped immediately. |

### `generateHistoryRuns(machineId, operatorId): List<ProductionRunDto>` — synthetic 30-day history

For each of the last 30 days (`daysAgo ∈ 1..30`), a single run is produced:

- `startedAt = (now − daysAgo·86 400 000) + 8 h` (08:00 of that day-anchor).
- `scheduledEndAt = startedAt + 8 h`.
- `endedAt = startedAt + 7 h 45 min`.
- `computedKwh = 15.0 × 7.75 × 0.75 ≈ 87.19` (constant for every run, regardless of `machineId`).
- `id = "run-hist-<machineId>-<daysAgo>"`, `syncStatus = "SYNCED"`, `flagStatus = null`.

The list is generated on demand per call (not cached) and is then filtered by the `[from, to]` window in `MockEnergyApi.getMachineHistory`.

### `pmTemplatesWithItems: List<Pair<PmTemplate, List<PmTemplateItem>>>` — 3 templates (Phase 2C PM seed)

All templates: `defaultTradeType = MECHANICAL`, `createdBy = "user-sup-001"`, `isActive = true`, `createdAt/updatedAt = now`.

| Template ID | Name | machineType | Est. duration (min) | # items |
|---|---|---|---|---|
| tmpl-roaster-monthly | Roaster monthly PM | ROASTER | 60 | 4 |
| tmpl-compressor-quarterly | Compressor quarterly PM | COMPRESSOR | 75 | 5 |
| tmpl-packing-monthly | Packing line monthly PM | PACKING_LINE | 45 | 3 |

Item IDs are stable strings (e.g. `item-roaster-1`, `item-compressor-3`, `item-packing-2`) so the rows survive destructive Room migrations without identity drift. Items mix `requiresPhoto = true` checks with `requiresMeasurement = true` checks; measurement items declare `measurementUnit` and `measurementMin/Max` bands (e.g. roaster sensor `±2 °C`, compressor pressure `6–9 bar`, packing seal `130–180 °C`, packing weighing `99.5–100.5 g`).

### `pmPlans: List<PmPlan>` — 3 plans, all `triggerType = TIME`, assigned to `user-002`, `isActive = true`

Computed to exercise the escalation-tier UI (notation: `DAY = 86_400_000 ms`).

| Plan ID | Machine | Template | interval (days) | lastCompletedAt | nextDueAt | Scenario |
|---|---|---|---|---|---|---|
| plan-roaster-001 | mach-001 | tmpl-roaster-monthly | 30 | now − 25 days | now + 5 days | Due in 5 days → WO auto-generation triggers |
| plan-packing-001 | mach-002 | tmpl-packing-monthly | 30 | now − 45 days | now − 15 days | Overdue by 15 days |
| plan-cooling-quarterly-001 | mach-004 | tmpl-compressor-quarterly | 90 | now − 60 days | now + 30 days | Mid-cycle |

`lastCompletedAtHours` is `null` on all three plans (time-triggered, not runtime-triggered). `createdAt` mirrors each plan's `lastCompletedAt`.
