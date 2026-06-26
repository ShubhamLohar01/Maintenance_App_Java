# Presentation — Auth, Home, Machine, History, Machine Master

This document covers the operator-facing entry surface of the FactoryOps Android app: login, the role-aware home router, the operator's machine list, machine detail (start/stop production with confirmation dialogs), 30-day run history, and the read-only Machine Master catalog. Once `LoginScreen` succeeds, navigation lands on `HomeRouterScreen`, which observes `AuthRepository.getCurrentUser()` and dispatches to a role-specific home destination — `OPERATOR` and `SUPERVISOR` to the operator `HomeScreen`, `TECHNICIAN` to the tickets surface, and `QC_HEAD` / `QC_MEMBER` to the QC inbox — falling back to login if no user materialises within 4 seconds. All ViewModels are constructor-injected by Hilt and resolved in Composables via `hiltViewModel()`; UI state is exposed as `StateFlow` and one-shot side effects (snackbars, navigation) as `SharedFlow`.

## Login

### LoginScreen

- File: [../app/src/main/java/com/candorfoods/factoryops/presentation/login/LoginScreen.kt](../app/src/main/java/com/candorfoods/factoryops/presentation/login/LoginScreen.kt)
- Renders a single centred form inside a `Scaffold`: the "FactoryOps" headline, a "Candor Foods" subtitle, two `OutlinedTextField`s (Username with `ImeAction.Next`; Password with `PasswordVisualTransformation` and an `IME Done` action that triggers login), an inline error `Text` (shown only when `uiState.error != null`), a full-width `PrimaryButton` ("Login"), and a faint demo-credentials hint (`operator1 / pass123`). A `LoadingOverlay` wraps everything and dims the form while `uiState.isLoading` is true. The password field has a trailing `IconButton` toggling `Visibility` / `VisibilityOff`.
- Key user actions: typing into username/password (`viewModel::onUsernameChanged`, `viewModel::onPasswordChanged`), tapping the eye icon to reveal the password, pressing IME `Done` or the Login button (`viewModel::onLoginClicked`).
- Navigation: collects `viewModel.uiEvent` in a `LaunchedEffect`; on `LoginUiEvent.NavigateToHome` it invokes the `onNavigateToHome` lambda supplied by the nav graph.

### LoginViewModel

- File: [../app/src/main/java/com/candorfoods/factoryops/presentation/login/LoginViewModel.kt](../app/src/main/java/com/candorfoods/factoryops/presentation/login/LoginViewModel.kt)
- Injected dependencies: `LoginUseCase`, `AuthRepository`.
- UI state `LoginUiState`: `username: String`, `password: String`, `isLoading: Boolean`, `error: String?`.
- Events `LoginUiEvent`: `NavigateToHome` (object).
- Exposed members: `uiState: StateFlow<LoginUiState>`, `uiEvent: SharedFlow<LoginUiEvent>`, `isLoggedIn: StateFlow<Boolean>` (seeded from `authRepository.isLoggedIn()` at construction).
- Actions:
  - `onUsernameChanged(v)` / `onPasswordChanged(v)` — update the field and clear any prior error.
  - `onLoginClicked()` — sets `isLoading = true`, invokes `loginUseCase(username, password)`. On `Result.Success` clears loading and emits `NavigateToHome`; on `Result.Error` clears loading and surfaces `result.message` into `error`.

## Home (Operator)

### HomeRouterScreen

- File: [../app/src/main/java/com/candorfoods/factoryops/presentation/home/HomeRouterScreen.kt](../app/src/main/java/com/candorfoods/factoryops/presentation/home/HomeRouterScreen.kt)
- Acts as a thin gate between login and a role-specific home. While the current user is resolving it shows a centered `CircularProgressIndicator`; if the defensive 4-second timeout elapses without a user, it swaps to "Session lost. Returning to login…" and triggers `onGiveUp`. Once a user is observed, it invokes `onResolved(user.role)` so the navigation graph can route to the correct destination per `UserRole` (`OPERATOR`, `TECHNICIAN`, `QC_HEAD`, `QC_MEMBER`, `SUPERVISOR`).
- Two `LaunchedEffect`s coordinate the handoff: one keyed on `user` calls `onResolved` when non-null, the other keyed on `giveUp`/`user` calls `onGiveUp` when the timeout fires without a user.

### HomeRouterViewModel

