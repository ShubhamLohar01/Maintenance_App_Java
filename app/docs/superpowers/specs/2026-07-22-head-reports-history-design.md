# Head Reports — History (Machine Readings, Floor Readings, Breakdowns, QC Activity) — Design

**Date:** 2026-07-22
**Status:** Approved by user, ready for implementation plan.

## Context

`HeadHomeScreen.kt:87` has a "Reports" tile ("Power consumption per warehouse") that is
currently wired (`NavGraph.kt:447`) to `Screen.ComingSoon.createRoute("Reports")` — a
placeholder, not a real screen. Every other Head-facing view is **live/current-state only**,
capped to a short window:

- `HeadBreakdownsScreen`/`HeadBreakdownsViewModel.kt:39-59` — open breakdowns + resolved
  capped to the last 7 days (`SEVEN_DAYS`).
- `HeadQcScreen`/`HeadQcViewModel.kt:43-59` — awaiting QC + decided capped to the last 7 days.
- `GET /head/reports/power` (`app/api/reports.py:62-79`) — per-warehouse **aggregate** kWh
  totals over a date range (`by_day`, `by_machine` sums), not a row-level list.
- `GET /floor-readings/system` (`app/api/floor_readings.py:80-120`) — one date's snapshot
  per building, no range, no history.

None of these satisfy "browse full history, date-wise, per machine/floor." This feature adds
that as a new Head-only Reports section, without touching the existing live/7-day operational
screens.

## Goal

A HEAD user opens Reports and gets four browsable history views: **Machine Readings**,
**Floor Readings**, **Breakdown History**, **QC Activity History**. Each is filterable by
date range; Machine Readings and Floor Readings are additionally filterable by
machine/floor. HEAD sees both plants (A-185 and W-202) by default, consistent with how
`scoped_buildings(user)` already scopes HEAD across both buildings elsewhere in the backend.

## Decisions (confirmed with user)

1. **QC Activity History shows the latest decision per record, not a full transition log.**
   No audit-log table exists today (`mt_breakdown_records` stores only the current
   `qc_status`/`qc_checked_by`/`qc_decided_at`). Building a true step-by-step audit trail
   would need a new table plus inserts wired into every existing QC transition endpoint —
   out of scope. Instead, a new date-ranged query surfaces the existing qc_* columns: if a
   record was approved, sent back, and re-approved, only the current/final decision shows.

## Architecture

### Navigation

- `HeadHomeScreen`'s Reports tile routes to a new `HeadReportsScreen` (hub with 4 entries)
  instead of `Screen.ComingSoon`.
- Reachable only from the Head screen tree (`presentation/head/`), consistent with how
  `NavGraph.kt:88` already routes `UserRole.HEAD` to its own screen tree separate from
  Dashboard (Supervisor/Technician) and QcHome (QC_HEAD/QC_MEMBER).

### Shared history view pattern

All four sections share one shape: **date range + optional entity filter (machine or
floor) + optional building filter (All / A-185 / W-202) + a row-level list**. Rather than
four bespoke screens, build one reusable `HistoryListScreen` + `HistoryListViewModel` pair,
parameterized by a small per-section spec (title, which filters apply, column layout, which
repository call to make) — mirrors the "one parameterized screen, not four" approach already
used for `ChecklistScreen`/`ChecklistType` and (per the A185 spec) `BreakdownFormScreen`.

Each section's spec:

| Section | Filters | Row columns | Source table |
|---|---|---|---|
| Machine Readings | building, machine, date range | date, machine, floor, kWh, source (RUN/SCHEDULE/MANUAL), started/ended, operator | `mt_machine_daily_kwh` |
| Floor Readings | building, floor, date range | date, floor, meter reading, system kWh | `mt_floor_utility_readings` |
| Breakdown History | building, date range, status (open/closed/all) | date reported, machine, severity, description, status, resolved-at | `mt_breakdown_records` |
| QC Activity History | building, date range | date decided, machine, decision (approved/disapproved), decided-by | `mt_breakdown_records` (qc_* columns); PM work-order QC decisions if time allows |

### Backend — new endpoints required (no backend code changes made in this session; handed off as a written prompt, matching this repo's `BACKEND_*_PROMPT.md` convention)

1. `GET /machines/daily-kwh/history?building=&machine_id=&from=&to=` — row-level
   `mt_machine_daily_kwh` list.
2. `GET /floor-readings/history?building=&floor=&from=&to=` — row-level
   `mt_floor_utility_readings` list.
3. `GET /breakdowns/history?building=&from=&to=&status=` — every breakdown in range
   regardless of status (supersedes the existing 7-day cap for this view only; the live
   7-day screens are untouched).
4. `GET /qc/history?building=&from=&to=` — every breakdown/work-order with a QC decision in
   range, latest-decision-only per the confirmed scope decision.

All four follow the existing `scoped_buildings(user)` convention: HEAD gets both buildings
unless a specific `building` is passed, same as `/head/reports/power` and
`/floor-readings/system` today.

### App-side components

- New Retrofit methods on the existing `AssetScheduleApi` (machine kWh history) and
  `FloorReadingApi` (floor reading history); a new history method on `BreakdownApi` (or a
  small new `HeadReportsApi` if that reads cleaner once in the plan) for breakdown + QC
  history.
- Repository methods added to the existing `AssetScheduleRepository`/`FloorReadingRepository`
  /`BreakdownRepository` implementations — no new repositories, following the existing
  one-repository-per-domain convention.
- New `presentation/head/reports/` package: `HeadReportsScreen` (hub), `HistoryListScreen`,
  `HistoryListViewModel`, plus the 4 section specs.

## Data flow

Technician/Head-facing screens already read these tables through existing repositories for
live/aggregate views; this feature adds sibling read paths (new endpoints, new repository
methods) for range-filtered row-level history. No writes, no schema changes, no new tables.

## Error handling

Until the backend ships the four endpoints, `HistoryListViewModel` surfaces the existing
`Result.Error` path (same pattern as every other repository call in the app) — the screen
shows a clear "couldn't load" state rather than crashing, and picks up real data as soon as
each endpoint is deployed.

## Testing

- Unit tests for `HistoryListViewModel`: date-range validation, filter changes, empty/error
  states — one test class parameterized or duplicated per section, consistent with existing
  ViewModel test coverage (`BreakdownListViewModelTest`, `PreventiveListViewModelTest`).
- No APK builds (per user preference). Final verification is the user exercising each
  section live in Android Studio once the corresponding backend endpoint is deployed.

## Risk

All four endpoints are new backend work happening outside this session — the app-side build
can proceed now against the contract above, but nothing will show real data until the
backend team implements and deploys it. The handoff prompt should be explicit that these are
read-only additions with no schema changes (endpoints 1–3 query existing tables as-is;
endpoint 4 queries existing columns on `mt_breakdown_records`), to keep backend turnaround
fast.
