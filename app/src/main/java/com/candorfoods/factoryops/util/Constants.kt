package com.candorfoods.factoryops.util

object Constants {
    const val DB_NAME = "factoryops.db"
    const val AUTH_PREFS = "auth_prefs"
    const val KEY_JWT_TOKEN = "jwt_token"
    const val KEY_USER_ID = "user_id"
    const val KEY_PLANT_ID = "plant_id"
    const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"

    // Shift boundaries in IST (seconds from midnight)
    const val SHIFT_A_START_HOUR = 8   // 08:00
    const val SHIFT_B_START_HOUR = 16  // 16:00
    const val SHIFT_C_START_HOUR = 0   // 24:00 / 00:00

    const val MISSED_STOP_THRESHOLD_MINUTES = 15L
    const val MISSED_START_THRESHOLD_MINUTES = 15L
    // Re-alert cooldown for a reminder the user was already buzzed for. While the
    // breakdown/PM stays open the 2-min poll keeps refreshing the notification, but it
    // only buzzes again once this long has passed (a tier escalation still alerts
    // immediately). Stops the "every 2 minutes" nagging after the user clears it.
    /** How long before a still-unresolved reminder is allowed to buzz again. 7 hours:
     *  roughly once a shift, so a standing problem is re-raised without nagging. */
    const val REMINDER_REPEAT_INTERVAL_MINUTES = 420L   // 7 hours

    const val DEFAULT_EXPECTED_RUN_HOURS = 8.0
    const val MAX_SYNC_RETRIES = 5
    const val SYNC_CHECK_INTERVAL_MINUTES = 5L

    /**
     * Fixed power factor for energy: kWh = ratedKw × run-hours × POWER_FACTOR.
     * Matches the backend's authoritative calculation (which now uses a single
     * fixed factor against the mt_asset_list power), replacing the old per-machine
     * load_factor — so the app's locally-computed kWh agrees with the backend.
     */
    const val POWER_FACTOR = 0.99

    const val NOTIFICATION_CHANNEL_SYNC = "sync_channel"
    const val NOTIFICATION_CHANNEL_FLAGS = "flags_channel"
    const val NOTIFICATION_ID_SYNC = 1001
    const val NOTIFICATION_ID_FLAG_BASE = 2000
    const val NOTIFICATION_ID_BREAKDOWN_BASE = 3000

    const val WORKER_TAG_SYNC = "sync_worker"
    const val WORKER_TAG_FLAG_CHECKER = "flag_checker_worker"
    const val WORKER_TAG_BREAKDOWN_POLL = "breakdown_poll_worker"

    const val BREAKDOWN_POLL_INTERVAL_MINUTES = 2L
    const val KEY_BREAKDOWN_LAST_SEEN = "breakdown_last_seen_at"

    const val NOTIFICATION_CHANNEL_BREAKDOWN = "breakdown_channel"

    // Phase 2C — Preventive Maintenance scheduler
    const val PM_SCHEDULER_INTERVAL_MINUTES = 60L
    /** Hour (IST) from which the day's single "PM pending" notice may be shown — shift
     *  start, so it lands with the technician's morning rather than at 2am. */
    const val PM_DUE_NOTICE_HOUR_IST = 8
    const val WORKER_TAG_PM_SCHEDULER = "pm_scheduler_worker"
    const val PM_GENERATION_LEAD_DAYS = 7

    // ─── Phase 2C-4 — PM escalation tiers ────────────────────────────────────
    /**
     * Days-overdue thresholds. A PM with `now - scheduledDate >= threshold` (in days)
     * gets the corresponding tier. Below all thresholds = tier 0 (on time).
     */
    const val PM_TIER_1_DAYS = 1
    const val PM_TIER_2_DAYS = 4
    const val PM_TIER_3_DAYS = 7
    const val PM_TIER_4_DAYS = 14

    const val NOTIFICATION_CHANNEL_PM_ESCALATION = "pm_escalation"
    const val NOTIFICATION_ID_PM_ESCALATION_BASE = 4000
    /** Fixed id: the daily "PM pending" summary replaces itself rather than stacking. */
    const val NOTIFICATION_ID_PM_DUE_SUMMARY = 3999

    // ─── Machine-transfer receipt reminders ──────────────────────────────────
    // Pushed to the receiving warehouse's people when a transfer is created, so they
    // know to acknowledge receipt. Own channel + id band keeps it separate from the rest.
    const val NOTIFICATION_CHANNEL_TRANSFER = "transfer_channel"
    const val NOTIFICATION_ID_TRANSFER_BASE = 5000

    // ─── Role-based reminders (2026-06-27) ────────────────────────────────────
    /**
     * Breakdown escalation thresholds in MINUTES for a MAJOR flag. Tier rises as the
     * pending state ages. Severity scales these (see ReminderEvaluator):
     * CRITICAL = ×0.5 (faster), MINOR = ×2.0 (slower).
     */
    const val BD_TIER_1_MINUTES = 30L
    const val BD_TIER_2_MINUTES = 60L   // also: escalate to Supervisor
    const val BD_TIER_3_MINUTES = 120L
    const val BD_TIER_4_MINUTES = 300L  // 5h — also: escalate to Head on a new breakdown (B1)

    /** PM handoff (review/QC) escalation thresholds in HOURS. */
    const val PM_HANDOFF_TIER_1_HOURS = 24L  // escalate to next role
    const val PM_HANDOFF_TIER_2_HOURS = 48L  // escalate to Head

    /**
     * Source of truth for reminder delivery. LOCAL_POLL = the on-device evaluator
     * computes + fires reminders (current). SERVER_PUSH = the backend fans out FCM
     * and the local evaluator stands down (future; see FcmService).
     */
    const val NOTIFICATIONS_SOURCE_LOCAL_POLL = "LOCAL_POLL"
    const val NOTIFICATIONS_SOURCE_SERVER_PUSH = "SERVER_PUSH"
    const val NOTIFICATIONS_SOURCE = NOTIFICATIONS_SOURCE_LOCAL_POLL
}
