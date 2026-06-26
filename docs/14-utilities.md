# Utilities

The `com.candorfoods.factoryops.util` package contains cross-cutting helpers used by the rest of the app: Indian-locale number and currency formatting (lakhs / crores grouping such as `Rs.1,23,456`), IST-anchored date / time / shift formatting (`dd MMM yyyy`, 24-hour `HH:mm`), offline-first on-disk photo storage for breakdown and PM workflows (under `filesDir`, exposed via `FileProvider`), a single `Constants` object that centralises database, auth-prefs, sync, notification channel, worker tag, and PM-tier values, and lightweight Kotlin extension helpers (UUID, kWh conversion, duration, "is today").

## BreakdownPhotoStorage.kt

File: [../app/src/main/java/com/candorfoods/factoryops/util/BreakdownPhotoStorage.kt](../app/src/main/java/com/candorfoods/factoryops/util/BreakdownPhotoStorage.kt)

Responsibility: writes captured photos to app-private storage for breakdown and PM flows, and converts those `File`s into shareable `content://` URIs via the app's `FileProvider`. Files live under `context.filesDir` so they survive process restarts and remain available offline until the sync layer uploads and purges them.

### `object BreakdownPhotoStorage`

Stores breakdown "after-fix" and QC photos. Ticket IDs are sanitised (non `[A-Za-z0-9_-]` replaced with `_`) before being used in filenames.

- Directory layout:
  - After-fix photos: `<filesDir>/breakdown_after/`
  - QC photos: `<filesDir>/breakdown_qc/`
- Filename format: `<safeTicketId>-<epochMillis>.jpg`

Public API:

- `fun createAfterPhotoFile(context: Context, ticketId: String): File` — creates (and ensures the directory exists) a new JPEG file for an "after" photo of the given ticket.
- `fun createQcPhotoFile(context: Context, ticketId: String): File` — same, for a QC photo.
- `fun fileToShareUri(context: Context, file: File): Uri` — wraps the file in a `FileProvider` URI using authority `"${context.packageName}.fileprovider"`. Uses `file.canonicalFile` so `/data/data` is resolved to `/data/user/0` and matches the `<files-path>` prefix even for older seed-data paths.

### `object PmPhotoStorage`

Phase 2C-2 helper that mirrors `BreakdownPhotoStorage` for preventive-maintenance item photos. Scoped per work-order and per checklist item so the file system can be cleaned by WO once the WO is closed (Phase 3 sync uploads then purges).

- Directory: `<filesDir>/pm_photos/`
- Filename format: `<safeWoId>_<safeItemId>_<epochMillis>.jpg`

Public API:

- `fun createItemPhotoFile(context: Context, woId: String, itemId: String): File` — creates a JPEG file for a PM checklist item photo. Both IDs are sanitised before use.
- `fun fileToShareUri(context: Context, file: File): Uri` — `FileProvider` URI wrapping, identical strategy to `BreakdownPhotoStorage`.

## Constants.kt

File: [../app/src/main/java/com/candorfoods/factoryops/util/Constants.kt](../app/src/main/java/com/candorfoods/factoryops/util/Constants.kt)

Responsibility: single source of truth for app-wide constants. All values live on `object Constants`.

### Database and auth prefs

- `DB_NAME = "factoryops.db"` — Room database filename.
- `AUTH_PREFS = "auth_prefs"` — `SharedPreferences` file for auth state.
- `KEY_JWT_TOKEN = "jwt_token"` — preference key for the JWT.
- `KEY_USER_ID = "user_id"` — preference key for the logged-in user id.
- `KEY_PLANT_ID = "plant_id"` — preference key for the active plant id.

### Shift boundaries (IST, hour-of-day)

- `SHIFT_A_START_HOUR = 8` — Shift A starts 08:00.
- `SHIFT_B_START_HOUR = 16` — Shift B starts 16:00.
- `SHIFT_C_START_HOUR = 0` — Shift C starts 00:00 (24:00).

