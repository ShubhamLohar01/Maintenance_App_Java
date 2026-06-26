# Checklist PDF Download — Design

**Date:** 2026-06-26
**Status:** Approved (pending spec review)
**Author:** Claude + ai.1@candorfoods.in

## Summary

Add a "Download PDF" action to the maintenance checklist UI so a user can export a
record as a PDF that mirrors the official Candor controlled-copy format. Two document
types are covered:

- **Preventive** — Monthly (50a / `CFPLA.C4.F.50a`) and Quarterly (50b / `CFPLA.C4.F.50b`)
  Preventive Maintenance Checklists, exported from a stored record.
- **Breakdown** — Breakdown Maintenance Record (F.06 / `CFPLA.C4.F.06`), exported from
  the entries currently on the F.06 form.

The generated PDF is delivered through the Android **share sheet** (Save to Files /
Downloads, email, print, WhatsApp, etc.), reusing the app's existing FileProvider pattern.

## Background & current state

- `minSdk = 24`, `compileSdk = 35`. No PDF library is on the classpath.
- FileProvider is already configured (`${applicationId}.fileprovider`,
  `@xml/file_paths`) and the app already shares files via
  `FileProvider.getUriForFile(...)` (see `util/BreakdownPhotoStorage.kt`).
- **Preventive** has a real records list → detail flow
  (`PreventiveListScreen` → `PreventiveDetailScreen`). `ChecklistRecordDetail`
  (`domain/model/ChecklistRecord.kt`) holds every field needed to fill the 50a/50b
  layout: `formType`, `docNo`, `checklistDate`, `doneBy`, `checkedBy`, `verifiedBy`,
  `remarks`, and `items` (`section`, `equipment`, `srNo`, `equipmentDate`,
  `checkpoint`, `status` = OK/NOT_OK, `remarks`).
- **Breakdown** has **no stored/viewable records** yet — `BreakdownListScreen` is an
  empty placeholder ("Records list will appear once the backend is wired"). The only
  place breakdown data exists in the app is the F.06 form
  (`BreakdownFormViewModel.BreakdownEntryState` + `verifiedBy`). Therefore the breakdown
  export is generated from the on-screen form entries.
- No Candor logo drawable exists (only `ic_launcher`). The header logo is rendered as a
  styled red "Candor Foods" text wordmark.

## Decisions (locked)

| Decision | Choice |
|---|---|
| Breakdown export source | On the **F.06 form**, generated from on-screen entries (no backend needed) |
| Preventive export source | The opened **stored record** (`PreventiveDetailScreen`) |
| Delivery mechanism | **Android share sheet** (reuse FileProvider) |
| Layout fidelity | **Faithful to the official format**, adapted to a single record |
| PDF engine | Native `android.graphics.pdf.PdfDocument` + `Canvas` (no new dependency) |

## Approach

Use the platform `android.graphics.pdf.PdfDocument` with manual `Canvas`/`Paint`
drawing. Rationale: zero new dependencies, works on minSdk 24, full control to
reproduce the Candor header/table/footer. Rejected alternatives: iText/PdfBox-Android
(heavy, licensing friction, overkill) and `WebView` HTML→print (forces the system print
dialog instead of a clean share, harder to lay out faithfully).

## Architecture & components

### 1. `CandorPdfCanvas` — `util/pdf/CandorPdfCanvas.kt`
Low-level, domain-agnostic drawing helpers over a `PdfDocument`:
- A4 page geometry constants (portrait 595×842 pt; landscape 842×595 pt), margins.
- `drawHeaderBlock(...)` — bordered table: left cell = red bold "Candor Foods" wordmark;
  center = company name + `Format: …` + `Document No: …`; right = Issue Date / Issue No /
  Revision Date / Revision No.
- `drawFooter(...)` — `Prepared By: FST` · `CONTROLLED COPY` · `Approved By: FSTL`.
- Table primitives: `drawRow(cells, widths, heights)`, cell borders, text wrapping
  within a cell.
- **Pagination**: a cursor that, when the next row would overflow the bottom margin,
  finishes the current page (drawing the footer), starts a new page, and redraws the
  header so every page is self-contained.

This class knows nothing about checklists — only pages, cells, text, and page breaks.

### 2. `ChecklistPdfRenderer` — `util/pdf/ChecklistPdfRenderer.kt`
Domain-aware. Depends on `@ApplicationContext` for `cacheDir`.
- `suspend fun renderPreventive(detail: ChecklistRecordDetail): File`
- `suspend fun renderBreakdown(doc: BreakdownDocData): File`

Responsibilities:
- Select per-form header metadata via a small static `DocMeta` table (see below).
- Flatten domain data into table rows (pure functions, unit-testable).
- Drive `CandorPdfCanvas` to draw header → body → footer with page breaks.
- Write to `cacheDir/checklist_pdfs/<filename>.pdf` and return the `File`.
- Runs drawing on `Dispatchers.Default`.

`BreakdownDocData` is a small render-input model (list of entry rows + `verifiedBy` +
`docNo`) mapped from `BreakdownEntryState`, so the renderer does not depend on a
ViewModel type.

### 3. `PdfShareLauncher` — `util/pdf/PdfShareLauncher.kt`
- `fun share(context: Context, file: File)` → `FileProvider.getUriForFile` +
  `Intent.ACTION_SEND` (`type = "application/pdf"`, `EXTRA_STREAM`,
  `FLAG_GRANT_READ_URI_PERMISSION`) wrapped in `Intent.createChooser`.
- Requires a new `file_paths.xml` entry:
  `<cache-path name="checklist_pdfs" path="checklist_pdfs/" />`.

### 4. UI wiring

