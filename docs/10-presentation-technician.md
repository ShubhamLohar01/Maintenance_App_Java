# Presentation — Technician (Breakdowns + PM Execution)

The technician surface is the day-to-day cockpit for plant maintenance staff. It is built on Jetpack Compose with `StateFlow`-backed, Hilt-injected ViewModels and routes the user through two parallel work streams from a single home:

1. **Breakdown tickets** — reactive flags raised by operators that the technician acknowledges and resolves with a parts-used note and after-photo. Three lifecycle-scoped tabs surface the queue: `Open`, `My Active`, `Resolved`.
2. **Preventive Maintenance (PM) tasks** — scheduler-assigned work orders the technician acknowledges, starts, then executes through a multi-step wizard before submitting for supervisor approval. Surfaced under a 4th `PM tasks` tab introduced in Phase 2C-2.

The home Scaffold uses a `ScrollableTabRow` with badge counts per tab and dispatches each row click into one of two detail flows. Both detail screens share Material 3 idioms (TopAppBar back, SnackbarHost, `LoadingOverlay`/`CircularProgressIndicator`), the shared `FactoryCard`/`PrimaryButton` toolkit from `presentation/common`, and the `FactoryTheme` semantic color tokens (`statusRunning`, `statusStopped`, `statusFlagged`, `info`, `onSurfaceMuted`).

A JIT permission flow runs on the home screen: on Android 13+ (Tiramisu), the first time the technician's list contains an overdue PM (`escalationTier > 0`), `POST_NOTIFICATIONS` is requested via `rememberLauncherForActivityResult`. Either grant or denial degrades silently — push is best-effort, the queue is still the source of truth (Phase 2C-4).

---

## Breakdown Ticket Flow

### [`TechnicianHomeScreen.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/technician/TechnicianHomeScreen.kt)

Top-level Composable for the technician inbox. Renders a `TopAppBar` showing `"FactoryOps — Maintenance"` plus `"<user.name> — <user.plantId>"`, with a Settings icon and an overflow menu (`MoreVert`) containing a `Machine Master` entry. Beneath it sits a `ScrollableTabRow` (`edgePadding = 0.dp`) with four tabs, each rendered through a private `TabItem(label, count, selected, onClick)` that appends `(count)` only when non-zero.

Key params:
- `onTicketClick: (String) -> Unit` — route to ticket detail by flag id.
- `onPmClick: (String) -> Unit` — route to PM detail by work-order id.
- `onSettingsClick: () -> Unit`, `onMachineMasterClick: () -> Unit`.
- `viewModel: TechnicianHomeViewModel = hiltViewModel()`.

State:
- `selectedTab: Int` (saveable via `rememberSaveable`) — initial value 0 (Open).
- `overflowExpanded: Boolean` — controls the overflow `DropdownMenu`.
- Reads `uiState` via `collectAsStateWithLifecycle()`.

Tab content:
- Tabs 0–2 render a `LazyColumn` of `TicketCard`s keyed by `flag.id`, with empty-state strings tuned to each tab (e.g. *"No open tickets — nothing flagged in this plant."*, *"Nothing acknowledged by you. Tap a ticket on the Open tab to acknowledge it."*, *"No resolved tickets in the last 30 days."*).
- Tab 3 delegates to a private `PmTasksList(rows, onPmClick)` that empty-states with *"No active PM tasks. Scheduler will assign them as plans come due."* and renders `PmTaskCard`s keyed by `wo.id`.

JIT notification request:
```kotlin
val hasOverduePm = uiState.pmTasks.any { it.wo.escalationTier > 0 }
LaunchedEffect(hasOverduePm) { /* request POST_NOTIFICATIONS once on Tiramisu+ */ }
```

### [`TechnicianHomeViewModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/technician/TechnicianHomeViewModel.kt)

`@HiltViewModel` aggregating four independent flows into a single `TechnicianHomeUiState`:

```kotlin
data class TechnicianHomeUiState(
    val currentUser: User? = null,
    val isLoading: Boolean = true,
    val openTickets: List<TicketRowUiModel> = emptyList(),
    val activeTickets: List<TicketRowUiModel> = emptyList(),
    val resolvedTickets: List<TicketRowUiModel> = emptyList(),
    val pmTasks: List<PmTaskRowUiModel> = emptyList()
)
```

Constructor injects `GetCurrentUserUseCase`, `BreakdownRepository`, `MachineRepository`, `PmWorkOrderRepository`.

Initialization:
- `init` block kicks off `machineRepository.syncMachines()` and `breakdownRepository.seedMockOpenFlagsIfEmpty()`.
- Then observes `getCurrentUser()` (filterNotNull); on first user, calls `startObserving(user)`.

`startObserving(user)` launches four collectors keyed off the user's plant/id, each `combine`-d with the cached `Map<String, Machine>` (built from `machineRepository.observeAllMachines()`):
- Open: `breakdownRepository.observeOpenFlagsByPlant(plantId)` → `openTickets`, also clears `isLoading`.
- Active: `breakdownRepository.observeAcknowledgedFlagsByUser(user.id)` → `activeTickets`.
- Resolved: `breakdownRepository.observeResolvedFlagsByPlant(plantId, thirtyDaysAgo)` → `resolvedTickets` (last 30 days).
- PM tasks: `pmWoRepository.observeForTechnician(user.id, listOf(NOTIFIED, ACKNOWLEDGED, IN_PROGRESS))` → `pmTasks`, sorted by `scheduledDate`. `SUBMITTED` PMs intentionally drop off this tab — they move to the supervisor queue.

Private mappers `List<BreakdownFlag>.toTicketRows(machineMap)` and `List<PmWorkOrder>.toPmRows(machineMap)` join each domain entity with its `Machine` (drops if machine missing).

### [`TicketCard.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/technician/TicketCard.kt)

Composable row used in all three ticket tabs. Wraps the shared `FactoryCard` and lays out:
- Header row: machine name (bold `titleMedium`) + machine code (muted `bodySmall`), right-aligned `CriticalityBadge` (A/B/C 24dp square) and `SeverityChip` (CRITICAL/MAJOR/MINOR pill).
- 2-line ellipsized `flag.description`.
- Conditional `QC kicked back` chip on `BreakdownStatus.REOPENED` (errorContainer background, `FontWeight.Black`).
- Footer `StatusLine`: produces a status string and color per `BreakdownStatus` (OPEN → error red `"Flagged …"`, ACKNOWLEDGED → dark amber `"Acknowledged by … <relative>"`, PENDING_QC → green `"Sent to QC by …"`, QC_APPROVED → green `"Approved by …"`, REOPENED → error red `"Disapproved by …"`). Right-aligned `PhotoCamera` icon shows if a before- or after-photo exists.

Palette: uses a high-contrast amber trio locally defined (`DarkAmberText = #92400E`, `LightAmberBg = #FEF3C7`, `AmberBorder = #F59E0B`) for B-criticality / MAJOR severity / acknowledged-state cues.

### [`TicketDetailScreen.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/technician/TicketDetailScreen.kt)

Full-screen detail for a single breakdown flag with status-driven action surface. Wraps content in `LoadingOverlay`; collects `TicketDetailUiEvent` (`ShowSnackbar`, `Acknowledged`, `Resolved`) — `Resolved` triggers `onBack()`.

Layout (scroll column):
- Header `FactoryCard`: machine code/location, severity chip, operator id, raised-at relative + absolute timestamp, full description, optional before-photo (`AsyncImage` at 180dp).
- Status-driven action zone (`when (flag.status)`):
  - **OPEN** → an intentionally one-off amber `Button` (`AmberBorder` container, black content, `TouchTarget.primary` height) labeled *"Acknowledge — I'll fix this"* with `Build` icon. Comment notes this is the only Warning-semantic exception; `PrimaryButton` doesn't cover Warning.
  - **ACKNOWLEDGED** → amber summary `Card` ("Acknowledged by … at …") followed by `PrimaryButton` Success *"Mark Resolved"* (`CheckCircle` icon) that calls `onOpenResolveSheet`.
  - **PENDING_QC** → surface-variant summary `Card` with resolved-by, parts-used, after-photo preview, and `"Picked up by QC: …"` if `qcAcknowledgedByName` set.
  - **QC_APPROVED** → green summary `Card` with QC name, time, optional QC notes.
  - **REOPENED** → error-container `Card` with reason from `qcDecisionNotes`, plus a `PrimaryButton` Success *"Re-resolve"* re-opening the resolve sheet.