### Flag / missed-event thresholds (minutes)

- `MISSED_STOP_THRESHOLD_MINUTES = 15L` — grace before a missed stop is flagged.
- `MISSED_START_THRESHOLD_MINUTES = 15L` — grace before a missed start is flagged.
- `FLAG_REPEAT_INTERVAL_MINUTES = 30L` — minimum interval between repeated flag notifications.

### Production defaults and sync

- `DEFAULT_EXPECTED_RUN_HOURS = 8.0` — default expected run hours per shift.
- `MAX_SYNC_RETRIES = 5` — sync worker retry ceiling.
- `SYNC_CHECK_INTERVAL_MINUTES = 5L` — periodic sync check cadence.

### Notification channels and IDs

- `NOTIFICATION_CHANNEL_SYNC = "sync_channel"` — channel id for sync status notifications.
- `NOTIFICATION_CHANNEL_FLAGS = "flags_channel"` — channel id for production flags.
- `NOTIFICATION_CHANNEL_BREAKDOWN = "breakdown_channel"` — channel id for breakdown alerts.
- `NOTIFICATION_CHANNEL_PM_ESCALATION = "pm_escalation"` — channel id for PM tier escalations.
- `NOTIFICATION_ID_SYNC = 1001` — fixed id for the sync foreground notification.
- `NOTIFICATION_ID_FLAG_BASE = 2000` — base id; per-flag ids are offset from this.
- `NOTIFICATION_ID_BREAKDOWN_BASE = 3000` — base id for breakdown notifications.
- `NOTIFICATION_ID_PM_ESCALATION_BASE = 4000` — base id for PM escalation notifications.

### WorkManager tags

- `WORKER_TAG_SYNC = "sync_worker"` — tag for the sync worker.
- `WORKER_TAG_FLAG_CHECKER = "flag_checker_worker"` — tag for the flag-checker worker.
- `WORKER_TAG_BREAKDOWN_POLL = "breakdown_poll_worker"` — tag for the breakdown poller.
- `WORKER_TAG_PM_SCHEDULER = "pm_scheduler_worker"` — tag for the PM scheduler.

### Breakdown polling

- `BREAKDOWN_POLL_INTERVAL_MINUTES = 2L` — how often the breakdown poll worker runs.
- `KEY_BREAKDOWN_LAST_SEEN = "breakdown_last_seen_at"` — pref key for the most recently observed breakdown timestamp.

### Phase 2C — Preventive Maintenance scheduler

- `PM_SCHEDULER_INTERVAL_MINUTES = 60L` — PM scheduler cadence.
- `PM_GENERATION_LEAD_DAYS = 7` — how many days ahead PM work orders are generated.

### Phase 2C-4 — PM escalation tiers

Days-overdue thresholds: a PM with `now - scheduledDate >= threshold` (in days) gets the corresponding tier. Below all thresholds is tier 0 (on time).

- `PM_TIER_1_DAYS = 1` — tier 1 trigger.
- `PM_TIER_2_DAYS = 4` — tier 2 trigger.
- `PM_TIER_3_DAYS = 7` — tier 3 trigger.
- `PM_TIER_4_DAYS = 14` — tier 4 trigger.

## DateTimeFormatter.kt

File: [../app/src/main/java/com/candorfoods/factoryops/util/DateTimeFormatter.kt](../app/src/main/java/com/candorfoods/factoryops/util/DateTimeFormatter.kt)

Responsibility: format epoch-millis timestamps in IST (`Asia/Kolkata`) using Indian-friendly patterns, plus shift / day boundary computation. All `SimpleDateFormat` instances are bound to `Locale.ENGLISH` and the IST `TimeZone`.

### Format strings

- `"dd MMM yyyy"` — used by `formatDate` and as the fallback in `formatRelative` for timestamps older than 30 days (e.g. `22 May 2026`).
- `"HH:mm"` — used by `formatTime` for 24-hour wall-clock display (e.g. `16:45`).
- `"dd MMM yyyy, HH:mm"` — used by `formatDateTime` for combined date + time stamps (e.g. `22 May 2026, 16:45`).

