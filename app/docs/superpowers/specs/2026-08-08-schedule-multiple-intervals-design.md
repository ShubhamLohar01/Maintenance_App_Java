# Asset schedule — several daily intervals (split shifts)

**Date:** 2026-08-08
**Status:** frontend implemented; backend handoff at
`docs/backend/asset-schedule-multiple-intervals.md`

## Goal

A supervisor can give a schedulable asset more than one recording window in a day —
10:00–13:00 and 15:00–22:30 — instead of being forced to choose between one long window
that over-counts the break, or losing the second stretch.

The windows repeat identically on every weekday the schedule already selects. This is
deliberately **not** a per-weekday timetable.

## Decisions

1. **Same intervals every selected day.** One list of windows plus the existing weekday
   chips. Confirmed with the user against the per-weekday alternative, which would have
   meant a much larger editor, payload and backend change.
2. **JSONB column, not a child table.** `schedule_intervals` on `mt_asset_list`, matching
   the existing grain of the schedule (which already stores `schedule_days` as a
   comma-separated string on the row rather than normalising it).
3. **One `mt_machine_daily_kwh` row per day, kWh summed** — not one row per interval. This
   is how a manually-run machine already behaves: a start-stop-start-stop day collapses to
   one daily row with the stretches in `mt_machine_run_segment`. Keeping that shape means
   `client_run_id` stays `sched-{asset}-{date}`, its UNIQUE-key idempotency is untouched,
   and **no report or query anywhere needs to change**.

## Model

```
ScheduleInterval(startMin, endMin)     minute-of-day, IST
ElectricAssetSchedule.intervals        the day's windows, sorted; empty = no schedule
ElectricAssetSchedule.startMin/endMin  derived summary (first start, last end)
```

`intervals` is the source of truth. `startMin`/`endMin` survive only because the backend
keeps writing them, and `is_schedule_locked()`, the `POST /energy/runs/start` 409 guard and
the `generate_due_rows` filter all read them. Treat them as a cached summary.

`is24h` still overrides everything: whole day, windows ignored.

### Reading either backend

`AssetScheduleDto.toIntervals()` prefers the new `intervals` array and falls back to the
`start_min`/`end_min` pair when it's absent, dropping any malformed entry. So the app works
against a backend with or without this change — an important property, because the app and
the backend ship separately.

On write the app sends **both**: the full `intervals` list *and* the derived pair, so a
backend that predates split shifts reads the pair as its one window rather than failing.

## Editor

`ScheduleEditorState.intervals` replaces the single `startMin`/`endMin` pair. One row per
window — two time-picker buttons and a remove button — with **+ Add interval** beneath, and
a running `Total: 9.5 h/day`.

A newly added window starts an hour after the current latest end and runs two hours, so it
is valid and non-overlapping the moment it appears; the supervisor adjusts it instead of
having to clear a clash first. Capped at 6 windows — a split shift, not a timetable.

The last window can't be removed: a schedule with no window is just no schedule, and
"Clear schedule" already exists for that.

`validationError` is a single derived property covering: at least one window, each end
after its start, and no two overlapping. Overlaps name **both** offending windows
("Intervals overlap: 10:00 AM–3:00 PM and 1:00 PM–10:30 PM") rather than failing
generically. It gates the Save button and renders inline, so the sheet never has to be
submitted to find out what's wrong. Boundaries are exclusive, so back-to-back windows
(13:00–15:00 then 15:00–18:00) are legal.

Saving sorts by start time, so stored order is always canonical regardless of entry order.

## Card summary

One or two windows are spelled out — `10:00 AM–1:00 PM, 3:00 PM–10:30 PM · ~209 kWh/day ·
Active`. Three or more collapse to `4 intervals · 11.5 h/day · …`, which is where spelling
them out stops being readable on a phone.

## Backend (separate repo)

One nullable `jsonb` column, interval validation in the PUT, and summing in
`generate_due_rows`. No new endpoint, no new table, no backfill — a row with
`schedule_intervals` NULL keeps working off the legacy pair. Full instructions, including
the exact helper functions and the SQL, are in
`docs/backend/asset-schedule-multiple-intervals.md`.

**One semantic change worth repeating:** `hours` (and so `est_daily_kwh`) becomes the *sum*
of the windows rather than the length of the one window. Identical for every schedule that
exists today, but any consumer assuming `hours == (end_min - start_min) / 60` is wrong once
a second window is added.

## Sequencing

The app tolerates either backend, but until the column lands a saved second interval would
be silently dropped — the backend would keep only the derived pair. **Ship the backend
first**, or the editor offers a control that half-works.

## Testing

`ScheduleIntervalsTest` — 13 tests: interval span and validity, overlap detection
(including the exclusive boundary and out-of-order entry), reading a split shift from the
API, the legacy single-window fallback, malformed entries dropped, editor validation
messages naming both offenders, `is24h` bypassing window validation, and the last window
being unremovable.

Full suite: 285 tests, 0 failures. No APK build, per the standing preference.
