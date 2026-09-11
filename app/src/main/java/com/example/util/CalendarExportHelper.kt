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

    /**
     * Opens the device default Calendar application.
     */
    fun openCalendar(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                val fallback = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALENDAR)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (fallback.resolveActivity(context.packageManager) != null) {
                    context.startActivity(fallback)
                }
            }
        } catch (_: Exception) {
            // Graceful fallback if calendar provider isn't present
        }
    }
}
