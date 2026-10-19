# Backend task — PM work orders for technicians (mt_pm_plan → mt_pm_work_order)

**Goal:** when a supervisor creates/assigns a PM plan (already saved in `mt_pm_plan`), the
assigned technician's app must see the resulting PM task. The app already POSTs plans and
now **fetches work orders** from `GET /pm/work-orders?technician_id=`. You need to
(1) **generate** work orders from due plans, and (2) **serve** them on that endpoint.

Right now nothing turns a plan into a work order on the server, so technicians see nothing.

---

## 1. Work-order generator (scheduled job — run hourly)

For each active plan in `mt_pm_plan` where `is_active = true`:

1. Compute `next_due_at` (already a column; for TIME: `(last_completed_at or created_at) +
   trigger_interval days`).
2. If `next_due_at` is **within the next 7 days**, AND there is **no open work order** for
   this plan (no `mt_pm_work_order` row with `plan_id = plan.id` and `status` in
   `NOTIFIED, ACKNOWLEDGED, IN_PROGRESS, SUBMITTED, SUPERVISOR_APPROVED, PENDING_QC`),
   then INSERT one `mt_pm_work_order` row:
   - `id` = server-generated stable id
   - `plan_id` = `plan.id`
   - `machine_id` = `plan.machine_id`, `machine_name` = `plan.machine_name`
   - `template_name` = `plan.machine_name`  *(the app shows this as the PM title)*
   - `scheduled_date` = `next_due_at`
   - `generated_at` = now
   - `status` = `"NOTIFIED"`
   - `assigned_technician_id` = `plan.assigned_technician_id`
   - `assigned_technician_name` = `plan.assigned_technician_name`
   - `task_logs` = copy of `plan.items` (the checklist array) → each item gets a
     generated `id` and `status = "PENDING"`
3. **Idempotent:** never create a second open WO for the same plan.

*(This is the same rule the app's on-device scheduler used; it's moving to the server so it
works across devices. 7-day lead + the "no open WO" guard are the important bits.)*

---

## 2. `GET /pm/work-orders`  — the endpoint the app calls

- **Query params:** `technician_id` (filter to that technician), optional `status`.
- **Auth:** Bearer JWT.
- **Returns:** JSON array of that technician's rows from `mt_pm_work_order`.

The app deserializes each row into this **exact** shape (snake_case keys).
⚠️ **CRITICAL: every timestamp is epoch MILLISECONDS as a JSON number** — not an ISO
string, not seconds. (`scheduled_date`, `generated_at`, `*_at`, `created_at`, `updated_at`.)

```json
{
  "id": "wo-...",
  "plan_id": "plan-...",
  "machine_id": "A185-CH-01",
  "machine_name": "Chiller 1",
  "template_name": "Chiller 1",
  "estimated_duration_minutes": 60,
  "scheduled_date": 1731560400000,
  "generated_at": 1731557000000,
  "status": "NOTIFIED",
  "assigned_technician_id": "user-tech-007",
  "assigned_technician_name": "Ravi Kumar",
  "acknowledged_at": null,
  "started_at": null,
  "submitted_at": null,
  "final_notes": null,
  "supervisor_approved_by": null,
  "supervisor_approved_by_name": null,
  "supervisor_approved_at": null,
  "supervisor_rejected_at": null,
  "supervisor_rejection_notes": null,
  "qc_acknowledged_by": null,
  "qc_acknowledged_by_name": null,
  "qc_acknowledged_at": null,
  "qc_checklist": null,
  "closed_at": null,
  "task_logs": [
    {
      "id": "log-...",
      "template_item_id": "item-...",
      "order_index": 0,
      "title": "Check refrigerant pressure",
      "description": "Read gauge at compressor",
      "expected_result": "4–6 bar",
      "requires_photo": false,
      "requires_measurement": true,
      "measurement_unit": "bar",
      "measurement_min": 4,
      "measurement_max": 6,
      "status": "PENDING",
      "measurement_value": null,
      "photo_url": null,
      "notes": null,
      "completed_at": null,
      "completed_by": null
    }
  ],
  "spares": [],
  "created_at": 1731557000000,
  "updated_at": 1731557000000
}
```

`status` enum (string): `NOTIFIED, ACKNOWLEDGED, IN_PROGRESS, SUBMITTED,
SUPERVISOR_APPROVED, PENDING_QC, QC_APPROVED, CLOSED` (also tolerate `OVERDUE`, `CANCELLED`).
Unknown/missing timestamps → `null`.

---

## 3. (Next phase, not blocking this) write-back endpoints

For the technician to **act** on a PM (acknowledge / start / log items / submit) and have it
persist server-side, you'll also need transition endpoints (e.g. `POST /pm/work-orders/{id}/
acknowledge`, `/start`, `/submit`, plus supervisor-approve and QC). Not required for the
technician to **see** the tasks — that's just steps 1 + 2 — but it's the immediate follow-up
so their actions don't stay local-only.

---

## 4. `GET /pm/plans` — NOW REQUIRED (technician "Assigned PM plans" view)

The technician screen now also lists the **plans** assigned to them straight from
`mt_pm_plan`, so an assignment is visible immediately (before the work-order generator
runs). The app calls **`GET /pm/plans`** (Bearer JWT) and filters client-side by
`assigned_technician_id`, so returning **all active plans is fine** (or pre-filter to the
JWT user — either works).

Return a JSON array; **timestamps in epoch milliseconds** (same rule as work orders).
Field shape the app expects per plan:

```json
{
  "id": "plan-...",
  "machine_id": "A185-CH-01",
  "machine_name": "Chiller 1",
  "description": "Monthly chiller service",
  "items": [ { "id": "item-...", "order_index": 0, "title": "...", "description": "...",
              "expected_result": "...", "requires_photo": false, "requires_measurement": true,
              "measurement_unit": "bar", "measurement_min": 4, "measurement_max": 6 } ],
  "trigger_type": "TIME",
  "trigger_interval": 30,
  "next_due_at": 1731560400000,
  "last_completed_at": null,
  "assigned_technician_id": "user-tech-007",
  "is_active": true,
  "created_by": "user-sup-001",
  "created_at": 1731557000000,
  "updated_at": 1731557000000
}
```

⚠️ **ID match matters:** `assigned_technician_id` must equal the id the technician logs in
with (the auth response `userId` / `mt_users.id`). If login returns a different id than
`mt_users.id`, the technician's plans won't match and the list stays empty — worth a quick
check.

## Notes
- Path convention `pm/...` matches the `POST /pm/plans` you already serve.
- How to test: supervisor assigns a plan due within 7 days → generator makes a WO →
  `GET /pm/work-orders?technician_id=<that tech>` returns it → it appears on the technician's
  PM Tasks screen.
