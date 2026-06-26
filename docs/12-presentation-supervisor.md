# Presentation — Supervisor (Templates, Plans, Approvals)

The supervisor role owns the configuration and approval surface of the PM workflow. The supervisor home is a single screen with a `ScrollableTabRow` holding four tabs — **Templates**, **Plans**, **Active WOs**, **Approvals** (Phase 2C-2) — and a context-sensitive FAB that creates whatever the visible tab is showing. Three secondary screens drill in from home:

- **Template Editor** — author/edit a PM checklist (name, machine type, trade, duration, ordered items with photo/measurement requirements and bounds).
- **Plan Editor** — bind a template to a machine and schedule (trigger type, interval, technician).
- **PM Review** — open a submitted work order, walk the checklist results, then **Approve** (status flips to `PENDING_QC`, machine flips to `PENDING_QC`, Phase 2C-3) or **Reject — needs rework** (status reverts to `IN_PROGRESS` with a mandatory reason). When a PM is bounced back by QC (`wo.qcDecidedAt != null && wo.status == SUBMITTED`), a red banner appears at the top and the Approve button relabels to **"Re-approve and send to QC"**.

---

## Home

### `SupervisorHomeScreen`

[`app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/SupervisorHomeScreen.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/SupervisorHomeScreen.kt)

**Responsibility:** Single-screen supervisor shell with four tabs. The top app bar shows `FactoryOps — Supervisor` plus the current user's name and `plantId` as a subtitle, and a Settings icon action. Tab selection is held in `rememberSaveable` `selectedTab` so rotation/process death keep the tab visible.

Tabs (`ScrollableTabRow`, `edgePadding = 0.dp`) — each tab label appends a count badge in parens when non-zero (`"$label ($count)"`):

| Index | Label | Content | Empty state |
|---|---|---|---|
| 0 | Templates | `TemplatesList` of `PmTemplate` cards — name, `${machineType} · ${estimatedDurationMinutes} min · ${defaultTradeType}`, description (max 2 lines). Tap → `onEditTemplate(id)`. | "No templates yet. Tap + to create one." |
| 1 | Plans | `PlansList` of `SupervisorPlanRow` cards — machine name + code, template name, interval label (`"Every N days"` vs `"Every N running hours"` based on `triggerType`), and `next due <date>`. Tap → `onEditPlan(id)`. | "No plans yet. Tap + to assign a template to a machine." |
| 2 | Active WOs | `WorkOrdersList` of `SupervisorWoRow` cards — machine + template, `WoStatusChip` on the right, scheduled date and technician. Read-only. | "No active work orders. Scheduler will generate them as plans come due." |
| 3 | Approvals | `PendingApprovalsList` of submitted-WO cards — machine, template, "Submitted by <tech> <relative-time> · N items". Tap → `onReviewPm(id)`. | "Nothing waiting for approval. Submitted PM work orders show up here." |

**FAB behavior** (`floatingActionButton`):

- Tab 0 → `ExtendedFloatingActionButton` "New template" → `onCreateTemplate()`.
- Tab 1 → `ExtendedFloatingActionButton` "New plan" → `onCreatePlan()`.
- Tabs 2, 3 → no FAB.

**WoStatusChip palette** (private composable): maps every `PmWoStatus` to a label + theme color, e.g. `SUBMITTED → "AWAITING SUPERVISOR" (info)`, `PENDING_QC → "PENDING QC" (statusFlagged)`, `OVERDUE → "OVERDUE" (statusStopped)`, `CLOSED/QC_APPROVED → statusRunning`.

**Key params:** `onCreateTemplate`, `onEditTemplate(String)`, `onCreatePlan`, `onEditPlan(String)`, `onReviewPm(String)`, `onSettingsClick`, `viewModel: SupervisorHomeViewModel = hiltViewModel()`.

### `SupervisorHomeViewModel`

[`app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/SupervisorHomeViewModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/SupervisorHomeViewModel.kt)

