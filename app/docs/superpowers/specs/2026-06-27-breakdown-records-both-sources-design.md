# Breakdown Records — Both Sources + Per-Record Download — Design

**Date:** 2026-06-27
**Status:** Approved (design + API contract approved by user)
**Builds on:** `2026-06-26-breakdown-f06-completion-design.md` (keeps the QC-approved flags list)
and reuses `ChecklistPdfRenderer.renderBreakdown`, `PdfShareLauncher`, `BreakdownRow`/`BreakdownDocData`.

## Problem (root cause found)

The Breakdown records page was empty after submitting an F.06 via **Create**. Root cause: a
**data-source mismatch** — two separate breakdown stores written by two different actions:
- **Create → submit** writes the F.06 sheet via `POST /preventive-maintenance/breakdowns` (the
  checklist/doc table). It is **submit-only** — no GET to read it back.
- The records page (Feature 2) reads `mt_breakdown_records` (operator QC flags) via
  `GET /breakdowns/open`.

So submitted F.06 sheets could never appear. User decisions: show **both** sources, and read the
submitted sheets from a **new backend GET** (cross-device). Build the app side now.

## New backend contract (user implements on the Python backend)

```
GET /preventive-maintenance/breakdowns
Header: Authorization: Bearer <token>
200 OK  → array of submitted F.06 entry rows (one object per entry; same field names as the
          POST body, plus `id` and `created_at`):
[
  {
    "id": 12,
    "doc_no": "CFPLA.C4.F.06",
    "record_date": "2026-06-27",
    "location": "W-202",
    "machine_name": "Band Sealer",
    "equipment_model_no": "W202-0007",
    "problem_in_brief": "Belt snapped",
    "type_of_maintenance": "Mechanical",
    "part_of_machine": "Belt",
    "temporary_reason": "",
    "duration_start": "10:00",
    "duration_end": "11:30",
    "machine_operator_sign": "Ravi",
    "maintenance_person_sign": "Suresh",
    "qc_clearance_sign": "QC Sam",
    "verified_by": "Head",
    "created_at": "2026-06-27T11:35:00Z"
  }
]
```
One endpoint suffices (full row returned → Download regenerates the PDF, no detail call). Newest
first preferred but the app also sorts client-side by `created_at`/`id` desc.

## App-side architecture

### Data
- **DTO** `BreakdownRecordDto` (in `data/remote/dto/ChecklistDtos.kt`, next to the other F.06 DTOs)
  with `@SerialName` matching the JSON above and a `toDomain()` extension. All fields default so a
  partial backend response never crashes deserialization.
- **Domain model** `BreakdownRecord` (`domain/model/`): `id: Int`, `docNo`, `recordDate`,
  `location`, `machineName`, `equipmentModelNo`, `problemInBrief`, `typeOfMaintenance`,
  `partOfMachine`, `temporaryReason`, `durationStart`, `durationEnd`, `machineOperatorSign`,
  `maintenancePersonSign`, `qcClearanceSign`, `verifiedBy`, `createdAt`.
- **API** `ChecklistApi.listBreakdowns(token): List<BreakdownRecordDto>` →
  `@GET("preventive-maintenance/breakdowns")`.
- **Repository** `ChecklistRepository.listBreakdowns(): Result<List<BreakdownRecord>>`; impl mirrors
  `list()` using the existing `call { token -> ... }` helper.

### Pure mapping (testable)
- `BreakdownRecord.toF06Row(): BreakdownRow` in `presentation/checklist/BreakdownF06Logic.kt`
  (`srNo = 1`, fields copied straight through; `machineName` falls back to `equipmentModelNo` if
  blank). Reuses the existing `BreakdownRow` (14 fields) and `renderBreakdown`.

### ViewModel — `BreakdownListViewModel` (extend existing)
Two sources into one state:
- **Submitted** (new): one-shot `checklistRepository.listBreakdowns()` on init + `refresh()`;
  `submittedLoading`/`submittedError` track it (best-effort — failure shows a soft message, the
  QC-approved section still works). Sorted by `id` desc.
- **QC-approved flags** (existing): `combine(observeAllFlags(), observeSavedIds())` → `items`.
Adds per-record export state (`isExporting`, `pdfToShare`, `exportError`, `onPdfShared()`,
`onExportErrorShown()`) and injects `ChecklistPdfRenderer`. `exportSubmitted(record)` →
`renderBreakdown(BreakdownDocData("CFPLA.C4.F.06", record.verifiedBy, listOf(record.toF06Row())))`.

State:
```
data class BreakdownListUiState(
    val isLoading: Boolean = true,                       // flags flow
    val items: List<BreakdownListItem> = emptyList(),    // QC-approved flags (existing)
    val submitted: List<BreakdownRecord> = emptyList(),  // submitted F.06 sheets
    val submittedLoading: Boolean = true,
    val submittedError: String? = null,
    val isExporting: Boolean = false,
    val pdfToShare: File? = null,
    val exportError: String? = null,
)
```

### Screen — `BreakdownListScreen` (extend)
One `LazyColumn`, two labelled sections (newest first within each):
1. **"Submitted F.06 records"** — cards (machine, date, problem) each with a **Download** action
   (direct one-row F.06 PDF). Shows a soft "Couldn't load submitted records" / "None yet" line
   when empty/errored. A small spinner while `submittedLoading`.
2. **"Awaiting F.06 completion (QC-approved)"** — the existing Feature-2 cards (approved → tap to
   complete; non-approved → locked). Unchanged behavior.
Adds the one-shot share effect + a `SnackbarHost` (the screen currently has none). Keeps the
Create FAB. If BOTH sections are empty, show the existing empty-state text.

## Reuse / no-change
`ChecklistPdfRenderer`, `PdfShareLauncher`, `BreakdownRow`, `BreakdownDocData`, the QC-approved
flags list + complete screen (Feature 2), the F.06 form (Create) — all unchanged.

## Error handling & edge cases
- Backend GET missing/erroring (expected until it ships) → `submittedError` set, soft message,
  no crash; QC-approved section unaffected.
- Not logged in → `call` returns `Result.Error("Not logged in")` → soft message.
- Export failure → "Couldn't create the PDF" snackbar (no crash); share returns false → "No app
  available to open the PDF".
- Online-only for submitted records (no local cache in v1) — matches the preventive list.

## Testing
JVM-unit-testable: `BreakdownRecord.toF06Row()` (field copy + machineName fallback) and
`BreakdownRecordDto.toDomain()`. Screen/network verified by build + manual on-device once the
backend endpoint exists; the QC-approved section testable now.

## Out of scope
- Local caching of submitted records (online-only v1).
- A separate breakdown detail screen for submitted records (download is on the card; the row is
  already complete).
- Editing submitted F.06 records (they were completed in the Create form).
- Backend implementation (user's separate Python service).

## Files
**New:** `domain/model/BreakdownRecord.kt`; unit test for the mapper/DTO.
**Edit:** `data/remote/dto/ChecklistDtos.kt` (+DTO+toDomain), `data/remote/api/ChecklistApi.kt`
(+listBreakdowns), `domain/repository/ChecklistRepository.kt` + `ChecklistRepositoryImpl.kt`
(+listBreakdowns), `presentation/checklist/BreakdownF06Logic.kt` (+toF06Row),
`presentation/checklist/BreakdownListViewModel.kt` (merge + export),
`presentation/checklist/BreakdownListScreen.kt` (two sections + download + share).
