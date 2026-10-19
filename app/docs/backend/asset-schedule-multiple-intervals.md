# Backend requirement — multiple daily intervals on an asset schedule

**Requested by:** FactoryOps Android app (Schedule Assets, 2026-08-08)
**Scope:** one nullable column + interval handling in `app/api/asset_schedules.py`.
No new endpoint, no new table, no data backfill.

## Context

A supervisor can give an Electric Asset or Production Equipment a daily recording window
(`PUT /asset-schedules/{machine_id}`), and the backend auto-generates that day's kWh once
the window has elapsed. Today that's **one** window per asset.

Real machines run in split shifts — the same machine runs 10:00–13:00, stops over the
break, then runs 15:00–22:30. Right now the supervisor has to choose between one long
window (which over-counts the 2-hour gap) or losing the second stretch entirely.

**What's wanted:** several intervals on the same day. The intervals repeat identically on
every weekday the schedule already selects — this is *not* a per-weekday schedule.

## Model

```
schedule (per asset, unchanged)    active · days · is_24h · last_generated · updated_by/at
  └── intervals (NEW)              [ 10:00–13:00, 15:00–22:30 ]   same on every selected day
```

`is_24h` still wins over everything: whole day, intervals ignored.

---

## 1. Migration

```sql
ALTER TABLE mt_asset_list ADD COLUMN schedule_intervals jsonb;
-- [{"start": 600, "end": 780}, {"start": 900, "end": 1350}]   minute-of-day, IST, sorted
```

**No backfill.** A row with `schedule_intervals` NULL is read as a single interval built
from the existing `schedule_start_min`/`schedule_end_min`, so every schedule that exists
today keeps working untouched and fills the column in when it's next edited.

Rollback is `ALTER TABLE mt_asset_list DROP COLUMN schedule_intervals;` — the derived
columns below mean nothing else depends on it.

## 2. `app/models.py` — `MtAsset`

```python
schedule_start_min:  Mapped[int | None]  = mapped_column(Integer, nullable=True)   # = first interval's start
schedule_end_min:    Mapped[int | None]  = mapped_column(Integer, nullable=True)   # = last interval's end
schedule_intervals:  Mapped[list | None] = mapped_column(PortableJSONB, nullable=True)  # NEW, authoritative
```

`PortableJSONB` (not raw `JSONB`), matching `PreventiveMaintenanceDoc.rows` — the test
harness runs on SQLite.

**Key decision: `schedule_start_min` / `schedule_end_min` stay, and become derived** — the
first interval's start and the last interval's end, written on every save. That is what
keeps `utils.is_schedule_locked()`, the `POST /energy/runs/start` 409 guard, the
`generate_due_rows` query filter, and any other existing reader working with **no change
at all**. They are never authoritative again; treat them as a cached summary.

## 3. `app/api/asset_schedules.py`

### 3a. Read intervals, with the legacy fallback

```python
def _intervals_of(a: MtAsset) -> list[tuple[int, int]]:
    """This asset's daily intervals, sorted. Falls back to the legacy single window
    when schedule_intervals is NULL, so pre-migration rows keep their behaviour."""
    raw = a.schedule_intervals
    if isinstance(raw, list) and raw:
        out = []
        for iv in raw:
            if not isinstance(iv, dict):
                continue
            s, e = iv.get("start"), iv.get("end")
            if isinstance(s, int) and isinstance(e, int) and 0 <= s < e <= _DAY_MINUTES:
                out.append((s, e))
        if out:
            return sorted(out)
    if a.schedule_start_min is not None and a.schedule_end_min is not None:
        return [(a.schedule_start_min, a.schedule_end_min)]
    return []
```

### 3b. `_hours` and `_est_kwh` sum across intervals

```python
def _total_hours(intervals: list[tuple[int, int]], is_24h: bool = False) -> float:
    if is_24h:
        return 24.0
    return round(sum(max(0, e - s) for s, e in intervals) / 60.0, 4)
```

`_est_kwh` then becomes `rated_kw × _total_hours(...) × settings.power_factor`, rounded to
3 as now. Keep the existing `_hours(start, end, is_24h)` if anything else calls it.

### 3c. `_to_dto` — emit the intervals, keep the old fields

```python
intervals = _intervals_of(a)
...
    start_min=a.schedule_start_min,            # unchanged (= first start)
    end_min=a.schedule_end_min,                # unchanged (= last end)
    intervals=[                                 # NEW
        AssetScheduleIntervalDto(
            start_min=s, end_min=e,
            start_label=_fmt_label(s), end_label=_fmt_label(e),
        ) for s, e in intervals
    ],
    hours=_total_hours(intervals, is_24h),      # now the SUM, see the note below
    est_daily_kwh=_est_kwh(rated, intervals, is_24h),
```

### 3d. `upsert_asset_schedule` — validate and store

`intervals` is optional on the request; when absent, the existing `start_min`/`end_min`
pair is used as a single interval, so an app build that predates this keeps working.

