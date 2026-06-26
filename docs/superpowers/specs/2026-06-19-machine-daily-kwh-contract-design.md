# Machine-level daily kWh — client ↔ backend contract

- **Date:** 2026-06-19
- **Status:** Design approved (recommendations 1–4 locked; pending written-spec review)
- **Client side:** Android (Kotlin), `D:\Maintenance module\FactoryOps` — already wired (see *Client reference* below)
- **Backend side:** FastAPI + PostgreSQL (`D:\Maintenance module\backend`) — **to be built**

## Context & correction

The Android app computes a **per-machine daily kWh total** and POSTs it on every
production-run stop. There is currently **no backend endpoint** to receive it — the
app only talks to an in-app mock (`USE_MOCK_API` toggles this). The earlier
`BACKEND_DAILY_KWH_PROMPT.md` described an endpoint + `mt_floor_utility_readings`
extension that was **never built**, so it is inaccurate and superseded by this doc.

Today the only daily-kWh storage on the backend is floor-level and read-only
(`FloorUtilityReading` / `mt_floor_utility_readings`). Posting the per-machine body
to a non-existent route would **404** (not silently drop fields). This spec defines a
**new, dedicated** machine-level path that leaves the floor-level table untouched.

## Decisions (recommendations 1–4)

1. **POST-only, no GET (for now).** The client never reads this data back — the
   machine history screen reads local `production_runs`, and the `list()` method on
   the client API interface is currently dead code. Add a GET later only if a
   backend-fed view appears.
2. **`building` + `floor` stored denormalized** on each row (the client sends both).
   Snapshots the values at reading time; no join needed for reporting.
3. **Keep `source`** (`VARCHAR(16) DEFAULT 'CALCULATED'`). The client always sends
   `"CALCULATED"`; the column cheaply leaves room for a future manual/admin-corrected
   value.
4. **`reading_date` is the device-local calendar day.** The client derives it from
   the run's start time via `startOfDayMillis` / `isoDate`. If backend reporting needs
   a canonical timezone, normalize on ingest — but the value on the wire is local.

## Data model — new table `mt_machine_daily_kwh`

```sql
CREATE TABLE mt_machine_daily_kwh (
    id            SERIAL        PRIMARY KEY,
    machine_id    VARCHAR(64)   NOT NULL,                 -- operational machine id, e.g. "MCH-0001"
    reading_date  DATE          NOT NULL,                 -- device-local calendar day
    building      VARCHAR(16)   NOT NULL DEFAULT 'W-202', -- "W-202" | "A-185"
    floor         VARCHAR(64)   NOT NULL,                 -- denormalized location text, e.g. "Floor 1 - Section A"
    daily_kwh     NUMERIC(14,4) NOT NULL,                 -- FULL-DAY total, not a delta
    source        VARCHAR(16)   NOT NULL DEFAULT 'CALCULATED',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_machine_daily UNIQUE (machine_id, reading_date)
);

-- Optional, only if backend reporting filters by building/floor:
CREATE INDEX idx_machine_daily_building_floor_date
    ON mt_machine_daily_kwh (building, floor, reading_date DESC);
```

- `machine_id` is the **operational** machine id served by `/machines` (`MCH-0001`),
  **not** the asset-register `asset_id` (`W202-0001`). FK/lookup target is the
  backend `machines` table.
- Grain is one row per `(machine_id, reading_date)`. This is independent of the
  floor-level `(building, floor, reading_date)` unique key on
  `mt_floor_utility_readings` — there is no collision between the two tables.

## Endpoint

```
POST /machines/{machine_id}/daily-kwh      → idempotent upsert (auth required)
```

### Request body (snake_case — matches the Kotlin `@SerialName`s exactly)

```json
{
  "machine_id":   "MCH-0001",
  "reading_date": "2026-06-18",
  "building":     "W-202",
  "floor":        "Floor 1 - Section A",
  "daily_kwh":    93.75,
  "source":       "CALCULATED"
}
```