### Public API

- `fun formatDate(epochMillis: Long): String` — formats an instant as `dd MMM yyyy` in IST.
- `fun formatTime(epochMillis: Long): String` — formats an instant as `HH:mm` in IST.
- `fun formatDateTime(epochMillis: Long): String` — formats an instant as `dd MMM yyyy, HH:mm` in IST.
- `fun formatDuration(startMillis: Long, endMillis: Long): String` — formats a span as `"<h>h <m>m"` (e.g. `2h 15m`).
- `fun formatRelative(thenMillis: Long, nowMillis: Long = System.currentTimeMillis()): String` — humanised relative age. Buckets: `< 30s` -> `"just now"`, `< 60s` -> `"<n>s ago"`, `< 60m` -> `"<n>m ago"`, `< 24h` -> `"<n>h ago"`, `< 30d` -> `"<n>d ago"`, otherwise falls back to `formatDate`. Negative diffs are clamped to 0.
- `fun startOfDayMillis(epochMillis: Long): Long` — returns midnight (IST) of the day containing the given instant.
- `fun currentShiftStartMillis(): Long` — returns the start instant (IST) of the shift currently in progress. Bucketing: hour >= 16 -> Shift B start 16:00, hour >= 8 -> Shift A start 08:00, otherwise -> Shift C start 00:00.

## Extensions.kt

File: [../app/src/main/java/com/candorfoods/factoryops/util/Extensions.kt](../app/src/main/java/com/candorfoods/factoryops/util/Extensions.kt)

Responsibility: small Kotlin extension helpers for IDs, energy conversion, and timestamp queries.

- `fun String.Companion.uuid(): String` — returns a random UUID string via `UUID.randomUUID().toString()`. Call site looks like `String.uuid()`.
- `fun Double.toKwh(ratedKw: Double, loadFactor: Double): Double` — treats the receiver as run-seconds, converts to hours (`/3600`), then computes energy as `ratedKw * runHours * loadFactor`.
- `fun Long.durationHours(endMillis: Long): Double` — treats the receiver as a start instant in millis and returns elapsed hours to `endMillis` as a `Double` (`(end - start) / 3_600_000.0`).
- `fun Long.isToday(): Boolean` — true if the receiver epoch-millis falls within the current IST day (uses `DateTimeFormatter.startOfDayMillis` for the lower bound and `start + 86_400_000` for the upper).

## NumberFormatter.kt

File: [../app/src/main/java/com/candorfoods/factoryops/util/NumberFormatter.kt](../app/src/main/java/com/candorfoods/factoryops/util/NumberFormatter.kt)

Responsibility: present numeric values with engineering units and Indian-locale digit grouping. Uses `Locale("en", "IN")`, which produces the Indian lakhs / crores grouping (e.g. `1,23,456` for 123,456 and `1,23,45,678` for 12,345,678) rather than Western thousands grouping. The currency formatter prefixes the Indian rupee symbol/code (typically rendered as `₹` or `Rs.` depending on glyph support, e.g. `Rs.1,23,456.00`).

- `fun formatKwh(kwh: Double): String` — formats energy as `"<x.xx> kWh"` (two decimals).
- `fun formatKw(kw: Double): String` — formats power as `"<x.x> kW"` (one decimal).
- `fun formatHours(hours: Double): String` — formats a duration as `"<x.x> h"` (one decimal).
- `fun formatCurrency(amount: Double): String` — formats `amount` as Indian rupees using `NumberFormat.getCurrencyInstance(Locale("en","IN"))`, applying the lakhs / crores digit grouping (e.g. `Rs.1,23,456.00`).
- `fun formatNumber(number: Int): String` — formats an integer with the Indian grouping pattern (e.g. `12,34,567`).
