# Backend fix — A-185 checklists are rejected with 422 (`form_type`)

**Reported:** 2026-08-10, A-185 technician (anil) — saving a preventive checklist as
DRAFT fails. Also affects SUBMITTED. **W-202 technicians are unaffected.**
**Severity:** A-185 cannot save any preventive checklist at all. Total block.
**Size:** one line + a test.

## Root cause

`app/schemas.py:302`

```python
PmFormType = Literal["MONTHLY", "QUARTERLY"]
```

The app sends the `ChecklistType` enum **name** as `form_type`. There are four:

| App enum | `doc_no` | Plant |
|---|---|---|
| `MONTHLY` | CFPLA.C4.F.50a | W-202 |
| `QUARTERLY` | CFPLA.C4.F.50b | W-202 |
| `A185_MONTHLY` | CFPLB.C4.F.41 | A-185 |
| `A185_QUARTERLY` | CFPLB.C4.F.41 | A-185 |

The two W-202 names happen to match the Literal, so W-202 works. The two A-185 names
don't, so **Pydantic rejects the body with 422 before `create_checklist` ever runs** —
which is why none of the `_validate_and_payload` DRAFT leniency helps.

A-185 has both blocks in one controlled document (F.41), so `doc_no` alone cannot tell
the Monthly record from the Quarterly one — the enum name is the discriminator. The app
also drives PDF layout (`pdfFormFor`) and draft re-opening (matching `formType` back to
a `ChecklistType`) off this value, so it can't just send `QUARTERLY` instead.

### Reproduction (run against the current schema, output verbatim)

```python
from app.schemas import PmChecklistRequest
body = {'form_type': 'A185_QUARTERLY', 'doc_no': 'CFPLB.C4.F.41', 'status': 'DRAFT',
        'checklist_date': '2026-08-10', 'done_by': '', 'checked_by': '', 'verified_by': '',
        'remarks': '', 'items': [{'section': 'Ground floor', 'equipment': 'Inspection Feeder',
        'sr_no': 1, 'equipment_date': '2026-08-10',
        'checkpoint': 'tightness of foundation bolts', 'status': 'OK', 'remarks': ''}]}
PmChecklistRequest(**body)
```

```
1 validation error for PmChecklistRequest
form_type
  Input should be 'MONTHLY' or 'QUARTERLY' [type=literal_error, input_value='A185_QUARTERLY', input_type=str]
```

The same body with `form_type='QUARTERLY'` validates fine.

## The change

`app/schemas.py:302`

```python
PmFormType = Literal["MONTHLY", "QUARTERLY", "A185_MONTHLY", "A185_QUARTERLY"]
```

That's the whole fix for the reported bug. Nothing else is required:

- **Storage** — `create_checklist` writes the header into `PreventiveMaintenanceDoc.rows`
  (JSONB), so there's no column width to worry about. (`pm_checklist_link.form_type` is
  `String(16)`; `A185_QUARTERLY` is 14 chars, so even that would fit.)
- **Read-back** — `PmChecklistListItemDto.form_type` / `PmChecklistDetailDto` are plain
  `str`, so listing and detail already round-trip the new values.
- **DRAFT/SUBMITTED rules** — unchanged and already correct (`UNSET` allowed on DRAFT,
  rejected on SUBMITTED).
- `app/checklist_catalog.py` already carries `CATALOG_A185` and
  `DOC_NO["A185"] = "CFPLB.C4.F.41"`, so the backend already models A-185 elsewhere —
  only this Literal was missed.

## Tests to add

In the PM checklist tests, mirroring the existing create tests:

- POST `/preventive-maintenance/checklists` with `form_type='A185_QUARTERLY'`,
  `status='DRAFT'` and an `UNSET` item → **201**, and GET detail returns
  `form_type == 'A185_QUARTERLY'`.
- Same with `form_type='A185_MONTHLY'`, `status='SUBMITTED'`, all items `OK` → **201**.
- `form_type='NONSENSE'` still → **422** (the Literal must stay closed).

## Worth knowing (not part of this fix)

`app/api/pm_checklist_sync.py:_period_key` branches on `form_type == "QUARTERLY"`, so an
A-185 quarterly form would get a monthly period key (`YYYY-MM`) rather than `YYYY-Qn`.
That path only builds auto-docs from `pm_checklist_link` rows, which are seeded
`MONTHLY`/`QUARTERLY`, so it isn't reachable today. If A-185 assets are ever linked for
PM auto-sync, that comparison needs to become something like
`form_type.endswith("QUARTERLY")`.

## Verifying from the app side

Log in as an A-185 technician, open a preventive checklist, tick one item and **Save as
draft**. Before the fix the request 422s; after it, the save succeeds and the draft
appears in the preventive list with its Quarterly/Monthly label intact.
