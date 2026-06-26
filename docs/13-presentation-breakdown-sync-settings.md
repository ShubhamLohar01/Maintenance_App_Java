# Presentation — Breakdown Flag, Sync Status, Settings

These three Compose screens round out the operator workflow outside of the run/timeline path. **Breakdown Flag** lets an operator raise a fault on their assigned machine with a severity, free-text description, and an optional before-photo. **Sync Status** surfaces the offline-first write queue (energy runs, breakdown flags) and lets the user force a retry. **Settings** shows the signed-in user, shift schedule reference, server mode, and the logout action. All three follow the same MVVM + Hilt pattern used elsewhere in the app: a `@HiltViewModel` exposes a `StateFlow<UiState>` consumed via `collectAsStateWithLifecycle`, with one-shot effects emitted on a `SharedFlow<UiEvent>`.

## Breakdown Flag

### Screen
File: [`../app/src/main/java/com/candorfoods/factoryops/presentation/breakdown/BreakdownFlagScreen.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/breakdown/BreakdownFlagScreen.kt)

Responsibility: capture a breakdown report against the machine passed in via the navigation argument and submit it through the ViewModel.

UI layout (wrapped in a `LoadingOverlay` driven by `uiState.isLoading`):

- `TopAppBar` titled "Flag Breakdown" with a back arrow that calls `onBack`.
- Severity section — a horizontal row of `FilterChip`s, one per `Severity` enum value (`CRITICAL`, `MAJOR`, `MINOR`). Each chip is color-coded from `FactoryTheme.colors`:
  - `CRITICAL` -> `statusStopped` (red)
  - `MAJOR` -> `statusFlagged` (amber)
  - `MINOR` -> `MaterialTheme.colorScheme.primary`
  Selected chip uses a 20% alpha container with the full color for the label.
- `OutlinedTextField` for description — multi-line (`minLines = 4`), placeholder text "Describe the breakdown — noise, smoke, vibration, etc.", `isError` is bound to `uiState.error != null`.
- Inline error `Text` rendered below the field when `uiState.error` is non-null.
- `FactoryCard` for the optional photo:
  - Title "Photo (Optional)" and a hint that camera capture is "available once permissions granted".
  - When `uiState.beforePhotoLocalPath` is non-null it shows the last 20 chars of the path as confirmation ("Photo attached: …`<tail>`").
  - Note: the camera launcher itself is not wired in this file — the screen renders the attached-state for a path produced elsewhere (CameraX intent flow that writes to `BreakdownPhotoStorage`) and surfaces it via `BreakdownFlagViewModel.onPhotoSelected`.
- `PrimaryButton` (`PrimaryButtonStyle.Danger`, primary size) titled "Submit Breakdown Flag" that calls `viewModel::onSubmit` and shows an inline spinner via its `loading` prop.

Event handling: `LaunchedEffect(Unit)` collects `viewModel.uiEvent`:
- `FlagRaised` -> calls `onBack()` to pop the screen on success.
- `ShowSnackbar(message)` -> displays the message on the screen's `SnackbarHostState`.

### ViewModel
File: [`../app/src/main/java/com/candorfoods/factoryops/presentation/breakdown/BreakdownFlagViewModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/breakdown/BreakdownFlagViewModel.kt)

Responsibility: hold the form state, preserve the machine context across config changes, and submit through `RaiseBreakdownFlagUseCase`.

- `machineId` is read from `SavedStateHandle` using `Screen.BreakdownFlag.ARG`, so the machine context survives process death and back-stack restoration.
- `BreakdownFlagUiState` — `severity: Severity = Severity.MAJOR` (default), `description: String = ""`, `beforePhotoLocalPath: String? = null`, `isLoading`, `error`.
- `BreakdownFlagUiEvent` — `FlagRaised` (success, screen pops) and `ShowSnackbar(message)` (error toast).
- Actions:
  - `onSeverityChanged(s)` — updates `severity`.
  - `onDescriptionChanged(d)` — updates `description` and clears any prior `error` so the field returns to a non-error state as the user types.
  - `onPhotoSelected(path)` — stores the local file path produced by the camera capture step.
  - `onSubmit()` — sets `isLoading = true`, calls `raiseBreakdownFlag(machineId, severity, description, beforePhotoLocalPath)`, then on `Result.Success` emits `FlagRaised`, on `Result.Error` writes the message to `error` and emits `ShowSnackbar`.

Validation rules:
- Severity is **required** but always satisfied (default `MAJOR`, no "unset" option).
- Description is **required** — `RaiseBreakdownFlagUseCase` rejects blank input with `Result.Error("Description cannot be empty")` before hitting the repository (see [`../app/src/main/java/com/candorfoods/factoryops/domain/usecase/breakdown/RaiseBreakdownFlagUseCase.kt`](../app/src/main/java/com/candorfoods/factoryops/domain/usecase/breakdown/RaiseBreakdownFlagUseCase.kt)).
- Before-photo is **optional** — `beforePhotoLocalPath` is nullable end-to-end and is passed through to the repository as-is. The repository / sync layer is responsible for uploading the file referenced by the path (captured via the CameraX intent flow and persisted by `BreakdownPhotoStorage`).

