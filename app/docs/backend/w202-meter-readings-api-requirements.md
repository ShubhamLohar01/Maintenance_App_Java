# Backend requirement — W-202 Main Meter & Solar daily readings

**Requested by:** FactoryOps Android app (Daily Reading section, 2026-08-03)
**Scope:** one new router, `app/api/w202_readings.py`. Both tables already exist.
**Model to copy:** `app/api/a185_readings.py` — this is the same feature for the other
plant, and the app's client is a deliberate mirror of the A-185 one. Copying that file and
changing the table/columns is the intended route.

## Context

W-202 has two electricity meters that a technician reads daily: the **main incoming meter**
and the **solar** meter. Each reading is one row per plant + date, holding opening and
closing KWH and KVAH, the two consumptions, and a power factor.

The app already has the whole UI built — history list, add/edit form, HEAD-only delete —
sitting behind two new tiles on the Daily Reading screen. It is waiting on these endpoints.

## The tables already exist

Both are live in `warehouse_db`. **Do not create them, and do not add computed columns** —
each table has a `BEFORE INSERT OR UPDATE` trigger that owns the derived values. Writing
`kwh_consumption` from Python would just be overwritten.

```
mt_w202_meterreading      -- kind = "main"
mt_w202solar_reading      -- kind = "solar"
```

Identical columns in both:

| column | type | notes |
|---|---|---|
| `id` | SERIAL PK | |
| `plant` | TEXT NOT NULL DEFAULT 'W-202' | matches `mt_a185_readings.plant` — see below |
| `reading_date` | DATE | |
| `initial_kwh` / `final_kwh` | NUMERIC(14,2) | |
| `kwh_consumption` | NUMERIC(14,2) | **trigger-owned** — never write it |
| `initial_kvah` / `final_kvah` | NUMERIC(14,2) | |
| `kvah_consumption` | NUMERIC(14,2) | **trigger-owned** — never write it |
| `power_factor` | NUMERIC(6,3) | main: accepted from client, default 0.98. solar: **trigger-owned** |
| `checked_by` / `verified_by` | TEXT | unused by the app; leave NULL |
| `remarks` | TEXT | the app's "remark" field maps here |
| `status` | VARCHAR(20) DEFAULT 'submitted' | app always sends nothing; leave the default |
| `created_by` | TEXT | set to the JWT's user, as the other routers do |
| `created_at` | TIMESTAMPTZ DEFAULT NOW() | |

What the triggers already do, so the router doesn't have to:

- both consumptions = `final - initial`, NULL when either side is NULL
- **main only:** when `initial_kwh` / `initial_kvah` arrive NULL, they are filled from the
  most recent *earlier-dated* row for the same plant — the carry-forward. A supplied value
  always wins.
- **main only:** a NULL `power_factor` falls back to 0.98
- **solar only:** `power_factor` = `kwh_consumption / kvah_consumption` to 3 dp, NULL when
  KVAH consumption is 0

Constraints that will reject bad input for you: `CHECK (final >= initial)` on both pairs,
and `UNIQUE (plant, reading_date)`. Translate the unique violation into the upsert (see
below) and the CHECK violation into a 400.

### Run the alignment DDL first

Both tables were originally created with a `warehouse` column holding the unhyphenated
`'W202'`. `mt_a185_readings` — the sibling feature this one copies — uses `plant` holding the
hyphenated `'A-185'`, and the app sends `"W-202"` to match. **`w202-meter-readings-align-schema.sql`
in this folder renames the column and recreates the two trigger functions** (a plpgsql body
is stored as text, so it does not follow a column rename). Both tables are empty, so it is a
clean run.

Do that first and the plant string needs no translation anywhere. Guard the value and move on:

```python
def _plant(value: str | None) -> str:
    squashed = (value or "").upper().replace("-", "").replace(" ", "")
    if squashed and squashed != "W202":
        raise HTTPException(400, "These readings exist for W-202 only")
    return "W-202"
```

(`mt_users.location` is inconsistent in the data — `'W202'`, `'W-202'`, `'W-202-Koparkhairne'`
all appear — which is why the guard squashes before comparing. The app already normalises via
`canonicalPlant`, so requests arrive as `"W-202"`.)

