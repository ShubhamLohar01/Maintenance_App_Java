# FactoryOps — Android ⇄ Backend Connection Map

Audited against the live Kotlin source on **2026-06-25** vs the backend API/DB contract.
This is the single page the Android/Kotlin team uses to wire every endpoint so that
**each required DB column actually gets filled**. Field names below are the real
`@SerialName` keys in the code; "Wiring" tells you whether the call is actually made.

> **Update — implemented 2026-06-25 (build green):**
> - **C-1 done:** the retired `POST /mt-machines/{id}/daily-kwh` path is fully deleted
>   (API + DTOs + repo + Hilt provider/binding + both call sites + `MACHINE_DAILY_KWH_UPSERT`
>   action + its test). Per-run kWh is recorded by the Stop flow only; runs are rows in
>   `mt_machine_daily_kwh` (the separate `mt_machine_runs` table is gone).
> - **`run_id`** is treated as an opaque `String` (now a numeric string like `"4217"`) — no UUID/prefix parsing.
> - **`MachineStatus.FLAGGED` removed.** Live status is now `RUNNING | PENDING_QC | IDLE`
>   (+ local `STOPPED`). Breakdown raise and QC-disapprove write `PENDING_QC`; a **missed-stop
>   keeps the machine `RUNNING`** (the run's `MISSED_STOP` flag + push notification raise the alert).
> - **`SUPERVISOR`** role parses/routes already; plant scoping (Supervisor + Head = both plants)
>   is server-side from the JWT, so the client sends no plant param.
>
> **Update — photos & transfers (build green):**
> - **C-3 done:** Machine Transfers client built — `MachineTransferApi` (`@Multipart POST` +
>   `GET`), DTOs, repo (`MachineTransferRepositoryImpl`), mock, DI, and `TransferFormViewModel.submit()`.
> - **Photo uploads = multipart file parts, never base64.** The repo streams the picked
>   `content://` uri's bytes via `ContentResolver` into a `proof_photo` part (validates
>   `image/jpeg`/`image/png`, ≤ 10 MB). **No AWS SDK / credentials on device** — the backend
>   owns S3 and returns `proof_photo_url`. Display is already Coil `AsyncImage`(the URL).
> - Still open: **C-2** (breakdown flag is now POSTed via `BreakdownApi.raiseFlag`, but QC
>   approve/ack/disapprove still write local Room only); **breakdown photos** still sent as a
>   local path in `after_photo_path` — awaiting backend contract (multipart file vs pre-uploaded
>   path) before wiring; **C-4** (no `/head/*` client).

---

## A. How the app connects (the transport layer)

| Concern | Where | Detail |
|---|---|---|
| Base URL | `BuildConfig.BASE_URL` → `NetworkModule.provideRetrofit()` | Single Retrofit, OkHttp + `TunnelColdStartRetryInterceptor` (retries 502/503/504 while the dev tunnel wakes) + body logging in DEBUG only. |
| Serializer | kotlinx.serialization | `Json { ignoreUnknownKeys = true; isLenient = true }`. Snake_case via `@SerialName`. **`encodeDefaults` is false** → request fields equal to their declared default are **omitted** from JSON. |
| Auth | **No interceptor.** | Every API method takes `@Header("Authorization") token: String`. Each repository must pass **`"Bearer $token"`** (verified: all current repos do). Token comes from `AuthPreferences`. |
| Mock switch | `BuildConfig.USE_MOCK_API` / `USE_MOCK_AUTH` | NetworkModule swaps each real API for a `Mock*Api()` when true. Production build = both false. |

**Date conventions (must match per section — the contract splits these):**
- **epoch milliseconds → `Long`**: `started_at, ended_at, raised_at, reported_at, acknowledged_at, decided_at, updated_at, expires_at` (operator + energy + breakdown endpoints).
- **`YYYY-MM-DD` → `String`**: `checklist_date, record_date, transfer date, reports from/to`.
- **ISO-8601 `…Z` → `String`**: `created_at, raised_at, resolved_at, run_started_at` in **`/head/*`** read views and checklist/transfer read-backs.

**THE #1 RULE — machine identity.** Everything energy/breakdown/kWh keys on a real
`mt_asset_list.asset_id` (e.g. `"A185-0001"`), **never** the legacy mock ids (`"mach-002"`).
The app already resolves real asset ids before `POST /energy/runs/start` and
`POST /breakdowns/flag` (via `MtMachineRepository`). Treat `GET /mt-machines` as the
asset register; `GET /machines/assigned` is the legacy dev table.

