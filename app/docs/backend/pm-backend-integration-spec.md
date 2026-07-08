# PM Backend Integration Spec (v1)

**Date:** 2026-07-08
**Audience:** Backend team (FastAPI + RDS Postgres)
**Goal:** Build the server side so the Android app can persist and sync Preventive Maintenance (PM) **plans** and **work orders**. Today the whole PM flow runs **local-only on the device** (Room); this spec is the contract to make it server-backed.

Companion overview: `pm-plans-backend-instructions.md`. This document is the detailed API + DB contract.

---

## 0. The flow in one paragraph

A **SUPERVISOR** creates a **PM Plan** = a checklist bound to one asset (`mt_asset_list.asset_id`) + a schedule + an assigned technician. A scheduler turns due plans into **work orders**. The **TECHNICIAN** acknowledges → starts → logs each checklist item (+ spare parts) → submits. The **SUPERVISOR** approves (or rejects back to the technician). **QC** acknowledges → approves (machine returns to service) or disapproves (back to supervisor). Status walks:

`NOTIFIED → ACKNOWLEDGED → IN_PROGRESS → SUBMITTED → SUPERVISOR_APPROVED → PENDING_QC → QC_APPROVED → CLOSED`

---

## 1. Conventions (apply to every endpoint)

| Topic | Rule |
|---|---|
| **Database** | RDS Postgres (`RdsBase` / `get_rds`), pgAdmin-visible. **Not** the local SQLite. |
| **Auth** | `Authorization: Bearer <token>` header on every call (same JWT as existing endpoints; dev-bypass token accepted in dev). Enforce roles server-side; return `403` with a JSON detail otherwise. |
| **IDs** | The app generates string ids **offline** (`plan-<uuid>`, `wo-…`, `item-…`, `log-…`, `spare-…`). **Accept them and upsert idempotently** (keyed on id) so a re-sync never duplicates. Do not renumber. |
| **Machine key** | `mt_asset_list.asset_id` everywhere (string). Always store a `machine_name` snapshot (assets can be renamed). |
| **User key** | `mt_users.id` (string). Store name snapshots where a person is displayed. |
| **Timestamps** | **Epoch milliseconds (int64)** on the wire — the app models all times as `Long` ms. Store as `TIMESTAMPTZ`; convert at the boundary. (Decision point — see §8. Recommend epoch-ms to avoid client conversion.) |
| **Enums** | Plain strings, exact values in §2. |
| **JSON casing** | `snake_case` keys (matches existing DTOs, e.g. `asset_id`, `machine_id`). |
| **Photos** | Uploaded to the backend as `multipart/form-data`; backend puts them in S3 and returns the URL. **No direct device→S3.** (Same as `POST /breakdowns/flag` / `work-done`.) |
| **Errors** | FastAPI default `{ "detail": "<message>" }` + proper 4xx/5xx. The app shows `detail` verbatim. |

---

## 2. Enum values (exact)

```
PmWoStatus       : DRAFT | NOTIFIED | ACKNOWLEDGED | IN_PROGRESS | SUBMITTED |
                   SUPERVISOR_APPROVED | PENDING_QC | QC_APPROVED | CLOSED | OVERDUE | CANCELLED
PmTriggerType    : TIME | USAGE
PmTaskLogStatus  : PENDING | PASS | FAIL | NOT_APPLICABLE
Role (mt_users)  : OPERATOR | TECHNICIAN | HEAD | QC_HEAD | QC_MEMBER | SUPERVISOR | ADMIN
```

---

## 3. Database schema (RDS Postgres)

