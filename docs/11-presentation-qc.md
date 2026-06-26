# Presentation — QC (Mixed Inbox + Checklist)

The QC layer is the FSSAI/BRC verification gate that sits between technician/supervisor work and an operator getting their machine back. Phase 2C-3 unified the QC inbox into a **mixed feed** of breakdowns (BD) and preventive-maintenance work orders (PM), distinguished by a leading red/blue **type chip** on every row. A single shared **`QcChecklistSheet`** drives the pass/fail evidence flow for both flavours — the only behavioural fork lives in the disapprove path:

- **BD disapprove → REOPENED** (bounces back to the technician).
- **PM disapprove → SUBMITTED** (bounces back to the **supervisor**, not the technician).

A QC user can hold one item at a time via "Acknowledge for QC". If another user is already holding it, only a **QC Head** sees an "Override Acknowledge" affordance.

Source root: [`app/src/main/java/com/candorfoods/factoryops/presentation/qc/`](../app/src/main/java/com/candorfoods/factoryops/presentation/qc/)

---

## Shared Checklist

### `QcChecklistData.kt`
File: [`QcChecklistData.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcChecklistData.kt)

Responsibility: the pure-data model and validation rules for a QC verification, identical for BD and PM.

- **`CheckResult`** — `PASS | FAIL | NA | UNANSWERED`.
- **`QcChecklistSnapshot`** (`@Serializable`) — what gets persisted as JSON: four sections (`VisualSection`, `HygieneSection`, `CalibrationSection`, `FunctionSection`) plus optional `notes`. Toggles (`isFoodContact`, `isCalibrated`) gate whether the corresponding section's items apply.
- **`QcChecklistDraft`** — the mutable UI-side draft. Default sections:
  - **Visual**: `noForeignMatter`, `noOilLeaks`, `guardsInPlace` (all required PASS).
  - **Hygiene** (gated by `isFoodContact`, default `true`): `surfacesSanitized`, `cleaningResidueOk`.
  - **Calibration** (gated by `isCalibrated`, default `false`): `calibrationVerified`.
  - **Function**: `testRunCompleted` (required PASS), `outputSampleChecked` (PASS or N/A allowed).
  - Plus `notes` (≤500 chars) and `afterPhotoLocalPath`.
- **`canApprove`** — true iff after-photo present, all applicable items PASS, and `outputSampleChecked` is PASS or NA. N/A is intentionally allowed *only* on `outputSampleChecked`.
- **`canDisapprove`** — true iff after-photo present and at least one item is answered (so we never persist an empty rejection).
- **`toSnapshot()`** — converts the draft into the serializable snapshot, dropping blank notes.

### `QcChecklistSheet.kt`
File: [`QcChecklistSheet.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcChecklistSheet.kt)

Responsibility: the `ModalBottomSheet` UI the QC user fills in. Shared between BD and PM detail screens.

- Header `"$machineName · $machineCode"`, then four `ChecklistSection` cards. Toggle rows (`isFoodContact`, `isCalibrated`) collapse their PASS/FAIL rows into "Section marked N/A — won't count toward decision." when off.
- **`PassFailRow`** renders selectable `ResultChip`s coloured from `FactoryTheme.colors`:
  - **Pass** → `statusRunning` (green)
  - **Fail** → `statusStopped` (red)
  - **N/A** → `statusFlagged` (amber), only shown when `allowNa = true`
  Selection paints background at `alpha = 0.18f`, a 2dp border, and bold label.
- **After-QC photo card** (required): launches `ActivityResultContracts.RequestPermission` for `CAMERA`, then `TakePicture` against a URI from `BreakdownPhotoStorage.createQcPhotoFile` / `fileToShareUri`. Re-take supported via a `TextButton`.
- **Notes** — `OutlinedTextField`, hard cap at 500 chars with live counter.
- **Approve** button — `PrimaryButtonStyle.Success`, enabled only when `draft.canApprove`.
- **Disapprove** button — intentionally one-off **amber** (`#F59E0B` on black text) per the in-code comment ("Warning semantic"). Opens an `AlertDialog` with a reason field; the **Submit disapproval** confirm is enabled only when `disapproveReason.trim().length in 4..500` and is coloured `FactoryTheme.colors.statusStopped`.
- Footer hint when neither action is available: *"Capture the QC photo and answer at least one item."*

---

## Inbox