## Sync Status

### Screen
File: [`../app/src/main/java/com/candorfoods/factoryops/presentation/sync/SyncStatusScreen.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/sync/SyncStatusScreen.kt)

Responsibility: show the pending offline write queue and let the user force an immediate retry.

UI layout (single `LazyColumn`):

- `TopAppBar` titled "Sync Status" with a back arrow and a `Refresh` action icon wired to `viewModel::onRetrySync`.
- `SyncSummaryCard` (always shown) — `Row` containing:
  - Left column: either "Syncing…" (when `isSyncing`) or `"$pendingCount pending actions"`, with a sub-line "Everything is up to date" / "Will sync when connected".
  - Right side: a "Retry" `Button` when `pendingCount > 0 && !isSyncing`, or a `CircularProgressIndicator` when `isSyncing`.
- "Pending Actions" section — header `Text` + a `SyncQueueCard` per `SyncQueueItem`. Each card shows `actionType.name`, the retry count, the formatted `createdAt` (via `DateTimeFormatter.formatDateTime`), and the `lastErrorMessage` in error color when present. Items are keyed by `item.id`.
- "Pending Breakdown Flags" section — header + a card per `BreakdownFlag` showing `severity.name` (bold) and `syncStatus.name`. Keyed by `flag.id`.
- Empty-state — when both `pendingCount == 0` and `syncQueue.isEmpty()`, a centered "All synced" message in the primary color.

Note: the screen does not render a "last sync time" field directly; freshness is conveyed indirectly via the per-item `createdAt` timestamps and the pending count (zero implies everything is current).

### ViewModel
File: [`../app/src/main/java/com/candorfoods/factoryops/presentation/sync/SyncStatusViewModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/sync/SyncStatusViewModel.kt)

Responsibility: combine the three independent reactive sync streams into a single UI state and trigger manual retries.

- `SyncStatusUiState` — `pendingCount: Int`, `syncQueue: List<SyncQueueItem>`, `pendingBreakdowns: List<BreakdownFlag>`, `isSyncing: Boolean`.
- `init` launches three concurrent collectors, each updating its own slice of the state:
  - `energyRepository.getPendingSyncCount()` -> `pendingCount`.
  - `breakdownRepository.getSyncQueue()` -> `syncQueue`.
  - `breakdownRepository.getPendingBreakdowns()` -> `pendingBreakdowns`.
- Action: `onRetrySync()` — sets `isSyncing = true`, calls `energyRepository.syncPendingRuns()`, then resets `isSyncing = false`. The breakdown queue is observed only (no explicit retry call from this VM); reactive Flows update it as the worker drains items.

Validation rules: none — this is a read-mostly screen. The only invariant is that `isSyncing` toggles around the manual retry call.

## Settings

### Screen
File: [`../app/src/main/java/com/candorfoods/factoryops/presentation/settings/SettingsScreen.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/settings/SettingsScreen.kt)

Responsibility: show user identity / plant context, static reference info, and provide a confirmed logout path.

UI layout (vertical `Column`):

- `TopAppBar` titled "Settings" with a back arrow calling `onBack`.
- User card (rendered only when `uiState.currentUser` is non-null) — shows `user.name` (title), `user.username`, `Role: ${user.role.name}`, `Plant: ${user.plantId}`.
- `HorizontalDivider`.
- Shift Schedule card — static reference text for Shifts A/B/C (`08:00–16:00`, `16:00–24:00`, `00:00–08:00` IST).
- Server card — static info: `"Mode: Mock (debug build)"` and `"Phase 2: Coming Soon"`.
- `Spacer(weight = 1f)` pushes the logout button to the bottom.
- Logout `Button` — full-width, 52 dp tall, error-color container, disabled while `uiState.isLoggingOut`. On click it sets `showLogoutDialog = true`.

Confirmation flow: when `showLogoutDialog` is true, a `ConfirmDialog` is rendered with title "Logout", message "Any unsynced data will be lost. Are you sure?", confirm label "Logout". Confirming closes the dialog and calls `viewModel.onLogout()`; dismissing just closes the dialog.

Event handling: `LaunchedEffect(Unit)` collects `viewModel.uiEvent`; on `LoggedOut` it calls the `onLogout` navigation callback (typically routing back to the auth graph).

### ViewModel
File: [`../app/src/main/java/com/candorfoods/factoryops/presentation/settings/SettingsViewModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/settings/SettingsViewModel.kt)

Responsibility: expose the current user and execute logout.

- `SettingsUiState` — `currentUser: User?` (null until first emission), `isLoggingOut: Boolean`.
- `SettingsUiEvent` — single `LoggedOut` object emitted after logout completes.
- `init` collects `getCurrentUser()` and assigns each `User` into `currentUser`.
- Action: `onLogout()` — sets `isLoggingOut = true`, runs `logoutUseCase()`, resets `isLoggingOut = false`, then emits `LoggedOut`. The button is disabled during this window to prevent double-tap.

Validation rules: none on the form side; the destructive action is gated by the `ConfirmDialog` in the screen layer, and the loading flag prevents reentry.