- File: [../app/src/main/java/com/candorfoods/factoryops/presentation/home/HomeRouterScreen.kt](../app/src/main/java/com/candorfoods/factoryops/presentation/home/HomeRouterScreen.kt) (declared alongside the screen)
- Injected dependencies: `AuthRepository`.
- Exposed state: `user: StateFlow<User?>`, `giveUp: StateFlow<Boolean>`.
- Flows observed: `authRepository.getCurrentUser()` — pushes the resolved `User?` into `_user`.
- Behavior: in `init` it launches a 4-second `delay`; if `_user.value` is still null afterwards it flips `_giveUp` to `true`, prompting the screen to send the navigation graph back to login.

### HomeScreen

- File: [../app/src/main/java/com/candorfoods/factoryops/presentation/home/HomeScreen.kt](../app/src/main/java/com/candorfoods/factoryops/presentation/home/HomeScreen.kt)
- The operator landing page. `TopAppBar` shows "FactoryOps" plus the current user's name and plant id, with actions: a `SyncIndicator` (pending count + spinner) wired to `onSyncClick`, a `Refresh` icon calling `viewModel::onRefresh`, a `Settings` icon, and a `MoreVert` overflow menu containing a single "Machine Master" entry that invokes `onMachineMasterClick`.
- Body renders one of three states:
  - `isLoading` — centered `CircularProgressIndicator`.
  - `error != null` — "Failed to load machines" message plus an outlined "Retry" `PrimaryButton`.
  - Otherwise — a search `OutlinedTextField` with leading `Search` icon and a `Close` clear-button, followed by a `LazyColumn` containing a header line ("Assigned Machines (N)" or "Showing X of N" while filtering), then `MachineCard` rows for `filteredMachines`. When the search has no matches a centred "No machines match …" message offers a "Clear search" `TextButton`.
- "Others" section: a `stickyHeader` ("Others (count)") appears only when `others` is non-empty or `othersError != null`. The header has a rotating chevron and toggles via `viewModel::onToggleOthers`. When expanded it renders either the `othersError` text, a "No others match …" empty state, or `OthersRow` cards (machine name, id + floor, `MachineTypeChip`, rated kW or `—`).
- Navigation: `onMachineClick(machine.id)` per row, `onSyncClick`, `onSettingsClick`, `onMachineMasterClick` (from overflow menu).

### HomeViewModel

- File: [../app/src/main/java/com/candorfoods/factoryops/presentation/home/HomeViewModel.kt](../app/src/main/java/com/candorfoods/factoryops/presentation/home/HomeViewModel.kt)
- Injected dependencies: `GetAssignedMachinesUseCase`, `GetCurrentUserUseCase`, `GetPendingSyncCountUseCase`, `MachineRepository`, `GetMachineMasterUseCase`.
- UI state `HomeUiState`: `machines: List<Machine>`, `isLoading: Boolean`, `error: String?`, `currentUser: User?`, `pendingSyncCount: Int`, `isSyncing: Boolean`, `searchQuery: String`, `others: List<MtMachine>`, `othersError: String?`, `othersExpanded: Boolean`. Derived: `filteredMachines` (case-insensitive match on name/code/location) and `filteredOthers` (match on machineName/machineId/floor).
- Flows observed in `init`:
  - `getCurrentUser()` → `currentUser`.
  - `getAssignedMachines()` → `machines` + `isLoading`/`error` mapped from `Result`.
  - `getPendingSyncCount()` → `pendingSyncCount`.
  - `getMachineMaster(category = MachineCategory.OTHERS)` (one-shot) → `others` / `othersError`.
- Actions:
  - `onSearchChange(query)` — updates `searchQuery`.
  - `onToggleOthers()` — flips `othersExpanded`.
  - `onRefresh()` — sets `isSyncing = true`, calls `machineRepository.syncMachines()`, clears the flag, and re-runs `loadOthers()`.

## Machine Detail

### MachineDetailScreen