### `QcInboxRow.kt`
File: [`QcInboxRow.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcInboxRow.kt)

Responsibility: the polymorphic row type the inbox renders.

- Sealed: `Breakdown(flag: BreakdownFlag, ...)` | `Pm(wo: PmWorkOrder, ...)`.
- Common fields: `id`, `machineCode`, `machineName`, `machineLocation`, `criticality`, `sortMillis`.
- `id` is the underlying flag id or work-order id (URL-safe → usable directly as a nav-arg).
- **`sortMillis`** Elvis chain picks the most-recent "needs attention" timestamp:
  - BD: `qcDecidedAt ?: qcAcknowledgedAt ?: resolvedAt ?: raisedAt`
  - PM: `qcDecidedAt ?: qcAcknowledgedAt ?: submittedAt ?: generatedAt`
  Higher = more recent; lists are `sortedByDescending { it.sortMillis }`.

### `QcTicketRowUiModel.kt`
File: [`QcTicketRowUiModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcTicketRowUiModel.kt)

Responsibility: legacy/auxiliary row model for breakdown-only contexts. Wraps a `BreakdownFlag` with the same machine metadata fields (`machineCode`, `machineName`, `machineLocation`, `criticality`) used by `QcInboxRow.Breakdown`.

### `QcHomeScreen.kt`
File: [`QcHomeScreen.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcHomeScreen.kt)

Responsibility: the QC user's landing dashboard — 3 tabs, mixed BD+PM feed, click-to-detail dispatch.

- **TopAppBar**: title "FactoryOps — QC Verification" with a sub-line `"{name} — {QC Head|QC Member|role} — {plantId}"`. Settings icon in actions.
- **3 tabs** (`rememberSaveable`): `Awaiting QC`, `My Active`, `Done today`. Each tab shows a count badge — `primary` colour by default, switching to `error` when count > 5.
- **`PullToRefreshBox`** triggers `viewModel.onRefresh()` which re-syncs machines.
- Body is a `LazyColumn` of **`QcInboxCard`**s keyed by `row.id`:
  - **`TypeChip`** is the load-bearing BD/PM identifier:
    - **BD** → `FactoryTheme.colors.statusStopped` (red) at 0.15f bg.
    - **PM** → `MaterialTheme.colorScheme.primary` (blue) at 0.15f bg.
  - Machine name + `"$code · $location"`, `CriticalityBadge` (A red / B amber / C blue), and only on BD a `SeverityChip` (CRITICAL/MAJOR/MINOR).
  - Body line differs by type: BD shows `flag.description` (2 lines, ellipsis); PM shows `wo.templateName` semibold.
  - **`StatusLine`** is tab-aware and type-aware:
    - Tab 0 BD: `"Sent to QC by {resolvedByName} {relative}"` in `statusFlagged`.
    - Tab 0 PM: `"Sent to QC by {supervisorApprovedByName} {relative}"` in `statusFlagged`.
    - Tab 1 (both): `"Picked up {relative}"` in `primary`.
    - Tab 2 BD: `"Approved"` / `"Disapproved"` (green / error).
    - Tab 2 PM: `"Approved"` / `"Sent back to supervisor"` (green / error).
- **Click dispatch**: `onRowClick(row)` is supplied by navigation; the caller pattern-matches `QcInboxRow.Breakdown` → `QcTicketDetailScreen` route, `QcInboxRow.Pm` → `QcPmDetailScreen` route.

### `QcHomeViewModel.kt`
File: [`QcHomeViewModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcHomeViewModel.kt)

Responsibility: builds the 3 tab lists by merging BD and PM streams per tab, scoped to the current user.

- State: `QcHomeUiState(currentUser, isLoading, isRefreshing, awaiting, myActive, doneToday)`.
- **Single `observerJob`** restart pattern: on every emission of `getCurrentUser().filterNotNull()` the previous `observerJob` is cancelled and `startObserving(user)` re-launches three child coroutines. This guarantees no leaked subscriptions if the logged-in user changes.
- Three `combine` blocks, each merging `(breakdownFlow, pmFlow, machinesMap)`:
  1. **Awaiting QC** — `breakdownRepository.observePendingQcByPlant(user.plantId)` + `pmRepository.observePendingQc()`. PM stream is **plant-scoped on the client**: `machineMap[w.machineId]?.takeIf { it.plantId == user.plantId }`. BD stream is already plant-scoped at the repo. Only this tab applies plant scoping.
  2. **My Active** — `observeQcAcknowledgedByUser(user.id)` for both BD and PM. No plant filter (once you've picked it up, it's yours wherever it lives).
  3. **Done today** — `observeQcDoneByUserSince(user.id, todayStart)` for both, where `todayStart = DateTimeFormatter.startOfDayMillis(now)`.
