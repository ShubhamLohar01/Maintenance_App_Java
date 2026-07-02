package com.candorfoods.factoryops.notification

import android.app.NotificationManager
import android.content.Context
import com.candorfoods.factoryops.domain.model.Reminder
import com.candorfoods.factoryops.domain.model.notificationId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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

    fun dispatch(reminders: List<Reminder>) {
        val desired: Map<Int, Reminder> = reminders.associateBy { it.notificationId() }
        val active = notificationManager.activeNotifications

        // 1) Cancel our reminders that are no longer desired (action completed).
        for (sbn in active) {
            val isOurs = sbn.notification.extras.getBoolean(NotificationHelper.EXTRA_IS_REMINDER, false)
            if (isOurs && !desired.containsKey(sbn.id)) {
                notificationHelper.cancel(sbn.id)
            }
        }

        // 2) Fire/update desired reminders; alert only when new or escalated.
        for ((id, reminder) in desired) {
            val priorTier = active.firstOrNull { it.id == id }
                ?.notification?.extras?.getInt(NotificationHelper.EXTRA_TIER, -1)
                ?: -1
            val alert = priorTier < 0 || reminder.tier > priorTier
            notificationHelper.showReminder(reminder, alert)
        }
    }
}