- File: [../app/src/main/java/com/candorfoods/factoryops/presentation/machine/MachineDetailScreen.kt](../app/src/main/java/com/candorfoods/factoryops/presentation/machine/MachineDetailScreen.kt)
- Sections (in a `LazyColumn` inside a `LoadingOverlay`):
  - `TopAppBar` with the machine name, a back arrow, and — for operators only — a red `Warning` action that invokes `onBreakdownClick(machineId)`.
  - `HeaderCard` — machine code, large `StatusBadge`, location, plus three `InfoItem`s (Rated Power, Load Factor, Criticality).
  - `LifecycleSection` — optional `LifecycleBanner` summarising any active `BreakdownFlag` (OPEN, ACKNOWLEDGED, PENDING_QC, REOPENED), then a context-sensitive primary action:
    - Technicians see read-only guidance text.
    - `PENDING_QC` + operator → no button (banner explains the wait).
    - `FLAGGED` → no button.
    - `RUNNING` with an active run → red `Production Stop` Hero button.
    - `IDLE` / `STOPPED` → green `Production Start` Hero button.
  - "Today's Runs" header with a `TextButton` to "30-day history" (calls `onHistoryClick(machine.id)`).
  - Either an empty-state "No runs logged today" or `RunRowItem` cards (start time, end time / "Running…", duration, computed kWh).
- Confirm dialogs: tapping Start / Stop opens a `ConfirmDialog` ("Start Production" / "Stop Production") asking to confirm against the machine name; confirm fires `onConfirmStart` / `onConfirmStop`, dismiss fires `onDismissDialogs`.
- `LifecycleBanner` additionally displays technician notes (`partsUsed` + after-photo via `AsyncImage`) when `PENDING_QC`, and the QC kick-back reason (`qcDecisionNotes`) when `REOPENED`.
- Snackbar host: collects `MachineDetailUiEvent.ShowSnackbar` events into a `SnackbarHostState`.
- Navigation calls: `onBack()`, `onHistoryClick(machineId)`, `onBreakdownClick(machineId)`.

### MachineDetailViewModel

- File: [../app/src/main/java/com/candorfoods/factoryops/presentation/machine/MachineDetailViewModel.kt](../app/src/main/java/com/candorfoods/factoryops/presentation/machine/MachineDetailViewModel.kt)
- Injected dependencies: `SavedStateHandle` (reads `Screen.MachineDetail.ARG` → `machineId`), `MachineRepository`, `EnergyRepository`, `BreakdownRepository`, `StartProductionRunUseCase`, `StopProductionRunUseCase`, `GetTodaysRunsUseCase`, `GetCurrentUserUseCase`.
- UI state `MachineDetailUiState`: `machine: Machine?`, `activeRun: ProductionRun?`, `todaysRuns: List<ProductionRun>`, `activeFlag: BreakdownFlag?`, `currentUser: User?`, `isLoading: Boolean`, `error: String?`, `showStartDialog: Boolean`, `showStopDialog: Boolean`. Derived: `isTechnician`, `isOperator` based on `currentUser.role`.
- Events `MachineDetailUiEvent`: `ShowSnackbar(message: String)`.
- Flows observed in `init`:
  - `machineRepository.getMachineById(machineId)` → `machine`.
  - `energyRepository.getActiveRunForMachine(machineId)` → `activeRun`.
  - `getTodaysRuns(machineId)` → `todaysRuns`.
  - `breakdownRepository.observeLatestFlagForMachine(machineId)` → `activeFlag`.
  - `getCurrentUser()` → `currentUser`.
- Actions:
  - `onStartClicked()` / `onStopClicked()` — open the corresponding confirm dialog.
  - `onDismissDialogs()` — closes both.
  - `onConfirmStart()` — dismisses the dialog; if `machine.currentStatus == PENDING_QC` it short-circuits with snackbar "Cannot start — QC verifying repair". Otherwise calls `startRunUseCase(machineId, machine.expectedRunHours)` and emits "Production started" or the error message.
  - `onConfirmStop()` — dismisses the dialog and calls `stopRunUseCase(activeRun.id)`, emitting "Production stopped" or the error.

## History

### HistoryScreen

- File: [../app/src/main/java/com/candorfoods/factoryops/presentation/history/HistoryScreen.kt](../app/src/main/java/com/candorfoods/factoryops/presentation/history/HistoryScreen.kt)
- `Scaffold` with a "30-Day History" `TopAppBar` and back arrow. Body branches on state: loading spinner, error text, "No history in the last 30 days" empty state, or a `LazyColumn`.
- The list begins with a `HistorySummaryCard` (a `primaryContainer`-tinted card with three centered `SummaryItem`s: Total Runs, Completed, Total kWh — `totalKwh` is the sum of `computedKwh` across runs). Beneath it, each `HistoryRunCard` shows the run date (bold), start → end (or "Running"), duration, and on the right the computed kWh plus the sync-status label.
- Navigation: `onBack()` only.

