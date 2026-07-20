# A185 Preventive Checklist + Breakdown Record — Design

**Date:** 2026-07-20
**Status:** Approved by user, ready for implementation plan.

## Context

The "Checklist Form" hub (`ChecklistHubScreen` → Preventive Checklist / Breakdown Checklist)
is the app's digitization of Candor Foods' paper SOP forms. Today it is hardcoded to a single
plant's forms:

- Preventive Checklist: W202's Monthly (`CFPLA.C4.F.50a`) and Quarterly (`CFPLA.C4.F.50b`)
  documents, transcribed verbatim into `ChecklistCatalog.kt`.
- Breakdown Checklist: W202's Breakdown Maintenance Record (`CFPLA.C4.F.06`), hardcoded in
  `BreakdownFormViewModel.submit()`/`exportPdf()`.

Every technician sees the same screen regardless of which plant they belong to. There is no
plant branching anywhere in this flow. `W-202` and `A-185` are plants (each containing many
individual assets in `mt_asset_list`), not machines — confirmed via `MtMachine.building` and
backend `ALL_BUILDINGS = ["A-185", "W-202"]`.

This is **separate** from the generic asset-driven PM Plan system (`mt_pm_plan` /
`PmSyncApi` / Supervisor "Create PM plan" screen), which is already fully generic per-asset
and requires no changes — any A185 asset can have a plan authored against it today.

Source documents for A185 (in `docs/`):
- `A185 CFPLB.C4.F.41 Preventive Maintenance Checklist - Quarterly and monthly.pdf` — one
  combined Quarterly + Monthly document (unlike W202's two separate documents), ~172
  checkpoints across ~100 pieces of equipment, sections: Ground floor, Mezzanine floor,
  Packing area, Monthly (Air curtain), Miscellaneous, Hygiene clearance (appears twice —
  after the Quarterly block and again at the end).
- `A185 CFPLB.C4.F.61 Breakdown Maintainance Record.docx` (actually a PDF despite the
  extension) — simpler column set than F.06: Sr No, Date, Machine Name, Equipment Model
  No./Sr No, Problem in Brief, Attendance Time, **Corrective/Preventive Action Taken**,
  Machine clearance Time, QC Clearance Sign, Maintenance Person Sign. No Type of
  Maintenance / Part of Machine / Temporary Reason / Machine Operator Sign / sheet-level
  Verified By.

## Goal

A technician whose `plantId == "A185"` sees and can fill their own Preventive Checklist and
Breakdown Record — matching their paper forms — through the same hub, screens, repositories,
and backend endpoints/tables already used for W202. A `plantId == "W202"` technician's
experience is unchanged.

## Decisions (confirmed with user)

1. **Plant routing: auto-detect, no visible picker.** The technician's own `plantId` (read
   via `GetCurrentUserUseCase`, the same source `BreakdownFormViewModel.warehouseName()`
   already uses) silently selects which form set they see. No UI for switching plants.
2. **"Corrective/Preventive Action Taken" (new F.61 field, no matching backend column):
   reuse an existing unused column** rather than requesting a backend schema change. Chosen
   column: `part_of_machine` (an A185 row won't use it for W202's meaning; `doc_no`
   disambiguates which schema a row belongs to). No backend changes at all for this feature.

## Architecture

### A. Preventive Checklist (F.41)

- `ChecklistType` gains a `plant: String` field. Existing `MONTHLY`/`QUARTERLY` get
  `plant = "W202"`. One new entry: `A185_PREVENTIVE("CFPLB.C4.F.41", "Preventive (Qtrly + Monthly)", plant = "A185")`.
  A185's paper form is one combined document, so it is **one** `ChecklistType`, not a
  Monthly/Quarterly split.
- `ChecklistCatalog` gains an `A185_PREVENTIVE` catalog: all ~172 checkpoints, transcribed
  verbatim from the PDF (already read in full during this session), preserving the paper
  form's section/equipment/checkpoint order and grouping — same transcription discipline as
  the existing `MONTHLY`/`QUARTERLY` catalogs (see the "verbatim" doc comment already on
  `ChecklistCatalog.kt`).
- `ChecklistViewModel` takes the current user's `plantId` and scopes `ChecklistType.entries`
  to that plant when building `rowsByType`, picking the initial `type`, and resolving drafts
  in `loadDraft`. A technician never sees another plant's type in this list.
