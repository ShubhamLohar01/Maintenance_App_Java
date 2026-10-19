package com.candorfoods.factoryops.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import com.candorfoods.factoryops.MainActivity
import com.candorfoods.factoryops.R
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.local.LanguagePreferences
import com.candorfoods.factoryops.domain.model.AppLanguage
import com.candorfoods.factoryops.domain.model.Reminder
import com.candorfoods.factoryops.domain.model.ReminderCategory
import com.candorfoods.factoryops.domain.model.notificationId
import com.candorfoods.factoryops.util.Constants
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authPreferences: AuthPreferences,
    private val languagePreferences: LanguagePreferences,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** A context localized to the signed-in user's language (English if nobody is signed in). */
    private fun localized(): Context {
        val userId = authPreferences.getUserId()
        val lang = if (userId != null) languagePreferences.languageForUser(userId) else AppLanguage.DEFAULT
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale(lang.tag)) }
        return context.createConfigurationContext(config)
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_FLAGS,
                    "Production Flags",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Missed start/stop alerts" }
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_SYNC,
                    "Sync Status",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Background sync notifications" }
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_BREAKDOWN,
                    "Breakdown Tickets",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "New breakdown tickets that need a technician"
                    enableVibration(true)
                }
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_PM_ESCALATION,
                    "PM Escalation",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Escalation alerts for overdue preventive maintenance"
                    enableVibration(true)
                }
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_TRANSFER,
                    "Machine Transfers",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "A machine was transferred into your warehouse — acknowledge receipt"
                }
            )
        }
    }

    fun showMissedStopNotification(machineId: String, machineName: String, runId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("machine_id", machineId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, machineId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val strings = localized()
        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_FLAGS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(strings.getString(R.string.notification_missed_stop_title, machineName))
            .setContentText(strings.getString(R.string.notification_missed_stop_body, machineName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(Constants.NOTIFICATION_ID_FLAG_BASE + machineId.hashCode(), notification)
    }

    fun showBreakdownNotification(
        flagId: String,
        machineName: String,
        severity: String,
        descriptionPreview: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ticket_id", flagId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, flagId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_BREAKDOWN)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(localized().getString(R.string.notification_breakdown_title, severity, machineName))
            .setContentText(descriptionPreview.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(descriptionPreview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(
            Constants.NOTIFICATION_ID_BREAKDOWN_BASE + flagId.hashCode(),
            notification
        )
    }

    /**
     * Phase 2C-4 — fires when a PM WO crosses an escalation tier.
     *
     * Tier drives priority + sound/vibration:
     *   T1 → DEFAULT priority, no sound
     *   T2 → DEFAULT priority, default sound
     *   T3 → HIGH priority, default sound
     *   T4 → HIGH priority, alert sound + vibration pattern
     */
    /**
     * One daily nudge covering everything still on the technician's plate today.
     *
     * Deliberately a single grouped notification on a fixed id: a technician with a dozen
     * due tasks needs one reminder, not a dozen, and re-issuing the same id updates the
     * existing notice instead of stacking another one.
     */
    fun showPmDueSummaryNotification(count: Int, leadTaskName: String, maxDaysOverdue: Long) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_pm_today", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, PM_DUE_SUMMARY_REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (count == 1) "1 PM task pending" else "$count PM tasks pending"
        val body = when {
            count == 1 && maxDaysOverdue <= 0L -> "$leadTaskName is due today."
            count == 1                          -> "$leadTaskName is ${maxDaysOverdue}d overdue."
            maxDaysOverdue <= 0L                -> "$leadTaskName and ${count - 1} more due today."
            else -> "$leadTaskName and ${count - 1} more — oldest ${maxDaysOverdue}d overdue."
        }

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_PM_ESCALATION)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (maxDaysOverdue >= Constants.PM_TIER_3_DAYS) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(Constants.NOTIFICATION_ID_PM_DUE_SUMMARY, notification)
    }


    /**
     * Fire or update a reminder notification. The stable id means re-calling with the
     * same reminder updates the existing notification rather than stacking. `alert`
     * controls whether the user is re-buzzed: pass true on first appearance or when
     * the tier increased, false for a silent refresh at the same tier. The current
     * tier and an is-reminder marker are stored in extras so ReminderDispatcher can
     * read prior tier and identify which active notifications it owns.
     */
    fun showReminder(reminder: Reminder, alert: Boolean) {
        val channel = when (reminder.category) {
            ReminderCategory.BREAKDOWN -> Constants.NOTIFICATION_CHANNEL_BREAKDOWN
            ReminderCategory.PM -> Constants.NOTIFICATION_CHANNEL_PM_ESCALATION
            ReminderCategory.TRANSFER -> Constants.NOTIFICATION_CHANNEL_TRANSFER
        }
        val id = reminder.notificationId()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(reminder.deepLinkExtraKey, reminder.entityId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val extras = Bundle().apply {
            putInt(EXTRA_TIER, reminder.tier)
            putBoolean(EXTRA_IS_REMINDER, true)
        }
        val priority = if (reminder.tier >= 1) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(reminder.title)
            .setContentText(reminder.body.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body))
            .setPriority(priority)
            .setOnlyAlertOnce(!alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addExtras(extras)
        if (alert) {
            builder.setDefaults(
                if (reminder.tier >= 2) NotificationCompat.DEFAULT_ALL else NotificationCompat.DEFAULT_SOUND
            )
        }
        notificationManager.notify(id, builder.build())
    }

    fun cancel(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    companion object {
        const val EXTRA_TIER = "reminder_tier"
        const val EXTRA_IS_REMINDER = "is_reminder"

        /** Its own PendingIntent request code, so the summary can't collide with a WO's. */
        private const val PM_DUE_SUMMARY_REQUEST = 3999
    }
}
