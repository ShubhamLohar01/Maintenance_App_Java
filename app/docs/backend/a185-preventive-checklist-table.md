# Backend requirement — `doc_preventive_maintenance_a185`

**Requested by:** FactoryOps Android app (A-185 Preventive Checklist F.41, 2026-08-08)
**Scope:** one new table + routing in `preventive_maintenance.py` and `head.py`.
No app change, no change to any W-202 behaviour or stored row.

## Context

The app now serves A-185 technicians their own Preventive Maintenance Checklist,
**CFPLB.C4.F.41**, split into the paper form's two blocks:

| `form_type`      | Block     | Sr range | `doc_no`        |
|------------------|-----------|----------|-----------------|
| `A185_QUARTERLY` | Quarterly | 1–163    | `CFPLB.C4.F.41` |
| `A185_MONTHLY`   | Monthly   | 164–172  | `CFPLB.C4.F.41` |

Both blocks live in one controlled document, so they share a `doc_no`; `form_type` is what
tells two records apart. W-202 keeps sending `MONTHLY` / `QUARTERLY` with
`CFPLA.C4.F.50a` / `.50b`.

These submissions are to be stored in their own table, **`doc_preventive_maintenance_a185`**,
rather than mixed into `doc_preventive_maintenance`.

The request/response payloads do not change at all — same
`POST/PUT/GET /preventive-maintenance/checklists`, same `PmChecklistRequest`, same JSONB
`rows` document. Only *which table the row lands in* changes.

---

## 1. The migration

The new table mirrors `doc_preventive_maintenance` exactly. **Diff this against your live
table first** — the model in `app/models.py` is what's reproduced here, and the live table
predates it.

```sql
-- A-185's Preventive Maintenance Checklist (CFPLB.C4.F.41) submissions.
-- Same shape as doc_preventive_maintenance; the full header + items[] document
-- lives in `rows`, the scalar columns are duplicated for SQL reporting.
CREATE TABLE IF NOT EXISTS doc_preventive_maintenance_a185 (
    id          integer     PRIMARY KEY,
    month       varchar(7),                     -- YYYY-MM, from checklist_date
    checked_by  varchar(128),
    verified_by varchar(128),
    rows        jsonb       NOT NULL,           -- {form_type, doc_no, status, items:[...]}
    warehouse   varchar(16),                    -- 'A-185'
    created_by  varchar(128),
    created_at  timestamp   NOT NULL DEFAULT (now() AT TIME ZONE 'utc')
);

-- IMPORTANT: ids must never collide with doc_preventive_maintenance.
-- GET /preventive-maintenance/checklists/{id} takes ONE integer and has to find the
-- record in whichever table holds it, so the two id ranges are kept disjoint by
-- starting this table at 1,000,000. The old table would need a million rows to reach it.
CREATE SEQUENCE IF NOT EXISTS doc_preventive_maintenance_a185_id_seq
    AS integer
    START WITH 1000000
    INCREMENT BY 1
    OWNED BY doc_preventive_maintenance_a185.id;

ALTER TABLE doc_preventive_maintenance_a185
    ALTER COLUMN id SET DEFAULT nextval('doc_preventive_maintenance_a185_id_seq');

-- Newest-first listing, and the status filter the list endpoint applies.
CREATE INDEX IF NOT EXISTS ix_pm_a185_created_at
    ON doc_preventive_maintenance_a185 (created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_pm_a185_status
    ON doc_preventive_maintenance_a185 ((rows ->> 'status'));
CREATE INDEX IF NOT EXISTS ix_pm_a185_form_type
    ON doc_preventive_maintenance_a185 ((rows ->> 'form_type'));
```

**Sanity check before running** — confirm the old table can't already reach the new range:

```sql
SELECT COALESCE(MAX(id), 0) AS max_existing_id FROM doc_preventive_maintenance;
-- must be well under 1000000; if it isn't, raise the START WITH above it and tell us.
```