`ResolveSheet` (private `ModalBottomSheet`):
- Multi-line `OutlinedTextField` *"Parts used / work done"* (minLines 4) with placeholder *"e.g. replaced V-belt, cleaned filter, tightened mounts"*.
- `Card` for after-repair photo (required): launches CAMERA-permission-gated `TakePicture()` writing to `BreakdownPhotoStorage.createAfterPhotoFile(...)` via `fileToShareUri`. Preview at 180dp + `Retake` text button.
- Inline error text + Success `PrimaryButton` *"Submit resolution"*, enabled only when `partsUsed.trim().length >= 4 && afterPhotoPath != null && !isSubmitting`.

### [`TicketDetailViewModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/technician/TicketDetailViewModel.kt)

`@HiltViewModel` keyed off `SavedStateHandle[Screen.TicketDetail.ARG]`.

State:
```kotlin
data class TicketDetailUiState(
    val flag: BreakdownFlag? = null,
    val machine: Machine? = null,
    val currentUser: User? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val showResolveSheet: Boolean = false,
    val partsUsedDraft: String = "",
    val afterPhotoDraft: String? = null
)
```

Events (`SharedFlow<TicketDetailUiEvent>`): `ShowSnackbar(message)`, `Acknowledged`, `Resolved`.

Initialization observes `observeFlagById(ticketId)` `flatMapLatest`-joined with `getMachineById(flag.machineId)` and clears `isLoading`.

Actions:
- `onAcknowledge()` → invokes `AcknowledgeBreakdownUseCase(flag.id, user.id, user.name)`; emits `Acknowledged` on success, `ShowSnackbar(message)` on error.
- `onOpenResolveSheet()` / `onDismissResolveSheet()` — toggles the bottom sheet and clears drafts on open.
- `onPartsUsedChanged(v)` / `onAfterPhotoCaptured(path)` — draft mutators.
- `onResolve()` → invokes `ResolveBreakdownUseCase(flag.id, user.id, user.name, partsUsedDraft, afterPhotoDraft)`; on success emits `Resolved` and closes sheet.

### [`TicketRowUiModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/technician/TicketRowUiModel.kt)

Thin presentation tuple joining a `BreakdownFlag` with its denormalized machine fields:

```kotlin
data class TicketRowUiModel(
    val flag: BreakdownFlag,
    val machineCode: String,
    val machineName: String,
    val criticality: Criticality
)
```

---

## PM Execution Flow

### [`PmTaskCard.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/technician/PmTaskCard.kt)

PM-tab row. Wraps `FactoryCard`; when `wo.escalationTier == 4` the card's `containerColor` is tinted with `errorContainer.copy(alpha = 0.35f)` (a full-card red wash routed through the card's container so it paints on top of the surface rather than behind it).

Layout:
- Header: machine name + `"<code> · <location>"` muted line; right side shows a red square `A` chip when `row.isCritical` and the lifecycle `StatusChip`.
- Body: template name (`bodyMedium`, Medium weight).
- Footer chip row: `DueDateChip(daysToDue)` + `TierChip(tier, daysOverdue)` + `"~<n> min · <tradeType>"` muted helper.

`StatusChip(status)` — pill (RoundedCornerShape(50%), 18%-alpha fill, bold):
- `NOTIFIED` → "NOTIFIED" `statusFlagged`
- `ACKNOWLEDGED` → "ACK" `statusFlagged`
- `IN_PROGRESS` → "IN PROGRESS" `info`
- other → status name, muted