```python
raw = payload.intervals or [{"start": payload.start_min, "end": payload.end_min}]
intervals = sorted((iv.start_min, iv.end_min) for iv in raw)   # adapt to your model shape

if not intervals:
    raise HTTPException(400, "at least one interval is required")
for s, e in intervals:
    if not (0 <= s < e <= _DAY_MINUTES):
        raise HTTPException(400, f"Require 0 <= start < end <= 1440 (got {s}..{e})")
for (s1, e1), (s2, e2) in zip(intervals, intervals[1:]):
    if s2 < e1:
        raise HTTPException(
            400,
            f"intervals overlap: {_fmt_label(s1)}-{_fmt_label(e1)} and "
            f"{_fmt_label(s2)}-{_fmt_label(e2)}",
        )

a.schedule_intervals = [{"start": s, "end": e} for s, e in intervals]
a.schedule_start_min = intervals[0][0]    # derived
a.schedule_end_min   = intervals[-1][1]   # derived
# ... active / days / is_24h / updated_by / updated_at exactly as now
```

Touching intervals **must not** stomp `schedule_last_generated` — leave it alone, same as
today, so a mid-day edit doesn't re-generate days already recorded.

### 3e. `clear_asset_schedule` — also clear the new column

```python
a.schedule_intervals = None      # NEW, alongside the existing start/end/active/days resets
```

### 3f. `generate_due_rows` — sum the day, keep one row per day

This is the important one, and the smallest change of the lot.

**Keep `client_run_id = f"sched-{asset}-{date}"` exactly as it is.** One
`mt_machine_daily_kwh` row per asset per day, still UNIQUE-keyed, still safe against
concurrent sweeps — and it matches how a manually-run machine already collapses a
start-stop-start-stop day into one daily row with the stretches in
`mt_machine_run_segment`. **No report or query anywhere needs to change.**

Inside the per-asset loop:

```python
intervals = _intervals_of(a)
if is_24h:
    intervals = [(0, _DAY_MINUTES)]
if not rated_kw or not intervals:
    continue
kwh = round(rated_kw * _total_hours(intervals, is_24h) * settings.power_factor, 3)

day_start_min = intervals[0][0]      # earliest start  -> started_at
day_end_min   = intervals[-1][1]     # latest end      -> ended_at AND the elapsed check
```

then `_window_end_ist(cursor, day_end_min)` for the "has this day fully elapsed?" test, and
`started_at`/`ended_at` from `day_start_min`/`day_end_min` — the same earliest-start /
latest-end convention the manual-run daily row already uses.

The gap between intervals produces no kWh, which is the point: 10:00–13:00 + 15:00–22:30
bills 9.5 h, not the 12.5 h a single 10:00–22:30 window would.

The query filter above (`schedule_start_min.isnot(None)`) needs no change, because the
derived columns are always written.

## 4. `app/schemas.py`

```python
class AssetScheduleIntervalDto(BaseModel):
    start_min: int
    end_min: int
    start_label: Optional[str] = None   # "10:00 AM"
    end_label: Optional[str] = None     # "1:00 PM"


class AssetScheduleIntervalIn(_Trimmed):
    start_min: int
    end_min: int
```

- `AssetScheduleDto` gains `intervals: List[AssetScheduleIntervalDto] = []`
- `AssetScheduleUpsertRequest` gains `intervals: Optional[List[AssetScheduleIntervalIn]] = None`
  and keeps `start_min` / `end_min` **required**, so nothing about the existing contract
  changes. The app sends all three: the derived pair plus the full list.

## Heads-up: `hours` changes meaning

`hours` (and therefore `est_daily_kwh`) goes from "length of the one window" to "sum of all
intervals". For every schedule that exists today the value is identical — a single interval
sums to itself — so nothing moves on migration day. But any consumer that assumed
`hours == (end_min - start_min) / 60` is no longer safe once a supervisor adds a second
interval. Worth a grep before shipping.

## Tests

- one interval → unchanged behaviour end to end (`hours`, `est_daily_kwh`, generated row)
- two intervals 600–780 and 900–1350 → `hours == 9.5`, one daily row, `started_at` 10:00,
  `ended_at` 22:30, kWh = rated × 9.5 × PF
- a day is generated only after the **last** interval has elapsed, not the first
- overlapping intervals → **400**, with both offending windows named
- `end <= start`, `start < 0`, `end > 1440` → **400**
- unsorted input is stored sorted, and `schedule_start_min`/`end_min` come back derived
- a pre-migration row (`schedule_intervals` NULL, old pair set) still lists, still
  generates, and reads back as exactly one interval
- an upsert with no `intervals` key (old app build) still works as a single window
- `is_24h` still overrides the intervals entirely
- `DELETE` clears `schedule_intervals` along with the rest
- a non-supervisor still gets **403**

## Verifying from the app side

As a supervisor: **Schedule Assets → tap a machine → + Add interval**, set 10:00–13:00 and
15:00–22:30, Save. The card should read `10:00 AM – 1:00 PM, 3:00 PM – 10:30 PM · 9.5 h`.
Then:

```sql
SELECT asset_id, schedule_start_min, schedule_end_min, schedule_intervals
FROM mt_asset_list WHERE asset_id = '<the asset>';
-- expect 600, 1350, [{"start":600,"end":780},{"start":900,"end":1350}]

SELECT reading_date, started_at, ended_at, daily_kwh, source, client_run_id
FROM mt_machine_daily_kwh
WHERE machine_id = '<the asset>' AND source = 'SCHEDULE'
ORDER BY reading_date DESC LIMIT 3;
-- expect ONE row for the day, 10:00 -> 22:30, kwh = rated_kw * 9.5 * power_factor
```
