# Backend Prompt — Per-Machine Daily kWh + Monthly PM Checklist

You're picking up the FastAPI + PostgreSQL backend at `D:\Maintenance module\backend\`. The Android app already has both features wired client-side; this prompt covers the backend changes so the existing `POST` and `GET` calls land successfully.

---

## Context

- The Android app (`D:\Maintenance module\FactoryOps`) is already in production for power consumption, breakdowns, and PM work orders. Phase 3 backend migration is in progress.
- Two new Android features were just added and need backend endpoints:
  1. **Per-machine daily kWh** — the operator's "stop production run" flow now also POSTs a per-machine daily total.
  2. **Monthly PM Checklist** — technician fills a CFPLA.C4.F.50a form once a month.
- The Android side is feature-flagged: today it talks to in-app Mock APIs. Flip `USE_MOCK_API=false` in `local.properties` once these endpoints are live.

---

## 1. Per-machine daily kWh

### 1a. Schema change — extend `mt_floor_utility_readings`

This table already exists in `warehouse_db`:

```
mt_floor_utility_readings (
  id            SERIAL PK,
  floor         VARCHAR(64),
  reading_date  DATE,
  meter_reading NUMERIC(14,4),
  daily_kwh     NUMERIC(14,4)
)
```

**Heads-up:** the existing ORM in `app/models.py` points at `floor_utility_readings` with a `floor_id` FK column. That does NOT match the real warehouse_db table. Fix the ORM first:

- Rename `__tablename__` from `"floor_utility_readings"` → `"mt_floor_utility_readings"`.
- Rename column `floor_id` → `floor` and drop the FK (column is denormalized text, e.g. `"Lower basement"`).
- Update any `Floor`-side relationship/back_populates if present.

Then **add two new columns** so the same table can hold per-machine daily rows alongside per-floor ones:

```sql
ALTER TABLE mt_floor_utility_readings
    ADD COLUMN machine_id VARCHAR(64),
    ADD COLUMN source     VARCHAR(16) NOT NULL DEFAULT 'METERED';

-- Idempotent upsert key (one row per machine per day)
CREATE UNIQUE INDEX uq_mt_machine_daily
    ON mt_floor_utility_readings (machine_id, reading_date)
    WHERE machine_id IS NOT NULL;

CREATE INDEX idx_mt_readings_floor_date
    ON mt_floor_utility_readings (floor, reading_date DESC);
```

**Grain rules:**
- Per-floor rows: `machine_id IS NULL`, `source = 'METERED'` (existing data).
- Per-machine rows: `machine_id IS NOT NULL`, `source = 'CALCULATED'` (new data from Android).

Generate an Alembic migration for the column additions + indexes. Don't drop/recreate the table.

### 1b. SQLAlchemy model

Update `app/models.py`:

```python
class FloorUtilityReading(Base):
    __tablename__ = "mt_floor_utility_readings"
    id            = Mapped[int]            (Integer, PK, autoincrement)
    floor         = Mapped[str]            (String(64), index=True, nullable=True)
    reading_date  = Mapped[date]           (Date, index=True)
    meter_reading = Mapped[float | None]   (Numeric(14,4), nullable=True)
    daily_kwh     = Mapped[float | None]   (Numeric(14,4), nullable=True)
    machine_id    = Mapped[str | None]     (String(64), index=True, nullable=True)
    source        = Mapped[str]            (String(16), default="METERED")
```

### 1c. Pydantic schemas

```python
class MachineDailyKwhUpsertDto(BaseModel):
    machine_id:   str
    reading_date: date            # ISO YYYY-MM-DD
    floor:        str
    daily_kwh:    float
    source:       str = "CALCULATED"

class MachineDailyKwhDto(BaseModel):
    machine_id:   str
    reading_date: date
    floor:        str | None
    daily_kwh:    float | None
    source:       str | None
```

### 1d. Endpoints (new file `app/api/machine_daily_kwh.py`)

```
POST /machines/{machine_id}/daily-kwh   →  upsert (ON CONFLICT DO UPDATE)
GET  /machines/{machine_id}/daily-kwh   →  list, optional ?from=YYYY-MM-DD&to=YYYY-MM-DD
```

**Upsert behavior** — the Android client recomputes the full day total each time
a run stops and POSTs the latest value, so the backend MUST do:

```sql
INSERT INTO mt_floor_utility_readings
    (machine_id, reading_date, floor, daily_kwh, source)
VALUES (...)
ON CONFLICT (machine_id, reading_date)
WHERE machine_id IS NOT NULL
DO UPDATE SET
    daily_kwh = EXCLUDED.daily_kwh,
    floor     = EXCLUDED.floor,
    source    = EXCLUDED.source;
```

Use `sqlalchemy.dialects.postgresql.insert(...).on_conflict_do_update(...)`.

Wire the router in `app/main.py`: `app.include_router(machine_daily_kwh.router)`.

### 1e. Auth + path-param check

- Both endpoints require `Depends(get_current_user)`.
- For upsert: 404 if `machine_id` not in `machines` table, OR if `path machine_id != body.machine_id`.
- For list: pure read, scope to the user's plant (`JOIN machines WHERE plant_id = user.plant_id`).

### 1f. Tests (`tests/test_machine_daily_kwh.py`)

- `test_upsert_creates_row` — first POST creates a row, returns the saved DTO.
- `test_upsert_overwrites_same_day` — second POST same (machine_id, date) overwrites `daily_kwh`, doesn't create a duplicate.
- `test_upsert_different_dates_creates_separate_rows` — two different `reading_date` for same machine → 2 rows.
- `test_list_filters_by_date_range` — `?from=&to=` returns only rows in range.
- `test_list_per_floor_rows_excluded` — list endpoint must NOT return rows where `machine_id IS NULL` (those are the legacy floor totals).
- `test_machine_id_mismatch_400` — body's `machine_id` != path's → 400.