---

## B. Endpoint → Kotlin → DB-column map

Status legend: ✅ wired & matches · ⚠️ wired but needs a fix · 🔴 **not wired** (call never made) · ❌ **no client code yet**

### AUTH — `mt_users` (read)
| Endpoint | Kotlin | Request keys | Response keys | Wiring |
|---|---|---|---|---|
| `POST /auth/login` | `AuthApi.login` / `AuthDtos.kt` | `username, password` | `token, user_id, name, role, plant_id, expires_at` | ⚠️ `expiresAt` typed `String?` but backend sends epoch-ms **number**; survives only because `isLenient=true`, then re-parsed in `AuthRepositoryImpl`. Should be `Long?`. |

### ASSET REGISTER — `mt_asset_list` (read-only via API)
| Endpoint | Kotlin | Query / Body | Response DTO | Wiring |
|---|---|---|---|---|
| `GET /mt-machines` | `MtMachineApi.list` / `MtMachineDto.kt` | `building, sub_location, category` (wire names correct; app fetches all & filters client-side) | `asset_id, asset_name, building, sub_location, category, power_load, rated_kw, quantity, condition, assigned_to` | ✅ matches. **`model_no` & `serial_no` are in the contract but not declared** → silently dropped by `ignoreUnknownKeys`. Add them only if the UI needs them. |

### ENERGY / RUNS — `mt_machine_daily_kwh` (one row per run, RUNNING→COMPLETE)
| Endpoint | Kotlin | Request keys | Response keys | DB columns filled | Wiring |
|---|---|---|---|---|---|
| `POST /energy/runs/start` | `EnergyApi.startRun` / `EnergyDtos.kt` | `machine_id, client_run_id, started_at(ms), scheduled_end_at(ms)` | `run_id, client_run_id, started_at, scheduled_end_at` | start: `machine_id, reading_date, building, floor, client_run_id, operator_id/name, started_at, status='RUNNING'` | ✅ |
| `POST /energy/runs/{run_id}/stop` | `EnergyApi.stopRun` | `ended_at(ms)` | `run_id, ended_at, computed_kwh` | stop: `ended_at, daily_kwh, status='COMPLETE'` | ✅ (`run_id` is now a numeric string — kept as opaque `String`, fine) |
| `GET /energy/runs/active` | `EnergyApi.getActiveRuns` | — | `ActiveRunDto {asset_id, run_id, operator_id, operator_name, started_at(ms), building}` | — | ✅ (drives Head live list locally) |
| `GET /energy/machines/{id}/history` | `EnergyApi.getMachineHistory` | `from(ms), to(ms)` | `DailyHistoryDto {date, total_run_hours, total_kwh, estimated_cost, runs[]}` | — | ✅ |
| ~~`POST /mt-machines/{id}/daily-kwh`~~ | `MachineDailyKwhApi.upsert` | — | — | — | 🔴 **DEAD — endpoint deleted on backend; remove (see C-1).** |

### OPERATOR BREAKDOWNS / QC — `mt_breakdown_records` (`source='OPERATOR_FLAG'`)
| Endpoint | Kotlin | Request keys | Response keys | DB columns filled | Wiring |
|---|---|---|---|---|---|
| `POST /breakdowns/flag` | `BreakdownApi.raiseFlag` / `BreakdownDtos.kt` | `machine_id, severity, description, raised_at(ms), operator_id` | `id, sync_status` | `asset_id, machine_name, location, reported_by, severity, description, status='OPEN', created_by, source` | 🔴 **defined but never called — flags are saved to local Room only.** |
| `POST /breakdowns/{id}/qc/acknowledge` | `BreakdownApi.qcAcknowledge` | `user_id, user_name, acknowledged_at(ms), override?` | `id, ticket_status, machine_status, qc_status, sync_status` | `acknowledged_by/at` | 🔴 not called (repo writes Room: `db.breakdownFlagDao().qcAcknowledge`) |
| `POST /breakdowns/{id}/qc/approve` | `BreakdownApi.qcApprove` | `user_id, user_name, decided_at(ms), checklist_json, after_photo_path?, notes?, reason?` | same QcUpdateResponse | `qc_status='QC_APPROVED', qc_decided_by/at, qc_checklist_json, after_photo_url, resolved_by/at` | 🔴 not called |
| `POST /breakdowns/{id}/qc/disapprove` | `BreakdownApi.qcDisapprove` | same QcDecideRequest | same | `status='REOPENED'` | 🔴 not called |
| `GET /breakdowns/open` | — | `plant_id (W202\|A185\|both)` | `OpenBreakdownDto {id, asset_id, asset_name, reported_by, reporter_name, severity, description, status, reported_at(ms), building}` | — (read) | ❌ **no API method + no `OpenBreakdownDto`.** App reads only local Room → no cross-device breakdowns. |

