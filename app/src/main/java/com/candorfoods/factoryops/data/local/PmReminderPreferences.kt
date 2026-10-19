package com.candorfoods.factoryops.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the day the technician was last reminded about their pending PM work.
 *
 * The PM worker runs hourly (it syncs), but a reminder about the same day's work is
 * useful once — not twelve times. Storing the IST date of the last notice keeps the
 * reminder to one a day even across app restarts and reboots.
 */
@Singleton
class PmReminderPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** ISO date (IST) of the last "PM due" notice, or null if none has been shown. */
    fun lastDueNoticeDate(): String? = prefs.getString(KEY_LAST_DUE_NOTICE, null)

    fun markDueNoticeShown(isoDate: String) =
        prefs.edit().putString(KEY_LAST_DUE_NOTICE, isoDate).apply()

    private companion object {
        const val PREFS_NAME = "pm_reminder_prefs"
        const val KEY_LAST_DUE_NOTICE = "last_due_notice_date"
    }
}
