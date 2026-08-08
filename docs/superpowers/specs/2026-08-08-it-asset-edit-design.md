# IT technician — open and edit an IT / Electrical asset

**Date:** 2026-08-08
**Status:** approved, ready to implement

## Goal

An `it_technician` browsing **IT Assets → Electrical / IT-Communication** can tap an
asset row, see its full record, and edit it. Today rows are inert: the list is the
end of the road, and the only asset editor in the app belongs to the supervisor's
Machine Detail screen.

## Where this sits

```
Dashboard (IT technician)
  └ IT Assets tile
      └ ItAssetCategoriesScreen        existing, unchanged
          └ ItAssetListScreen          rows become tappable
              └ ItAssetDetailScreen    NEW — read-only record, ✎ opens the sheet
                  └ AssetEditSheet     shared, lifted out of MachineDetailScreen
```

## Navigation

New route `Screen.ItAssetDetail("it/asset/{assetId}")`, keyed on `assetId` alone —
`asset_id` is the immutable backend key and the category is already implied by where
you came from. The id is pushed into the ViewModel from the screen via
`LaunchedEffect`, matching `ItAssetListScreen` and `RaisedTicketDetailScreen` rather
than injecting `SavedStateHandle`.

`ItAssetListScreen` gains an `onAssetClick: (String) -> Unit`, `ItAssetRow` becomes
clickable, and the screen re-fetches on `Lifecycle.Event.ON_RESUME` (the pattern
already used by `UtilityHistoryScreen`) so an edit is visible on the way back.

## Reusing the edit form

`EditAssetSheet` and `EditField` are private inside `MachineDetailScreen.kt`, and
`AssetEditForm` lives in `MachineDetailViewModel.kt`. All three move to
`presentation/common/components/AssetEditSheet.kt` as public API, with two new
parameters:

- `title` — "Edit asset" here, the existing supervisor string there.
- `lockedCategory: String?` — when set, Category renders read-only and disabled
  (the same device `AddMachineSheet` already uses), so an IT edit cannot move an
  asset out of the category whose list it was opened from.

`MachineDetailScreen` then imports the shared sheet. Its behaviour is unchanged.

## Editable fields

Exactly the supervisor set: asset name, building, category (locked here), floor /
sub-location, quantity, model no., serial no., power load, condition, remarks.

Not editable: `asset_id` (backend key), `rated_kw` (backend-computed from power
load), and `assigned_to` — the backend's `PUT /mt-machines/{asset_id}` does not
persist `assigned_to` today, so it stays read-only on the detail screen rather than
offering a field whose value would be silently dropped.

## ItAssetDetailViewModel

Injects `MtMachineRepository` and `GetCurrentUserUseCase`. Loads with
`getByAssetId(assetId)`.

State: `asset`, `isLoading`, `error`, `showEditSheet`, `editForm`, `isSavingEdit`,
`editError`, `canEdit`.

`canEdit` is true for `IT_TECHNICIAN`, `SUPERVISOR`, `HEAD`, `ADMIN` — mirroring
what the backend will accept, so the pencil never appears to someone who would only
earn a 403.

Save follows the proven supervisor path: validate asset name and building non-blank
locally, `updateAsset(current.copy(...))`, swap in the returned row (the backend
recomputes `rated_kw`), emit a "Asset updated" snackbar. On `Result.Error` the
backend message goes straight into `editError` inside the still-open sheet, so a 403
reads as *"Not allowed to edit assets"* instead of failing silently.

## Backend dependency

This is blocked end-to-end until the backend allows the role.
`app/api/mt_machines.py` gates edits on `_EDIT_ROLES = {SUPERVISOR, HEAD, ADMIN}`
and creates on `_CREATE_ROLES = {SUPERVISOR, HEAD, TECHNICIAN, ADMIN}`;
`IT_TECHNICIAN` is in neither, so the **Add asset** FAB already on this screen 403s
today as well.

The handoff in `app/docs/backend/it-assets-api-requirements.md` is updated to ask
for a category-scoped grant on both `POST /mt-machines` and
`PUT /mt-machines/{asset_id}`: an `IT_TECHNICIAN` may write only rows whose category
is `"Electric Asset"` or `"IT/Communication"` (case-insensitive), and on edit both
the stored row's category *and* the incoming payload's must be in that set, so the
role cannot move an asset out of its own scope.

The frontend ships and compiles ahead of that change; saves surface the 403 message
until it lands.

## Testing

`ItAssetDetailViewModelTest`, alongside the existing `it/` ViewModel tests
(MockK + `UnconfinedTestDispatcher`, same shape as `ItAssetListViewModelTest`):

- loads the asset by id
- unknown id → error state, no asset
- a load failure surfaces the backend message
- opening the sheet prefills the form from the asset
- a successful save swaps in the row the backend returned
- a rejected save keeps the sheet open and shows the backend message
- blank asset name or building blocks the call and shows a local validation error
- `canEdit` is false for `OPERATOR`, true for `IT_TECHNICIAN`
