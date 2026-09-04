package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.PersonalManagerApplication
import com.example.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "personal_manager_reminders"
        const val ACTION_TRIGGER_REMINDER = "com.example.action.TRIGGER_REMINDER"
        const val ACTION_MARK_DONE = "com.example.action.MARK_DONE"
        const val ACTION_SNOOZE_10M = "com.example.action.SNOOZE_10M"
        const val ACTION_SNOOZE_1H = "com.example.action.SNOOZE_1H"

        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_EVENT_TITLE = "extra_event_title"
        const val EXTRA_IS_CRITICAL = "extra_is_critical"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PersonalManagerApplication
        val repository = app?.repository

        when (intent.action) {
            ACTION_MARK_DONE -> {
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
                val notificationId = eventId.hashCode()
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)

                if (repository != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        repository.dismissReminder(eventId)
                    }
                }
            }
            ACTION_SNOOZE_10M -> {
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
                val notificationId = eventId.hashCode()
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)

                if (repository != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        repository.snoozeReminder(eventId, 10)
                        val reminder = repository.getPendingActiveReminders().firstOrNull { it.eventId == eventId }
                        if (reminder != null) {
                            AlarmScheduler(context).scheduleReminderAlarm(reminder)
                        }
                    }
                }
            }
            ACTION_SNOOZE_1H -> {
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
                val notificationId = eventId.hashCode()
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)

                if (repository != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        repository.snoozeReminder(eventId, 60)
                        val reminder = repository.getPendingActiveReminders().firstOrNull { it.eventId == eventId }
                        if (reminder != null) {
                            AlarmScheduler(context).scheduleReminderAlarm(reminder)
                        }
                    }
                }
            }
            ACTION_TRIGGER_REMINDER -> {
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
                val title = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Event Reminder"
                val isCritical = intent.getBooleanExtra(EXTRA_IS_CRITICAL, false)

                showNotification(context, eventId, title, isCritical)
            }
        }
    }

    private fun showNotification(context: Context, eventId: String, title: String, isCritical: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reminders & Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Offline alarms and agenda alerts for Personal Manager"
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap intent to open app
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Mark Done" quick action
        val doneIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MARK_DONE
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            (eventId + "_done").hashCode(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Snooze 10m" quick action
        val snooze10Intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_10M
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        val snooze10PendingIntent = PendingIntent.getBroadcast(
            context,
            (eventId + "_snooze10").hashCode(),
            snooze10Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Snooze 1h" quick action
        val snooze1hIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_1H
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        val snooze1hPendingIntent = PendingIntent.getBroadcast(
            context,
            (eventId + "_snooze1h").hashCode(),
            snooze1hIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (isCritical) "🚨 High-Priority Reminder" else "Reminder")
            .setContentText(title)
            .setPriority(if (isCritical) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setSound(soundUri)
            .addAction(android.R.drawable.checkbox_on_background, "Mark Done", donePendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze 10m", snooze10PendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze 1h", snooze1hPendingIntent)
            .build()

        notificationManager.notify(eventId.hashCode(), notification)
    }
}
