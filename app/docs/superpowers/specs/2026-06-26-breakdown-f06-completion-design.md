# Breakdown F.06 Completion (QC-approved → fillable draft → save → download) — Design

**Date:** 2026-06-26
**Status:** Approved (pending spec review)
**Builds on:** `2026-06-26-checklist-pdf-download-design.md` (reuses `ChecklistPdfRenderer.renderBreakdown`, `PdfShareLauncher`, `BreakdownDocData`/`BreakdownRow`).

## Summary

Replace the empty Breakdown records placeholder with a list of **all** breakdowns from
`mt_breakdown_records` (synced into Room as `breakdown_flags`). Each row shows its status.
A breakdown that QC has **approved** becomes **editable**: tapping it opens an F.06 form
**pre-filled** with the data the system already has (date, machine, problem, who fixed it),
where the technician fills in the remaining F.06 columns. A **Save** persists the entered
fields **on the device**; after saving, a **Download PDF** button produces the completed
F.06 (reusing the existing renderer). Breakdowns not yet QC-approved are shown but **locked**.

## User-confirmed decisions

| Decision | Choice |
|---|---|
| Which entries appear | **All** breakdowns; only **QC-approved** ones are editable/fillable, the rest are shown locked |
| What "QC-approved" means | `qcStatus == APPROVED` (equivalently `status == CLOSED`) |
| After filling | **Save on device** (local draft, reopenable), **then** a Download PDF button appears |
| Delivery | Android share sheet (reuses existing `PdfShareLauncher`) |
| Backend | No backend changes; uses the existing `GET /breakdowns/open` pull + local Room |

## Background (current state)

- `breakdown_flags` (Room) mirrors `mt_breakdown_records`; pulled via
  `BreakdownRepository.refreshOpenFlags("both")` → `GET /breakdowns/open`.
- `BreakdownFlag` domain model already exposes everything the form needs to pre-fill:
  `id`, `machineId` (asset id), `assetName`, `building`, `description` (problem),
  `raisedAt` (date), `severity`, `status` (`BreakdownStatus`), `qcStatus` (`QcStatus`),
  `acknowledgedByName`, `resolvedByName`, `qcDecidedByName`.
- QC approval (`qcApprove`) sets `status = CLOSED`, `qcStatus = APPROVED` on the device that
  approves. `BreakdownStatus = { OPEN, ACKNOWLEDGED, PENDING_QC, CLOSED, REOPENED }`.
- Room is at **version 8** with destructive migration acceptable (per `AppDatabase` comment).
- The F.06 renderer (`ChecklistPdfRenderer.renderBreakdown(BreakdownDocData)`) already draws
  a multi-column F.06 from a list of `BreakdownRow`; here we pass a single row.

## Known limitation (flagged to user, accepted)