**Responsibility:** Feeds the four tabs from independent repository Flows, joining domain models into display rows in the ViewModel so the UI stays declarative.

**State (`SupervisorHomeUiState`):**

- `currentUser: User?` — populated from `GetCurrentUserUseCase().filterNotNull()`; supplies the header subtitle and is the source of `plantId` for scoping.
- `isLoading: Boolean` — flipped to `false` after the first template emission.
- `templates: List<PmTemplate>` — `templateRepo.observeActiveTemplates()`.
- `plans: List<SupervisorPlanRow>` — `combine(planRepo.observeAllPlans(), machineRepo.observeAllMachines(), templateRepo.observeActiveTemplates())` joins each `PmPlan` to its `Machine` and `PmTemplate`, dropping plans whose machine no longer exists. Template-missing case is rendered as `"(template missing)"`.
- `workOrders: List<SupervisorWoRow>` — `combine(woRepo.observeAll(), machineRepo.observeAllMachines())` sorted by `scheduledDate`.
- `pendingApprovals: List<SupervisorWoRow>` (Phase 2C-2) — `combine(woRepo.observeByStatus(PmWoStatus.SUBMITTED), machineRepo.observeAllMachines())`, sorted by `submittedAt` (nulls last via `Long.MAX_VALUE`).

**Plant scoping:** the underlying `observeAll*` repository Flows return scoped data; `currentUser.plantId` is exposed in the header for visibility but the filtering itself lives in the data layer.

**Row records:**

- `SupervisorPlanRow(plan, machineName, machineCode, templateName, templateMachineType)`.
- `SupervisorWoRow(wo, machineName, machineCode)`.

---

## Template Editor

### `TemplateEditorScreen`

[`app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/TemplateEditorScreen.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/TemplateEditorScreen.kt)

**Responsibility:** Create or edit a `PmTemplate` plus its ordered `PmTemplateItem` list. Title flips between "New template" and "Edit template" based on `uiState.isEditMode`. Loading shows a centered spinner; events drive snackbars and navigation.

**Top form card — "Template details":**

- `Name` — `OutlinedTextField`, single line. Required.
- `Machine type` — `EnumDropdown<MachineType>` (`MachineType.values()`).
- `Description` — multi-line text field (`minLines = 2`).
- `Estimated duration (minutes)` — numeric `OutlinedTextField` (digits-only filter in the VM).
- `Default trade type` — `EnumDropdown<TradeType>`.

**Items section** ("Checklist items" header with a count on the right):

Each item is rendered by the private `ItemEditorCard(index, total, item, onChange, onMoveUp, onMoveDown, onRemove)` and contains:

- Header row with `Item N` plus three `IconButton`s:
  - **Up arrow** (`ArrowUpward`) — `enabled = index > 0`.
  - **Down arrow** (`ArrowDownward`) — `enabled = index < total - 1`.
  - **Delete** (`Delete`) — removes; the VM always keeps at least one item slot.
- `Title` (required), `Instructions`, `Expected result` text fields.
- A divider, then two `ToggleRow` switches:
  - **Photo required** → `requiresPhoto`.
  - **Measurement required** → `requiresMeasurement`.
- **Only when `requiresMeasurement == true`**: a `Unit` field (e.g. "mm", "°C", "bar") and a side-by-side `Min` / `Max` numeric pair. These collapse out of the layout when the measurement toggle is off.

Below the list, an outlined "Add item" button (with `Add` icon) appends a fresh `ItemDraft`.

**Save:** `PrimaryButton` styled `Success`, size `Primary`, label `"Create template"` / `"Save changes"`. `enabled = uiState.canSave && !uiState.isSaving`; `loading = uiState.isSaving`. `canSave` requires non-blank name plus every item having a non-blank title (the only structural validation surfaced in the UI). Errors from the repository surface as inline red text and a snackbar.

**Key params:** `onBack: () -> Unit`, `onSaved: () -> Unit`, `viewModel: TemplateEditorViewModel = hiltViewModel()`.

### `TemplateEditorViewModel`

[`app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/TemplateEditorViewModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/TemplateEditorViewModel.kt)

