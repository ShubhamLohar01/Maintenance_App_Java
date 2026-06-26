# Design — "Working now" section + Operator breakdown reflection

Date: 2026-06-24
Status: Approved, implemented client-side; backend pending (`BACKEND_BREAKDOWN_RECORDS_PROMPT.md`).

## Problem

1. **Breakdown doesn't affect the machine in-app.** When an operator raises a breakdown flag, nothing changes on the asset's screen. Root cause: `BreakdownRepositoryImpl.raiseBreakdownFlag` writes status to the legacy `machines` table (`machineDao.updateStatus`), but the operator UI reads the asset register (`MtMachine`) — the status lands in a table the UI never reads. The flag must also persist to `mt_breakdown_records` (backend).
2. **No "Working" view.** Operators can't see which machines are currently in production at a glance.

## Decisions (from the user)

- Working source: **live from backend** (`GET /energy/runs/active`, reads `production_runs` open rows), not cache.
- Working layout: **pinned "Working now" section on top** of operator Home.
- Breakdown effect: **badge + block Production Start** until a technician/QC resolves it.

## Design

### A. "Working now" (operator Home)
- `HomeViewModel` polls `GetActiveRunsUseCase` every 30s (+ on refresh); stores `activeRuns: List<ActiveRun>`.
- `HomeUiState.working` maps active runs → `WorkingMachine(assetId, assetName, operatorName, startedAtEpochMs)`, names resolved from the loaded register.
- `HomeScreen` renders a "Working now (N)" section at the top of the list (green-accent cards, "Running Xh Ym", operator). Hidden when empty.

### B. Breakdown reflection
- New asset-safe query `BreakdownFlagDao.observeActiveFlagMachineIds()` — `DISTINCT machineId WHERE status <> 'QC_APPROVED'`, no legacy-table join → `BreakdownRepository.observeMachinesUnderBreakdown(): Flow<Set<String>>`.
- `HomeViewModel` exposes `breakdownAssetIds`; `MachineCard` shows an **UNDER BREAKDOWN** badge for those assets.
- `MachineDetailViewModel` observes `observeActiveFlagForMachine(assetId)` → `activeBreakdown`; `underBreakdown = activeBreakdown != null`. `onStartClicked` is guarded.
- `MachineDetailScreen`: shows a `BreakdownBanner`, **disables Production Start**, and hides "Report Breakdown" while a breakdown is open. Clears automatically when status → `QC_APPROVED`.
- Persistence: no app change — the existing `POST /breakdowns/flag` sync writes to `mt_breakdown_records` (backend work).

## Implementation plan (all client changes shipped)

1. `BreakdownFlagDao.observeActiveFlagMachineIds()` ✅
2. `BreakdownRepository(+Impl).observeMachinesUnderBreakdown()` ✅
3. `HomeViewModel`: inject `GetActiveRunsUseCase` + `BreakdownRepository`; 30s active-runs poll; `WorkingMachine`; `breakdownAssetIds` ✅
4. `HomeScreen`: "Working now" section + `WorkingMachineCard`; pass `underBreakdown` to `MachineCard` ✅
5. `MachineCard`: `underBreakdown` badge ✅
6. `MachineDetailViewModel`: inject `BreakdownRepository`; observe active flag; guard start ✅
7. `MachineDetailScreen`: `BreakdownBanner`, disabled Start, hide Report when flagged ✅
8. Backend: `BACKEND_BREAKDOWN_RECORDS_PROMPT.md` (flag → `mt_breakdown_records`, status lifecycle, `GET /breakdowns/open`) ✅

## Out of scope / follow-ups

- Cross-device breakdown read (`GET /breakdowns/open`) so technicians/Head on other devices see operator flags — defined in the backend prompt; app wiring to consume it is a follow-up.
- The 2 stale open runs in `production_runs` will show in "Working now" until stopped.

See [[head-interface]], [[build-cache-classnotfound]], `BACKEND_ACTIVE_RUNS_PROMPT.md`.
