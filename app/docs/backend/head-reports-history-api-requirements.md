# Backend Requirement — Head Reports: Breakdown & QC Activity History

**For:** Backend (Python/FastAPI) team
**Priority:** Required — the Android app's Head Reports "Breakdown History" and "QC Activity
History" screens are built and waiting on these two read-only endpoints.

## Context

Head Reports needs to browse full breakdown/QC history (date-ranged, status-filterable),
distinct from the existing `GET /breakdowns/open` (open-only) and `GET /head/breakdowns`
(open + CLOSED-within-7-days) endpoints, neither of which supports an arbitrary date range
or includes older closed records.

**Note:** Head Reports' other two sections — Machine Readings and Floor Readings — already
have their backend endpoints (`GET /reports/machines`, `GET /reports/floor-readings`, built
separately) and need no further backend work.

Both endpoints below are **read-only** and query the existing `mt_breakdown_records` table —
no schema changes, no new tables, no new columns.

## Endpoint 1: Breakdown history (all statuses)

`GET /breakdowns/history?from=&to=&plant_id=both&status=`

- `from`/`to`: ISO `YYYY-MM-DD`, inclusive. Filter by `reported_at` falling in this range.
- `plant_id`: `"W202"` / `"A185"` / `"both"` (default `"both"`) — same convention as the
  existing `GET /breakdowns/open?plant_id=` and `GET /head/breakdowns` endpoints. Apply the
  existing `scoped_buildings(user)`/`norm_plant()` helpers so a non-HEAD caller is
  automatically narrowed to their own plant if `plant_id` is omitted.
- `status`: optional exact match (`"OPEN"` / `"ACKNOWLEDGED"` / `"PENDING_QC"` / `"CLOSED"` /
  `"REOPENED"`). Omitted = every status.
- Unlike `/breakdowns/open`, this must include CLOSED rows with no time cap (not just the
  last 7 days).

### Response (200, JSON array, `[]` when empty, never 404)

Same shape as `OpenBreakdownDto` (`GET /breakdowns/open`) **plus** the resolved/QC-decision
fields that endpoint doesn't carry (because open breakdowns haven't reached them yet):

```json
[
  {
    "id": "bd-1",
    "asset_id": "A185-0007",
    "asset_name": "Roaster 1",
    "building": "A-185",
    "reported_by": "op-1",
    "reporter_name": "Ravi",
    "severity": "MAJOR",
    "description": "Belt snapped",
    "status": "CLOSED",
    "reported_at": 1690000000000,
    "acknowledged_by": "tech-1",
    "acknowledged_by_name": "Suresh",
    "acknowledged_at": 1690001000000,
    "resolved_by": "tech-1",
    "resolved_by_name": "Suresh",
    "resolved_at": 1690004000000,
    "parts_used": "V-belt A-45",
    "qc_status": "APPROVED",
    "qc_decided_by": "qc-1",
    "qc_decided_by_name": "Sam",
    "qc_decided_at": 1690005000000,
    "qc_decision_notes": "Looks good"
  }
]
```

All timestamp fields: epoch-ms (`Long`), matching every other endpoint's convention. Any
field with no value: send `null` — the app's DTO defaults every optional field, so omitting a
key is also safe, but never send a bare empty string for the numeric/timestamp fields.

## Endpoint 2: QC activity history

`GET /qc/history?from=&to=&plant_id=both`

- Same `from`/`to`/`plant_id` semantics as Endpoint 1, but filter by **`qc_decided_at`**
  falling in the range (not `reported_at`) — i.e. every breakdown that received a QC decision
  in the window, regardless of its current `status`.
- No `status` param — every row returned already has a QC decision by construction.
- **Scope note (confirmed with the app team):** this surfaces only the **current/latest** QC
  decision per record — there is no separate audit-log table, so a record that was approved,
  sent back, and re-approved appears once, showing only its final decision. A full
  step-by-step audit trail was explicitly descoped; do not build one for this endpoint.

### Response

Identical shape to Endpoint 1's response object (same `BreakdownHistoryDto` on the app side).

## Auth / scope

Both: `Authorization: Bearer <token>`, any authenticated role may call them (the app only
exposes these under Head Reports, but there's no reason to restrict server-side beyond
normal auth — same posture as `GET /head/reports/power`). Use `scoped_buildings(user)` so a
non-HEAD caller is automatically narrowed to their own plant if `plant_id` is omitted.

## Out of scope

No new tables, no new columns, no writes, no audit-log table.

## Acceptance / quick test

```
curl -H "Authorization: Bearer <token>" \
  "https://<api-base>/breakdowns/history?from=2026-07-01&to=2026-07-25"
curl -H "Authorization: Bearer <token>" \
  "https://<api-base>/breakdowns/history?from=2026-07-01&to=2026-07-25&plant_id=A185&status=CLOSED"
curl -H "Authorization: Bearer <token>" \
  "https://<api-base>/qc/history?from=2026-07-01&to=2026-07-25"
```

Each should return `200` with a JSON array (`[]` when empty, never `404`).