- `ChecklistScreen`'s `TypeToggle` renders nothing when only one type is available for the
  current plant (A185's case today) instead of a single always-selected chip.
- `ChecklistPdfModel.kt`: new `PdfForm.PREVENTIVE_A185` case in `docMetaFor` (documentNo
  `CFPLB.C4.F.41`, issueDate `04/08/2021`, revisionDate `15/04/2026`, revisionNo `02`,
  formatTitle `"Preventive Maintenance Checklist- Quarterly and monthly"`), a
  `pdfFormFor("A185_PREVENTIVE")` mapping, and a `pdfFileName` case. `ChecklistPdfRenderer`
  itself needs no changes — it already flattens sections/equipment/checkpoints generically
  via `flattenPreventive()`.
- `ChecklistHubScreen`'s subtitle text ("Monthly (50a) / Quarterly (50b)" /
  "Breakdown maintenance record (F.06)") becomes plant-aware, sourced from the same
  plant-scoped `ChecklistType`/breakdown-spec data rather than a hardcoded string.

### B. Breakdown Record (F.61)

- Introduce a small `BreakdownFormSpec` (docNo, which of the existing `BreakdownEntryState`
  fields are shown, their labels, whether the sheet-level Verified By field shows) resolved
  from `user.plantId` — mirrors the `ChecklistType`/`ChecklistCatalog` pattern (one
  parameterized screen + a small per-plant spec) rather than duplicating the whole
  screen/ViewModel.
  - W202 spec: today's behavior unchanged (all current fields, `docNo = "CFPLA.C4.F.06"`).
  - A185 spec: `docNo = "CFPLB.C4.F.61"`; visible fields = Date, Machine Name, Equipment
    Model No., Problem in Brief, Attendance Time (`durationStart`, relabeled), Corrective/
    Preventive Action Taken (new — written into the reused `partOfMachine` field), Machine
    clearance Time (`durationEnd`, relabeled), Maintenance Person Sign, QC Clearance Sign.
    Hidden: Type of Maintenance, Temporary Reason, Machine Operator Sign, sheet-level
    Verified By.
- `BreakdownFormViewModel.submit()`/`exportPdf()` use the resolved spec's `docNo` instead of
  the hardcoded `"CFPLA.C4.F.06"`.
- `BreakdownRow`/`BreakdownDocData` and the breakdown PDF renderer render only the columns
  relevant to the active spec (A185's table has fewer columns than F.06's).
- `BreakdownRecord` (read-back model, used by `BreakdownListViewModel`/`PreventiveListScreen`
  equivalents) already carries every field generically — reading back A185 rows needs no
  model changes; only the detail/list UI's per-doc-type label ("Corrective/Preventive Action
  Taken" vs whatever `part_of_machine` normally means) needs to switch off `doc_no`.

### What does NOT change

- No backend code or schema changes. `doc_no` and `items` (JSONB) on the preventive-checklist
  endpoint, and every column on the breakdown endpoint, are already free-form/generic.
- The asset-driven `mt_pm_plan` PM Plan system is untouched.
- W202 technician behavior and stored data are unchanged.

## Data flow (unchanged shape, new content)

Both flows already POST to the same generic endpoints regardless of plant:
`POST/PUT /preventive-maintenance/checklists` (JSONB `items`, free-form `doc_no`) and
`POST /preventive-maintenance/breakdowns` (fixed columns, free-form `doc_no`). A185 submissions
differ only in the `doc_no` sent and the catalog/field-set used to build the payload — no new
endpoints, no new tables.

## Testing

- Unit tests mirroring existing coverage: `ChecklistPdfModelTest` (add `PREVENTIVE_A185`
  metadata/filename cases), `BreakdownF06LogicTest`-equivalent for the A185 field spec,
  `PreventiveListViewModelTest`/`BreakdownListViewModelTest` (plant-scoped type filtering).
- No APK builds (per user preference) — final verification is the user reviewing the
  transcribed `A185_PREVENTIVE` catalog against the source PDF and exercising the screens
  live in Android Studio.

## Risk

Hand-transcribing ~172 checkpoints is the largest error surface in this change. The catalog
will be transcribed directly from the PDF content already captured in this session; the user
should skim the resulting `ChecklistCatalog.kt` A185 section against the paper form before
relying on it.