### 1g. Wire matches the Android contract exactly

Android's request body (snake_case via `@SerialName` on Kotlin DTOs):

```json
{
  "machine_id":   "MCH-0034",
  "reading_date": "2026-05-27",
  "floor":        "Service floor",
  "daily_kwh":    11.25,
  "source":       "CALCULATED"
}
```

Response (`MachineDailyKwhDto`):

```json
{
  "machine_id":   "MCH-0034",
  "reading_date": "2026-05-27",
  "floor":        "Service floor",
  "daily_kwh":    11.25,
  "source":       "CALCULATED"
}
```

Pydantic v2: serialize `date` as ISO string in the response, not epoch.

---

## 2. Monthly PM Checklist (CFPLA.C4.F.50a)

Spec was already given to the team; the Android side is wired against this exact endpoint:

```
POST /api/documentations/preventive-maintenance
GET  /api/documentations/preventive-maintenance?page=1&per_page=50&warehouse=A185
GET  /api/documentations/preventive-maintenance/{id}
```

### 2a. Table

```sql
CREATE TABLE doc_preventive_maintenance (
    id          SERIAL PRIMARY KEY,
    month       VARCHAR(7),                                   -- "YYYY-MM"
    checked_by  VARCHAR(128),
    verified_by VARCHAR(128),
    rows        JSONB NOT NULL DEFAULT '[]'::jsonb,
    warehouse   VARCHAR(16),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(128)
);
CREATE INDEX idx_doc_pm_warehouse_month
    ON doc_preventive_maintenance (warehouse, month DESC);
```

### 2b. rows JSONB shape (each element)

```json
{
  "section":    "Lower Basement",
  "equipment":  "Lift 1 Old",
  "checkpoint": "Electrical connections, Doors, Switches",
  "status":     "OK",
  "remarks":    "No issues"
}
```

`status` allowed values: `"OK" | "Needs Repair" | "N/A" | ""` (empty string = unanswered, still allowed).

### 2c. Wire the Android contract — request body

```json
{
  "month":       "2026-05",
  "checked_by":  "Priya Sharma",
  "verified_by": "Shubham Lohar",
  "warehouse":   "PLANT-01",
  "created_by":  "technician1",
  "rows":        [ ... 18 row objects ... ]
}
```

Response (after `POST`):
```json
{
  "id":         42,
  "month":      "2026-05",
  "warehouse":  "PLANT-01",
  "created_at": "2026-05-27T14:30:00Z"
}
```

### 2d. List endpoint

`GET /api/documentations/preventive-maintenance?page=1&per_page=50&warehouse=A185`

Returns:
```json
{
  "items": [
    { "id": 42, "month": "2026-05", "checked_by": "Priya Sharma", "warehouse": "PLANT-01", "created_at": "..." }
  ],
  "total": 1,
  "page": 1,
  "per_page": 50
}
```

Filter by `warehouse` query param (optional). Order by `created_at DESC`.

### 2e. Detail endpoint

`GET /api/documentations/preventive-maintenance/{id}` returns the full record including the `rows` JSONB array.

### 2f. Tests

- Round-trip submit + GET-by-id; assert all 5 row fields preserved verbatim.
- List pagination + warehouse filter.
- Empty `rows` is allowed (default `'[]'::jsonb`).
- Status values outside the allowed set are accepted at the API level (no enforcement at JSONB layer) — Android validates client-side.

---

## 3. Process you must follow

1. **Read** `D:\Maintenance module\FactoryOps\PROGRESS.md` and the Android contract files referenced inline:
   - `app/src/main/java/com/candorfoods/factoryops/data/remote/dto/MachineDailyKwhDtos.kt`
   - `app/src/main/java/com/candorfoods/factoryops/data/remote/api/MachineDailyKwhApi.kt`
   - `app/src/main/java/com/candorfoods/factoryops/data/remote/dto/PmChecklistDtos.kt`
   - `app/src/main/java/com/candorfoods/factoryops/data/remote/api/PmChecklistApi.kt`
2. **Fix the existing `floor_utility_readings` ORM mismatch first** (see §1a). Confirm the table name + column name change works by querying the real `warehouse_db`.
3. **Generate the Alembic migration** for the new columns + indexes. Show diff before running.
4. **Build endpoints incrementally** in this order: schema → models → schemas → endpoint → tests. Verify after each step.
5. **Hand back**: confirmed endpoint URLs, sample curl invocations, and any deviations from this spec.

---

## 4. Don't touch (out of scope)

- Existing `/floors/...` endpoints, `/machines/assigned`, `/energy/runs/...`, `/breakdowns/...`, `/mt-machines`, `/auth/login`. The Android app is happy with all of those today.
- The `production_runs.computed_kwh` write path — Android continues to compute per-run kWh client-side and sync it via `/energy/runs/.../stop`. This prompt's daily-kWh feature is purely additive (writes to `mt_floor_utility_readings`, not `production_runs`).
- The `mt_machine_list` admin-tagging feature. Storage is client-side DataStore for now (no backend change required).

---

## 5. After deploy

Have the Android team flip `local.properties`:
```
USE_MOCK_API=false
BASE_URL=https://<your-tunnel-or-prod>/
```
…then rebuild the debug APK and verify:
- Operator starts + stops a run → server-side `SELECT * FROM mt_floor_utility_readings WHERE machine_id='...' AND reading_date='2026-05-27'` shows the row.
- Technician submits a checklist → `SELECT id, month, jsonb_array_length(rows) FROM doc_preventive_maintenance ORDER BY id DESC LIMIT 1` shows 18.