**Preventive** (`PreventiveDetailScreen` + `PreventiveDetailViewModel`):
- Add a "Download PDF" `IconButton` (PictureAsPdf / Download icon) to the TopAppBar,
  enabled when `uiState.record != null`.
- `viewModel.exportPdf()` renders off-main-thread, then exposes the result as a nullable
  `pdfToShare: File?` in the UI state with a `onPdfShared()` clear method — matching the
  existing one-shot pattern in `BreakdownFormViewModel` (`submitted` + `onSubmittedHandled()`).
- The screen observes `pdfToShare`; when non-null it calls
  `PdfShareLauncher.share(localContext, file)` then `viewModel.onPdfShared()`.
- A transient spinner/disabled state on the action while rendering; failure surfaces a
  Snackbar "Couldn't create PDF".

**Breakdown** (`BreakdownFormScreen` + `BreakdownFormViewModel`):
- Add a "Download PDF" action (TopAppBar icon and/or button near Submit).
- `viewModel.exportPdf()` maps the current `entries` + `verifiedBy` into
  `BreakdownDocData`, renders, and exposes `pdfToShare` / `onPdfShared()` the same way.
- Enabled when at least one entry has data (won't produce an empty sheet); same
  off-main-thread + error handling.

The ViewModels gain `@ApplicationContext` (or the injected `ChecklistPdfRenderer`,
which itself holds the app context) so rendering stays out of the composables and off
the main thread. Composables remain thin: trigger export, observe event, launch share.

## Document metadata (static template constants)

Reproduced from the controlled-copy PDFs in `public/`. Lives in one place
(`DocMeta` map keyed by form):

| Form | Document No | Format title | Issue No | Rev No | Issue Date | Revision Date | Orientation |
|---|---|---|---|---|---|---|---|
| Preventive Monthly | CFPLA.C4.F.50a | Preventive Maintenance Checklist- Monthly | 06 | 05 | 01/11/2027* | 13/12/2025 | Portrait |
| Preventive Quarterly | CFPLA.C4.F.50b | Preventive Maintenance Checklist-Quarterly | 05 | 04 | 01/11/2027* | 01/10/2025 | Portrait |
| Breakdown | CFPLA.C4.F.06 | Breakdown Maintainance Record | 04 | 03 | 01/11/2017 | 01/10/2025 | Landscape |

\* The 50a/50b PDFs print Issue Date `01/11/2027`, which is almost certainly a typo for
`2017`. We mirror the controlled copy verbatim; the value is a single constant and easy
to correct later if the source is fixed.

## Layout details

### Preventive (portrait, multi-page)
1. Header block (repeats every page).
2. Record summary card: Date, Done by, Checked by, Verified by, Remarks.
3. Body table grouped exactly like the form:
   - `SECTION` header row (e.g. "Lower Basement"),
   - `Sr.No + Equipment` row (e.g. "1. Lift 1 Old"),
   - checkpoint rows with columns **Checkpoint | Status (OK / NOT OK) | Remarks**.
   The blank multi-date grid of the printed form is replaced by the single Status column,
   reflecting one saved record.
4. Footer (repeats every page). 50a is ~70 Sr items → genuinely multi-page.

### Breakdown (landscape, typically 1 page)
1. Header block.
2. F.06 register table, one row per entry, 14 columns: Sr No, Date, Location, Machine
   Name, Equipment Model/Sr No, Problem in Brief, Type of Maintenance, Part of machine,
   Reason, Duration Start, Duration End, Machine operator sign, Maintenance Person Sign,
   QC Clearance Sign.
3. `Verified By:` line + footer. Page-breaks if entries overflow.

## Filenames
- Preventive: `Preventive_<form>_<date>_rec<id>.pdf`
  (e.g. `Preventive_50a_Monthly_2026-06-26_rec123.pdf`).
- Breakdown: `Breakdown_F06_<date>.pdf` (e.g. `Breakdown_F06_2026-06-26.pdf`).

## Error handling & edge cases
- All rendering off the main thread; brief spinner on the action.
- Render failure → Snackbar "Couldn't create PDF" (no crash).
- Breakdown with no filled entries → action disabled (no empty sheet).
- Draft preventive records export fine; blank signer fields render as "—".
- Old PDFs in `cacheDir/checklist_pdfs/` are transient; rely on OS cache eviction (no
  explicit retention logic in v1).

## Testing strategy
`android.graphics` is stubbed (no-op / returns 0) in JVM unit tests, so actual drawing
is verified **manually** by generating and opening a PDF on device/emulator for each of
50a, 50b, and F.06. Pure, testable logic is extracted into functions:
- `DocMeta` selection by form type.
- `ChecklistRecordDetail` → ordered table rows (section/equipment grouping).
- `BreakdownEntryState` list → `BreakdownDocData` rows.
- Filename builder.
- Page-break decision (given cursor Y, row height, page bottom → break?).

## Out of scope (v1)
- A breakdown records list/detail backend and exporting *stored* breakdown records.
- Embedding a real Candor logo image (text wordmark used until an asset is provided).
- Pixel-perfect reproduction of the blank multi-date grid and CONTROLLED COPY stamp
  graphic.
- Server-side PDF generation.

## Files touched (anticipated)
- **New:** `util/pdf/CandorPdfCanvas.kt`, `util/pdf/ChecklistPdfRenderer.kt`,
  `util/pdf/PdfShareLauncher.kt`, render-input model for breakdown.
- **Edit:** `res/xml/file_paths.xml` (add `checklist_pdfs` cache path);
  `PreventiveDetailScreen.kt` + `PreventiveDetailViewModel.kt`;
  `BreakdownFormScreen.kt` + `BreakdownFormViewModel.kt`.
- **Tests:** unit tests for the pure logic above.