## Endpoints

All four take the JWT the way `a185_readings.py` does. `kind` is `"main"` or `"solar"` and
picks the table — reject anything else with 400.

```python
_TABLES = {"main": "mt_w202_meterreading", "solar": "mt_w202solar_reading"}
```

### `POST /w202-readings` — upsert one day

Body the app sends (every meter field is always present, `null` when blank on screen — a
`null` is meaningful, it means "not taken yet", so do not treat it as "leave unchanged"):

```json
{
  "kind": "main",
  "plant": "W-202",
  "reading_date": "2026-08-03",
  "initial_kwh": 452480.0,
  "final_kwh": 452905.0,
  "initial_kvah": 461600.0,
  "final_kvah": 462050.0,
  "power_factor": 0.98,
  "remark": null
}
```

`power_factor` is **only sent for `kind: "main"`**. For solar the app omits the key entirely,
because the trigger derives it — if a solar request somehow carries one, drop it.

Upsert on `(plant, reading_date)` — re-submitting a date overwrites that day, which is how
the app's edit flow saves. `ON CONFLICT (plant, reading_date) DO UPDATE` works directly
against the partial unique index.

Return the **stored row** (`RETURNING *`), so the app gets the trigger's computed values back
in the same round trip. Include `kind` in the response.

### `GET /w202-readings?kind=&plant=&from=&to=` — history

Newest first (`ORDER BY reading_date DESC`). `from`/`to` inclusive ISO dates, both optional.
Returns a **bare JSON array** of the same row shape as POST returns — not wrapped in an
object. The app also uses this with `from == to` to load a single day.

### `GET /w202-readings/prefill?kind=&plant=&date=` — opening values

What the form calls before showing a blank day. `date` defaults to today.

```json
{
  "reading_date": "2026-08-03",
  "source_date": "2026-08-02",
  "openings": { "initial_kwh": 452480.0, "initial_kvah": 461600.0 },
  "power_factor": 0.98,
  "already_saved": false
}
```

- `openings` — for **main**, the previous reading day's `final_kwh` / `final_kvah` (same
  query the trigger uses: same plant, `reading_date < :date`, newest first). For
  **solar**, return `null` for both — solar openings are entered each time.
- `source_date` — the date those openings came from; `null` when there is no earlier row.
- `power_factor` — 0.98 for main; `null` for solar (derived, so the form shows it read-only).
- `already_saved` — whether a row exists for this exact date. The app uses it to decide
  between "New reading" and "Edit reading", and to pull the stored row.

Everything null / `already_saved: false` on the very first entry is correct and expected —
the app handles it.

### `DELETE /w202-readings/{date}?kind=&plant=` — remove a day

**HEAD only.** Return **403** for any other role — the app relies on that specific status to
show "Only a Head can delete a day's reading", and it hides the delete icon for non-HEADs
already. 404 when the date has no row.

## Who can use it

Same as `a185_readings.py`: any authenticated user of the plant may read and submit; only
HEAD may delete. A HEAD sees both plants, so don't gate reads on the caller's own location.

## Tests worth having

Mirroring `tests/test_a185_readings.py`:

- POST main with both readings → 201, `kwh_consumption` computed, `power_factor` 0.98
- POST main for the next day **omitting the initials** → openings equal the previous day's
  closings (this is the carry-forward, and it's the behaviour most worth locking down)
- POST main with an explicit `initial_kwh` → the supplied value survives
- POST main with `power_factor: 0.95` → stored as 0.95; omitted → 0.98
- POST solar with all four readings → `power_factor` derived (e.g. 372.5 / 420 → 0.887)
- POST solar with a `power_factor` in the body → ignored, still derived
- POST the same date twice → one row, updated (not a 500 from the unique index)
- POST with `final < initial` → 400, not a 500
- POST with `plant: "A-185"` → 400
- `prefill` for solar → `openings` both null, `power_factor` null
- `prefill` on an empty table → nulls, `already_saved: false`, no error
- DELETE as a non-HEAD → 403