`GET /breakdowns/open` returns only **not-CLOSED** breakdowns, so a CLOSED (QC-approved)
breakdown is present in Room only on the **device where it was approved** (or until a
device's local copy is updated). Reliable cross-device visibility of approved breakdowns
needs a future backend endpoint (e.g. `GET /breakdowns?status=CLOSED` or include recent
CLOSED in the open pull). This design builds the full app side now; the cross-device server
piece is an explicit follow-up. The list simply renders whatever `breakdown_flags` Room
holds.

## Architecture & components

### Data layer

**1. New Room entity `BreakdownF06DraftEntity`** — `data/local/entity/`
Table `breakdown_f06_drafts`, one row per breakdown, holding ONLY the technician-entered
F.06 columns (the date/machine/problem stay sourced from the breakdown record, never copied):
```
@PrimaryKey breakdownId: String
location: String                 // defaults from flag.building, editable
equipmentModel: String           // defaults from flag.machineId, editable
typeOfMaintenance: String
partOfMachine: String
reason: String
durationStart: String            // "HH:mm"
durationEnd: String              // "HH:mm"
machineOperatorSign: String
maintenancePersonSign: String    // default suggestion flag.resolvedByName
qcClearanceSign: String          // default suggestion flag.qcDecidedByName
verifiedBy: String
updatedAt: Long
```
Existence of a row = "saved/completed".

**2. New DAO `BreakdownF06DraftDao`** — `data/local/dao/`
```
@Upsert suspend fun upsert(draft: BreakdownF06DraftEntity)
@Query("SELECT * FROM breakdown_f06_drafts WHERE breakdownId = :id")
fun observeDraft(id: String): Flow<BreakdownF06DraftEntity?>
@Query("SELECT * FROM breakdown_f06_drafts WHERE breakdownId = :id")
suspend fun getDraftOnce(id: String): BreakdownF06DraftEntity?
@Query("SELECT breakdownId FROM breakdown_f06_drafts")
fun observeSavedIds(): Flow<List<String>>
```

**3. `AppDatabase`** — add `BreakdownF06DraftEntity` to `entities`, add
`abstract fun breakdownF06DraftDao(): BreakdownF06DraftDao`, bump **version 8 → 9**, add a
migration comment. (Destructive fallback already configured — verify in `DatabaseModule`;
`breakdown_flags` re-syncs from the backend after a wipe.)

**4. DI** — `DatabaseModule` provides `breakdownF06DraftDao(db)`; `RepositoryModule` binds the
new repository.

**5. `BreakdownFlagDao`** — add:
```
@Query("SELECT * FROM breakdown_flags ORDER BY raisedAt DESC")
fun observeAll(): Flow<List<BreakdownFlagEntity>>
```

**6. `BreakdownRepository`(+Impl)** — add `fun observeAllFlags(): Flow<List<BreakdownFlag>>`
(reuses the existing `toDomain` mapper).

**7. New `BreakdownF06DraftRepository`(+Impl)** + domain model `BreakdownF06Draft`:
`observeDraft(id): Flow<BreakdownF06Draft?>`, `getDraftOnce(id)`, `saveDraft(draft)`,
`observeSavedIds(): Flow<Set<String>>`. Entity↔domain mapper. `updatedAt` is passed in by
the caller (ViewModel) so the repo/JVM stays time-source-free where practical.

### Presentation layer

**8. `BreakdownListViewModel`** (new) — on init: `refreshOpenFlags("both")` (best-effort
pull), then `combine(breakdownRepository.observeAllFlags(), draftRepo.observeSavedIds())` →
list items. Each item:
```
data class BreakdownListItem(
    val flag: BreakdownFlag,
    val isApproved: Boolean,   // flag.qcStatus == APPROVED
    val isCompleted: Boolean,  // flag.id in savedIds
)
```
State: `{ isLoading, items, error }`. Newest first (already ordered by query).

**9. `BreakdownListScreen`** (rewrite the placeholder) — `LazyColumn` of cards:
- Card shows: `assetName` (fallback `machineId`), `building`, raised date, a **status chip**
  (Open / Acknowledged / Pending QC / Approved / Reopened), and a one-line problem.
- **Approved** item → card is tappable (`onOpen(flag.id)`), with a trailing affordance:
  "Completed ✓ — tap to view/download" if `isCompleted`, else "Tap to complete F.06".
- **Not approved** → card rendered **disabled/muted** with an "Awaiting QC approval" label;
  not tappable.
- Keeps the existing **Create** FAB (`onCreate` → blank F.06 form).
- Loading / empty ("No breakdown records yet.") / error states.

**10. Navigation** — new route
`Screen.BreakdownComplete : Screen("checklist/breakdown/complete/{id}") { createRoute(id); ARG="id" }`.
`BreakdownListScreen` gains `onOpen: (String) -> Unit`; `NavGraph` wires
`onOpen = { navController.navigate(Screen.BreakdownComplete.createRoute(it)) }` and adds a
`composable(Screen.BreakdownComplete.route)` hosting `BreakdownCompleteScreen`.

**11. `BreakdownCompleteViewModel`** (new, `SavedStateHandle` for the id) — loads
`combine(observeFlagById(id), draftRepo.observeDraft(id))` into form state on first emit:
read-only header from the flag; editable fields seeded from the draft if present, else
seeded with defaults (`location ← building`, `equipmentModel ← machineId`,
`maintenancePersonSign ← resolvedByName`, `qcClearanceSign ← qcDecidedByName`). Field
setters update in-memory state. `save()` → `draftRepo.saveDraft(...)` with
`updatedAt = System.currentTimeMillis()`, sets `isSaved = true`. `exportPdf()` builds a
one-row `BreakdownDocData` from flag+current fields, calls `renderBreakdown`, emits
`pdfToShare`. Standard `onPdfShared()` / `onExportErrorShown()`.
State: `{ isLoading, header (flag-derived display), fields (editable), isApproved, isSaved,
isExporting, pdfToShare, exportError, error }`. **Guard:** if the flag is not approved
(`qcStatus != APPROVED`), the screen is view-only (no editing/save) — defense in depth even
though the list won't open non-approved items.

**12. `BreakdownCompleteScreen`** (new) — TopAppBar "Complete F.06" + back. Body (scroll):
- Read-only header card: machine, building, date, status, problem, reporter, resolver.
- Editable fields (reusing the same field/time-picker patterns as `BreakdownFormScreen`):
  Location, Equipment Model/Sr No, Type of Maintenance, Part of machine, Reason,
  Duration Start/End (time pickers), Machine operator sign, Maintenance person sign,
  QC clearance sign, Verified By.
- **Save** button (primary). Save persists the current on-screen fields and sets
  `isSaved = true`.
- **Download PDF** button — shown only once the draft has been **saved at least once**
  (`isSaved == true`); a freshly-opened, already-saved draft shows it immediately. Download
  is **WYSIWYG**: it exports the current on-screen field values (not a re-read of the DB), so
  it always matches what the technician sees. (Editing more after a save keeps Download
  visible; the technician taps Save again to persist those edits — Download itself never
  depends on whether the latest keystroke was saved.)
- Share effect + snackbars, mirroring the other screens.

**13. Flag(+draft) → `BreakdownRow` mapping** (in `BreakdownCompleteViewModel`, reusing the
existing `BreakdownRow`/`BreakdownDocData`):
```
date = formatDate(flag.raisedAt)         // yyyy-MM-dd, see util
location = fields.location
machineName = flag.assetName ?: flag.machineId
equipmentModel = fields.equipmentModel
problem = flag.description
typeOfMaintenance = fields.typeOfMaintenance
partOfMachine = fields.partOfMachine
reason = fields.reason
durationStart = fields.durationStart
durationEnd = fields.durationEnd
machineOperatorSign = fields.machineOperatorSign
maintenancePersonSign = fields.maintenancePersonSign
qcClearanceSign = fields.qcClearanceSign
```
`BreakdownDocData(docNo = "CFPLA.C4.F.06", verifiedBy = fields.verifiedBy, rows = [row])`.

**14. Date util** — small helper `epochMillisToDate(ms: Long): String` ("yyyy-MM-dd") using
`java.time` (core-library desugaring is enabled). Reuse an existing formatter if one already
exists in the codebase (check `presentation/technician/TicketCard` / ticket row models).

## Status → label/color mapping (list chips)
`OPEN`→"Open", `ACKNOWLEDGED`→"In progress", `PENDING_QC`→"Pending QC",
`CLOSED`+`qcStatus=APPROVED`→"Approved", `REOPENED`→"Reopened". Reuse `FactoryTheme.colors`
(e.g. `statusRunning` for Approved, `statusPendingQc` for Pending QC, muted for locked).

## Error handling & edge cases
- Approved-but-no-draft → editable, Download hidden until first Save.
- Re-open a completed item → fields pre-filled from saved draft; Download available.
- Non-approved item opened directly (deep link) → view-only guard in the ViewModel.
- PDF render failure → snackbar "Couldn't create PDF" (no crash); share `ActivityNotFound`
  → "No app available to open the PDF" (already handled in `PdfShareLauncher`).
- DB v8→v9 destructive: local `breakdown_flags` wiped then re-synced; no draft data lost
  (new table).

## Testing strategy
JVM-unit-testable (no Android deps):
- `BreakdownFlag` → `BreakdownRow` mapping (incl. assetName fallback to machineId, default
  seeding of maintenance/qc signs).
- `epochMillisToDate` formatting.
- Status → label mapping.
- `BreakdownListItem` derivation (isApproved from qcStatus; isCompleted from savedIds).
Room DAO (`breakdown_f06_drafts` upsert/observe) covered by an instrumented test if the
project runs them, else verified manually. UI/persistence verified on-device.

## Files (anticipated)
**New:** `BreakdownF06DraftEntity.kt`, `BreakdownF06DraftDao.kt`, `BreakdownF06Draft.kt`
(domain), `BreakdownF06DraftRepository.kt` + Impl, `BreakdownListViewModel.kt`,
`BreakdownCompleteViewModel.kt`, `BreakdownCompleteScreen.kt`, a date util (if none exists),
and a small pure mapping file (flag→row, status→label) with unit tests.
**Edit:** `BreakdownFlagDao.kt` (+observeAll), `BreakdownRepository.kt`(+Impl)
(+observeAllFlags), `AppDatabase.kt` (entity+dao+version 9), `DatabaseModule.kt` (+dao
provider), `RepositoryModule.kt` (+repo bind), `Screen.kt` (+BreakdownComplete),
`NavGraph.kt` (+onOpen wiring +composable), `BreakdownListScreen.kt` (rewrite).

## Coordination note
The `breakdown`/`qc` packages are under active concurrent edit (the live QC/technician status
refactor seen during Feature 1). This feature edits `BreakdownFlagDao`, `AppDatabase`, and the
breakdown screens — overlapping that area. Recommend implementing once that refactor has
settled (or coordinating) to avoid merge/compile churn.
