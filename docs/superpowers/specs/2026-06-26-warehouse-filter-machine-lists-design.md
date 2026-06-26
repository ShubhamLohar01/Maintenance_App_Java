# Warehouse Filter for Machine/Asset Lists — Design

- **Date:** 2026-06-26
- **App:** FactoryOps (Android, Kotlin + Jetpack Compose)
- **Status:** Approved design, pending implementation plan
- **Scope:** Frontend-only. No backend or database changes.

## Problem

The factory has two warehouses: **A-185** and **W-202**. Several screens show a list
of machines/assets drawn from the asset register (`mt_asset_list`, served by
`GET /mt-machines`). That endpoint is **not** building-scoped and the Android app
already fetches it **without** a `building` filter, so every machine/asset list
currently shows **both warehouses' machines jumbled together** with no way to
narrow to one warehouse.

Users (all roles) want a simple warehouse dropdown on every machine/asset list so
they can filter the list to **A-185** or **W-202**.

## What this is (and isn't)

Because the data for both warehouses is **already on the device** and live status
is **already derived client-side** (active runs + breakdown observers), this is a
pure **display filter**. It does **not**:

- change any API, endpoint, schema, or database;
- change permissions / who-can-see-what (both warehouses were already visible);
- change any action authorization (flagging breakdowns, etc. are untouched);
- touch work-list screens (breakdowns, PM tickets, QC queue, transfers list).

## Locked decisions

1. **Access:** All roles can view/filter both warehouses. (Already the case — the
   register fetch is unscoped; this only adds the UI filter.)
2. **List contents:** Each machine/asset list shows **all** machines in the selected
   warehouse (the full register), filtered by the dropdown. Unchanged from today
   except for the new building predicate.
3. **Dropdown:** **Fixed** two options — `A-185` and `W-202`. **No "All" option.**
   Defaults to the **user's home warehouse**.
4. **Coverage:** Only true **machine/asset list** screens (and machine pickers).
   Work-list screens (breakdowns / PM / QC queues) are **out of scope**.
5. **Roles:** Universal — every role gets the filter wherever it opens a machine/
   asset list. Coverage is achieved because the target screens are shared across
   roles (see matrix).

## Screen / role coverage matrix

| Screen | Reached by (role) | Data source | Building field | Change |
|---|---|---|---|---|
| `presentation/machinelist/MachineListScreen.kt` (+ `MachineListViewModel.kt`) | OPERATOR (home); SUPERVISOR & HEAD via Dashboard "Machine List" tile → `Screen.MachineList` | `/mt-machines` register (`MtMachine`) | `MtMachine.building` | **Add** dropdown + `selectedBuilding` state (default home) + filter predicate |
| `presentation/machinemaster/MachineMasterScreen.kt` (+ `MachineMasterViewModel.kt`) | TECHNICIAN via Dashboard "Machine List" tile → `Screen.MachineMaster` | `/mt-machines` register (`MtMachine`) | `MtMachine.building` | **Standardize:** replace the existing *dynamic* building dropdown with the shared fixed `A-185/W-202` control; seed default to home; drop the "All buildings" null option |
| `presentation/admin/AdminHomeScreen.kt` (+ its ViewModel) | ADMIN | legacy `Machine` (SQLite) | `Machine.building` | **Add** dropdown + filter predicate |
| `presentation/supervisor/PlanEditorScreen.kt` (machine picker) | SUPERVISOR | legacy `Machine` | `Machine.building` | **Add** warehouse filter to the machine picker before selection |
| ~~`presentation/transfer/TransferFormScreen.kt`~~ | — | — | — | **Dropped from scope** (resolved during planning): this screen has no browsable machine list — it uses a free-text machine-name autocomplete and already has explicit From/To warehouse route dropdowns. Nothing to filter. |

Out of scope (work-lists, unchanged): `HeadBreakdownsScreen`, `HeadQcScreen`,
`TechnicianHomeScreen`, `SupervisorHomeScreen`, `QcHomeScreen`,
`BreakdownListScreen`, `PreventiveListScreen`, history/detail screens.

## Components

### 1. Shared building constants + home-warehouse resolver

A single small utility (e.g. `presentation/common/Warehouses.kt` or a domain-level
`Buildings` object) mirroring the backend `ALL_BUILDINGS` in `backend/app/utils.py`:

```kotlin
object Buildings {
    val ALL = listOf("A-185", "W-202")          // canonical, mirrors backend ALL_BUILDINGS

    /** Resolve any plantId spelling to a canonical building, or null.
     *  Mirrors backend building_for(): match the code ("A185"/"W202") OR just
     *  its digits ("185"/"202") inside the normalized plantId. */
    fun forPlant(plantId: String?): String? { /* normalize + match */ }

    /** Default selection for a user: their home warehouse, else first ALL. */
    fun defaultFor(plantId: String?): String = forPlant(plantId) ?: ALL.first()
}
```

- `forPlant("A-185-Koparkhairne")` → `"A-185"`, `forPlant("185")` → `"A-185"`,
  `forPlant("W202")` → `"W-202"`, `forPlant("both")` / blank / unknown → `null`.
- HEAD/SUPERVISOR (often `plantId` = `both`/blank) therefore default to `A-185`.

### 2. Shared `WarehouseFilter` composable

Extract the existing generic `FilterDropdown` (currently private in
`MachineMasterScreen.kt:204-241`, Material3 `ExposedDropdownMenuBox`) into a shared
location and wrap it as a fixed-option warehouse control:

```kotlin
@Composable
fun WarehouseFilter(
    selected: String,                 // "A-185" | "W-202"
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
)  // options = Buildings.ALL, label = "Warehouse", no null/"All" entry
```

Placed at the **top** of each list (above existing search / category tabs).

### 3. Per-ViewModel wiring

Each target ViewModel gains:

- `selectedBuilding: String` in its UI state, **initialized** to
  `Buildings.defaultFor(currentUser?.plantId)` once the current user loads
  (seed on first user emission; keep current selection on later emissions).
- `onBuildingSelected(building: String)` handler that updates state.
- The existing filtered list (e.g. `MachineListUiState.items`,
  `MachineMasterViewModel.filtered`) gains one predicate:
  `asset.building.equals(selectedBuilding, ignoreCase = true)`, composed with the
  existing search/category/status filters.

For `MachineListScreen`, derived counts (Running/Breakdown/Under QC/Idle) and the
category tab set recompute over the **building-filtered** subset so they stay
consistent with what is shown.

## Data flow (unchanged except for the predicate)

```
GET /mt-machines (no building param)  ->  full register (A-185 + W-202)  ->  cache/state
                                                                              |
            active runs + breakdown observers --> live status (client-side)   |
                                                                              v
   selectedBuilding (default = home) --> filter: asset.building == selected --> rendered list
```

Legacy-`Machine` screens (Admin, pickers) follow the same shape over their existing
`Machine` list, filtering on `Machine.building`.

## Edge cases

- **Value matching:** dropdown values and stored `building` are both canonical
  (`A-185`/`W-202`) → exact (case-insensitive) match. Only the *default* needs the
  `forPlant` normalizer.
- **Empty result:** selected warehouse has no matching machines → existing
  empty-state UI; no special handling.
- **Blank/unknown building on a row:** rows whose `building` is blank or
  non-canonical do not match either option and are hidden while a warehouse is
  selected. (Acceptable; register rows carry canonical buildings. Flag any such
  rows during planning if the seed data has blanks.)
- **MachineList shared by non-operators:** the filter renders for all roles on
  `MachineListScreen`; it is **not** gated behind the existing `isOperator` flag.

## Testing

ViewModel-level unit tests (no UI/integration needed):

1. `Buildings.forPlant` / `defaultFor` across `"A-185-Koparkhairne"`, `"185"`,
   `"W202"`, `"W-202"`, `"both"`, `""`, `null`.
2. Default `selectedBuilding` equals the user's home warehouse on load; HEAD/
   blank-plant defaults to `A-185`.
3. Switching warehouse filters the list to that building only.
4. Building predicate composes correctly with existing search + category + status
   filters (order-independent).
5. `MachineList` status counts and category tabs reflect the building-filtered set.

## Open questions

- ~~`TransferFormScreen`~~ — **Resolved (dropped):** free-text machine-name
  autocomplete with existing From/To warehouse route dropdowns; no browsable
  machine list to filter.
