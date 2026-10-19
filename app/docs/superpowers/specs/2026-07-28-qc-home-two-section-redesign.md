# QC home redesign — two-section landing + all-time History + drafts in Records

**Date:** 2026-07-28  ·  **Status:** Approved, implementing  ·  **Scope:** frontend only

## Goal
Restructure the QC role's home so login lands on a two-card chooser, move the records
archive into a named section, add an all-time History tab, and surface draft PM records.

## Changes

### 1. New landing `QcLandingScreen` (the new QC home)
- Mirrors the existing hub-card layout (`QcHistoryHubScreen`). Header: *name — role — plant*
  (small `QcLandingViewModel` reading `GetCurrentUserUseCase`). Settings icon in the top bar.
- Two cards: **QC Verification** → `QcHome`; **Checklist Records** → the records hub.
- Role routing: `QC_HEAD` / `QC_MEMBER` → `QcLanding` (was `QcHome`). New route `qc/landing`.

### 2. QC Verification (`QcHomeScreen` + `QcHomeViewModel`)
- Remove the top-bar **History** icon. Add a back arrow (pops to landing). Keep Settings.
- Add a 4th tab **"History"** beside "Done today" = all-time items this QC user has verified.
  ViewModel adds an all-time "done" flow reusing `observeQcDoneByUserSince(user.id, since = 0)`
  for both breakdown + PM; new `uiState.history` list + count.

### 3. Checklist Records (repurpose `QcHistoryHubScreen`)
- Relabel title to **"Checklist Records"** (subtitle "PM & Breakdown — view and download").
  Reached from the landing card; the old top-bar entry is gone.
- **PM list includes drafts**: `QcPmHistoryViewModel` calls `checklistRepository.list()` (no
  status filter) instead of `list("SUBMITTED")`. Draft rows show a **"DRAFT"** chip
  (`ChecklistRecordSummary.isDraft`). View + PDF download reused unchanged.
- Breakdown records unchanged (no draft state; all submitted).

## Reuse (no change)
`PdfDownloader`, `ChecklistPdfRenderer`, `PreventiveDetailScreen` (read-only view + download),
`ChecklistRepository`/`ChecklistApi`, the live verification/approve-reject flow, the backend.
The backend `GET /checklists` already returns all statuses when `status` is omitted — no change.

## New / edited files
- New: `presentation/qc/QcLandingScreen.kt`, `QcLandingViewModel.kt`.
- Edit: `QcHomeScreen.kt`, `QcHomeViewModel.kt`, `QcHistoryHubScreen.kt`,
  `QcPmHistoryScreen.kt`, `QcPmHistoryViewModel.kt`, `navigation/Screen.kt`, `navigation/NavGraph.kt`.

## Testing
Build in Android Studio (no APK build here). Manual: QC login → landing 2 cards; Verification
has 4 tabs incl. all-time History; Records shows PM (with DRAFT chips) + Breakdown, both
view + download.
