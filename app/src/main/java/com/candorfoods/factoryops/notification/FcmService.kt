package com.candorfoods.factoryops.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // TODO: handle push notifications from server (PM reminders, supervisor overrides)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO: send token to server when backend is ready
    }
}