**Rollback** is `DROP TABLE doc_preventive_maintenance_a185;` — nothing else references it.

### One thing to check before this goes live: payload size

A-185's Quarterly form is much bigger than anything W-202 submits. A complete sheet is
**1,144 items** in one JSONB document — 161 machines, each contributing its checkpoints plus
the 4 after-maintenance checks — roughly **170 KB of JSON per POST/PUT**. (Monthly is 15
items and trivial.)

`jsonb` handles that comfortably, but please confirm nothing in front of the API truncates
or rejects it: nginx `client_max_body_size`, any API-gateway body limit, and the request
timeout on a slow factory connection. The app also PUTs the whole document on every draft
save, so this is a repeated cost, not a one-off.

---

## 2. `app/models.py`

Add next to `PreventiveMaintenanceDoc`:

```python
class PreventiveMaintenanceDocA185(RdsBase):
    """A-185 PM checklist submissions (CFPLB.C4.F.41). Identical shape to
    PreventiveMaintenanceDoc but a separate table, so the two plants' controlled
    documents stay in their own registers.

    Its id sequence starts at 1,000,000 so ids never collide with the W-202 table —
    GET /checklists/{id} resolves an id against both tables and exactly one can match."""
    __tablename__ = "doc_preventive_maintenance_a185"

    id:          Mapped[int]        = mapped_column(Integer, primary_key=True, autoincrement=True)
    month:       Mapped[str | None] = mapped_column(String(7), nullable=True)
    checked_by:  Mapped[str | None] = mapped_column(String(128), nullable=True)
    verified_by: Mapped[str | None] = mapped_column(String(128), nullable=True)
    rows:        Mapped[dict]       = mapped_column(PortableJSONB, nullable=False)
    warehouse:   Mapped[str | None] = mapped_column(String(16), nullable=True)
    created_by:  Mapped[str | None] = mapped_column(String(128), nullable=True)
    created_at:  Mapped[datetime]   = mapped_column(DateTime, default=datetime.utcnow)
```

`PortableJSONB` (not raw `JSONB`) for the same reason the existing model uses it — the test
harness runs on SQLite.

Note the sequence lives in the migration, not the model: on SQLite the plain
`autoincrement=True` is fine, because tests only ever exercise one table at a time.

---

## 3. `app/api/preventive_maintenance.py`

### 3a. Route writes by document

```python
from ..models import PreventiveMaintenanceDoc, PreventiveMaintenanceDocA185, MtUser

A185_PM_DOC_NO = "CFPLB.C4.F.41"


def _model_for(req: PmChecklistRequest):
    """Which register this submission belongs in. Keyed on the controlled document
    number — A-185's Quarterly and Monthly blocks are one document and share it."""
    if (req.doc_no or "").strip().upper() == A185_PM_DOC_NO:
        return PreventiveMaintenanceDocA185
    return PreventiveMaintenanceDoc


def _get_doc(db: Session, checklist_id: int):
    """Find a checklist by id in whichever register holds it. The id ranges are
    disjoint (the A-185 sequence starts at 1,000,000), so at most one can match."""
    return (
        db.get(PreventiveMaintenanceDoc, checklist_id)
        or db.get(PreventiveMaintenanceDocA185, checklist_id)
    )
```

### 3b. `create_checklist` — two changed lines

```python
payload = _validate_and_payload(req, user)
Model = _model_for(req)                      # NEW
doc = Model(                                 # was: PreventiveMaintenanceDoc(
    month=(req.checklist_date or "")[:7],
    checked_by=req.checked_by,
    verified_by=req.verified_by,
    created_by=user.username,
    warehouse=user.plant_id,                 # NEW — 'A-185' / 'W-202', handy for SQL reporting
    rows=payload,
)
```

### 3c. `update_checklist` — one changed line

```python
doc = _get_doc(db, checklist_id)             # was: db.get(PreventiveMaintenanceDoc, checklist_id)
if doc is None:
    raise HTTPException(status_code=404, detail="not found")
```

