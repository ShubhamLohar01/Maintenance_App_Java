# Backend Prompt — Operator Breakdown Flags → `mt_breakdown_records` (+ live status)

You're picking up the FastAPI + PostgreSQL backend at `D:\Maintenance module\backend\`. The Android app lets an operator flag a machine as broken; this prompt makes that flag **persist to `mt_breakdown_records`** and drive a status lifecycle the app reads back.

---

## Context

- An operator opens a machine (asset register, `mt_asset_list`) and taps **Report Breakdown** → picks severity + description → submits.
- The app already calls **`POST /breakdowns/flag`** (today, possibly into a different/legacy table or a no-op). We need that write to land in **`mt_breakdown_records`** as an OPEN record, keyed by **asset_id**.
- The app reflects status locally already (badge + blocks Production Start until the breakdown is QC-approved). For **other** devices (technician/Head) to see an operator's breakdown, add the read endpoint in §3.
- IDs are asset_ids now, e.g. `W202-0123` / `A185-0001` (NOT the legacy `MCH-*`). `machine_id` in the request body carries the asset_id.

---

## 1. Schema — `mt_breakdown_records`

You created this table for the F.06 Breakdown *Checklist* register. Operator flags now share it. Add a `source` to tell them apart, and the lifecycle columns the app drives. If your existing columns differ, map by meaning — the **bold** ones are what the contract needs.

```sql
ALTER TABLE mt_breakdown_records
    ADD COLUMN IF NOT EXISTS source        VARCHAR(16)  NOT NULL DEFAULT 'OPERATOR_FLAG', -- 'OPERATOR_FLAG' | 'F06_RECORD'
    ADD COLUMN IF NOT EXISTS asset_id      VARCHAR(64),
    ADD COLUMN IF NOT EXISTS reported_by   VARCHAR(64),   -- operator_id (mt_users.id)
    ADD COLUMN IF NOT EXISTS severity      VARCHAR(16),   -- 'CRITICAL' | 'MAJOR' | 'MINOR'
    ADD COLUMN IF NOT EXISTS description   TEXT,
    ADD COLUMN IF NOT EXISTS before_photo_url TEXT,
    ADD COLUMN IF NOT EXISTS status        VARCHAR(16) NOT NULL DEFAULT 'OPEN', -- see lifecycle below
    ADD COLUMN IF NOT EXISTS reported_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS acknowledged_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS resolved_by   VARCHAR(64),
    ADD COLUMN IF NOT EXISTS resolved_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS parts_used    TEXT,
    ADD COLUMN IF NOT EXISTS after_photo_url TEXT,
    ADD COLUMN IF NOT EXISTS qc_status     VARCHAR(16),   -- 'PENDING' | 'APPROVED' | 'DISAPPROVED'
    ADD COLUMN IF NOT EXISTS qc_decided_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS qc_decided_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_mt_breakdown_open
    ON mt_breakdown_records (asset_id, status)
    WHERE status <> 'QC_APPROVED';
```

### Status lifecycle (the app depends on these exact values)

```
OPEN ──(technician acks)──► ACKNOWLEDGED ──(technician resolves)──► PENDING_QC
       PENDING_QC ──(QC approves)──► QC_APPROVED      ← machine usable again
       PENDING_QC ──(QC disapproves)──► REOPENED ──(re-resolve)──► PENDING_QC
```

**A machine is "under breakdown" (Start blocked) while `status <> 'QC_APPROVED'`.** Only `QC_APPROVED` clears it.

---

## 2. `POST /breakdowns/flag` — write the OPEN record

The Android request body (existing `BreakdownFlagRequestDto`, snake_case):

```json
{
  "machine_id":  "W202-0123",
  "operator_id": "42",
  "severity":    "MAJOR",
  "description": "Sealing jaw not heating, packets opening",
  "raised_at":   1750768500000
}
```

Insert one row:

```sql
INSERT INTO mt_breakdown_records
    (source, asset_id, reported_by, severity, description, status, reported_at)
VALUES
    ('OPERATOR_FLAG', :machine_id, :operator_id, :severity, :description, 'OPEN',
     to_timestamp(:raised_at / 1000.0));
```

- `raised_at` is **epoch milliseconds** (same units as `/energy/runs/start`). Convert as shown.
- Response (existing `BreakdownFlagResponseDto`): `{ "id": "<new id>", "sync_status": "SYNCED" }`.
- Auth: `Depends(get_current_user)`.
- 404 if `asset_id` not in `mt_asset_list`.

The technician **acknowledge/resolve** and **QC approve/disapprove** endpoints the app already calls (`/breakdowns/{id}/qc/acknowledge`, `/qc/approve`, `/qc/disapprove`, plus the ack/resolve actions the sync worker sends) must `UPDATE` this same row's `status` + the matching `*_by/*_at` columns per the lifecycle.

---

## 3. `GET /breakdowns/open` — cross-device read (recommended)

So a technician/Head on a **different** device sees operator-raised breakdowns (today the app only knows about flags raised on its own device + push):

```
GET /breakdowns/open?plant_id=<W202|A185|both>
Authorization: Bearer <token>
```

Return every row with `status <> 'QC_APPROVED'`, scoped to plant (join `mt_asset_list` for `plant_id` + `building`):

```json
[
  {
    "id": "…",
    "asset_id": "W202-0123",
    "asset_name": "Sealing Machine 2",
    "reported_by": "42",
    "reporter_name": "Ravi Kumar",
    "severity": "MAJOR",
    "description": "…",
    "status": "OPEN",
    "reported_at": 1750768500000,
    "building": "W-202"
  }
]
```

`reported_at` = epoch ms. Empty `[]` when none open. (If you prefer, fold this into the existing breakdown sync the app already polls — the key requirement is that open records are fetchable per plant.)

---

## 4. Tests

- `test_flag_creates_open_record` — POST /breakdowns/flag → a `mt_breakdown_records` row with `source='OPERATOR_FLAG'`, `status='OPEN'`, asset_id/severity/description set, `reported_at` round-trips.
- `test_flag_unknown_asset_404`.
- `test_lifecycle_status_transitions` — ack → ACKNOWLEDGED, resolve → PENDING_QC, qc approve → QC_APPROVED, qc disapprove → REOPENED.
- `test_open_endpoint_excludes_qc_approved` — GET /breakdowns/open omits QC_APPROVED rows.
- `test_open_endpoint_plant_scope` — only the caller's plant; `both` sees all.
- `test_f06_and_flag_coexist` — an F.06 record (`source='F06_RECORD'`) is NOT returned by /breakdowns/open.

---

## 5. Process

1. Read the Android contracts: `data/remote/dto/BreakdownDtos.kt` (`BreakdownFlagRequestDto`/`Response`), `data/remote/api/BreakdownApi.kt`.
2. Add columns (§1) via Alembic — show diff before running. Don't drop the table (F.06 data lives there).
3. Wire `POST /breakdowns/flag` to insert (§2); confirm the ack/resolve/QC endpoints update `status`.
4. Add `GET /breakdowns/open` (§3).
5. Hand back: confirmed URLs, a sample `curl` flag→open round-trip, and any column-name deviations.

---

## 6. After deploy

- Operator flags a machine → row appears: `SELECT id, asset_id, status, severity FROM mt_breakdown_records WHERE source='OPERATOR_FLAG' ORDER BY reported_at DESC LIMIT 1;` → `OPEN`.
- App side (already built): the machine shows **UNDER BREAKDOWN** and Production Start is disabled until QC marks it `QC_APPROVED`.
- Technician notification + Head visibility across devices depends on §3 (or your existing breakdown sync) returning these open rows.