`DueDateChip(daysToDue)`:
- `< 0` → red `Overdue Nd`
- `0` → flagged amber `Due today`
- `1` → flagged amber `Due tomorrow`
- `2–3` → flagged amber `Due in Nd`
- `> 3` → green `Due in Nd`

`TierChip(tier, daysOverdue)` — the escalation pulse logic (T0 collapses to no-op):
| Tier | Fill / Text | Label | Animation |
|------|--------------|--------|-----------|
| 0 | — (early return) | — | none |
| 1 | `statusFlagged` 15% | `Overdue Nd` (amber) | none |
| 2 | `statusFlagged` 20% | `Overdue Nd` (amber) | infinite `tween(800)` alpha pulse 1f ⇄ 0.55f, `RepeatMode.Reverse` |
| 3 | `statusStopped` 15% | `Overdue Nd` (red) | none |
| 4 | `statusStopped` 20% | `CRITICAL Nd` (red) | none (the full-card wash on the parent card provides the emphasis) |

The pulse alpha multiplies both the chip background and its 1dp border for a synchronized fade.

### [`PmTaskRowUiModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/technician/PmTaskRowUiModel.kt)

Presentation tuple plus a few derived properties:

```kotlin
data class PmTaskRowUiModel(
    val wo: PmWorkOrder,
    val machineName: String,
    val machineCode: String,
    val machineLocation: String,
    val criticality: Criticality
) {
    val daysToDue: Int   // negative = overdue, 0 = today, positive = future
    val isOverdue: Boolean
    val isCritical: Boolean   // criticality == Criticality.A
}
```

`daysToDue` is computed by quantizing both `now` and `wo.scheduledDate` to day buckets (`/86_400_000L`) and subtracting.

### [`TechnicianPmDetailScreen.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/technician/TechnicianPmDetailScreen.kt)

PM-task pre-execution screen. Status-driven hero action button on top of a context summary card; collects `TechnicianPmDetailEvent` (`Acknowledged` snackbar, `Started` → `onLaunchExecution(woId)`, `ShowError` snackbar).

Params: `onBack`, `onLaunchExecution: (String) -> Unit`, injected `viewModel`.

Layout (scroll column inside `Scaffold`):
- **Rejection banner** (conditional): shown when `wo.supervisorRejectedAt != null && wo.status == IN_PROGRESS`. Red `errorContainer` `Card` with `Warning` icon, *"Supervisor sent this back <relative>"*, divider, *"Reason"* + `supervisorRejectionNotes`, and a hint *"Tap Continue execution below to address feedback and re-submit."*.
- Header `FactoryCard`: machine name, `"<code> · <location>"`, template name, `"Estimated ~N min · <trade>"`, `"Scheduled <date>"`, optional `"<n> checklist items · ~<n> require photos"` summary.
- Status-driven action button (`when (wo.status)`):
  - **NOTIFIED** → `PrimaryButton` Outlined Primary *"Acknowledge"* with `Warning` icon (commented as the warning-semantic exception, matching the breakdown ack styling intent).
  - **ACKNOWLEDGED** → `PrimaryButton` Success **Hero** size *"Start PM"* with `PlayArrow` icon — the green hero CTA.
  - **IN_PROGRESS** → `PrimaryButton` Primary Primary *"Continue execution"* with `PlayArrow`, directly calling `onLaunchExecution(wo.id)` without going through the VM (the WO is already started).
  - **SUBMITTED** → read-only `ReadOnlySummaryBanner("Submitted for supervisor approval", "Submitted <relative>. You'll get a notification when the supervisor decides.")`.
  - **SUPERVISOR_APPROVED** → read-only banner *"Supervisor approved"* with approver name and *"Awaiting QC verification next (Phase 2C-3)."*.
  - else → generic banner with `status.name` and *"This PM is in a final or non-actionable state."*.

### [`TechnicianPmDetailViewModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/technician/TechnicianPmDetailViewModel.kt)

