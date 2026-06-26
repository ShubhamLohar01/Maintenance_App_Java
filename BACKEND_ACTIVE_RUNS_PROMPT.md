# Backend Prompt — Live "Active Production Runs" for Head Oversight

You're picking up the FastAPI + PostgreSQL backend at `D:\Maintenance module\backend\`. The Android app already has this feature wired client-side; this prompt covers the ONE new endpoint it needs.

---

## Context

- The Head role in the Android app (`D:\Maintenance module\FactoryOps`) has a read-only oversight screen **"Machine List (live)"**.
- It now lists the **full asset register** (`mt_asset_list`, served by `GET /mt-machines`) and overlays a green **CURRENTLY WORKING** badge on every asset that an operator currently has a production run open on — across **all** operators and devices, not just the Head's own device.
- That cross-device "who is running what right now" data does not exist as a read endpoint yet. Operators only ever **write** runs via `POST /energy/runs/start` and `POST /energy/runs/{run_id}/stop`. This prompt adds the matching **read**.
- The Android side is feature-flagged behind in-app Mock APIs (today the mock returns an empty list). Flip nothing on the backend side except shipping this endpoint; the app already calls it on a 30s poll and degrades gracefully (shows the register with no badges) if it 404s/errors.

---

## The endpoint

```
GET /energy/runs/active
Authorization: Bearer <jwt>          # Depends(get_current_user)
```

**Definition of "active":** a row in `production_runs` (the same table `/energy/runs/start` writes) where the run has started and **`ended_at IS NULL`** (not yet stopped).

**Scope:** return active runs for the caller's plant. Reuse whatever plant scoping the other Head/operator endpoints already use (`JOIN` the asset/machine row and filter by the user's `plant_id`). If a Head's `plant_id` is `"both"`, return active runs for **both** buildings.

### Response `200 OK` — JSON array (empty `[]` when nobody is running)

```json
[
  {
    "asset_id":      "W202-0123",
    "run_id":        "9f3a1c4e-…",
    "operator_id":   "42",
    "operator_name": "Ravi Kumar",
    "started_at":    1750768500000,
    "building":      "W-202"
  }
]
```

### Field rules (match the Android DTO exactly — `ActiveRunDto`)

| Field | Type | Notes |
|---|---|---|
| `asset_id` | string | **Required.** The `mt_asset_list.asset_id` the run is on. This is the value `production_runs.machine_id` stores (the operator flow renamed machine→asset but kept the column name `machine_id`). |
| `run_id` | string \| null | Server run id. Optional for the UI; include it. |
| `operator_id` | string \| null | `production_runs.operator_id`. |
| `operator_name` | string \| null | Display name. `JOIN mt_users ON mt_users.id = production_runs.operator_id` → name. Send `null` (not `""`) if unknown — the app renders `Operator: —`. |
| `started_at` | **integer (epoch milliseconds, UTC)** | **Critical:** must be epoch **millis**, same units as `POST /energy/runs/start`'s `started_at`. If `production_runs.started_at` is a `timestamptz`, convert: `EXTRACT(EPOCH FROM started_at) * 1000` cast to bigint. If it's already stored as bigint millis, pass through. Do **not** send ISO-8601 here. |
| `building` | string \| null | `"W-202"` / `"A-185"` from `mt_asset_list.building` for that `asset_id`. Optional; the app also shows building from its own register copy, but include it for consistency. |

### Suggested query shape

```sql
SELECT
    r.machine_id                              AS asset_id,
    r.run_id                                  AS run_id,
    r.operator_id                             AS operator_id,
    u.name                                    AS operator_name,
    (EXTRACT(EPOCH FROM r.started_at) * 1000)::bigint AS started_at,
    a.building                                AS building
FROM production_runs r
LEFT JOIN mt_users      u ON u.id       = r.operator_id
LEFT JOIN mt_asset_list a ON a.asset_id = r.machine_id
WHERE r.ended_at IS NULL
  AND (:plant = 'both' OR a.plant_id = :plant);   -- adapt to your real scoping
```

(Adjust column/table names to the real schema — confirm `production_runs.started_at`'s type first, that drives the `started_at` conversion above.)

---

## Pydantic schema

```python
class ActiveRunDto(BaseModel):
    asset_id:      str
    run_id:        str | None = None
    operator_id:   str | None = None
    operator_name: str | None = None
    started_at:    int                 # epoch millis UTC
    building:      str | None = None
```

Wire the route onto the existing energy router (the one serving `/energy/runs/...`) so auth + prefix are consistent.

---

## Tests (`tests/test_active_runs.py`)

- `test_returns_open_run` — start a run (no stop) → it appears, with `ended_at IS NULL`.
- `test_excludes_stopped_run` — a run that's been stopped does **not** appear.
- `test_started_at_is_epoch_millis` — `started_at` is a 13-digit-ish integer, and round-trips to the same instant as the start request.
- `test_operator_name_joined` — `operator_name` matches `mt_users.name`; `null` when the user row is missing.
- `test_empty_when_none_running` — returns `[]`, not `404`.
- `test_plant_scope` — a Head scoped to one building doesn't see the other building's active runs; `"both"` sees all.
- `test_requires_auth` — no/invalid token → 401.

---

## Process

1. **Read** the Android contract so the JSON matches byte-for-byte:
   - `app/src/main/java/com/candorfoods/factoryops/data/remote/dto/EnergyDtos.kt` → `ActiveRunDto`
   - `app/src/main/java/com/candorfoods/factoryops/data/remote/api/EnergyApi.kt` → `getActiveRuns`
2. **Confirm** the real `production_runs` schema, especially the type of `started_at` (drives the millis conversion) and the `operator_id`→`mt_users` join key.
3. **Build** schema → query → route → tests, verifying after each step.
4. **Hand back**: confirmed URL, a sample `curl` with a real bearer token, and any deviations.

---

## Don't touch (out of scope)

- `POST /energy/runs/start`, `POST /energy/runs/{run_id}/stop`, `GET /energy/machines/{machine_id}/history` — the app is happy with all three. This is purely an additive read.
- `/mt-machines`, `/machines/assigned`, `/breakdowns/...`, `/auth/login`, daily-kWh.
- No schema changes are required — this endpoint is a pure read over existing `production_runs` + `mt_users` + `mt_asset_list`.

---

## After deploy

The app already polls this every 30s on the Head's **Machine List (live)** screen. To verify end-to-end:
1. Operator logs in, starts production on an asset → row stays open (`ended_at IS NULL`).
2. Head logs in, opens **Machine List** → that asset sorts to the top with a green **CURRENTLY WORKING** badge, the operator's name, and a live "Running Xh Ym".
3. Operator stops the run → within ~30s the badge clears on the Head's screen.