**Responsibility:** Holds the editable draft, supports edit-mode hydration, exposes reorder/add/remove ops, and persists via `PmTemplateRepository.saveTemplate(template, items)`.

**State (`TemplateEditorUiState`):** `name`, `machineType` (default `OTHER`), `description`, `estimatedDurationMinutes` (default `"60"`, digits-only), `defaultTradeType` (default `MECHANICAL`), `items: List<ItemDraft>` (starts with a single blank draft), plus `isLoading`/`isSaving`/`isEditMode`/`templateId`/`error`.

`ItemDraft` carries `id`, `title`, `description`, `expectedResult`, `requiresPhoto`, `requiresMeasurement`, `measurementUnit`, `measurementMin`, `measurementMax` (last three as strings so the editor never has to coerce empty input).

**Hydration:** reads `Screen.SupervisorTemplateEditor.ARG` from `SavedStateHandle`. If non-blank, calls `templateRepo.getTemplateById(id)` and maps each `PmTemplateItem` back into an `ItemDraft`; if the template's items list is empty, seeds one blank draft.

**Actions:** `onNameChange / onMachineTypeChange / onDescriptionChange / onDurationChange / onTradeTypeChange`, `onItemChange(index, transform)` to mutate a single item via a copy lambda, `onAddItem`, `onRemoveItem(index)` (re-seeds a blank when emptied), `onMoveUp(index)` / `onMoveDown(index)` for arrow-button reorder.

**Save:** validates `canSave`, mints `templateId = "tmpl-${UUID}"` for new templates, maps drafts to `PmTemplateItem` with `orderIndex = idx` (so list order is persisted authoritatively), assigns IDs (`"item-${UUID}"` for new, preserves existing), parses `measurementMin/Max` via `toDoubleOrNull()`, then writes through the repo. Emits `TemplateEditorEvent.Saved` on success and `ShowError(message)` on failure.

---

## Plan Editor

### `PlanEditorScreen`

[`app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/PlanEditorScreen.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/PlanEditorScreen.kt)

**Responsibility:** Bind one `PmTemplate` to one `Machine` and define when/how often it triggers. Title flips between "New PM plan" and "Edit plan" based on `uiState.isEditMode`.

**Form card — "Plan setup":**

- **Machine dropdown** (`MachineDropdown`, `ExposedDropdownMenuBox`) — displays `"${name} (${code} · ${machineType})"` when selected, `"Select a machine"` when not. Menu items show `"${name} — ${machineType}"`.
- **Template dropdown** (`TemplateDropdown`) — `enabled` only when a machine is picked; the option list is `uiState.templatesForSelectedMachine`, computed in the VM as `allTemplates.filter { it.machineType == machine.machineType }` (so the type filter is implicit). When the picked machine has no matching templates an error-colored hint reads: "No templates exist for this machine type yet. Create one first." Placeholder is `"Pick a machine first"` until a machine is chosen.
- **Trigger type** (`EnumDropdownInline<PmTriggerType>`) — labels rendered as `"TIME (calendar days)"` or `"USAGE (running hours)"`.
- **Interval** — numeric `OutlinedTextField`; label switches between `"Interval (days)"` and `"Interval (running hours)"` based on `triggerType`.
- **Assigned technician (user id)** — text field; supporting text reads `"Default: user-002 (Priya). Phase 3 backend will provide a dropdown."`.

**Save:** `PrimaryButton` styled `Success` / size `Primary`, label `"Create plan"` / `"Save changes"`. `enabled = uiState.canSave && !uiState.isSaving`. `canSave` requires both a selected machine and template plus `triggerInterval > 0`.

**Key params:** `onBack`, `onSaved`, `viewModel: PlanEditorViewModel = hiltViewModel()`.

### `PlanEditorViewModel`

[`app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/PlanEditorViewModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/PlanEditorViewModel.kt)