### HistoryViewModel

- File: [../app/src/main/java/com/candorfoods/factoryops/presentation/history/HistoryViewModel.kt](../app/src/main/java/com/candorfoods/factoryops/presentation/history/HistoryViewModel.kt)
- Injected dependencies: `SavedStateHandle` (reads `Screen.History.ARG` → `machineId`), `GetMachineHistoryUseCase`.
- UI state `HistoryUiState`: `runs: List<ProductionRun>`, `isLoading: Boolean`, `error: String?`, `machineId: String`.
- Flows observed: `getMachineHistory(machineId, from = now - 30d, to = now)` — emits `Loading` / `Success(runs)` / `Error(message)`, which feed `isLoading`, `runs`, and `error`. Loaded once in `init`; no user-facing refresh action.

## Machine Master

### MachineMasterScreen

- File: [../app/src/main/java/com/candorfoods/factoryops/presentation/machinemaster/MachineMasterScreen.kt](../app/src/main/java/com/candorfoods/factoryops/presentation/machinemaster/MachineMasterScreen.kt)
- Read-only plant-wide catalog opened from `HomeScreen`'s overflow menu. `TopAppBar` carries "Machine Master", a back arrow, and a `Refresh` action wired to `viewModel::onRetry`. Body branches on `isLoading` (spinner), `error` (an `ErrorBlock` with retry `PrimaryButton`), or `Content`.
- `Content` lays out a `FilterBar` and a `LazyColumn` of category sections:
  - `FilterBar` — two side-by-side `FilterDropdown`s (Floor and Type, each with an "All …" first option), then a "Show Others" `Switch`. Floor/type options are derived from the loaded data.
  - Each `MachineCategory` (`PRODUCTION` always, plus `OTHERS` when the toggle is on) renders as a `stickyHeader` with title + count and a rotating chevron expand/collapse, followed by either a "No matches in this section" placeholder or `MtMachineRow` cards.
  - `MtMachineRow` shows machine name (bold), a pill `IdChip` with the machine id, the floor (muted), and a row with `MachineTypeChip` plus rated kW or `—`.
- No outbound navigation beyond `onBack()`.

### MachineMasterViewModel

- File: [../app/src/main/java/com/candorfoods/factoryops/presentation/machinemaster/MachineMasterViewModel.kt](../app/src/main/java/com/candorfoods/factoryops/presentation/machinemaster/MachineMasterViewModel.kt)
- Injected dependencies: `GetMachineMasterUseCase`.
- UI state `MachineMasterUiState`: `isLoading`, `error`, `rows: List<MtMachine>`, `selectedFloor: String?`, `selectedType: MachineType?`, `showOthers: Boolean` (default false), `expandedProduction: Boolean` (default true), `expandedOthers: Boolean` (default false). Derived: `floors` (distinct sorted floor values from `rows`), `types` (distinct sorted `MachineType`s from `rows`), `sections` (fixed ordering: PRODUCTION first, OTHERS only when `showOthers`; each paired with filtered rows so filter-empty sections still render with a placeholder), and `isExpanded(category)`.
- Flows observed: none — `load()` calls `getMachineMaster()` once and folds the `Result` into `rows`/`error`.
- Actions:
  - `load()` / `onRetry()` — fetch and replace the catalog.
  - `onFloorSelected(floor)` / `onTypeSelected(type)` — update filter selection.
  - `onShowOthersChange(show)` — toggle inclusion of the OTHERS section.
  - `onToggleSection(category)` — flip `expandedProduction` or `expandedOthers`.

### MachineTypeChip

- File: [../app/src/main/java/com/candorfoods/factoryops/presentation/machinemaster/MachineTypeChip.kt](../app/src/main/java/com/candorfoods/factoryops/presentation/machinemaster/MachineTypeChip.kt)
- A reusable pill rendering a `MachineType.name` label, used by both `MachineMasterScreen.MtMachineRow` and `HomeScreen.OthersRow`. The colour is selected by mapping `MachineType` to a `MachineTypeBucket` (`Production`, `Utility`, `Handling`, `Other`) via the `colorBucket()` extension, then resolving to a theme accent (`statusRunning`, `statusIdle`, `statusFlagged`, or muted onSurface). The chip uses an 18%-alpha tinted background with a fully rounded shape, the accent colour for the label, and `TouchTarget.chip` height so it stays tappable-sized even though it carries no click handler here.
