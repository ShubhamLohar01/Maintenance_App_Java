# Backend Instructions — Preventive Maintenance (PM) Plans

**Date:** 2026-07-08
**Audience:** Backend team (FastAPI + RDS Postgres)
**Status:** Requirements for making PM plans server-backed. Today the whole PM flow is **local-only on the Android device** (Room); nothing is persisted server-side yet.

---

## 1. What a "PM Plan" is

A supervisor creates a **PM Plan** = a maintenance **checklist** bound to **one asset** + a **schedule**. From a plan, a **work order** is generated when it comes due; a technician executes it; a supervisor and QC approve it.

The supervisor form (already built in the app) collects exactly:

- **Machine** — searched and picked from the asset register (`mt_asset_list`, by `asset_name`). This is the plan's identity; there is no separate "name".
- **Description** (optional)
- **Schedule** — trigger type (`TIME` = calendar days, `USAGE` = running hours) + interval
- **Assigned technician** — a `TECHNICIAN`-role user
- **Checklist items** — ordered steps (title, instructions, expected result, photo-required, measurement-required + unit/min/max)

**Canonical rule:** the machine reference everywhere is `mt_asset_list.asset_id`. Do **not** use the legacy `machines` table for PM — it is deprecated for this feature.

---

## 2. Endpoints the app needs

### 2.1 Asset list — ALREADY EXISTS, just confirm
- `GET /mt-machines` (backed by `mt_asset_list`) already returns `asset_id` + `asset_name`. The plan form fetches the full register and filters client-side.
- **Action:** none — just confirm it returns every asset a supervisor may pick.

### 2.2 Technician roster — NEW (needed now)
The form assigns a plan to a technician; the app currently hard-codes this list.