**Responsibility:** Loads machines and active templates once on init, hydrates an existing plan when launched in edit mode, and writes back through `PmPlanRepository.savePlan`.

**State (`PlanEditorUiState`):** `machines`, `allTemplates`, `selectedMachineId`, `selectedTemplateId`, `triggerType` (default `TIME`), `triggerInterval` (default `"30"`, digits-only), `assignedTechnicianId` (default `"user-002"`), plus `isLoading`/`isSaving`/`isEditMode`/`planId`/`error`.

Derived:
- `templatesForSelectedMachine` — type-filtered list driving the template dropdown.
- `canSave` — machine + template selected and integer interval `> 0`.

**Hydration:** in `init`, fetches `machineRepo.observeAllMachines().first()` and `templateRepo.observeActiveTemplates().first()`, then reads `Screen.SupervisorPlanEditor.ARG`. If a plan id is present, `planRepo.getPlanById(planId)` fills in `selectedMachineId/templateId/triggerType/triggerInterval/assignedTechnicianId`.

**Actions:**
- `onMachineSelected(id)` — sets the machine and **resets** `selectedTemplateId` to `null` if the previously-selected template's `machineType` no longer matches.
- `onTemplateSelected(id)`, `onTriggerTypeChange(t)`, `onIntervalChange(v)` (digit filter), `onTechnicianIdChange(v)`.

**Save (`onSave`):** mints `planId = "plan-${UUID}"` for new plans, parses `interval` (default `30`), and computes `nextDueAt = now + interval * 86_400_000L` (i.e. interval × ms-per-day — regardless of trigger type, the next-due timestamp is laid out in calendar days as the initial cadence). Builds a `PmPlan` with `isActive = true`, calls `planRepo.savePlan(plan)`, and emits `PlanEditorEvent.Saved` or `ShowError`.

---

## PM Review (Approvals)

### `SupervisorPmReviewScreen`

