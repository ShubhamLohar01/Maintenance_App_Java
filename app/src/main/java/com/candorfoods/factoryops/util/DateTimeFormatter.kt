package com.candorfoods.factoryops.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeFormatter {
    private val IST = TimeZone.getTimeZone("Asia/Kolkata")
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).also { it.timeZone = IST }
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH).also { it.timeZone = IST }
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH).also { it.timeZone = IST }
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).also { it.timeZone = IST }

    /** ISO `YYYY-MM-DD` in IST — what the backend stores in `reading_date`. */
    fun isoDate(epochMillis: Long): String = isoDateFormat.format(Date(epochMillis))

    fun formatDate(epochMillis: Long): String = dateFormat.format(Date(epochMillis))
    fun formatTime(epochMillis: Long): String = timeFormat.format(Date(epochMillis))
    fun formatDateTime(epochMillis: Long): String = dateTimeFormat.format(Date(epochMillis))

    fun formatDuration(startMillis: Long, endMillis: Long): String {
        val diffMillis = endMillis - startMillis
        val hours = diffMillis / 3_600_000
        val minutes = (diffMillis % 3_600_000) / 60_000
        return "${hours}h ${minutes}m"
    }

    fun formatRelative(thenMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diffMs = (nowMillis - thenMillis).coerceAtLeast(0)
        val sec = diffMs / 1000
        val min = sec / 60
        val hr  = min / 60
        val day = hr / 24
        return when {
            sec < 30   -> "just now"
            min < 1    -> "${sec}s ago"
            min < 60   -> "${min}m ago"
            hr  < 24   -> "${hr}h ago"
            day < 30   -> "${day}d ago"
            else       -> formatDate(thenMillis)
        }
    }

    fun startOfDayMillis(epochMillis: Long): Long {
        val cal = Calendar.getInstance(IST).apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun currentShiftStartMillis(): Long {
        val cal = Calendar.getInstance(IST)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val shiftHour = when {
            hour >= 16 -> 16
            hour >= 8  -> 8
            else       -> 0
        }
        cal.set(Calendar.HOUR_OF_DAY, shiftHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
