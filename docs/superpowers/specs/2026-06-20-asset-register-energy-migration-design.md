# Migrate the production / energy flow to `mt_asset_list`

- **Date:** 2026-06-20
- **Status:** Design — pending user review, then writing-plans
- **App:** FactoryOps Android (`com.candorfoods.factoryops`)
- **Backend:** FastAPI + Postgres (contract verified live through the dev tunnel on 2026-06-20)

## Problem

"Stop production" fails with **HTTP 404**: the app POSTs daily kWh to
`machines/{id}/daily-kwh` (no such route) using `MCH-####` ids from the old
`machines` catalog (214 rows). The backend's energy + reading endpoints are
keyed to the **`mt_asset_list` asset register** (864 rows, ids `W202-####` /
`A185-####`). The two id-spaces do **not** overlap.

**Decision (product owner):** move the production-run / energy flow onto the
asset register. Operators select an **asset** (`assetId`); runs + daily-kWh are
keyed by `assetId`. Reuse the existing `mt-machines` plumbing (`MtMachineApi`,
`MtMachineDto`, `MtMachineRepository`, `GetMachineMasterUseCase`) rather than
building new.

## Locked decisions

1. **Home becomes the asset list.** The operator Home switches its data source
   from `Machine` / `GET machines/assigned` to `MtMachine` / `GET mt-machines`.
   It lists only **runnable** machines (rule below) so operators don't scroll
   past ~800 fixtures; the full register stays in Machine Master.
2. **Offline = snapshot at Start.** The asset register is not cached locally. On
   Start, copy `assetId`, `assetName`, `ratedKw` onto the local
   `ProductionRunEntity`. Stop computes `kWh = ratedKw × hours × 0.99` from the
   snapshot — no catalog lookup, works offline. `SyncWorker` syncs runs later.
3. **Runnable rule.** "Start production" appears when:
   ```
   power_load is non-blank  AND  category ∉ EXCLUDED_CATEGORIES
   EXCLUDED_CATEGORIES = {
     "Electric Asset", "Housekeeping", "Utility", "IT/Communication",
     "Power Distribution", "Infrastructure", "Fire & Safety", "Utility/Infrastructure"
   }
   ```
   Verified against live data: yields ~64 real production machines (Packaging,
   Roasting, Sealing, Printing/Coding, Pumps & Compressors, Conveying,
   Detection/Weighing, Mixing/Grinding, Cooling/Freezing, Cutting/Slicing,
   Coating/Chocolate, Material Handling, Production Equipment, Lab Equipment).
   Stored as an editable constant (case-insensitive compare), not an enum.

## Authoritative backend contract (verified live)

All endpoints need `Authorization: Bearer <jwt>` from `POST auth/login`
(`{username,password}`). JSON snake_case.

- `GET /mt-machines?building=&category=&sub_location=` → array of:
  `{ asset_id, asset_name, building, sub_location, category, model_no, serial_no,
     power_load, rated_kw, quantity, condition, assigned_to }`.
  Filter + response field is **`category`** (not `sub_category`). `rated_kw` is
  backend-parsed from `power_load`, may be null.
- `POST /mt-machines/{asset_id}/daily-kwh`
  body `{ machine_id, reading_date, daily_kwh, source }`; `machine_id` must equal
  `{asset_id}` (else 400); asset must exist (else 404); `source ∈
  CALCULATED|MEASURED|MANUAL`; `building`/`floor` ignored if sent; idempotent
  upsert on `(machine_id, reading_date)`. Response adds `building`, `floor`,
  `updated_at`.
- `POST /energy/runs/start` body `{ machine_id(=asset_id), client_run_id,
  started_at, scheduled_end_at }`. `client_run_id` **required** (idempotency).
  Response `{ run_id, client_run_id, started_at, scheduled_end_at }` — **no
  `operator_id`**.
- `POST /energy/runs/{run_id}/stop` body `{ ended_at }` → `{ run_id, ended_at,
  computed_kwh }` (`computed_kwh = rated_kw × hours × 0.99`, server-computed).
- `GET /energy/machines/{asset_id}/history?from=<ms>&to=<ms>` → **list** of
  `{ date, total_run_hours, total_kwh, estimated_cost, runs: [ { id, started_at,
  ended_at, duration_hours, kwh } ] }`.

## Architecture & data flow

```
Home (operator)
  └─ MtMachineRepository.list()  → GET /mt-machines
       → filter to runnable (power_load + blocklist)
       → list of runnable MtMachine (assetId, name, ratedKw, building, category…)
  └─ tap asset → start/stop run screen

Start run (offline-first)
  device makes clientRunId = local UUID
  ProductionRunEntity{ id=clientRunId, machineId=assetId,
                       assetName, ratedKw (snapshot), startedAt, scheduledEndAt,
                       serverRunId=null, syncStatus=PENDING }  → Room

Stop run (offline-first)
  computedKwh = ratedKw × hours × 0.99   (from snapshot)
  → ProductionRunEntity updated; daily-kWh upsert (assetId)

SyncWorker.syncPendingRuns
  start: POST /energy/runs/start {machine_id=assetId, client_run_id, …}
         → store response.run_id as serverRunId
  stop:  POST /energy/runs/{serverRunId}/stop {ended_at}
  (idempotent via client_run_id; stop requires serverRunId from start)

SyncWorker.retryFailedDailyKwhUpserts
  POST /mt-machines/{assetId}/daily-kwh {machine_id=assetId, reading_date, daily_kwh, source}
```

