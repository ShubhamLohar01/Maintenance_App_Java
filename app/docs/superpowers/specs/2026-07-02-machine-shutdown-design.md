# Supervisor Machine Shut-Down (Soft Decommission) — Design Spec

**Date:** 2026-07-02
**Status:** Approved (brainstorming) → ready for implementation plan
**Module:** FactoryOps (Android, Kotlin, Jetpack Compose)

## 1. Goal

Let a Supervisor/Admin/Head "delete" (shut down) any machine. Shutting down sets the
asset's **condition** column to a `Shut Down` sentinel; a shut-down machine is **locked
for everyone** (no production, no breakdown reporting, not selectable for new work). The
state is **reversible** — the same roles can restore it, which clears the condition.

## 2. Locked decisions (from brainstorming)

1. **Mechanism:** Reuse the existing `MtMachine.condition` free-text field. Shut down =
   write the sentinel `"Shut Down"`; restore = clear to blank/null. No new enum, no DB
   migration, no new endpoint — it flows through the existing `PUT /mt-machines/{asset_id}`.
2. **Reversible + visible:** Shut-down machines stay in the asset register, shown greyed
   with a "Shut Down" badge for all roles. Supervisor/Admin/Head can restore them.
3. **Who can shut down / restore:** `SUPERVISOR`, `ADMIN`, `HEAD`. (Head is otherwise
   read-only but may toggle this state.)
4. **Restore value:** clears `condition` to blank/null (the prior "Good"/"Fair" text is
   not preserved).
5. **Not blocked:** completing an *in-flight* PM work order or closing an *existing*
   breakdown on a shut-down machine remains allowed (finishing maintenance is desirable).

## 3. Scope

**In scope**
- Sentinel constant + `MtMachine.isShutDown()` helper (single source of truth).
- Shut-down / restore actions in `MachineDetailScreen` (role-gated, confirmation dialog).
- Guards blocking new actions on a shut-down machine (Section 6).
- Greyed display + "Shut Down" badge in the machine list and detail.

**Out of scope**
- Hard delete / `DELETE` endpoint.
- Preserving/restoring the prior condition text.
- Blocking in-flight PM or existing-breakdown completion.
- Any change to the separate `Machine`/`MachineStatus` model (the app treats it as
  read-only; the UI runs on `MtMachine`).
- The role-based reminders feature (separate branch/spec).

## 4. Grounding — current code (verified)

- `MtMachine` (`domain/model/MtMachine.kt`) has `val condition: String? = null`. It is the
  field the machine list/detail already read and the supervisor Edit sheet already writes.
- Write path: `MachineDetailViewModel.onSaveEdit()` → `MtMachineRepository.updateAsset(edited)`
  → `PUT /mt-machines/{asset_id}` with `MtMachineUpdateDto` (which includes `condition`).
  `MtMachineRepositoryImpl` has **no local Room cache** — `list()` fetches live, so a
  condition change is visible on every device on next load (true cross-user lock).
- `MachineDetailViewModel` UI state already exposes `isSupervisor`, `isOperator`,
  `underBreakdown`, and the loaded `asset`. `MachineDetailScreen` already gates the Edit
  sheet with `isSupervisor` and disables "Production Start" with a message when
  `underBreakdown` — the exact precedent to mirror for shut-down.
- Roles: `UserRole { OPERATOR, TECHNICIAN, HEAD, QC_HEAD, QC_MEMBER, SUPERVISOR, ADMIN }`.

## 5. Data & core helper

- **Sentinel:** `const val CONDITION_SHUT_DOWN = "Shut Down"` (single canonical spelling),
  defined alongside `MtMachine` (same file).
- **Helper:**
  ```kotlin
  fun MtMachine.isShutDown(): Boolean =
      condition?.trim().equals(CONDITION_SHUT_DOWN, ignoreCase = true) == true
  ```
  Trim + case-insensitive so pre-existing/backend variants match robustly. Every guard and
  display uses this helper — no ad-hoc string compares.
- **Set:** `mtMachineRepository.updateAsset(machine.copy(condition = CONDITION_SHUT_DOWN))`.
- **Restore:** `mtMachineRepository.updateAsset(machine.copy(condition = null))`.

## 6. Behaviour

### 6.1 Trigger UI (`MachineDetailScreen` + `MachineDetailViewModel`)
- Add derived flag `canToggleShutDown = currentRole in setOf(SUPERVISOR, ADMIN, HEAD)` to
  the detail UI state (independent of the existing supervisor-only `isSupervisor` edit gate).
- Top-bar action:
  - machine active → **"Shut Down"** action; tapping opens a confirmation dialog (reuse the
    existing `ConfirmDialog`): *"Shut down {assetName}? It will be locked for all users
    until restored."* → `viewModel.onShutDown()`.
  - machine already shut down → **"Restore"** action; confirm *"Restore {assetName}?"* →
    `viewModel.onRestore()`.
- `onShutDown()/onRestore()` call `updateAsset(...)`, then refresh the asset; surface
  failures as an error message (the call is backend-authoritative).

### 6.2 Locks (all keyed off `MtMachine.isShutDown()`)
| Action | File / method | Guard |
|---|---|---|
| Production **Start** | `MachineDetailViewModel.onStartClicked()` | early-return if shut down; Start button rendered disabled with "Machine is shut down" message (mirror `underBreakdown`) |
| Production **Resume** | `MachineDetailViewModel.onResumeClicked()` | same early-return |
| **Report Breakdown** button | `MachineDetailScreen` | show only when `isOperator && !underBreakdown && !isShutDown` |
| Breakdown **submit** | `BreakdownFlagViewModel.onSubmit()` | fetch machine; block + error if shut down (defensive, in case the screen was reached another way) |
| **Runnable-asset picker** | `GetRunnableAssetsUseCase.invoke()` | add `.filter { !it.isShutDown() }` alongside the existing `isRunnable()` filter |
| **PM-plan machine picker** | `PlanEditorViewModel` | exclude shut-down machines from the selectable list |

### 6.3 Display
- **Machine list** (`MachineListScreen` / `MachineListViewModel`): shut-down rows shown
  greyed with a "Shut Down" badge for all roles; tap still opens detail (read-only/locked
  for non-power roles).
- **Machine detail** (`MachineDetailScreen`): a prominent "SHUT DOWN" banner; operator
  action buttons hidden; power roles see the **Restore** action.

## 7. Testing

- **Pure unit tests (JUnit 5):**
  - `MtMachine.isShutDown()` — true for "Shut Down", " shut down ", "SHUT DOWN"; false for
    "Good"/null/blank.
  - `GetRunnableAssetsUseCase` — a shut-down machine is excluded even if otherwise runnable.
- **Manual verification:** Supervisor shuts a machine down → an Operator login sees it
  greyed, cannot Start or Report Breakdown, and it's absent from the runnable picker; a Head
  login restores it → machine usable again and condition blank.

## 8. Risks & notes

- **Free-text condition overwrite:** shutting down replaces any prior condition text; restore
  clears it (accepted per decision 4). The `isShutDown()` helper is the only place that
  interprets the sentinel, so changing the scheme later is a one-line edit.
- **Backend dependency:** relies on the existing `PUT /mt-machines/{asset_id}` accepting
  `condition`; already used by the supervisor Edit flow, so no backend change is required.
- **Two machine models:** the operational `MachineStatus` enum (on `Machine`) is intentionally
  untouched; the lock lives entirely on the asset-register `MtMachine.condition`.