[`app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/SupervisorPmReviewScreen.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/SupervisorPmReviewScreen.kt)

**Responsibility:** Read-only render of a submitted PM work order with two terminal actions — Approve or Reject. Layout is a vertically scrolling column inside a `Scaffold` with a back-arrow top bar titled "Review PM" and a `SnackbarHost` for errors.

**QC bounceback banner (Phase 2C-3):** When `wo.qcDecidedAt != null && wo.status == PmWoStatus.SUBMITTED` (i.e. QC sent the PM back to the supervisor), a `Card` rendered in `MaterialTheme.colorScheme.errorContainer` appears at the top with:

- Bold title **"QC sent this back"**.
- `"by ${qcDecidedByName ?: "QC"} · <relative time>"`.
- **Reason** sub-header and the QC decision notes when present.
- Optional `AsyncImage` of `qcAfterPhotoLocalPath` (180 dp tall).
- Helper line: "Approve to re-send to QC, or reject to send to technician for rework."

**Header card** (always visible): machine name (bold), `"${code} · ${location}"` muted, divider, template name (semi-bold), `"Submitted by ${assignedTechnicianName} <relative time>"`.

**Checklist results:** `Text("Checklist results", titleSmall, bold)` followed by one `ItemReviewCard(idx + 1, log)` per `PmWoTaskLog`. Each card shows:

- Numbered title row with a status pill on the right — `StatusPill(PmTaskLogStatus)`:
  - `PASS` → green (`statusRunning`), `FAIL` → red (`statusStopped`), `NOT_APPLICABLE` → muted "N/A", `PENDING` → amber "PENDING" (`statusFlagged`). Pill is a rounded `Box` with 18% color fill.
- If `requiresMeasurement`: a single-line readout `"Measured: <value><unit> (acceptable <min>–<max><unit>)"`. The line is **green when the parsed value falls within `[min, max]` (or bounds are missing on that side), red otherwise** (`parsedOk` check against `measurementMin`/`measurementMax`).
- If `requiresPhoto && photoLocalPath != null`: a full-width 180 dp `AsyncImage` of the technician's photo on a `surfaceVariant` background.
- Optional `"Notes: ..."` below.

**Spares used card:** shown only when `wo.spares` is non-empty — bulleted list `"• ${spareName} — ${quantityUsed} ${unit}"` with optional indented notes.

**Final tech notes card:** shown only when `wo.finalNotes` is non-blank.

**Action buttons:**

- **Approve** — `PrimaryButton` styled `PrimaryButtonStyle.Success` at `PrimaryButtonSize.Hero` (green, full width, hero height). Label is `"Approve"` normally and **`"Re-approve and send to QC"`** when the QC-bounceback condition holds. Calls `viewModel::onApprove`.
- **Reject — needs rework** — raw Material3 `Button` (not `PrimaryButton`; documented as the same exception as the breakdown ack), `containerColor = AmberBorder (0xFFF59E0B)`, black content, height = `TouchTarget.primary` (64 dp), full width. Tapping opens a `ModalBottomSheet`.

**Reject bottom sheet:** `Text("Reject — reason", titleLarge, bold)`, an `OutlinedTextField` (`minLines = 4`) whose label shows `"Why is this being sent back? (${len}/500)"`, capped at 500 chars in the `onValueChange`. Helper text: "Required: 4–500 characters." Two stacked buttons:

- `Submit rejection` — `PrimaryButton` `Danger`/`Primary`, `enabled = rejectReason.trim().length in 4..500 && !isSubmitting`. Closes sheet and calls `viewModel.onReject(rejectReason)`.
- `Cancel` — `PrimaryButton` `Outlined`/`Min`, dismisses the sheet.

**Key params:** `onBack: () -> Unit`, `onDecided: () -> Unit`, `viewModel: SupervisorPmReviewViewModel = hiltViewModel()`. Both `Approved` and `Rejected` events route through `onDecided()` (typically a pop back to the home Approvals tab).

### `SupervisorPmReviewViewModel`

[`app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/SupervisorPmReviewViewModel.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/SupervisorPmReviewViewModel.kt)

**Responsibility:** Loads a single work order plus its machine, captures the acting supervisor's identity, and posts approve/reject decisions through the work-order repository.

**State (`SupervisorPmReviewUiState`):** `workOrder: PmWorkOrder?`, `machine: Machine?`, `currentUser: User?`, `isLoading`, `isSubmitting`, `error`.

**Hydration:** `woId` is required and read from `Screen.SupervisorPmReview.ARG` via `SavedStateHandle` (`checkNotNull`). Two coroutines start in `init`:

1. `getCurrentUser().first()` → `currentUser`.
2. `woRepo.observeById(woId).collect { wo -> ... }` — on each emission resolves the matching machine via `machineRepo.observeAllMachines().first()`. `null` work order surfaces as `error = "Work order not found."`.

**Actions:**

- `onApprove()` — calls `woRepo.supervisorApprove(woId, user.id, user.name)`. On `Result.Success` emits `SupervisorPmReviewEvent.Approved`; on `Result.Error` emits `ShowError`. The repository is responsible for the Phase 2C-3 side effects (WO status → `PENDING_QC`, machine status → `PENDING_QC`).
- `onReject(notes)` — trims and re-validates `length in 4..500` (defense-in-depth against the button-disabled path), then calls `woRepo.supervisorReject(woId, user.id, user.name, trimmed)`. On success emits `Rejected`; on failure `ShowError`. The repository reverts WO status to `IN_PROGRESS` and persists the rejection notes.

**Events (`SupervisorPmReviewEvent`):** `Approved`, `Rejected`, `ShowError(message)`.
