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

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val reminder = NotificationEnvelope.parse(message.data) ?: return
        reminderDispatcher.dispatch(listOf(reminder))
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Contract for Phase 3 backend: POST /devices/token { userId, token, platform:"android" }.
        // No-op while NOTIFICATIONS_SOURCE = LOCAL_POLL and the endpoint does not exist yet.
    }
}