```sql
-- 3.1 PLAN = checklist + machine + schedule (checklist items as JSONB).
CREATE TABLE IF NOT EXISTS mt_pm_plan (
    id                     VARCHAR(64)  PRIMARY KEY,           -- app-generated 'plan-…'
    machine_id             VARCHAR(64)  NOT NULL,              -- = mt_asset_list.asset_id
    machine_name           VARCHAR(255) NOT NULL,              -- snapshot of asset_name
    description            TEXT         NOT NULL DEFAULT '',
    items                  JSONB        NOT NULL DEFAULT '[]'::jsonb,   -- see §3.4
    trigger_type           VARCHAR(8)   NOT NULL DEFAULT 'TIME',
    trigger_interval       INTEGER      NOT NULL,              -- days (TIME) | running-hours (USAGE)
    next_due_at            BIGINT       NOT NULL,              -- epoch ms
    last_completed_at      BIGINT,                             -- epoch ms, null until first completion
    assigned_technician_id VARCHAR(64)  NOT NULL,              -- = mt_users.id
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by             VARCHAR(128),
    created_at             BIGINT       NOT NULL,              -- epoch ms
    updated_at             BIGINT       NOT NULL,
    CONSTRAINT ck_pm_plan_trigger CHECK (trigger_type IN ('TIME','USAGE'))
);
CREATE INDEX IF NOT EXISTS ix_pm_plan_machine ON mt_pm_plan (machine_id);
CREATE INDEX IF NOT EXISTS ix_pm_plan_due     ON mt_pm_plan (is_active, next_due_at);
CREATE INDEX IF NOT EXISTS ix_pm_plan_tech    ON mt_pm_plan (assigned_technician_id);

-- 3.2 WORK ORDER = one generated execution instance of a plan.
CREATE TABLE IF NOT EXISTS mt_pm_work_order (
    id                          VARCHAR(64) PRIMARY KEY,       -- 'wo-<plan>-<ts>'
    plan_id                     VARCHAR(64) NOT NULL,
    machine_id                  VARCHAR(64) NOT NULL,          -- = asset_id
    machine_name                VARCHAR(255) NOT NULL,         -- snapshot
    template_name               VARCHAR(255) NOT NULL,         -- plan/checklist name snapshot
    estimated_duration_minutes  INTEGER     NOT NULL DEFAULT 0,
    scheduled_date              BIGINT      NOT NULL,          -- epoch ms
    generated_at                BIGINT      NOT NULL,
    status                      VARCHAR(24) NOT NULL DEFAULT 'NOTIFIED',
    assigned_technician_id      VARCHAR(64) NOT NULL,
    assigned_technician_name    VARCHAR(128),
    acknowledged_at             BIGINT,
    started_at                  BIGINT,
    submitted_at                BIGINT,
    final_notes                 TEXT,
    supervisor_approved_by      VARCHAR(128),
    supervisor_approved_by_name VARCHAR(128),
    supervisor_approved_at      BIGINT,
    supervisor_rejected_at      BIGINT,
    supervisor_rejection_notes  TEXT,
    qc_acknowledged_by          VARCHAR(128),
    qc_acknowledged_by_name     VARCHAR(128),
    qc_acknowledged_at          BIGINT,
    qc_decided_by               VARCHAR(128),
    qc_decided_by_name          VARCHAR(128),
    qc_decided_at               BIGINT,
    qc_decision_notes           TEXT,
    qc_after_photo_url          TEXT,                          -- S3 URL
    qc_checklist                JSONB,                         -- QC sign-off blob
    closed_at                   BIGINT,
    escalation_tier             SMALLINT    NOT NULL DEFAULT 0,   -- 0..4
    spares                      JSONB       NOT NULL DEFAULT '[]'::jsonb,  -- see §3.5
    created_at                  BIGINT      NOT NULL,
    updated_at                  BIGINT      NOT NULL,
    CONSTRAINT ck_pm_wo_status CHECK (status IN (
        'DRAFT','NOTIFIED','ACKNOWLEDGED','IN_PROGRESS','SUBMITTED',
        'SUPERVISOR_APPROVED','PENDING_QC','QC_APPROVED','CLOSED','OVERDUE','CANCELLED')),
    CONSTRAINT ck_pm_wo_tier CHECK (escalation_tier BETWEEN 0 AND 4)
);
CREATE INDEX IF NOT EXISTS ix_pm_wo_status ON mt_pm_work_order (status);
CREATE INDEX IF NOT EXISTS ix_pm_wo_tech   ON mt_pm_work_order (assigned_technician_id);
CREATE INDEX IF NOT EXISTS ix_pm_wo_plan   ON mt_pm_work_order (plan_id);

-- 3.3 TASK LOG = one checklist step of a work order (queryable: "which checkpoints fail").
CREATE TABLE IF NOT EXISTS mt_pm_wo_task_log (
    id                   VARCHAR(64) PRIMARY KEY,
    wo_id                VARCHAR(64) NOT NULL REFERENCES mt_pm_work_order(id) ON DELETE CASCADE,
    template_item_id     VARCHAR(64),
    order_index          INTEGER     NOT NULL DEFAULT 0,
    title                VARCHAR(255) NOT NULL,
    description          TEXT        NOT NULL DEFAULT '',
    expected_result      TEXT        NOT NULL DEFAULT '',
    requires_photo       BOOLEAN     NOT NULL DEFAULT FALSE,
    requires_measurement BOOLEAN     NOT NULL DEFAULT FALSE,
    measurement_unit     VARCHAR(32),
    measurement_min      DOUBLE PRECISION,
    measurement_max      DOUBLE PRECISION,
    status               VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    measurement_value    DOUBLE PRECISION,
    photo_url            TEXT,                                 -- S3 URL
    notes                TEXT,
    completed_at         BIGINT,
    completed_by         VARCHAR(128),
    CONSTRAINT ck_pm_log_status CHECK (status IN ('PENDING','PASS','FAIL','NOT_APPLICABLE'))
);
CREATE INDEX IF NOT EXISTS ix_pm_log_wo ON mt_pm_wo_task_log (wo_id);
```