- Rows from each combine are merged into a single `buildList`, joined with the machine map via `rowFor(flag, machine)` / `rowFor(wo, machine)` (drops rows whose machine isn't in the local cache), then `sortedByDescending { it.sortMillis }`.
- `onRefresh()` toggles `isRefreshing` while `machineRepository.syncMachines()` runs.

---

## Breakdown Detail

### `QcTicketDetailScreen.kt`
File: [`QcTicketDetailScreen.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcTicketDetailScreen.kt)

Responsibility: the BD-side QC verification page. Layout (top → bottom):

1. **`MachineCard`** — name, `code · location`, `CritBadge` (A/B/C), `SevChip` (CRITICAL/MAJOR/MINOR).
2. **`OperatorReportCard`** — reporter, raised-at relative + absolute, description, optional before-photo via `AsyncImage(File(beforePhotoLocalPath))`.
3. **`TechnicianRepairCard`** — `resolvedByName`, resolved-at relative + absolute, parts-used text, optional after-repair photo.
4. **`QcActionSection`** — status-driven block (see below).
5. **`QcChecklistSheet`** — shown when `uiState.showChecklistSheet` is true.

`QcActionSection` switches on `flag.status`:
- **`PENDING_QC`** with `qcAcknowledgedBy == null` → full-width amber **"Acknowledge for QC"** button (one-off `AmberBorder` on black, `TouchTarget.primary` height, Build icon).
- **`PENDING_QC` + `isHeldByMe`** → amber "Picked up by you" card + **Hero "Run QC checklist"** (`PrimaryButtonStyle.Primary`, CheckCircle).
- **`PENDING_QC` + `isHeldByOther`** → amber "Picked up by {name}" card + (only if `isQcHead`) outlined **"Override — take this over as QC Head"** with `SwapHoriz` icon.
- **`QC_APPROVED`** → green summary card (`statusRunning` @ 0.18f bg): "QC approved", by-name, notes, after-QC photo.
- **`REOPENED`** → `errorContainer` card: **"Disapproved — sent back to technician"**, by-name, reason, after-QC photo.
- Anything else → muted "This ticket is not awaiting QC."

Snackbar / nav side-effects on `viewModel.uiEvent`: `Acknowledged` → toast, `Overridden` → toast, `Approved` → toast + `onBack()`, `Disapproved` → "Sent back to technician." + `onBack()`.

### `QcTicketDetailViewModel.kt`
File: [`QcTicketDetailViewModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcTicketDetailViewModel.kt)

Responsibility: actions and state for a single breakdown under QC.

- State: `QcTicketDetailUiState(flag, machine, currentUser, isLoading, isSubmitting, error, showChecklistSheet, checklist)` with derived `isHeldByMe`, `isHeldByOther`, `isQcHead`.
- Boot: collects `getCurrentUser()`; in parallel `breakdownRepository.observeFlagById(ticketId).flatMapLatest { flag -> machineRepository.getMachineById(flag.machineId) }` to emit the (flag, machine) pair.
- **Actions**:
  - `onAcknowledgeForQc()` → `breakdownRepository.qcAcknowledge(flag.id, user.id, user.name)`. Emits `Acknowledged` or snackbar error.
  - `onOverrideAcknowledgeAsHead()` → role-gated to `UserRole.QC_HEAD`, calls `qcOverrideAcknowledge(...)`.
  - `onOpenChecklistSheet()` resets a fresh `QcChecklistDraft()`.
  - `onApprove()` — guards on `draft.canApprove`, JSON-encodes the snapshot, calls `breakdownRepository.qcApprove(flagId, userId, userName, checklistJson, afterPhotoLocalPath, notes)`, emits `Approved` on success.
  - `onDisapprove(reason)` — guards on `draft.canDisapprove` and `trimmed.length in 4..500`, calls `breakdownRepository.qcDisapprove(...)`, emits `Disapproved`. Per the channel contract this transitions the flag to `REOPENED` so the **technician** picks it up again.

---

## PM Detail

### `QcPmDetailScreen.kt`
File: [`QcPmDetailScreen.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcPmDetailScreen.kt)

Responsibility: PM-side mirror of the BD detail, with PM-specific evidence rendering.

Layout:
1. **`HeaderCard`** — leading **blue "PM" chip** (`primary` @ 0.15f bg) + machine name, `code · location`, `templateName`.
2. **`SubmissionCard`** — "Performed by {assignedTechnicianName} · submitted {relative}", and "Approved by {supervisorApprovedByName}" (the supervisor sign-off that originally moved the WO into `PENDING_QC`).
3. Section title **"Checklist results"** then `wo.taskLogs.forEachIndexed { idx, log -> ItemRowCard(idx + 1, log) }`. `ItemRowCard` renders:
   - `"$idx. ${log.title}"` + a coloured `StatusPill` (PASS green / FAIL red / N/A muted / PENDING amber).
   - When `log.requiresMeasurement` is true: `"Measured: {value} {unit} (acceptable min–max unit)"`, coloured `statusRunning` when within bounds, `statusStopped` when out-of-range or missing.
   - When `log.requiresPhoto` and `photoLocalPath != null`: 180dp `AsyncImage`.
   - Optional `"Notes: ${log.notes}"`.
4. `SparesCard` (if `wo.spares.isNotEmpty()`) — `"• {spareName} — {quantityUsed} {unit}"` rows.
5. `FinalNotesCard` (if `wo.finalNotes` non-blank).
6. **`QcActionSection`** — switches on `wo.status`:
   - **`PENDING_QC` + `qcAcknowledgedBy == null`** → full-width **amber 64dp "Acknowledge for QC"** (`TouchTarget.primary` = 64dp, AmberBorder on black, Build icon).
   - **`PENDING_QC` + `isHeldByMe`** → amber "Picked up by you" card + **Hero green "Run QC checklist"** (`PrimaryButtonStyle.Primary`, CheckCircle).
   - **`PENDING_QC` + `isHeldByOther`** → amber "Picked up by {name}" card + (only if `isQcHead`) outlined **"Override — take this over as QC Head"** (`PrimaryButtonStyle.Outlined`, SwapHoriz).
   - **`QC_APPROVED`** → green summary card (`statusRunning` @ 0.18f bg): "QC approved" + decided-by + notes + optional `qcAfterPhotoLocalPath`.
   - **`SUBMITTED`** (back-from-QC) → **red `errorContainer` card** "Sent back to supervisor" + decided-by + reason. This is the PM-specific bounce target — not the technician.
   - Else → "This PM is not awaiting QC."
7. `QcChecklistSheet` when `uiState.showChecklistSheet`.

Snackbar / nav events (`QcPmDetailUiEvent`): `Acknowledged` → "Picked up for QC."; `Overridden` → "QC override applied."; `Approved` → "QC approved — machine returned to operator." + `onBack()`; `Disapproved` → **"Sent back to supervisor."** + `onBack()`.

### `QcPmDetailViewModel.kt`
File: [`QcPmDetailViewModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcPmDetailViewModel.kt)

Responsibility: actions and state for a single PM work order under QC. Structurally a mirror of `QcTicketDetailViewModel`.

- State: `QcPmDetailUiState(workOrder, machine, currentUser, isLoading, isSubmitting, error, showChecklistSheet, checklist)` with derived `isHeldByMe`, `isHeldByOther`, `isQcHead`.
- Boot: `pmRepo.observeById(woId).flatMapLatest { wo -> machineRepository.getMachineById(wo.machineId) }`.
- **Actions** (all guard on `state.currentUser` and `state.workOrder`):
  - `onAcknowledgeForQc()` → `pmRepo.qcAcknowledge(wo.id, user.id, user.name)`.
  - `onOverrideAcknowledgeAsHead()` → role-gated `UserRole.QC_HEAD`, calls `pmRepo.qcOverrideAcknowledge(...)`.
  - `onOpenChecklistSheet()` resets a fresh draft.
  - `onApprove()` — guards `draft.canApprove`, JSON-encodes snapshot, calls `pmRepo.qcApprove(woId, userId, userName, checklistJson, afterPhotoLocalPath, notes)`.
  - `onDisapprove(reason)` — guards `draft.canDisapprove` and `trimmed.length in 4..500`, calls `pmRepo.qcDisapprove(...)`. Per the channel contract this transitions the WO to `SUBMITTED` so the **supervisor** picks it up again (not the technician).
