package com.candorfoods.factoryops.presentation.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Requests the Android 13+ POST_NOTIFICATIONS runtime permission once, the first time the
 * screen is shown, if it isn't already granted. On Android 12 and below the permission
 * doesn't exist, so this is a no-op there.
 *
 * Silent either way — a denial just means this user won't see push / escalation
 * notifications (data still syncs). Placed on every home screen whose role RECEIVES
 * reminders (technician, supervisor, head, QC) so escalations actually surface on newer
 * phones, not only the technician's.
 */
@Composable
fun RequestNotificationPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — silent degrade either way */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
