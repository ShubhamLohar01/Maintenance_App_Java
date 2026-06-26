package com.candorfoods.factoryops.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import com.candorfoods.factoryops.MainActivity
import com.candorfoods.factoryops.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_FLAGS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Missed Stop — $machineName")
            .setContentText("You forgot to stop $machineName. Tap to confirm stop time.")
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
            .setContentTitle("$severity breakdown — $machineName")
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
    fun showPmEscalationNotification(
        woId: String,
        machineName: String,
        templateName: String,
        tier: Int,
        daysOverdue: Long
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("pm_wo_id", woId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, woId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (priority, defaultsFlag) = when (tier) {
            1    -> NotificationCompat.PRIORITY_DEFAULT to 0
            2    -> NotificationCompat.PRIORITY_DEFAULT to NotificationCompat.DEFAULT_SOUND
            3    -> NotificationCompat.PRIORITY_HIGH to NotificationCompat.DEFAULT_SOUND
            else -> NotificationCompat.PRIORITY_HIGH to NotificationCompat.DEFAULT_ALL
        }

        val title = when (tier) {
            1    -> "PM overdue — $machineName"
            2    -> "PM overdue ${daysOverdue}d — $machineName"
            3    -> "PM very overdue — $machineName"
            else -> "PM critical — $machineName"
        }

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_PM_ESCALATION)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("$templateName — ${daysOverdue}d overdue")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$templateName has been overdue for $daysOverdue days."))
            .setPriority(priority)
            .setDefaults(defaultsFlag)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(
            Constants.NOTIFICATION_ID_PM_ESCALATION_BASE + woId.hashCode(),
            notification
        )
    }
}
