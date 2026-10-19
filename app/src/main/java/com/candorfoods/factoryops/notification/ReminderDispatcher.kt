package com.candorfoods.factoryops.notification

import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import com.candorfoods.factoryops.domain.model.Reminder
import com.candorfoods.factoryops.domain.model.notificationId
import com.candorfoods.factoryops.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure alerting policy, extracted for unit testing. Decides whether a reminder should
 * BUZZ (vs. refresh silently):
 *  - a tier increase while still in the shade → always alert (escalation breaks through);
 *  - still in the shade at the same/lower tier → silent refresh;
 *  - not in the shade (new, or the user cleared it) → alert only if [cooldownMs] has
 *    elapsed since we last buzzed for it. This is what stops the every-2-min nagging.
 */
internal fun shouldAlert(
    priorTier: Int,
    tier: Int,
    lastAlertAtMs: Long,
    nowMs: Long,
    cooldownMs: Long,
): Boolean = when {
    priorTier in 0 until tier -> true          // escalated while visible
    priorTier >= 0 -> false                    // visible, not escalated → silent
    else -> nowMs - lastAlertAtMs >= cooldownMs // gone/new → gated by cooldown
}

/** What to do with a reminder this poll. */
internal enum class ReminderAction { ALERT, SILENT_UPDATE, SKIP }

/**
 * Pure per-reminder decision, extracted for unit testing. Combines [shouldAlert] with
 * whether the notification is currently in the shade:
 *  - ALERT         → post with sound (new / escalated / cooldown elapsed);
 *  - SILENT_UPDATE → still showing → refresh in place with no sound (setOnlyAlertOnce);
 *  - SKIP          → user dismissed it and we're within cooldown → do NOT re-post.
 *
 * SKIP is the key case: on Android O+ a fresh re-post to a high-importance channel
 * BUZZES regardless of setOnlyAlertOnce, so silencing a swiped-away reminder means not
 * re-posting it at all until the cooldown elapses (or it escalates).
 */
internal fun reminderAction(
    showing: Boolean,
    priorTier: Int,
    tier: Int,
    lastAlertAtMs: Long,
    nowMs: Long,
    cooldownMs: Long,
): ReminderAction = when {
    shouldAlert(priorTier, tier, lastAlertAtMs, nowMs, cooldownMs) -> ReminderAction.ALERT
    showing -> ReminderAction.SILENT_UPDATE
    else -> ReminderAction.SKIP
}

/**
 * Reconciles the desired reminder set against the OS notification shade — which IS
 * our dedupe/escalation state (no DB). For each desired reminder it fires/updates,
 * alerting only on first appearance or a tier increase. Reminders that are no longer
 * desired (the underlying action was completed) get cancelled, so stale "please act"
 * alerts auto-clear.
 *
 * Only notifications we own (marked via NotificationHelper.EXTRA_IS_REMINDER) are
 * cancelled — missed-stop and the existing PM-overdue (P1) notifications are left alone.
 */
@Singleton
class ReminderDispatcher @Inject constructor(
    @ApplicationContext context: Context,
    private val notificationHelper: NotificationHelper,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Persists the last time we BUZZED for each notification id, so a reminder the user
    // cleared doesn't re-buzz on the next 2-min poll — only after the cooldown elapses.
    private val alertTimes = context.getSharedPreferences("reminder_alert_times", Context.MODE_PRIVATE)
    private val cooldownMs = Constants.REMINDER_REPEAT_INTERVAL_MINUTES * 60_000L

    /**
     * Full reconcile — used by the LOCAL POLL, which computes the COMPLETE set of reminders
     * the user should currently see. Cancels our reminders that are no longer desired (the
     * action was completed), then fires/updates the rest.
     *
     * Do NOT use this for a single pushed reminder: it treats its argument as the whole
     * desired set, so a one-item list would cancel every OTHER active reminder. Push uses
     * [deliver] instead.
     */
    fun dispatch(reminders: List<Reminder>) {
        val desired: Map<Int, Reminder> = reminders.associateBy { it.notificationId() }
        val active = notificationManager.activeNotifications

        // 1) Cancel our reminders that are no longer desired (action completed). Also drop
        //    their cooldown timestamp so a fresh occurrence later alerts right away.
        for (sbn in active) {
            val isOurs = sbn.notification.extras.getBoolean(NotificationHelper.EXTRA_IS_REMINDER, false)
            if (isOurs && !desired.containsKey(sbn.id)) {
                notificationHelper.cancel(sbn.id)
                alertTimes.edit().remove(sbn.id.toString()).apply()
            }
        }

        // 2) Fire/update desired reminders; alert only when new or escalated.
        desired.values.forEach { fireOrUpdate(it, active) }
    }

    /**
     * Incremental delivery — used by the FCM PUSH path, where a message carries only the
     * reminder(s) that just became relevant, not the complete set. Fires/updates them
     * WITHOUT the cancel-sweep, so an incoming push never wipes the phone's other active
     * reminders. Alerting stays escalation-aware and the notification id is keyed on
     * (type, entity), so a push and a later poll about the same breakdown share one
     * notification and never double-buzz.
     */
    fun deliver(reminders: List<Reminder>) {
        val active = notificationManager.activeNotifications
        reminders.forEach { fireOrUpdate(it, active) }
    }

    /**
     * Fire or update one reminder. Alerts on first appearance / tier increase / after the
     * cooldown; refreshes silently while still in the shade; and — crucially — does NOT
     * re-post a reminder the user swiped away until the cooldown elapses, so the poll
     * can't re-buzz it every 2 minutes (see [reminderAction]).
     */
    private fun fireOrUpdate(reminder: Reminder, active: Array<StatusBarNotification>) {
        val id = reminder.notificationId()
        val prior = active.firstOrNull { it.id == id }
        val priorTier = prior?.notification?.extras?.getInt(NotificationHelper.EXTRA_TIER, -1) ?: -1
        val showing = prior != null
        val now = System.currentTimeMillis()
        val lastAlertAt = alertTimes.getLong(id.toString(), 0L)
        when (reminderAction(showing, priorTier, reminder.tier, lastAlertAt, now, cooldownMs)) {
            ReminderAction.ALERT -> {
                alertTimes.edit().putLong(id.toString(), now).apply()
                notificationHelper.showReminder(reminder, alert = true)
            }
            ReminderAction.SILENT_UPDATE -> notificationHelper.showReminder(reminder, alert = false)
            ReminderAction.SKIP -> Unit   // dismissed + within cooldown → don't nag
        }
    }
}
