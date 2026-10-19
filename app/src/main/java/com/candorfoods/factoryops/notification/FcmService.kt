package com.candorfoods.factoryops.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives server-pushed reminders. Parses the data payload via [NotificationEnvelope]
 * and routes it through the SAME [ReminderDispatcher] as the local poll engine, so the
 * delivery/dedup behaviour is identical whether a reminder came from polling or push.
 */
@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject lateinit var reminderDispatcher: ReminderDispatcher
    @Inject lateinit var tokenRegistrar: FcmTokenRegistrar

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val reminder = NotificationEnvelope.parse(message.data) ?: return
        // Incremental: a push carries only THIS reminder, not the full desired set — use
        // deliver() so it doesn't cancel the phone's other active reminders.
        reminderDispatcher.deliver(listOf(reminder))
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // FCM rotated this device's token — re-register it so the backend keeps pushing
        // to the right address (POST /devices/token). No-op if signed out.
        tokenRegistrar.register(token)
    }
}
