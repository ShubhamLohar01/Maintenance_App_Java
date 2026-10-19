package com.candorfoods.factoryops.notification

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.DeviceApi
import com.candorfoods.factoryops.data.remote.dto.DeviceTokenRequestDto
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the backend where to reach THIS phone with push. Registers the device's current
 * FCM token against the signed-in user via POST /devices/token, so the server can push a
 * breakdown/PM reminder straight here instead of the app polling for it.
 *
 * Best-effort and fire-and-forget: a failure (offline, backend down, signed out) is
 * swallowed — the 2-minute BreakdownPollWorker stays as the safety net, and registration
 * retries on the next login / app start / token rotation.
 */
@Singleton
class FcmTokenRegistrar @Inject constructor(
    private val deviceApi: DeviceApi,
    private val authPreferences: AuthPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Ask FCM for this device's current token, then register it. Call on login + app start. */
    fun registerCurrentToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) task.result?.let { register(it) }
        }
    }

    /** Register a known FCM token (e.g. from onNewToken). No-op when signed out. */
    fun register(fcmToken: String) {
        val userId = authPreferences.getUserId() ?: return
        val jwt = authPreferences.getToken() ?: return
        scope.launch {
            runCatching {
                deviceApi.registerToken(
                    "Bearer $jwt",
                    DeviceTokenRequestDto(userId = userId, token = fcmToken),
                )
            }
        }
    }
}