`@HiltViewModel` keyed off `SavedStateHandle[Screen.TechnicianPmDetail.ARG]`. Injects `PmWorkOrderRepository`, `MachineRepository`.

State:
```kotlin
data class TechnicianPmDetailUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val workOrder: PmWorkOrder? = null,
    val machine: Machine? = null,
    val error: String? = null
)
```

Events (`Channel<…>(BUFFERED).receiveAsFlow()`): `Acknowledged`, `Started`, `ShowError(message)`.

`init` collects `woRepo.observeById(woId)`; resolves the machine via `machineRepo.observeAllMachines().first().firstOrNull { it.id == wo.machineId }`. On null WO, posts `"PM work order not found."`.

Actions:
- `onAcknowledge()` → `woRepo.acknowledge(woId)`; emits `Acknowledged` or `ShowError`.
- `onStart()` → `woRepo.start(woId)`; emits `Started` (which the screen turns into navigation to the executor) or `ShowError`.

### [`TechnicianPmExecuteScreen.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/technician/TechnicianPmExecuteScreen.kt)

Multi-step wizard for PM execution. Driven by `currentIndex` over `wo.taskLogs`; when index ≥ total, the screen flips into a Summary view. Collects `TechnicianPmExecuteEvent`: `Submitted → onSubmitted()`, `DraftSaved → onBack()`, `ShowError` snackbar.

Top-bar title flips between *"Item N of M"* and *"Summary"* depending on `uiState.isOnSummary`.

A thin `LinearProgressIndicator` (4dp) sits below the top bar:
```kotlin
LinearProgressIndicator(progress = { currentIndex / total.coerceAtLeast(1) }, …)
```

**Per-item view** (`ItemExecutionView`):
- `FactoryCard` showing `log.title` (titleMedium bold), description, and (if present) a divider + *"Expected"* label + `expectedResult`.
- `PassFailNaButtons` row — three equal-weight `Button`s, each fixed at **64dp height**. Selected button paints with its semantic color (`statusRunning` green for Pass, `statusStopped` red for Fail, `onSurfaceMuted` grey for N/A) with white content; unselected uses `outlinedButtonColors`.
- `MeasurementInput` (when `log.requiresMeasurement`): numeric `OutlinedTextField`, label `"Measurement (unit)"`. Parses input via `toDoubleOrNull()`; computes `outOfRange = parsed < min || parsed > max`. When out of range, the field shows `isError = true` and its `supportingText` becomes a red helper *"Outside acceptable range — verify before submit. Acceptable <min>–<max> <unit>"* — soft-warning only, the value still submits. When in range, supporting text shows the acceptable range / `"Min N"` / `"Max N"` / `"Numeric value"`.
- `PhotoCaptureBlock` (when `log.requiresPhoto`): CAMERA-permission-gated capture via `PmPhotoStorage.createItemPhotoFile(context, woId, itemId)` and `fileToShareUri`. 180dp `AsyncImage` preview + Outlined `Retake`/`Capture photo` Min-size button.
- Optional `OutlinedTextField` *"Notes (optional)"* (minLines 2).
- Navigation row: `Back` (Outlined, weight 1, hidden when `currentIndex == 0`) + `Next` / `Finish items` (Primary, weight 2, label flips on last item).
- `Save draft & exit` Outlined Min-size button below.

**Summary view** (`SummaryView`, when `currentIndex >= totalItems`):
- `FactoryCard` *Items* — numbered list of all task logs (`1. <title>`), each row clickable to `onJumpToItem(idx)` for review. Right-side `StatusPill` shows PASS (green) / FAIL (red) / N/A (muted) / PENDING (amber).
- `FactoryCard` *Spares used* — header with `+` `IconButton` that calls `onRefreshSuggestions("")` then opens a `ModalBottomSheet` with `AddSpareForm`. Empty-state *"No spares logged yet."*; otherwise each `PmWoSpare` shows name + `"<qty> <unit>"` with a `Delete` `IconButton`.
- `Final notes (optional)` `OutlinedTextField`.
- `Submit for supervisor approval` Success **Hero** `PrimaryButton`.
- `Save draft & exit` Outlined Min button.

