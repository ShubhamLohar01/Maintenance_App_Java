# Raised Tickets — IT technician works WhatsApp complaints

**Date:** 2026-08-01
**Status:** BUILT — backend and app both implemented and tested
(`:app:testDebugUnitTest` 233 passing, `:app:assembleDebug` green)

## Problem

Staff raise maintenance complaints over WhatsApp (asset, area, photo, problem).
They land in `wa_ticket` and nobody ever sees them: the it_technician's only tile
today is IT Assets. The complaint sits in a table.

The technician needs to see live tickets, take one, fix it, and record what they
did.

## Scope

**In:** a "Raised Tickets" tile on the IT home, a live list, a ticket detail with
the photo, Acknowledge, and Resolve-with-corrective-action, plus a history list.

**Out:** raising a ticket from the app (WhatsApp is the intake), reassigning,
editing a ticket, push notifications, comments/threads.

## Decisions

| Question | Decision |
|---|---|
| Who sees which tickets | Every technician sees every ticket; first to acknowledge owns it |
| `assigned_to` holds | The technician's NAME (column widened to `VARCHAR(120)`) |
| Corrective action lives on | `wa_receives.corrective_action`, not the ticket — it is that person's record of what THEY did |
| `closed_at` | Stamped when the technician resolves |
| Photo | Public S3 URL, loaded directly. No auth header needed |

## Backend (done — no app-side work needed for these)

Base URL is the existing API host; auth is the existing Bearer JWT.

### `GET /wa-tickets?scope=live|history|all&limit=100`

`live` = `open` + `acknowledged`, **oldest first** (longest-waiting is next to do).
`history` = `resolved`, newest first. Any authenticated user may read, so
supervisors can watch the queue.

```json
[
  {
    "id": 1,
    "ticket_no": "TCKT-260801",
    "module": "maintenance",
    "asset": "Laptop",
    "area": "office",
    "description": "Only for testing purpose laptop is not working",
    "photo_url": "https://machineimg.s3.ap-south-1.amazonaws.com/Asset%20Ticket/1.jpg",
    "status": "open",
    "assigned_to": null,
    "raised_by": "Shubham lohar",
    "created_at": "2026-08-01T13:21:17+05:30",
    "closed_at": null
  }
]
```

`status` is one of `open` / `acknowledged` / `resolved`.
`photo_url` is null when the reporter skipped the photo — the UI must handle that.
All timestamps are **already IST**, ISO-8601 with offset. Do not convert again.

### `GET /wa-tickets/{id}`

Same object plus `recipients` — who the ticket was put in front of and what they
did:

```json
"recipients": [
  {
    "id": 3,
    "name": "Vipul ware",
    "ack_status": "done",
    "done_by": "Vipul ware",
    "done_at": "2026-08-01T15:04:00+05:30",
    "corrective_action": "Replaced the charging cable",
    "received_at": "2026-08-01T13:21:20+05:30"
  }
]
```

### `POST /wa-tickets/{id}/acknowledge`

No body. Sets `status=acknowledged`, `assigned_to=<your name>`, and the caller's
`wa_receives.ack_status=acknowledged`. Returns the updated ticket.

- `403` — caller is not a technician
- `404` — no such ticket
- `409` — already resolved, **or already acknowledged by someone else** (the
  message names them). Refresh the list and show who has it.

Acknowledging your own ticket twice is fine (a double tap must not fail).

### `POST /wa-tickets/{id}/resolve`

```json
{ "corrective_action": "Replaced the charging cable" }
```

Sets `status=resolved`, `closed_at=now`, and on the caller's `wa_receives` row:
`ack_status=done`, `done_by`, `done_at`, `corrective_action`. Returns the ticket.

- `400` — `corrective_action` missing or blank (**required**)
- `409` — already resolved, or assigned to a different technician

Resolving without acknowledging first is allowed and still attributes the ticket
to the caller — a technician who just fixes it shouldn't need two taps.

## App-side work

### 1. Dashboard tile

`DashboardTile.kt`, in the `UserRole.IT_TECHNICIAN` branch alongside IT Assets:

```kotlin
DashboardTile("Raised Tickets", Icons.Filled.ReportProblem,
    DashboardAction.Open(Screen.RaisedTickets.route))
```

A `TileBadge` for the live count would be a genuine improvement — the technician
should see there is work without opening the screen — but the existing badge enum
is PM/QC-specific, so treat it as optional follow-up rather than v1.

### 2. Screens

- `Screen.RaisedTickets` — `tickets/raised`
- `Screen.RaisedTicketDetail` — `tickets/raised/{ticketId}` (Int argument)

**List screen.** Two tabs, Live and History, over the same list composable
(`scope=live` / `scope=history`). Each row: ticket no, asset — area, a status
chip, relative age ("2h ago") and a small thumbnail when `photo_url` is non-null.
Empty state for each tab; they will be empty often and that is the normal state,
not an error.

**Detail screen.** Photo full width (tap to zoom), then asset, area, problem,
raised-by, raised-at, status, assigned-to. Then, by status:

| Status | Shown |
|---|---|
| `open` | **Acknowledge** button |
| `acknowledged` | Who has it + **Resolve** button |
| `resolved` | Read-only: who closed it, when, and the corrective action |

**Resolve** opens a dialog with a multi-line text field and a Submit that is
disabled while blank — the API rejects an empty note with 400, so don't let the
user reach that.

### 3. Data layer

Follow the existing IT-assets shape: `WaTicketDto` / `WaReceiveDto` in
`data/remote/dto`, calls on the existing Retrofit service, a repository +
use-cases, a `RaisedTicketsViewModel` holding `StateFlow<UiState>`.

Pull-to-refresh on the list. No polling loop in v1: the technician opens the
screen when they want to look, and a poll would drain battery for a queue that
sees a handful of tickets a day.

### 4. Error handling

- **409 on acknowledge** is the one that matters: two technicians open the same
  ticket, one taps first. Show "Already taken by <name>", refresh, do not retry.
- 403 should not be reachable (the tile is IT-only) — treat as a bug, log it.
- The photo is a plain public URL; a failed image load must not blank the screen.
  Show a placeholder and keep the ticket text.

## Testing

- List renders live vs history from a fake repository
- Acknowledge success moves the ticket to `acknowledged` and swaps the button
- Acknowledge 409 shows the taken-by message and refreshes
- Resolve disabled while the note is blank
- Detail with `photo_url = null` renders without a gap

## Database (already migrated)

```sql
ALTER TABLE wa_ticket   ALTER COLUMN assigned_to TYPE VARCHAR(120);
ALTER TABLE wa_receives ADD COLUMN IF NOT EXISTS corrective_action TEXT;
```

`wa_receives` rows are created when the ticket is raised (one per technician,
`ack_status='pending'`), so the app never creates them.

## Note on photos

Ticket photos are publicly readable and **auto-delete after 45 days** (S3
lifecycle). A history ticket older than that will have a `photo_url` that 404s.
The image loader must degrade to a placeholder rather than showing a broken view.