**3.4 `mt_pm_plan.items` JSONB element:**
```json
{ "id": "item-…", "order_index": 0, "title": "Check belt tension",
  "description": "…", "expected_result": "…",
  "requires_photo": true, "requires_measurement": false,
  "measurement_unit": null, "measurement_min": null, "measurement_max": null }
```

**3.5 `mt_pm_work_order.spares` JSONB element:**
```json
{ "id": "spare-…", "spare_name": "V-belt A-45", "quantity_used": 1,
  "unit": "pcs", "notes": null, "logged_at": 1690000000000, "logged_by": "4" }
```

---

## 4. Endpoints

### 4.1 Technician roster — `GET /users?role=TECHNICIAN`
Auth: any authenticated user. Response:
```json
[ { "id": "4", "name": "Anil Yadav", "role": "TECHNICIAN", "plant_id": "185-Koparkhairnae" } ]
```

### 4.2 Plans
| Method | Path | Role | Body |
|---|---|---|---|
| POST | `/pm/plans` | SUPERVISOR | plan object (§4.3) |
| GET | `/pm/plans` | any | — → `[plan]` |
| GET | `/pm/plans/{id}` | any | — → plan |
| PUT | `/pm/plans/{id}` | SUPERVISOR | plan object |
| DELETE | `/pm/plans/{id}` | SUPERVISOR | — (or `PATCH {is_active:false}`) |

### 4.3 Plan object (request & response)
```json
{
  "id": "plan-abc",
  "machine_id": "W202-0005",
  "machine_name": "Roaster 1",
  "description": "",
  "trigger_type": "TIME",
  "trigger_interval": 30,
  "next_due_at": 1691712000000,
  "last_completed_at": null,
  "assigned_technician_id": "4",
  "is_active": true,
  "created_by": "user-sup-001",
  "created_at": 1690000000000,
  "updated_at": 1690000000000,
  "items": [ /* §3.4 */ ]
}
```

### 4.4 Work orders — read
| Method | Path | Purpose |
|---|---|---|
| GET | `/pm/work-orders?technician_id={id}&status=NOTIFIED,ACKNOWLEDGED,IN_PROGRESS` | Technician's active tasks |
| GET | `/pm/work-orders?status=PENDING_QC` | QC inbox (unassigned) |
| GET | `/pm/work-orders?status=SUBMITTED` | Supervisor approvals |
| GET | `/pm/work-orders/{id}` | One WO incl. `task_logs[]` + `spares[]` |

**Work-order object** = every column in §3.2 as snake_case keys, plus:
```json
{ "...": "...", "task_logs": [ /* §4.6 element */ ], "spares": [ /* §3.5 */ ] }
```

### 4.5 Work orders — lifecycle transitions
All are `POST /pm/work-orders/{id}/…`, return the updated WO (or `{ "ok": true }`).

| Path | Role | Body |
|---|---|---|
| `/acknowledge` | TECHNICIAN | `{}` → status `ACKNOWLEDGED`, set `acknowledged_at` |
| `/start` | TECHNICIAN | `{}` → `IN_PROGRESS`, set `started_at` |
| `/submit` | TECHNICIAN | `{ "final_notes": "...", "task_logs": [...], "spares": [...] }` → `SUBMITTED` (see §4.6) |
| `/supervisor/approve` | SUPERVISOR | `{ "supervisor_id": "…", "supervisor_name": "…" }` → `SUPERVISOR_APPROVED` then `PENDING_QC` |
| `/supervisor/reject` | SUPERVISOR | `{ "supervisor_id": "…", "supervisor_name": "…", "notes": "…" }` → back to `IN_PROGRESS` |
| `/qc/acknowledge` | QC_MEMBER/HEAD | `{ "user_id": "…", "user_name": "…" }` |
| `/qc/override-acknowledge` | QC_HEAD | `{ "user_id": "…", "user_name": "…" }` |
| `/qc/approve` | QC | multipart (§4.7) → `QC_APPROVED` → `CLOSED`; machine returns to service |
| `/qc/disapprove` | QC | multipart (§4.7) → back to `SUBMITTED` |