`AddSpareForm` (private):
- `Part name` field; on each keystroke calls `onRefreshSuggestions(it)`.
- *"Previously used:"* pill chips (autocomplete) appear when suggestions are non-empty and the typed name doesn't already match the top suggestion; displays up to 5 chips ordered by usage frequency (Q3 — sourced from prior PM spare history). Each chip click sets `name = suggestion`.
- Quantity (numeric, digits + dot filter) + Unit row.
- Optional Notes field.
- Success `Add` button enabled iff `name.isNotBlank() && qty > 0 && unit.isNotBlank()`; Outlined `Cancel` button.

### [`TechnicianPmExecuteViewModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/technician/TechnicianPmExecuteViewModel.kt)

`@HiltViewModel` keyed off `SavedStateHandle[Screen.TechnicianPmExecute.ARG]`. Injects `PmWorkOrderRepository`, `GetCurrentUserUseCase`.

Local draft type for the active item:
```kotlin
data class ItemExecutionDraft(
    val logId: String,
    val status: PmTaskLogStatus = PENDING,
    val measurementText: String = "",
    val photoLocalPath: String? = null,
    val notes: String = ""
)
```

State `TechnicianPmExecuteUiState` exposes derived helpers:
- `totalItems = workOrder?.taskLogs?.size ?: 0`
- `isOnSummary = currentIndex >= totalItems`
- `currentLog = workOrder?.taskLogs?.getOrNull(currentIndex)`

Plus `spareSuggestions: List<String>` for the autocomplete and `finalNotes: String`.

Events: `Submitted`, `DraftSaved`, `ShowError(message)`.

Initialization:
- Captures the current user via `getCurrentUser().first()`.
- Collects `woRepo.observeById(woId)`. On the first load, **auto-resumes** to the first `PENDING` task-log index, or jumps straight to the summary if all items are done. The draft is hydrated from the resolved log (`PmWoTaskLog.toDraft()`).

Per-item mutators (all simply update `draft` inside `uiState`):
- `onStatusSelect(status)`
- `onMeasurementChange(v)` — filters to digits, dot, minus
- `onPhotoCaptured(path)`
- `onNotesChange(v)`
- `onFinalNotesChange(v)`

`onNext()` validates the current item:
- Status must not be `PENDING` → otherwise `"Pick Pass, Fail, or N/A before continuing."`.
- If `log.requiresPhoto` and no photo → `"This item requires a photo."`.
- If `log.requiresMeasurement` and value isn't numeric (and status ≠ `NOT_APPLICABLE`) → `"Enter a numeric measurement (or mark N/A)."`.

On pass, calls `woRepo.logTaskResult(logId, woId, status, measurementValue, photoLocalPath, notes, userId)`, advances `currentIndex` by one, and rebuilds `draft` from the next log (or null when on summary).

`onBack()` decrements `currentIndex` (clamped at 0) and rebuilds the draft.

`onJumpToItem(index)` (used by the summary list) coerces index into `[0, totalItems]` and rebuilds the draft.

`onSaveDraft()` — emits `DraftSaved` only; per-item upserts already happened on each `onNext`, so nothing further to persist.

`onSubmit()` — blocks if any task log is still `PENDING` (emits *"All items must be completed before submit."*); otherwise calls `woRepo.submit(woId, finalNotes.takeIf { it.isNotBlank() })` and emits `Submitted` on success.

Spare management:
- `onAddSpare(name, qty, unit, notes)` → `woRepo.logSpare(...)`. Validates `name.isNotBlank() && qty > 0`.
- `onDeleteSpare(spare)` → `woRepo.deleteSpare(spare.id, woId)`.
- `refreshSpareSuggestions(query)` → `woRepo.suggestSpareNames(query, limit = 10)` populating `spareSuggestions` for the autocomplete chips.
