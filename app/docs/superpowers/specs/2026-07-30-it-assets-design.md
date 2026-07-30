# IT Technician interface — IT Assets (browse + add)

**Date:** 2026-07-30
**Status:** Approved, ready for implementation plan

## Problem

`mt_users` already carries `role = it_technician`, but the app has no such role. An
IT person logging in today falls through `AuthRepositoryImpl`'s `else` branch to
`OPERATOR` and lands on the operator home — a production-run screen they have no
business in. IT owns two asset categories in `mt_asset_list` (`Electric Asset` and
`IT/Communication`) and needs to see them category-wise and add new ones.

## Scope

**In:** role plumbing, an IT home with one "IT Assets" tile, a two-card category
chooser, a category-scoped asset list, and adding an asset into the drilled-in
category.

**Out (deliberately):** editing or shutting down assets, breakdown raise/resolve on
IT assets, PM plans, notifications, an asset detail screen. Rows are not tappable in
v1. These are separate features if IT wants them later.

## Decisions

| Question | Decision |
|---|---|
| Capability in v1 | Browse + add only |
| Categories shown | Fixed two: `Electric Asset`, `IT/Communication` |
| Plant scope | Both plants, with the A-185 / W-202 / Both toggle Machine Master uses. Assumption stated at design time and accepted. |
| Home screen | Reuse the shared `Dashboard` grid (tiles are pure config) rather than a third bespoke home |

## Architecture

Five thin edits to existing files plus one new `presentation/it` package. Nothing
existing changes behaviour for existing roles.

### 1. Role plumbing

- `Enums.kt` — add `IT_TECHNICIAN` to `UserRole`.
- `AuthRepositoryImpl` role mapping — `"IT_TECHNICIAN"`, `"IT TECHNICIAN"`, `"IT"`
  → `UserRole.IT_TECHNICIAN`. The backend's `MtUser.norm_role` upper-cases and
  trims, so `it_technician` arrives as `IT_TECHNICIAN`; the tolerant variants guard
  against a space-separated spelling being typed into pgAdmin.
- `NavGraph` role router — its `when (role)` is exhaustive with no `else`, so adding
  the enum value is a compile error until this branch exists:
  `IT_TECHNICIAN -> Screen.Dashboard.createRoute(role)`.
- `Screen.kt` — two routes: `ItAssetCategories` (`it/assets`) and
  `ItAssetList` (`it/assets/{category}`). The argument is the **enum name**
  (`ELECTRICAL` / `IT_COMMUNICATION`), never the backend string — `IT/Communication`
  contains a slash that would need URL-encoding and could break route matching.

Side effect, intentional and free: `UserFormSheet` enumerates `UserRole.values()`,
so head/admin can create IT users from the app the moment the enum value lands.

### 2. IT home

`dashboardTilesFor(IT_TECHNICIAN)` returns a single tile:
`DashboardTile("IT Assets", Icons.Filled.Devices, Open(Screen.ItAssetCategories.route))`.
No badge — there is no live count an IT user must react to yet.

### 3. Category definition — single source of truth

```kotlin
enum class ItAssetCategory(val label: String, val backendValue: String) {
    ELECTRICAL("Electrical Assets", "Electric Asset"),
    IT_COMMUNICATION("IT / Communication", "IT/Communication"),
}
```

`backendValue` strings match the ones already declared in `MtMachine.kt`'s
`NON_RUNNABLE_CATEGORIES` / `OPERATOR_HIDDEN_CATEGORIES` sets — the register's real
values. Adding a third IT category later is one line here; both screens follow.
All matching against `MtMachine.category` is trim + case-insensitive, so a casing
variant in the register still shows up.

### 4. Category chooser — `it/assets`

Two cards, one per `ItAssetCategory`, each with a live count of matching assets.
Counts come from the existing `GetMachineMasterUseCase` (`GET /mt-machines`), which
the ViewModel loads once. Tapping a card navigates to the asset list with the
category as the route argument.

### 5. Category asset list — `it/assets/{category}`

`ItAssetListScreen` + `ItAssetListViewModel`, fed by `GetMachineMasterUseCase`.
UI state filters, in order: category (fixed by the route arg) → building → floor →
search. Same filter set and interaction model as Machine Master, so it reads as
part of the same app:

- Building capsules: A-185 / W-202 / Both. Switching building resets the floor
  filter, matching `MachineMasterViewModel.onBuildingSelected`.
- Floor dropdown, scoped to the selected building.
- Search over asset name, asset id, and sub-location.
- Rows: asset id chip, asset name, `building · floor`, condition. Not clickable.

A "+ Add asset" FAB opens the add sheet with the category locked to the current one,
so an IT-added asset cannot land in a production category by typo. Submit calls the
existing `MtMachineRepository.addAsset` (`POST /mt-machines`) and refreshes the list
on success.

### 6. Shared-component change

`AddMachineSheet` gains two optional parameters:

- `title: String = "Add new machine"`
- `lockedCategory: String? = null`

When `lockedCategory` is non-null the category renders as a read-only field and the
"Use Production Equipment for a production asset" hint is suppressed (it is wrong
advice for an IT user). The existing machine-list caller passes neither, so its
behaviour is byte-for-byte unchanged.

## Backend dependency

`_CREATE_ROLES` in `backend/app/api/mt_machines.py` is
`{SUPERVISOR, HEAD, TECHNICIAN, ADMIN}`. An `it_technician` therefore gets **403 on
POST /mt-machines** until `IT_TECHNICIAN` is added to that set. Reads
(`GET /mt-machines`) already accept any authenticated user, so browsing works
immediately.

Per project workflow the backend is not edited from this repo — this ships as a
copy-paste handoff prompt for the backend team. Until it lands, the browse half of
the feature is fully usable and the add attempt surfaces the backend's
"Not allowed to add assets" message inside the sheet.

## Error handling

| Case | Behaviour |
|---|---|
| Register load fails | Inline "Failed to load asset register" + message + Retry, matching Machine Master |
| Add fails (403, network, validation) | Error text inside the sheet; entered values preserved so the user can retry or fix |
| Category has no assets | "No assets in this category yet" empty state; the Add FAB stays available |
| Unknown/unparseable category route arg | Falls back to `ELECTRICAL` rather than crashing on `valueOf`, matching how `NavGraph` already guards the dashboard's role argument with `runCatching { … }.getOrDefault(…)` |

## Verification

This repo has no JVM test harness for presentation classes, and per project
workflow APKs are not built here. Verification is a manual pass in Android Studio
by the user:

1. Log in as an `it_technician` user → lands on the IT home with the single
   "IT Assets" tile (not the operator home).
2. Tap the tile → two category cards with non-zero counts.
3. Open each category → only that category's assets, correct in both buildings;
   building toggle and floor filter narrow correctly; search matches name/id/floor.
4. Add an asset → category field is read-only and pre-set; on success the new asset
   appears in that category's list with a backend-assigned asset id.
5. Regression: technician, supervisor, head, QC and operator logins reach exactly
   the same screens as before; the machine-list Add sheet is unchanged.

Step 4 fails with a 403 until the backend change above is deployed — expected, and
worth confirming the error is the readable backend message.
