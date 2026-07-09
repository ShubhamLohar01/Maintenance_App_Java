# Backend Requirement — List Submitted Breakdown F.06 Records

**For:** Backend (Python) team
**Date:** 2026-06-27
**Priority:** Required — the Android "Submitted F.06 records" list is built and waiting on this.

## Context

The app already **submits** F.06 Breakdown Maintenance Records via the existing
`POST /preventive-maintenance/breakdowns` (one DB row per register entry; the response returns
`{"ids": [...]}`). There is currently **no way to read them back**, so the app's Breakdown records
page can't show them. We need a **GET** to list those stored rows.

This mirrors how preventive checklists already work
(`GET /preventive-maintenance/checklists` → list).

---

## Endpoint 1 (REQUIRED): list submitted F.06 records

```
GET /preventive-maintenance/breakdowns
Authorization: Bearer <token>        # same auth as every other endpoint
```

**Returns:** `200 OK` with a **top-level JSON array** — one object per stored breakdown entry row
(the same rows created by the POST), **newest first** (order by `created_at` / `id` descending).
If none exist, return `[]` (HTTP 200) — **not** 404.

### Response object schema (field names must match EXACTLY — snake_case)

| JSON key | Type | Notes |
|---|---|---|
| `id` | integer | **Required.** The row id (same ids returned by the POST). |
| `doc_no` | string | `"CFPLA.C4.F.06"` |
| `record_date` | string | `"YYYY-MM-DD"` |
| `location` | string | |
| `machine_name` | string | |
| `equipment_model_no` | string | asset id / Sr. No. |
| `problem_in_brief` | string | |
| `type_of_maintenance` | string | |
| `part_of_machine` | string | |
| `temporary_reason` | string | |
| `duration_start` | string | `"HH:mm"` (may be empty) |
| `duration_end` | string | `"HH:mm"` (may be empty) |
| `machine_operator_sign` | string | |
| `maintenance_person_sign` | string | |
| `qc_clearance_sign` | string | |
| `verified_by` | string | |
| `created_at` | string | ISO-8601 timestamp, e.g. `"2026-06-27T11:35:00Z"` |

Rules:
- These are the **same field names** the POST already accepts, plus `id` and `created_at`.
- `id` must be present and an integer. All other fields: send the stored value, or `""` for
  empty (do not omit). The app tolerates missing string fields (defaults to `""`), but **never
  send `null` for `id`**.
- One object **per entry/row** (a single F.06 sheet submitted with 3 entries → 3 objects).

### Example response

```json
[
  {
    "id": 12,
    "doc_no": "CFPLA.C4.F.06",
    "record_date": "2026-06-27",
    "location": "W-202",
    "machine_name": "Band Sealer",
    "equipment_model_no": "W202-0007",
    "problem_in_brief": "Belt snapped",
    "type_of_maintenance": "Mechanical",
    "part_of_machine": "Belt",
    "temporary_reason": "",
    "duration_start": "10:00",
    "duration_end": "11:30",
    "machine_operator_sign": "Ravi",
    "maintenance_person_sign": "Suresh",
    "qc_clearance_sign": "QC Sam",
    "verified_by": "Head",
    "created_at": "2026-06-27T11:35:00Z"
  }
]
```

### Acceptance / quick test
```
curl -H "Authorization: Bearer <token>" https://<api-base>/preventive-maintenance/breakdowns
```
- Submit an F.06 via the app (or POST), then this GET returns that row with its `id`.
- Empty DB → `[]` with 200.
- Field names match the table above exactly (the app deserializes by these keys).

---

## Endpoint 2 (OPTIONAL / Phase 2): cross-device visibility of QC-approved breakdowns

Today `GET /breakdowns/open` returns only **not-CLOSED** breakdowns. So a QC-approved (CLOSED)
breakdown is only visible on the device where QC approved it. The app's "Operator breakdowns
(QC-approved can be completed)" section therefore only shows approved items present in that
device's local store.

To make QC-approved breakdowns appear on **any** device, do **one** of:
- **(a)** Include recently-CLOSED rows in the existing `GET /breakdowns/open` response (e.g. CLOSED
  within the last N days), **or**
- **(b)** Add `GET /breakdowns?status=CLOSED` (or `GET /breakdowns/approved`) returning the same
  `OpenBreakdownDto` shape already used by `/breakdowns/open` (id, asset_id, asset_name,
  reported_by, reporter_name, severity, description, status, reported_at, building).

Not required for Endpoint 1 to work — this only affects cross-device visibility of the
operator-breakdown section.

---

## Summary for the ticket
1. **Add `GET /preventive-maintenance/breakdowns`** → JSON array of stored F.06 entry rows
   (schema above), Bearer auth, newest first, `[]` when empty. **(required)**
2. *(optional, later)* expose QC-approved (CLOSED) breakdowns for cross-device read.
