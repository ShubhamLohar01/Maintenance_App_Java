# A-185 Preventive Checklist (F.41) + Breakdown Record (F.61) — implementation

**Date:** 2026-08-08
**Status:** implemented
**Supersedes the open decisions in** `2026-07-20-a185-preventive-breakdown-forms-design.md`

## Goal

A technician whose `plantId` is A-185 sees and fills their own controlled forms, through
the same hub, screens, repositories and endpoints W-202 already uses. A W-202 technician's
experience and stored data are unchanged. There is no plant picker — the signed-in user's
own plant decides, so a technician can neither see nor submit the other plant's form.

## Two decisions changed from the 2026-07-20 design

**1. F.41 is split into Quarterly and Monthly, not one combined form.** The paper document
is one controlled copy, but it has a Quarterly block (Sr 1–163) and a Monthly block
(164–172), each closed by its own Hygiene clearance. Presenting it whole would mean
scrolling past 160 quarterly items to tick 9 monthly ones. Both `ChecklistType`s carry the
same `docNo` (CFPLB.C4.F.41) because it *is* one document; the enum name
(`A185_QUARTERLY` / `A185_MONTHLY`) is what distinguishes two records on read-back.

**2. The breakdown record needs no new column, and no backend change at all.** The
2026-07-20 design was written against the copy of F.61 in `docs/` — **Rev 02, 27/02/2025**
— which has a narrower column set and a "Corrective/Preventive Action Taken" column with
no home in the schema. The user supplied the current controlled copy, **Rev 03,
02/02/2026**, whose columns are identical to W-202's F.06: Type of Maintenance, Part of
machine, "(If Temporary maintenance) Reason", Duration Start/End, Machine operator sign.

So A-185's breakdown record differs from W-202's in exactly two ways: the document number
and the printed header block. Nothing about the entry fields, the submission payload or
the stored row changes. **The `.docx` in `docs/` is out of date and should be replaced with
the Rev 03 copy.**

## Preventive — F.41

`ChecklistType` gains `plant`, plus `plantOf()` (normalising "A-185"/"a185" → "A185",
unknown → W202) and `forPlant()`. `ChecklistCatalog` gains `A185_QUARTERLY` (Ground floor
1–3, Mezzanine floor 4–103, Packing area 104–163) and `A185_MONTHLY` (Monthly 164,
Miscellaneous 165–172) — 170 equipment entries, 503 checkpoints.

`ChecklistViewModel` resolves the plant from `GetCurrentUserUseCase` and exposes
`availableTypes`; rows are still built for every type so a draft of any form has somewhere
to merge into, and a draft that is already open keeps its own type. `TypeToggle` renders
nothing when a plant has one form. `ChecklistHubViewModel` names both documents after the
plant, replacing the hardcoded "Monthly (50a) / Quarterly (50b)".

### Transcription

Extracted structurally from the PDF rather than retyped: bold cells are equipment or
section headers, regular cells are checkpoints, and Sr numbers sit in their own left
column, vertically centred on each block. Five spots needed hand-correction where a table
cell merged across a page break or a section header, each verified against the raw text
layer first — item 113's name split across pages 20/21, the blank Sr cell on "Lab AC"
(p24), "Fire Water Pump Panel" absorbed into 152 (p28), and the Miscellaneous block
absorbed into 164 (p30/31).

The form's own quirks are preserved rather than tidied, matching the verbatim discipline
the W-202 catalogs already follow: **Sr 41, 88 and 129 are absent** from the controlled
document, **"Lab AC" carries no Sr number at all**, and **item 133 reads "Cold Room AC1,
AC2, AC3, AC$"** — that `$` is the source document's own typo. `A185FormsTest` asserts all
three, so a future "cleanup" fails the build instead of silently diverging from the paper.

## Breakdown — F.61

`BreakdownFormSpec` carries the only three things that vary: the plant, the document
number, and which controlled-copy header block the export prints.

- **W202** — CFPLA.C4.F.06 → `PdfForm.BREAKDOWN`
- **A185** — CFPLB.C4.F.61 → `PdfForm.BREAKDOWN_A185`
  (Issue Date 01/11/2017, Issue No 04, Revision Date 02/02/2026, Revision No. 03,
  "Format: Breakdown  Maintenance Record")

`BreakdownFormViewModel` resolves it from the signed-in user's plant and uses its `docNo`
on submit, in the export, and in the screen's subtitle. The entry fields, the submission
payload and the stored row are untouched — a W-202 sheet is byte-for-byte what it was.

Downloading a *stored* record resolves the spec from that record's `doc_no` rather than
from whoever is looking, so an A-185 sheet prints the A-185 header even when a head or QC
user exports it from the history screens.

## Record lists are plant-scoped too

Filling the right form isn't enough — a technician must not see the other plant's history
either. All three technician-facing lists are scoped to the signed-in user's plant:

| List | Scoped by |
|---|---|
| Preventive checklist records | `ChecklistType.plantOfRecord(formType, docNo)` |
| Submitted breakdown records | `BreakdownFormSpec.forDocNo(docNo).plant` |
| Open breakdown flags awaiting a form | the flag's `building` |

Two deliberate choices in how the edges behave:

- **A record that can't be attributed goes to W-202.** Everything predating the A-185 forms
  is W-202's, including the auto-generated rows the PM sync writes with no recognisable form
  type — so they stay visible to the technicians they belong to instead of vanishing.
- **A breakdown flag with no `building` stays visible to both.** Hiding it would hide work
  that still has to be done; showing it to the wrong plant is the cheaper mistake.

`BreakdownListViewModel` also now calls `refreshOpenFlags(plant)` instead of `"both"`, so a
device stops pulling the other plant's flags at all. Room may still hold rows from earlier
`"both"` syncs, which is why the flags are filtered on read as well as on fetch.

The filtering is client-side: `GET /preventive-maintenance/checklists` and
`/breakdowns` return every plant's records, and the app drops what isn't its own. That
satisfies the requirement without a backend dependency, but the other plant's records are
still fetched to the device. Scoping the two endpoints by `plant_id` server-side — the way
`head.py` already scopes by `scoped_buildings` — would be the stronger fix and is worth
doing when the backend is next touched.

**Head and QC screens are deliberately left cross-plant.** Those roles are oversight roles;
`head.py` already scopes them by the Head's own `scoped_buildings`.

## Not changed

W-202 behaviour and stored data; the breakdown entry fields, payload and columns for both
plants; the asset-driven `mt_pm_plan` PM Plan system; every endpoint, table and schema
involved. **No backend change is needed for this feature.**

## Testing

`A185FormsTest` — 11 tests: plant scoping and plantId spelling variants, breakdown spec
resolution by plant and by `doc_no`, F.41 structure (section split, Sr 1–172 with the three
real gaps, unnumbered Lab AC, 503 checkpoints, quarterly/monthly boundary at 163/164), and
the controlled-copy header metadata for both A-185 documents.

`PreventiveListViewModelTest` and `BreakdownListViewModelTest` cover the record scoping:
each plant sees only its own records, unattributable records stay with W-202, flags with no
building stay visible to both, and only the technician's own plant is synced.

Full suite: 260 tests, 0 failures. Per the standing preference, no APK build — final
verification is the user checking the transcribed catalog against the paper form and
exercising both screens in Android Studio.