> Field shapes of the four `BreakdownApi` DTOs are **already correct** (snake_case + epoch-ms). The gap is purely that nothing invokes them (see C-2). Minor: `BreakdownFlagRequestDto.operatorId` is non-null but the contract treats it as optional (JWT fallback) — harmless because the app always sends it.

### PREVENTIVE MAINTENANCE — `doc_preventive_maintenance` (JSONB `rows`)
| Endpoint | Kotlin | Body / keys | Wiring |
|---|---|---|---|
| `POST /preventive-maintenance/checklists` | `ChecklistApi.submitChecklist` / `ChecklistDtos.kt` | `form_type, doc_no, status, checklist_date, done_by, checked_by, verified_by, remarks, items[{section, equipment, sr_no?, equipment_date, checkpoint, status, remarks}]` → 201 `{id}` | ✅ |
| `PUT /preventive-maintenance/checklists/{id}` | `ChecklistApi.updateChecklist` | same body → `{id}` | ✅ |
| `GET /preventive-maintenance/checklists` | `ChecklistApi.listChecklists` | → `ChecklistRecordSummaryDto[]` | ✅ |
| `GET /preventive-maintenance/checklists/{id}` | `ChecklistApi.getChecklist` | → `ChecklistRecordDetailDto` (+items) | ✅ |

> Item `status` is a free `String`, so it can carry `OK / NOT_OK / UNSET` — the type is fine; just keep the UI from emitting `UNSET` on a **SUBMITTED** payload (backend 400s). The `// "OK"|"NOT_OK"` comment in `ChecklistDtos.kt` is stale — update to include `UNSET`.

### F06 BREAKDOWN SHEET — `mt_breakdown_records` (`source='F06_RECORD'`)
| Endpoint | Kotlin | Body | Wiring |
|---|---|---|---|
| `POST /preventive-maintenance/breakdowns` | `ChecklistApi.submitBreakdown` / `BreakdownSheetDto` | `doc_no, verified_by, entries[{record_date, location, machine_name, equipment_model_no, problem_in_brief, type_of_maintenance, part_of_machine, temporary_reason, duration_start, duration_end, machine_operator_sign, maintenance_person_sign, qc_clearance_sign}]` → 201 `{ids[]}` | ✅ `record_date` nullable/`ifBlank{null}` is **correct** — the contract says entry fields are lenient and empty `record_date` → NULL. |

### MACHINE TRANSFERS — `doc_machine_transfer` (+ S3 photo)
| Endpoint | Kotlin | Shape | Wiring |
|---|---|---|---|
| `POST /machine-transfers` | — | **multipart/form-data**: `from_warehouse*, to_warehouse*, machine_name*, date?, machine_code?, condition?, reason?, authorised_person?, remarks?, proof_photo?` → 201 `{id, proof_photo_url}` | ❌ **no client code** (see C-3) |
| `GET /machine-transfers` | — | `[{id, date, from_warehouse, to_warehouse, machine_name, condition, created_at(ISO-Z), proof_photo_url}]` | ❌ no client code |

### HEAD VIEWS — `/head/*` (HEAD sees both plants; timestamps are **ISO-Z strings**)
| Endpoint | Kotlin | Currently served by | Wiring |
|---|---|---|---|
| `GET /head/machines/live` | — | derived locally from `GET /energy/runs/active` | ❌ no `/head` client |
| `GET /head/breakdowns` | — | local Room (`observeOpenFlagsByPlant`) | ❌ |
| `GET /head/qc` | — | local Room (`observePendingQcByPlant`) | ❌ |
| `GET /head/checklists` | — | reuses `GET /preventive-maintenance/checklists` | ❌ (works, but not SUBMITTED-filtered server-side) |
| `GET /head/transfers` | — | nothing | ❌ |
| `GET /head/escalations?min_tier=` | — | local `ComputePmEscalationTierUseCase` | ❌ |
| `GET /head/reports/power?from=&to=` | — | nothing | ❌ |

> The Head UI currently runs on **locally-derived/Room data**. To make Head reflect the
> real backend across devices, add a `HeadApi` with these 7 calls + DTOs (remember the
> ISO-Z string timestamps here, *not* epoch ms).

---

## C. Prioritized action list