The rest of the function is unchanged — it writes through whichever instance it got. A draft
never moves table, because `doc_no` can't change between saves for a given form.

### 3d. `list_checklists` — read both registers

```python
docs = []
for Model in (PreventiveMaintenanceDoc, PreventiveMaintenanceDocA185):
    docs.extend(db.query(Model).all())
# One merged newest-first ordering across both tables.
docs.sort(key=lambda d: (d.created_at or datetime.min, d.id), reverse=True)
```

Everything below that line — the `status` allow-list filter and the `PmChecklistListItemDto`
construction — is unchanged; `_header_and_items` works on either model.

### 3e. `get_checklist` — one changed line

```python
doc = _get_doc(db, checklist_id)             # was: db.get(PreventiveMaintenanceDoc, checklist_id)
```

---

## 4. `app/api/head.py` — `head_checklist_records`

Without this, A-185 checklists exist but never appear in the Head's oversight screens.
Same merge as 3d, around line 164:

```python
docs = []
for Model in (PreventiveMaintenanceDoc, PreventiveMaintenanceDocA185):
    docs.extend(db.query(Model).all())
docs.sort(key=lambda d: (d.created_at or datetime.min, d.id), reverse=True)
```

The existing `plant_id` scoping below it already does the right thing — it reads
`rows["plant_id"]`, which the app fills from the submitting user, so an A-185 record is
correctly withheld from a W-202-only Head.

---

## 5. `app/api/pm_checklist_sync.py` — leave alone

This auto-ticks W-202 form rows from QC-closed PM work orders via `pm_checklist_link`, which
is seeded only for `MONTHLY` / `QUARTERLY`. It has no A-185 links, so it will never touch the
new table and needs no change. If you later want the same auto-fill for A-185, that's its own
piece of work (seeding `pm_checklist_link` for `A185_*` first).

---

## 6. Tests

- `POST /preventive-maintenance/checklists` with `doc_no='CFPLB.C4.F.41'`,
  `form_type='A185_QUARTERLY'` → **201**, and the row lands in
  `doc_preventive_maintenance_a185`, not `doc_preventive_maintenance`
- the same POST with `form_type='MONTHLY'`, `doc_no='CFPLA.C4.F.50a'` → row lands in
  `doc_preventive_maintenance` (unchanged behaviour)
- `GET /checklists` returns records from **both** tables, newest first, correctly interleaved
- `GET /checklists/{id}` resolves an A-185 id and a W-202 id, and 404s on an unknown one
- `PUT /checklists/{id}` on an A-185 draft updates the A-185 row and leaves the W-202 table
  untouched
- `?status=SUBMITTED` filters A-185 drafts out just as it does W-202 drafts
- `GET /head/checklist-records` includes an A-185 SUBMITTED record for an A-185-scoped Head,
  and excludes it for a W-202-only Head

---

## 7. Verifying from the app side

Log in as an A-185 technician → **Checklist Form → Preventive Checklist**. The toggle shows
**Quarterly / Monthly**; Quarterly lists Ground floor / Mezzanine floor / Packing area
(163 items), Monthly lists Air curtain + Miscellaneous (9 items). Fill one item, **Save
draft**, then reopen it from the records list — it should come back with the entry intact.

Then confirm placement directly:

```sql
SELECT id, month, warehouse, rows ->> 'form_type' AS form_type, rows ->> 'status' AS status
FROM doc_preventive_maintenance_a185
ORDER BY id DESC LIMIT 5;
```

The id should be ≥ 1000000 and `form_type` one of `A185_QUARTERLY` / `A185_MONTHLY`.

---

## Note on the alternative we did not take

Merging both plants into `doc_preventive_maintenance` and filtering on
`rows->>'form_type'` would need no migration and no id juggling. A separate table was asked
for, and it does keep the two controlled registers cleanly apart — the cost is the shared-id
constraint above, which every future reader of these tables has to respect. If you'd rather
have the single table, say so and the app side still needs no change; only §3–§4 differ.