## Change points

### DTO / API
- **`MtMachineDto` + `MtMachineApi`** — `@SerialName("sub_category")`/`@Query("sub_category")`
  → **`category`**. Add `@SerialName("rated_kw") ratedKw: Double?`,
  `@SerialName("condition") condition: String?`,
  `@SerialName("assigned_to") assignedTo: String?`.
- **`MtMachine` model** — add `category: String`, `ratedKw` (prefer backend
  `rated_kw`, fall back to `PowerLoad.parseKw(powerLoad)`), `condition`,
  `assignedTo`. The existing derived `MachineCategory` (Production/Others
  grouping in Machine Master) keys off `category == "Production Equipment"`;
  rename the derived field to `section` to avoid clashing with the new
  `category: String`. Add `fun MtMachine.isRunnable()`.
- **`MachineDailyKwhApi`** — path `machines/{id}/daily-kwh` →
  **`mt-machines/{id}/daily-kwh`**. Drop the unused GET `list()`.
- **`MachineDailyKwhUpsertDto`** — `machineId` = the `assetId`; keep
  `building`/`floor` optional (or omit — server overwrites). Reuses today's
  `MachineDailyKwhRepositoryImpl.upsertForMachineOnDate` shape.
- **`RunStartRequestDto`** — add `@SerialName("client_run_id") clientRunId`.
- **`RunStartResponseDto`** — remove `operatorId`, add `clientRunId`.
- **History DTOs** — replace `MachineHistoryDto { runs }` with
  `List<DailyHistoryDto>` where `DailyHistoryDto = { date, totalRunHours,
  totalKwh, estimatedCost, runs: List<DailyRunDto> }` and `DailyRunDto = { id,
  startedAt, endedAt, durationHours, kwh }`. Update `EnergyApi.getMachineHistory`
  return type + the history screen mapping.

### Local DB / Room (version 6 → 7, destructive fallback)
- **`ProductionRunEntity`** — add `assetName: String?`, `ratedKw: Double?`
  (snapshot), `serverRunId: String?` (from start response, used for stop).
  `machineId` now stores the `assetId`.

### Repositories / sync
- **`EnergyRepositoryImpl`**
  - `startProductionRun(assetId, assetName, ratedKw, scheduledEndAt)` — build the
    entity with snapshot + `clientRunId = id`.
  - `stopProductionRun` — compute kWh from the snapshot `ratedKw` (null-safe: if
    `ratedKw` null, leave `computedKwh` null and let the server compute on sync);
    daily-kWh keyed by `assetId`.
  - `syncPendingRuns` — **fix the run-id bug:** store `serverRunId` from the start
    response and POST stop to `serverRunId` (today it posts stop to the local id).
    Order start-before-stop; a run with no `serverRunId` syncs start first.
  - Drop the old `machineDao().updateStatus` writes — run state derives from
    `production_runs` (`getActiveRunForMachine(assetId)`).
- **`MachineDailyKwhRepositoryImpl`** — `upsertForMachineOnDate(assetId, atMillis)`
  unchanged in shape; sums today's local runs (now keyed by assetId) and posts to
  the new path.
- **`SyncWorker.retryFailedDailyKwhUpserts`** — uses the new path + `assetId`.

### Presentation
- **`HomeViewModel` / `HomeScreen`** — source from `MtMachineRepository`
  (via a use case), map to a runnable list, keep the search + category/building
  chip filters (now over `MtMachine.category`). Tapping a runnable asset opens
  the run flow with `assetId`/`assetName`/`ratedKw`.
- **Run screen (`MachineDetailViewModel`/Screen or equivalent)** — start/stop by
  `assetId`; show snapshot `ratedKw`. History screen consumes the new list shape.

## Testing (TDD)

Unit (JVM, JUnit5 — existing harness):
- DTO (de)serialization: `RunStartRequestDto` includes `client_run_id`;
  `RunStartResponseDto` parses without `operator_id` and with `client_run_id`;
  `MtMachineDto` parses `category`/`rated_kw`/`condition`/`assigned_to`;
  `DailyHistoryDto` list shape; `MachineDailyKwhUpsertDto` serializes `machine_id`.
- `MtMachine.isRunnable()` — power_load present + not in blocklist (table-driven:
  Tubelight/Electric Asset → false, CCTV/IT → false, Panel/Power Distribution →
  false, Packaging/Roasting/etc. with power_load → true, machinery with blank
  power_load → false).
- `MachineDailyKwhRepositoryImpl` — builds request with `assetId` in path + body.
- Stop kWh = `ratedKw × hours × 0.99` from snapshot; null `ratedKw` → null kWh.

## Acceptance criteria

- Selecting a runnable asset, running, stopping → `POST
  /mt-machines/{asset_id}/daily-kwh` returns **200**; row in `mt_machine_daily_kwh`;
  run in `production_runs`.
- `POST /energy/runs/start` 200 (no 422); `…/stop` 200 with non-null
  `computed_kwh`.
- Home shows runnable assets from `/mt-machines`, filterable by
  building/category/sub_location; non-machinery (lights, CCTV, panels, furniture)
  show **no** Start option.
- No call to `machines/{id}/daily-kwh` remains.

## Out of scope / cautions

- **No backend changes** — the contract above is live.
- No `MCH-*` → `asset_id` mapping (none exists).
- Old `machines/assigned` + `MachineApi` may remain for other screens, but the
  production/energy flow must not use it.
- Backend's trapped `bcrypt __about__` warning is harmless.
