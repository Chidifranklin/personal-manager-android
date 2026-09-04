package com.example.util

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

object CalendarExportHelper {
    /**
     * Exports an event to the device's default Calendar application without
     * requesting intrusive runtime READ_CALENDAR or WRITE_CALENDAR permissions.
     */
    fun exportEventToCalendar(
        context: Context,
        title: String,
        description: String = "Scheduled via Personal Manager",
        startTimeMillis: Long,
        endTimeMillis: Long = startTimeMillis + (60 * 60 * 1000L) // Default 1 hour
    ) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis)
            putExtra(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }
}
