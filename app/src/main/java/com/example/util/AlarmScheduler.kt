package com.example.util

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.local.entity.EventReminderEntity
import com.example.receiver.AlarmReceiver

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleReminderAlarm(reminder: EventReminderEntity) {
        if (reminder.triggerTime <= System.currentTimeMillis() || reminder.isDismissed) return

        // On Android 12+ (API 31+), verify exact alarm capability
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // If permission is not granted, fall back to setAndAllowWhileIdle
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_TRIGGER_REMINDER
                    putExtra(AlarmReceiver.EXTRA_EVENT_ID, reminder.eventId)
                    putExtra(AlarmReceiver.EXTRA_EVENT_TITLE, reminder.title)
                    putExtra(AlarmReceiver.EXTRA_IS_CRITICAL, reminder.isCritical)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    reminder.eventId.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerTime, pendingIntent)
                return
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_REMINDER
            putExtra(AlarmReceiver.EXTRA_EVENT_ID, reminder.eventId)
            putExtra(AlarmReceiver.EXTRA_EVENT_TITLE, reminder.title)
            putExtra(AlarmReceiver.EXTRA_IS_CRITICAL, reminder.isCritical)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use AlarmClockInfo for reliable exact delivery even in Android Doze mode
        val showIntent = Intent(context, com.example.MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            reminder.eventId.hashCode(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(reminder.triggerTime, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }

    fun cancelReminderAlarm(eventId: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