### 4.6 Submit payload & task-log element
`task_logs` is the full array (one per checklist step), each:
```json
{ "id": "log-…", "template_item_id": "item-…", "order_index": 0,
  "title": "…", "description": "…", "expected_result": "…",
  "requires_photo": true, "requires_measurement": false,
  "measurement_unit": null, "measurement_min": null, "measurement_max": null,
  "status": "PASS", "measurement_value": null,
  "photo_url": "https://s3/…", "notes": null,
  "completed_at": 1690000500000, "completed_by": "4" }
```
> Task-log photos are uploaded first (see §4.7) and referenced here by `photo_url`.

### 4.7 Photo uploads
Two photo needs: per-task-log photos and the QC after-photo. Two options — **pick one and tell the app team**:

- **Option A (recommended): dedicated upload → URL.** `POST /pm/photos` `multipart` (field `file`, image/jpeg|png ≤ 10 MB) → `{ "url": "https://s3/…" }`. The app uploads, gets the URL, then puts it in the `submit` / `qc/approve` JSON. Cleanest; keeps transition bodies as JSON.
- **Option B: multipart transitions** (mirrors breakdowns). `qc/approve` = multipart with parts `user_id, user_name, checklist_json, notes, after_photo(file?)`. Task-log photos still need Option A since submit carries many.

Whichever: backend uploads to S3 and stores the returned URL in `photo_url` / `qc_after_photo_url` (never a device path).

### 4.8 Work-order generation (scheduler)
Recommended: a **backend cron/worker** (single source of truth; works offline). Algorithm (mirrors the app's current local scheduler):
```
for each plan where is_active:
    base      = plan.last_completed_at or plan.created_at
    next_due  = base + trigger_interval * (1 day in ms)      # TIME; USAGE = running-hours later
    if (next_due - now) <= PM_GENERATION_LEAD_DAYS
       and no open (non-CLOSED/CANCELLED) work order exists for plan.id:
         insert work_order(status='NOTIFIED', scheduled_date=next_due,
                           machine_id/name, template_name=plan.machine_name,
                           assigned_technician_id=plan.assigned_technician_id,
                           task_logs = snapshot of plan.items)
```
Also compute `escalation_tier` from days overdue and (later) push FCM to the tech/supervisor.

---

## 5. Field mapping (app ↔ DB ↔ JSON)

| App (Kotlin) | DB column | JSON key |
|---|---|---|
| `PmPlan.machineId` | `machine_id` | `machine_id` (= asset_id) |
| `PmTemplate.name` (plan name) | `machine_name` | `machine_name` |
| `PmPlan.triggerType/Interval` | `trigger_type/trigger_interval` | same |
| `PmPlan.assignedTechnicianId` | `assigned_technician_id` | same (= mt_users.id) |
| `PmTemplateItem.*` | `mt_pm_plan.items[]` | `items[]` |
| `PmWorkOrder.qcChecklistJson` | `qc_checklist` (JSONB) | `qc_checklist` |
| `PmWoTaskLog.photoLocalPath` | `photo_url` (S3) | `photo_url` |
| `PmWorkOrder.qcAfterPhotoLocalPath` | `qc_after_photo_url` (S3) | `qc_after_photo_url` |

---

## 6. Auth / role matrix

| Action | Allowed role |
|---|---|
| Create/edit/delete plan | SUPERVISOR |
| Read plans | any authenticated |
| Read my work orders | TECHNICIAN (own), SUPERVISOR/HEAD (all) |
| acknowledge/start/submit | TECHNICIAN (assignee) |
| supervisor approve/reject | SUPERVISOR |
| qc acknowledge/approve/disapprove | QC_MEMBER / QC_HEAD |

Enforce server-side (`403` + `{detail}` otherwise).

---

## 7. Suggested build order

1. **`GET /users?role=` + `mt_pm_plan` + Plans CRUD** — unblocks create/list/edit (the screen that's already built).
2. **Work-order table + read endpoints + generator cron** — plans start producing tasks.
3. **Lifecycle transitions + photo upload** — full execute → approve → QC loop.
4. FCM push for escalations (later).

---

## 8. Decisions the two teams must lock first

1. **Timestamp wire format:** epoch-ms (recommended, matches the app's `Long`) vs ISO-8601 `…Z`. Pick one for all PM endpoints.
2. **Photo strategy:** §4.7 Option A (upload→URL, recommended) vs B (multipart transitions).
3. **WO generation owner:** backend cron (recommended) vs app.
4. **Delete vs deactivate** plans: hard `DELETE` vs `is_active=false`.

Once 1–3 are locked, the app team wires Retrofit `PmPlanApi` / `PmWorkOrderApi` + repository sync to these contracts.
