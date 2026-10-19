# Schedule Production Equipment + operator lockout

**Date:** 2026-07-23
**Status:** Approved (verbal), implementing

## Problem

Today only **Electric Asset** category machines can be given a daily recording schedule
(the "Schedule Electric Assets" supervisor screen). Production Equipment machines are run
manually by operators (start/stop production runs). We want:

1. **Production Equipment machines to be schedulable** by a supervisor, exactly like
   Electric Assets (a daily start/end window + weekdays, or 24h).
2. **One control path per machine.** If a supervisor has set an (active) schedule on a
   machine, the operator loses the start/stop production button for it — the machine is
   schedule-controlled. A Production machine *without* a schedule keeps the normal operator
   start/stop.

## Key facts

- Schedules are **columns on `mt_asset_list`** (`schedule_start_min/end_min/active/days/is_24h/...`),
  not a separate table — so they already work for any category. The only thing gating them
  to electric is the hardcoded `ELECTRIC_CATEGORY = "Electric Asset"` string used in 3 spots
  in `app/api/asset_schedules.py`.
- A scheduled asset **auto-generates daily kWh** (`rated_kw × window-hours × power_factor`,
  `source='SCHEDULE'`) via lazy backfill — no operator run needed. That is exactly the
  "runs on its own, operator locked out" behaviour we want for scheduled production machines.
- The operator app reads machines from `GET /mt-machines` → `MtMachineDto`, which has no
  schedule flag today, so the app can't yet know a machine is locked.

## Design

### Backend (`D:\Maintenance module\backend`)

1. **Allow Production Equipment to be scheduled.** In `app/api/asset_schedules.py` replace the
   single `ELECTRIC_CATEGORY` check with `SCHEDULABLE_CATEGORIES = {"Electric Asset",
   "Production Equipment"}` in the three sites: the `generate_due_rows` backfill query, the
   `GET /asset-schedules` list query, and the PUT/DELETE validation guard
   (`_get_schedulable_asset`). No DB migration (columns already exist).
2. **`schedule_locked` flag.** Add a `is_schedule_locked(asset)` helper in `app/utils.py`
   (true when `schedule_active` and a window or `is_24h` is set) and surface it as
   `schedule_locked: bool` on `MtMachineDto` (`app/schemas.py` + `app/api/mt_machines.py`).
3. **Server-side enforcement.** In `app/api/energy.py` `start_run`, reject (409) when the
   asset is schedule-locked, so a stale client can't start a scheduled machine.
4. **Category on schedule rows.** Add `category` to `AssetScheduleDto` so the schedule
   screen can label electric vs production.

### Frontend (`FactoryOps/app`)

5. **`scheduleLocked`** added to `MtMachineDto` (+ `toDomain`) and the `MtMachine` domain
   model.
6. **Operator lockout** on the machine-detail screen: when `asset.scheduleLocked`, the
   Start button is disabled with a note "Under supervisor schedule — recording
   automatically." `MachineDetailViewModel.onStartClicked`/`onResumeClicked` also guard on it.
7. **Schedule screen** now lists production machines automatically once the backend returns
   them. Rename title "Schedule Electric Assets" → "Schedule Assets" and show the category
   on each card. Add `category` to `AssetScheduleDto`/`ElectricAssetSchedule`.

### Behaviour summary

- Supervisor schedules a Production machine → it auto-records kWh on the window and operators
  can't start it.
- No schedule → operator runs it normally, as today.
- One control path per machine at a time (schedule *or* manual), enforced on client and server.

## Out of scope / non-goals

- Supervisor manual start/stop button (the schedule auto-runs; no manual override needed).
- Changing which machines the operator *sees* (Production Equipment is already visible to
  operators; only the start button changes).
- Any new DB table or migration.

## Testing

- Backend pytest: Production Equipment is schedulable (PUT succeeds, appears in GET);
  operator `start_run` on a schedule-locked machine → 409; `GET /mt-machines` returns
  `schedule_locked`.
- Frontend: build in Android Studio (no APK build here).