- `GET /users?role=TECHNICIAN`
- Returns from `mt_users` (filter `role='TECHNICIAN'`; optionally scope by the caller's plant):
```json
[
  { "id": "4", "name": "Anil Yadav", "role": "TECHNICIAN", "plant_id": "185-Koparkhairnae" },
  { "id": "5", "name": "Manish Kumar", "role": "TECHNICIAN", "plant_id": "W-202-Koparkhairne" }
]
```
- Auth: any authenticated user (supervisor is the caller).

### 2.3 PM Plan CRUD — NEW (core)
- `POST   /pm/plans`         — supervisor creates a plan
- `GET    /pm/plans`         — list (for the supervisor "Plan" tab)
- `GET    /pm/plans/{id}`    — detail incl. checklist items
- `PUT    /pm/plans/{id}`    — update (edit checklist/schedule/technician)
- `DELETE /pm/plans/{id}`    — or `PATCH {is_active:false}` to deactivate

**Request/response body:**
```json
{
  "id": "plan-…",                 // app-generated; accept & preserve (idempotent upsert)
  "machine_id": "W202-0005",      // = mt_asset_list.asset_id
  "machine_name": "Roaster 1",    // snapshot of asset_name at save time
  "description": "",
  "trigger_type": "TIME",         // TIME | USAGE
  "trigger_interval": 30,         // days (TIME) or running-hours (USAGE)
  "next_due_at": "2026-08-07T00:00:00Z",
  "last_completed_at": null,
  "assigned_technician_id": "4",  // = mt_users.id
  "is_active": true,
  "items": [
    {
      "id": "item-…", "order_index": 0,
      "title": "Check belt tension", "description": "…", "expected_result": "…",
      "requires_photo": true, "requires_measurement": false,
      "measurement_unit": null, "measurement_min": null, "measurement_max": null
    }
  ]
}
```
- Auth: **SUPERVISOR** creates/edits; others read.

### 2.4 Work orders — NEXT phase
Once plans persist, the execution side (list my WOs, acknowledge, start, log items, submit, supervisor approve/reject, QC approve/reject) needs endpoints. Design mirrors the app's current local `PmWorkOrder` lifecycle: `NOTIFIED → ACKNOWLEDGED → IN_PROGRESS → SUBMITTED → SUPERVISOR_APPROVED → PENDING_QC → QC_APPROVED → CLOSED`.

---

## 3. Who generates work orders?

Decide explicitly:

- **Recommended: the backend** runs a scheduled job (cron/worker) that scans active plans, computes `next_due_at`, and inserts a work order when due. Single source of truth, works even when no device is online. The app's on-device `PmSchedulerWorker` then becomes a fallback/none.
- Alternative: the app keeps generating and syncs up — messier (duplicate suppression across devices).

---

## 4. Database schema (RDS Postgres, `RdsBase`, pgAdmin-visible)

Follows the "Mixed" model agreed earlier: real tables for plan + task logs; JSONB for the write-once checklist definition. The earlier `mt_pm_templates` table is **folded into the plan** (`items` JSONB), because in the UI a plan owns its checklist.

```sql
-- One PM plan = a checklist bound to one asset + a schedule.
CREATE TABLE IF NOT EXISTS mt_pm_plan (
    id                     VARCHAR(64)  PRIMARY KEY,          -- app-generated ('plan-…')
    machine_id             VARCHAR(64)  NOT NULL,             -- = mt_asset_list.asset_id
    machine_name           VARCHAR(255) NOT NULL,             -- snapshot of asset_name
    description            TEXT         NOT NULL DEFAULT '',
    items                  JSONB        NOT NULL DEFAULT '[]'::jsonb,  -- checklist steps
    trigger_type           VARCHAR(8)   NOT NULL DEFAULT 'TIME',
    trigger_interval       INTEGER      NOT NULL,
    next_due_at            TIMESTAMPTZ  NOT NULL,
    last_completed_at      TIMESTAMPTZ,
    assigned_technician_id VARCHAR(64)  NOT NULL,             -- = mt_users.id
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by             VARCHAR(128),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_pm_plan_trigger CHECK (trigger_type IN ('TIME','USAGE'))
);
CREATE INDEX IF NOT EXISTS ix_pm_plan_machine ON mt_pm_plan (machine_id);
CREATE INDEX IF NOT EXISTS ix_pm_plan_due     ON mt_pm_plan (is_active, next_due_at);
CREATE INDEX IF NOT EXISTS ix_pm_plan_tech    ON mt_pm_plan (assigned_technician_id);

-- Generated work orders (execution). Task logs are a real table (queryable);
-- the machine/plan snapshot rides on scalar columns. See the full mixed-model DDL
-- (mt_pm_work_orders + mt_pm_wo_task_logs) in the PM DDL draft — machineId there is
-- also an asset_id, and add a machine_name snapshot column to match this plan table.
```

> The full `mt_pm_work_orders` + `mt_pm_wo_task_logs` DDL was drafted separately (mixed model: JSONB snapshot header + spares, task logs as a real table). Reuse it as-is, with `machine_id = asset_id` and a `machine_name` snapshot.

---

## 5. Conventions to match existing tables

- **Machine key:** `mt_asset_list.asset_id` everywhere. Store a `machine_name` snapshot (assets can be renamed).
- **Technician:** `assigned_technician_id = mt_users.id`.
- **IDs:** the app generates string ids offline (`plan-…`, `item-…`, `wo-…`); accept them and upsert idempotently so a re-sync doesn't duplicate.
- **Timestamps:** `TIMESTAMPTZ` in DB. Agree the wire format with the app team (epoch-ms vs ISO-8601 `…Z`); the existing PM checklist API uses ISO `…Z`.
- **Auth/roles:** SUPERVISOR creates/edits plans; TECHNICIAN reads assigned; QC reads for sign-off. Enforce server-side (return 403 otherwise) — the app surfaces that as an error.
- **Two DBs:** these are maintenance tables → **RDS Postgres** (`RdsBase` / `get_rds`), not the local SQLite.

---

## 6. Summary of the ask

| # | Item | Status | Priority |
|---|------|--------|----------|
| 1 | `GET /mt-machines` returns asset_id + asset_name | exists | confirm |
| 2 | `GET /users?role=TECHNICIAN` from mt_users | **new** | high |
| 3 | `mt_pm_plan` table + `POST/GET/PUT/DELETE /pm/plans` | **new** | high |
| 4 | Work-order tables + endpoints + generator | **new** | next phase |
| 5 | Decide plan→WO generation owner (recommend backend) | decision | high |
