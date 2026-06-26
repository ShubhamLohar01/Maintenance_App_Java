# Android Handoff — Breakdown + Energy backend wiring fixes (2026-06-25)

Context: operator breakdown flags, the live "active runs" list, and Start/Stop
production were not landing in the backend (`mt_breakdown_records` /
`mt_machine_daily_kwh`), and tapping a breakdown showed **"Ticket not found."** Root
cause was app-side wiring, not the backend. Below is everything that changed, what you
must do to ship it, and what's still open.

> ⚠️ These are source edits only. They do nothing until you **rebuild + reinstall**.
> The changes were not compiled here — please do a Gradle build pass and report any errors.

---

## 1. What changed in the app

### Breakdowns — raise now hits the live pipeline (was going to the F.06 archive)
- `data/repository/BreakdownRepositoryImpl.kt`
  - `raiseBreakdownFlag()` now pushes to **`POST /breakdowns/flag`** (was wrongly posting
    a single-row F.06 sheet → `source='F06_RECORD'`, a write-only bucket nothing reads).
  - The **server's numeric id is now the Room row id** (was a client UUID). Offline →
    falls back to a UUID + a PENDING sync-queue entry.
  - `pushFlagToBackend()` rewritten to call `breakdownApi.raiseFlag(...)` and return the id.
- `data/remote/api/BreakdownApi.kt` — added `GET breakdowns/open` (`listOpen`).
- `data/remote/dto/BreakdownDtos.kt` — added `OpenBreakdownDto`.
- `data/remote/mock/MockBreakdownApi.kt` — added the `listOpen` override (returns empty).
- `domain/repository/BreakdownRepository.kt` + impl — added
  `refreshOpenFlags(plantId)` : pulls `GET /breakdowns/open` and **upserts** rows into
  Room keyed by the server id (preserves local unsynced edits).

### "Ticket not found" — list/detail now sync from the server
- `presentation/technician/TechnicianHomeViewModel.kt` — calls `refreshOpenFlags("both")`
  in `init`.
- `presentation/technician/TicketDetailViewModel.kt` **and**
  `presentation/qc/QcTicketDetailViewModel.kt` — **self-heal**: each calls
  `refreshOpenFlags("both")` on open, then resolves the ticket from Room. Loading state is
  held until the sync completes so it won't flash "not found". This covers every entry
  point (technician, QC, cross-device).

### Energy — Start/Stop now reach the backend immediately
- `data/repository/EnergyRepositoryImpl.kt`
  - `startProductionRun()` and `stopProductionRun()` now call `syncPendingRuns()` inline
    (best-effort) right after the local write, so a run lands in `mt_machine_daily_kwh`
    immediately when online. Offline → stays PENDING and `SyncWorker` retries (FAILED runs
    are already retried — `getPendingRunsOnce()` includes PENDING + FAILED).
  - Previously Start/Stop only wrote a local PENDING row and waited for the periodic
    `SyncWorker` (≥15 min + network constraint), so rows appeared late or never.

---

## 2. Backend coordination (must be done together)
- **Restart the backend** — a connection-pool fix (`pool_pre_ping`) was added; without the
  restart, RDS endpoints intermittently 500 ("server closed the connection unexpectedly")
  and every sync fails.
- `GET /energy/runs/active` already returns `run_id`, `operator_id`, `operator_name`,
  `started_at` (epoch **ms**), `asset_id`, `building`.
- New: `POST /energy/runs/close-stale` and auto-close of runs open > 16h (caps the old
  ~115h orphans). Orphans now drop off `/energy/runs/active` automatically.
- Full request/response field contract: `backend/docs/API_CONTRACT_FOR_ANDROID.md`.

---

## 3. To ship & verify
1. Restart the backend.
2. Build `FactoryOps` with `USE_MOCK_API=false` (`local.properties`). Run a Gradle build
   and fix any compile errors (changes were not compiled here).
3. **Clean reinstall / clear app data once** — old Room rows (client-UUID flags, mock
   leftovers, runs stuck FAILED) won't match server ids.
4. Smoke test:
   - Operator raises a breakdown → row appears in `mt_breakdown_records`
     (`source='OPERATOR_FLAG'`); machine shows under breakdown.
   - Tap that breakdown → detail opens (no "Ticket not found").
   - Operator Start production → row in `mt_machine_daily_kwh` (status RUNNING) right away;
     `GET /energy/runs/active` shows it with `run_id` + `operator_id`.
   - Stop → same row flips to COMPLETE with `daily_kwh`.

> Note: only breakdowns raised on the **new build** appear in `/breakdowns/open`. Anything
> raised by the old build went to the F.06 archive and won't show.

---

## 4. Still open (need decisions / follow-up — NOT done)
1. **QC actions don't reach the backend.** `acknowledge/approve/disapprove` only update
   local Room and enqueue a sync item — and **nothing drains the sync queue** (no worker
   replays it). Wiring them is blocked on a lifecycle decision: the app models `PENDING_QC`
   and a QC-pickup step the backend's lifecycle (`OPEN → ACKNOWLEDGED → QC_APPROVED |
   REOPENED`) doesn't. Decide the mapping, then either wire the `breakdownApi.qc*` calls
   inline (like raise) or build a real sync-queue drainer.
2. **Head breakdown list won't show asset-register flags.** `observeOpenFlagsByPlant`
   JOINs the legacy `machines` table; asset ids (`A185-0001`) never match. Switch it to a
   no-join query (like `observeAllOpenFlags`) or filter by building another way.
3. **No detail for closed tickets.** `/breakdowns/open` excludes `QC_APPROVED`, so a closed
   ticket opened from history won't resolve. Needs a `GET /breakdowns/{id}` (backend) + a
   detail fetch if required.
4. **Offline-raised flags keep a UUID** until a sync-queue drainer reconciles them to the
   server id.
