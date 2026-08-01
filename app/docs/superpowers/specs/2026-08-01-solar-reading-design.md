# Solar Energy Reading — design

**Date:** 2026-08-01
**Backend:** `E:\QC\complaint-module\backend` (QC / complaint-module FastAPI, not the Maintenance backend)
**Scope:** W202 only. One new table, one registry entry, one frontend registry entry.

## What it records

A daily solar meter sheet at W202: the KWH and KVAH readings taken at the start
and end of the period, the consumption across each, and the resulting power
factor.

| Field | Source |
|---|---|
| Initial KWH | entered |
| Final KWH | entered |
| KWH consumption | derived — `final_kwh - initial_kwh` |
| Initial KVAH | entered |
| Final KVAH | entered |
| KVAH consumption | derived — `final_kvah - initial_kvah` |
| Power factor | derived — `kwh_consumption / kvah_consumption`, 3 dp |

Plus the sheet's own metadata: `reading_date`, `checked_by`, `verified_by`,
`remarks`, `status`, `warehouse`.

## Architecture — no new router

The backend already serves every controlled form through one generic endpoint,
`/api/docs/{form_type}` (`app/routers/documentations.py` +
`app/services/documentations_service.py`). The service reads the target table's
column list out of `information_schema` and persists any payload key matching a
real column, so a new form needs **a table plus a `DOC_REGISTRY` entry** — no
router, no Pydantic schema, no model class.

Solar Reading is an ordinary form of that kind. The only thing that makes it
special is `"warehouses": ["W202"]`, which the existing router already honours:

- **create** — stamps `warehouse = "W202"` when the client sends none
- **list** — clamps the query to W202
- **both** — 403s a request arriving from any other plant

### Registry entry

`app/config/doc_registry.py`, alongside the other entries:

```python
"solar-reading": {
    "table": "doc_solar_reading",
    "label": "Solar Energy Reading",
    "date_column": "reading_date",
    "doc_no": "CFPLA.C4.F.__",   # fill in the controlled document number
    "warehouses": ["W202"],
},
```

`doc_no` is the one value this design can't supply — take it from the printed
form. Everything else is final.

### Frontend registry mirror

`frontend/src/config/doc-forms.ts` mirrors the same shape so the form is hidden
outside W202 (the backend does the real enforcement):

```ts
"solar-reading": { formType: "solar-reading", routeSlug: "solar-reading", label: "Solar Energy Reading", docNo: "CFPLA.C4.F.__", dateField: "reading_date", listColumns: ["reading_date", "kwh_consumption", "kvah_consumption", "power_factor", "status"], warehouses: ["W202"] },
```

## Why the derived columns are a trigger, not `GENERATED`

`GENERATED ALWAYS AS ... STORED` is the obvious way to keep consumption from
drifting from the readings, but it breaks against this particular service:
`create_record` builds `INSERT INTO t (<every payload key that is a column>)`,
and Postgres rejects an INSERT that *names* a generated column at all. A client
that helpfully sent `kwh_consumption` would get a 500.

A `BEFORE INSERT OR UPDATE` trigger gives the same guarantee — the stored value
is always the server's arithmetic, never the client's — while overwriting a
client-sent value instead of rejecting it. It also survives the partial UPDATE
the service does: `NEW` on an UPDATE already carries the untouched columns, so
changing only `final_kwh` recomputes consumption against the stored
`initial_kwh`.

Derivation rules, all three in one function:

- either reading NULL → consumption NULL (a draft with the final reading not yet
  taken is still valid)
- `kvah_consumption` zero or NULL → `power_factor` NULL, no division by zero
- power factor rounded to 3 dp

## Data integrity

- `CHECK (final >= initial)` on both pairs — a meter only counts up. NULLs pass,
  so drafts are unaffected.
- `UNIQUE (warehouse, reading_date) WHERE reading_date IS NOT NULL` — one sheet
  per plant per day, and a partial row with no date yet doesn't collide.
- `NUMERIC(14,2)` for readings (a cumulative meter reaches seven figures),
  `NUMERIC(6,3)` for power factor.
- `status VARCHAR(20) DEFAULT 'submitted'` — present from the start so
  "Submit Partially" works, rather than being retrofitted the way
  `doc_lux_monitoring` and `doc_temperature_humidity` were.

## Access

Inherits the generic doc rules unchanged, so nothing new to build:

- any authenticated user may create and update
- date/time columns are editable only by an admin (`admin_scope_for`)
- delete is admin-only; a scoped admin is checked against the record's
  `warehouse`

W202 has no scoped admin today (`SCOPED_ADMIN_EMAILS` lists only A185), so
`pooja.parkar@candorfoods.in` administers these records.

## API

All four verbs already exist. Create:

```
POST /api/docs/solar-reading
X-User-Email: <user>

{
  "reading_date": "2026-08-01",
  "initial_kwh": 124530.00,
  "final_kwh":   124902.50,
  "initial_kvah": 129100.00,
  "final_kvah":   129520.00,
  "checked_by": "…",
  "verified_by": "…",
  "remarks": "",
  "status": "submitted"
}
```

The response is `{"success": true, "data": {...}}` from `RETURNING *`, so the
client gets the derived values back in the same round trip — 372.50 kWh,
420.00 kVAh, power factor 0.887 — and never has to compute them itself.

Others: `GET /api/docs/solar-reading?page=&per_page=` (sorted by `reading_date`
DESC), `GET /api/docs/solar-reading/{id}` (includes `_prev_id` / `_next_id`),
`PUT`, `DELETE`.

## Migration

`backend/database_migrations/create_solar_reading.sql` — written, idempotent
(`CREATE TABLE IF NOT EXISTS`, `CREATE OR REPLACE FUNCTION`, `DROP TRIGGER IF
EXISTS` before `CREATE TRIGGER`), so it is safe to re-run.

Requires Postgres 11+ for `EXECUTE FUNCTION`.

## Testing

1. Run the migration; confirm the table and trigger exist.
2. POST a full reading → 201, and `kwh_consumption` / `kvah_consumption` /
   `power_factor` come back computed.
3. POST the same body with a deliberately wrong `kwh_consumption` → stored value
   is the server's arithmetic, not the client's.
4. POST with `final_kwh` omitted (draft) → 201, consumption and power factor
   NULL.
5. PUT only `final_kwh` on that draft → consumption recomputes against the
   stored `initial_kwh`.
6. POST `final_kwh < initial_kwh` → rejected by the CHECK constraint.
7. POST a second sheet for the same `reading_date` → rejected by the unique
   index.
8. POST with `warehouse: "A185"` → 403 from `_guard_form_plant`.
9. `initial_kvah == final_kvah` → `power_factor` NULL, no division error.
