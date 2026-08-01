# Main Meter Reading — design

**Date:** 2026-08-01
**Backend:** `E:\QC\complaint-module\backend` (QC / complaint-module FastAPI)
**Scope:** W202 only. Sibling of [Solar Energy Reading](2026-08-01-solar-reading-design.md).

## What it records

The daily reading of W202's main incoming meter: opening and closing KWH and
KVAH, the consumption across each, and the power factor.

| Field | Source |
|---|---|
| Initial KWH | carried forward from the previous date's Final KWH, editable |
| Final KWH | entered |
| KWH consumption | derived — `final_kwh - initial_kwh` |
| Initial KVAH | carried forward from the previous date's Final KVAH, editable |
| Final KVAH | entered |
| KVAH consumption | derived — `final_kvah - initial_kvah` |
| Power factor | **entered**, defaulting to 0.98 |

## Table name

`mt_w202_meterreading`, not a `doc_*` name. The `doc_*` tables are formats that
exist at every plant and are told apart by their `warehouse` column; this one is
W202's own meter, so the name says so. It still keeps the `warehouse` column —
the generic service's plant scoping, the admin checks, and the carry-forward
lookup all key off it, and none of them would work on a table without it.

## Two differences from the solar sheet

Everything else — the generic `/api/docs/{form_type}` endpoint, the
`"warehouses": ["W202"]` gating, the draft/submit flow, the admin rules — is
identical, so only what diverges is worth stating.

**Power factor is entered, not derived.** On the solar sheet it is
`kwh_consumption / kvah_consumption`. Here it is a plain `NUMERIC(6,3)` column
with `DEFAULT 0.98`, editable on the form. The trigger coalesces a blank value
back to 0.98 rather than storing NULL, so a sheet is never left without one.

**The opening readings carry forward.** A new sheet opens at the previous date's
closing figures, so the operator only takes the final reading. This is
implemented twice, deliberately:

- *In the trigger* — when `initial_kwh` / `initial_kvah` arrive NULL, they are
  filled from the most recent earlier-dated row for the same warehouse. This is
  the authoritative copy: it holds for any client, including a draft that gets
  its date filled in later.
- *In the form* — a `docsApi.list(..., { per_page: 1 })` on mount prefills the
  same two fields and shows which date they came from. Purely so the operator
  can see the opening numbers before saving, and correct them when a meter has
  been replaced or reset.

Both use the same rule (same warehouse, strictly earlier `reading_date`,
most recent first), so they cannot disagree. A supplied value always wins over
the carry-forward — `COALESCE(NEW.initial_kwh, prev_kwh)`, not the reverse.

The unique index on `(warehouse, reading_date)` is what makes "the previous
date" unambiguous; without it the carry-forward would have to pick between two
sheets for the same day.

## Not duplicatable

`main-meter-reading` is deliberately absent from `DUPLICATABLE_SLUGS`. A copied
sheet would arrive carrying the source record's opening readings, which is
exactly what the carry-forward exists to prevent. Solar has no such coupling and
stays duplicatable.

## Files

Backend:

- `database_migrations/create_mt_w202_meterreading.sql` — new
- `app/config/doc_registry.py` — `"main-meter-reading"` entry added

Frontend:

- `src/components/forms/MainMeterReadingForm.tsx` — new
- `src/app/documentations/main-meter-reading/{page,create/page,[id]/page,[id]/edit/page}.tsx` — new
- `src/config/doc-forms.ts` — `DOC_FORMS` entry (not `DUPLICATABLE_SLUGS`)
- `src/app/documentations/page.tsx` — grid card, `warehouses: ['W202']`

`npx tsc --noEmit` passes; the registry entry loads (46 forms).

## Testing

1. Run the migration; confirm the table and trigger exist.
2. POST a sheet for day 1 with both initial and final readings → 201, consumption
   computed, `power_factor` 0.98.
3. POST a sheet for day 2 sending **no** initial readings → the opening figures
   equal day 1's closing figures, and consumption is computed from them.
4. POST day 3 with an explicit `initial_kwh` → the supplied value is kept, not
   overwritten by day 2's close.
5. POST with `power_factor: 0.95` → stored as 0.95.
6. POST with `power_factor` omitted or null → stored as 0.98.
7. Open the create form after step 2 → the opening fields are prefilled and the
   banner names the previous date.
8. POST a back-dated sheet (between two existing dates) → it carries forward from
   the sheet immediately before it, not from the newest one.
9. The first sheet ever, with no earlier row → opening readings stay as sent
   (NULL if none), no error.
10. `final_kwh < initial_kwh` → rejected by the CHECK constraint.
11. Second sheet for the same `reading_date` → rejected by the unique index.
12. `warehouse: "A185"` → 403 from `_guard_form_plant`.