**🔴 C-1 — Remove the deleted `daily-kwh` endpoint (will 404 in production).**
The backend deleted `POST /mt-machines/{asset_id}/daily-kwh`, but it's still called:
- `EnergyRepositoryImpl.kt:86` — `machineDailyKwhRepository.upsertForMachineOnDate(...)` in `stopProductionRun()`
- `SyncWorker.kt:49` — `retryFailedDailyKwhUpserts()` loop
Daily kWh is now produced entirely by the run start/stop flow. **Delete** `MachineDailyKwhApi`, `MachineDailyKwhRepository(Impl)`, `MachineDailyKwhDtos`, the `NetworkModule` provider, both call sites, and the retry loop in `SyncWorker`. (Its idempotency assumption is also now invalid: the contract says there's **no** UNIQUE(machine_id, reading_date) — a machine can have several rows/day.)

**🔴 C-2 — Wire breakdown flags + QC to the backend so `mt_breakdown_records` fills.**
`BreakdownApi` (raiseFlag / qcAcknowledge / qcApprove / qcDisapprove) and its DTOs are correct but **never invoked** — `BreakdownRepositoryImpl` only writes local Room and `SyncWorker` injects `breakdownRepository` but never uses it. To fill the `OPERATOR_FLAG` rows:
- On flag create / QC transition, also call the matching `BreakdownApi` method (directly, or enqueue a `SyncQueue` action and drain it in `SyncWorker`, mirroring `energyRepository.syncPendingRuns()`).
- Add `GET /breakdowns/open` + an `OpenBreakdownDto`, and refresh Room from it so Head/QC see other devices' open breakdowns.

**🔴 C-3 — Build the Machine Transfers client (multipart).** No API/DTO exists. Add:
```kotlin
@Multipart @POST("machine-transfers")
suspend fun create(
    @Header("Authorization") token: String,
    @Part("from_warehouse") fromWarehouse: RequestBody,
    @Part("to_warehouse")   toWarehouse: RequestBody,
    @Part("machine_name")   machineName: RequestBody,
    @Part("date") date: RequestBody?, @Part("machine_code") machineCode: RequestBody?,
    @Part("condition") condition: RequestBody?, @Part("reason") reason: RequestBody?,
    @Part("authorised_person") authorisedPerson: RequestBody?, @Part("remarks") remarks: RequestBody?,
    @Part proofPhoto: MultipartBody.Part?,                 // name MUST be "proof_photo"
): MachineTransferResponseDto                              // {id, proof_photo_url}

@GET("machine-transfers")
suspend fun list(@Header("Authorization") token: String): List<MachineTransferListItemDto>
```
`from_warehouse` must differ from `to_warehouse` (else 400); photo jpeg/png ≤10 MB.

**🟡 C-4 — Add `HeadApi` (7 calls)** if Head must reflect the live backend across devices (currently local-only). Use ISO-Z `String` for timestamps in these DTOs.

**🟢 C-5 — Cosmetic correctness:** `LoginResponseDto.expiresAt` → `Long?`; update the stale `// "OK"|"NOT_OK"` comment to include `UNSET`; rename the misleading `subCategory` param to `category`; add `model_no`/`serial_no` to `MtMachineDto` only if the UI needs them.

---

## D. DB table → who fills each column from the app

| Table | Filled by (Kotlin call) | Status |
|---|---|---|
| `mt_users` | read via `AuthApi.login` | ✅ |
| `mt_asset_list` | read via `MtMachineApi.list` | ✅ (write `add` is admin-only) |
| `mt_machine_daily_kwh` | `EnergyApi.startRun` (RUNNING row) + `stopRun` (kWh, COMPLETE) | ✅ — and **no longer** via daily-kwh upsert |
| `mt_breakdown_records` (OPERATOR_FLAG) | should be `BreakdownApi.raiseFlag` + qc* | 🔴 **not wired (C-2)** |
| `mt_breakdown_records` (F06_RECORD) | `ChecklistApi.submitBreakdown` | ✅ |
| `doc_preventive_maintenance` | `ChecklistApi.submitChecklist` / `updateChecklist` | ✅ |
| `doc_machine_transfer` | should be `MachineTransferApi.create` | ❌ **no client (C-3)** |

---

## E. Errors (handle uniformly)
`401` missing/invalid Bearer · `404` unknown asset_id/run/flag/id · `400` validation (empty entries, bad date, same from/to warehouse, UNSET in a SUBMITTED checklist) · `422` Pydantic body `{"detail":[...]}`. All JSON.
