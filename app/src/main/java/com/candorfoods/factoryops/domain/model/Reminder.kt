package com.candorfoods.factoryops.domain.model

import com.candorfoods.factoryops.util.Constants

/** Which workflow a reminder belongs to (selects the OS channel + id band). */
enum class ReminderCategory { BREAKDOWN, PM }

/** One row of the role×event matrix. */
enum class ReminderType {
    B1_NEW_BREAKDOWN,
    B2_ACK_STALE,
    B3_AWAITING_QC,
    B4_REOPENED,
    P2_AWAITING_SUPERVISOR,
    P3_SUPERVISOR_REJECTED,
    P4_AWAITING_QC,
    P5_QC_REJECTED,
}

/**
 * A reminder the current device's user should see right now. Pure value object —
 * built by ReminderEvaluator, rendered by ReminderDispatcher/NotificationHelper.
 */
data class Reminder(
    val type: ReminderType,
    val category: ReminderCategory,
    val entityId: String,
    val recipientRole: UserRole,
    val tier: Int,
    val title: String,
    val body: String,
    /** Intent extra key MainActivity reads to deep-link (e.g. "ticket_id", "pm_wo_id"). */
    val deepLinkExtraKey: String,
)

/**
 * Stable per-(type, entity) notification id. Re-firing the same id updates the
 * existing OS notification instead of stacking — this is the dedupe mechanism.
 * Type name is folded into the hash so different reminders about one entity don't
 * collide; the category base keeps breakdown/PM in separate bands.
 */
fun Reminder.notificationId(): Int {
    val base = when (category) {
        ReminderCategory.BREAKDOWN -> Constants.NOTIFICATION_ID_BREAKDOWN_BASE
        ReminderCategory.PM -> Constants.NOTIFICATION_ID_PM_ESCALATION_BASE
    }
    return base + (type.name + entityId).hashCode()
}