| field          | type            | notes                                  |
|----------------|-----------------|----------------------------------------|
| `machine_id`   | string          | must equal the path `{machine_id}`     |
| `reading_date` | string (ISO date) | `YYYY-MM-DD`, device-local day        |
| `building`     | string          | `"W-202"` \| `"A-185"`                  |
| `floor`        | string          | denormalized location text             |
| `daily_kwh`    | number (float)  | full-day total                         |
| `source`       | string          | defaults to `"CALCULATED"`             |

### Upsert behavior (last-write-wins)

The client recomputes the full-day total and re-POSTs it on every stop (and
re-sends trailing days via `SyncWorker`), so the backend MUST replace on conflict:

```sql
INSERT INTO mt_machine_daily_kwh
    (machine_id, reading_date, building, floor, daily_kwh, source)
VALUES (:machine_id, :reading_date, :building, :floor, :daily_kwh, :source)
ON CONFLICT (machine_id, reading_date) DO UPDATE SET
    building   = EXCLUDED.building,
    floor      = EXCLUDED.floor,
    daily_kwh  = EXCLUDED.daily_kwh,
    source     = EXCLUDED.source,
    updated_at = now();
```

Use `sqlalchemy.dialects.postgresql.insert(...).on_conflict_do_update(...)`.

### Validation / errors

- `401` — missing/invalid token (`Depends(get_current_user)`).
- `404` — `machine_id` not found in `machines`.
- `400` — path `{machine_id}` ≠ body `machine_id`.

### Response

Echo the saved row (Pydantic v2 — serialize `reading_date` as ISO string, not epoch),
matching the client's read DTO (all value fields nullable-tolerant):

```json
{
  "machine_id":   "MCH-0001",
  "reading_date": "2026-06-18",
  "building":     "W-202",
  "floor":        "Floor 1 - Section A",
  "daily_kwh":    93.75,
  "source":       "CALCULATED"
}
```

`204 No Content` is also acceptable — the client ignores the response body
(`upsertForMachineOnDate` only checks for success/failure).

## Client reference (already implemented)

- DTOs: `data/remote/dto/MachineDailyKwhDtos.kt` (`MachineDailyKwhUpsertDto`,
  `MachineDailyKwhDto`) — `building` field added 2026-06-19.
- API: `data/remote/api/MachineDailyKwhApi.kt` →
  `@POST("machines/{machine_id}/daily-kwh")`.
- Caller: `EnergyRepositoryImpl.stopProductionRun` →
  `MachineDailyKwhRepositoryImpl.upsertForMachineOnDate(machineId, atMillis)`, plus
  `SyncWorker` for trailing days.
- Source values: `daily_kwh` = `ProductionRunDao.sumComputedKwhInRange(machineId,
  dayStart, nextDayStart)`; `building`/`floor` = local `MachineEntity.building` /
  `.location`; `reading_date` = `isoDate(atMillis)`.

No further client changes are required to match this contract.

## Out of scope

- The floor-level `mt_floor_utility_readings` table and its
  `(building, floor, reading_date)` unique key (a separate, pre-existing concern).
- `production_runs` / per-run kWh sync (unchanged; this feature is additive).
- A GET/history endpoint for per-machine daily kWh (deferred — decision 1).

## Backend implementation checklist

1. Alembic migration: create `mt_machine_daily_kwh` + unique constraint (+ optional index).
2. SQLAlchemy model `MachineDailyKwh`.
3. Pydantic schemas: `MachineDailyKwhUpsert` (request) and `MachineDailyKwhRead` (response).
4. `POST /machines/{machine_id}/daily-kwh` route with the upsert + validation above.
5. Tests: create-row, overwrite-same-day (no dup), separate-dates → 2 rows, machine-id mismatch → 400, unknown machine → 404, auth required.
6. Hand back: confirmed URL + a sample `curl`, and the values to set in `local.properties` (`USE_MOCK_API=false`) so the Android team can flip off the mock.
```
