# Backend Prompt — Breakdown Maintenance Record (CFPLA.C4.F.06)

You're on the FastAPI + PostgreSQL backend at `D:\Maintenance module\backend\`. The
Android app has a Breakdown Maintenance Record form wired client-side; this prompt
adds the endpoint so its **Save** POST lands. Auth is `Depends(get_current_user)`,
JSON is snake_case, same as the other endpoints.

## Table (already created)

The normalized table exists in `warehouse_db` — one row per breakdown event:

```sql
CREATE TABLE mt_breakdown_records (
    id                      SERIAL PRIMARY KEY,
    doc_no                  VARCHAR(32) NOT NULL DEFAULT 'CFPLA.C4.F.06',
    sr_no                   INTEGER,
    record_date             DATE,
    location                VARCHAR(128),
    machine_name            VARCHAR(128),
    equipment_model_no      VARCHAR(128),
    problem_in_brief        TEXT,
    type_of_maintenance     VARCHAR(32),
    part_of_machine         VARCHAR(128),
    temporary_reason        TEXT,
    duration_start          VARCHAR(32),
    duration_end            VARCHAR(32),
    machine_operator_sign   VARCHAR(128),
    maintenance_person_sign VARCHAR(128),
    qc_clearance_sign       VARCHAR(128),
    verified_by             VARCHAR(128),
    created_by              VARCHAR(128),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Add the SQLAlchemy model `BreakdownRecord` mapping these columns if not present.

## Endpoint — submit a breakdown sheet

```
POST /preventive-maintenance/breakdowns          (Bearer auth)
```

The app submits the whole sheet (one or more breakdown rows + one `verified_by`).
**Explode `entries` into N rows**, stamping `verified_by` / `created_by` /
`created_at` on each.

### Pydantic (v2) schemas

```python
class BreakdownEntryIn(BaseModel):
    record_date:             date | None = None      # ISO yyyy-MM-dd
    location:                str = ""
    machine_name:            str = ""
    equipment_model_no:      str = ""
    problem_in_brief:        str = ""
    type_of_maintenance:     str = ""
    part_of_machine:         str = ""
    temporary_reason:        str = ""
    duration_start:          str = ""
    duration_end:            str = ""
    machine_operator_sign:   str = ""
    maintenance_person_sign: str = ""
    qc_clearance_sign:       str = ""

class BreakdownSheetIn(BaseModel):
    doc_no:      str = "CFPLA.C4.F.06"
    verified_by: str = ""
    entries:     list[BreakdownEntryIn]
```

Be lenient (trim strings; `record_date` accepts `null`/empty → store NULL) so the
client never 422s on a partially-filled optional field — mirror the PM checklist's
leniency.

### Behavior

- 401 if no/invalid token.
- 400 if `entries` is empty.
- Insert one `mt_breakdown_records` row per entry: copy the entry fields, set
  `doc_no` (from the sheet, default `CFPLA.C4.F.06`), `verified_by` (sheet-level),
  `created_by = current_user`, `sr_no = index+1` (1-based, as ordered).
- Return the new ids:

```json
201  { "ids": [12, 13, 14] }
```

Wire the router in `app/main.py` (e.g. `app.include_router(breakdown.router)`).

## Optional (not needed by the app yet)

- `GET /preventive-maintenance/breakdowns?from=&to=&machine_name=` → list rows
  (newest first), and `GET /preventive-maintenance/breakdowns/{id}` → one row.
  Skip for now unless you want a read view.

## Android contract — the exact body the app POSTs

```json
{
  "doc_no": "CFPLA.C4.F.06",
  "verified_by": "Ravi",
  "entries": [
    {
      "record_date": "2026-06-22",
      "location": "Lower Basement",
      "machine_name": "Auto L-sealer",
      "equipment_model_no": "SLR-200 / SN-1182",
      "problem_in_brief": "Heater coil not heating",
      "type_of_maintenance": "Temporary",
      "part_of_machine": "Heater coil",
      "temporary_reason": "Spare not in stock",
      "duration_start": "10:30",
      "duration_end": "12:00",
      "machine_operator_sign": "Anil",
      "maintenance_person_sign": "Suresh",
      "qc_clearance_sign": "Ravi"
    }
  ]
}
```
Field names map 1:1 to the table columns. (`record_date` and `temporary_reason`
are the only renames from the on-screen labels "Date" and "Reason".)

## Tests (`tests/test_breakdowns.py`)

- `test_submit_creates_one_row_per_entry` — 2 entries → 2 rows, ids returned, each
  carries the sheet `verified_by` and `created_by = current_user`.
- `test_empty_entries_400`.
- `test_null_record_date_ok` — entry with no date stores NULL, no 422.
- `test_requires_auth_401`.

## After deploy

Tell the Android team the confirmed path; the client is already built to POST this
shape — flipping the breakdown "Save" from local-stub to the real call is a one-line
endpoint match (the app uses `BASE_URL` + Bearer auth like everything else).
