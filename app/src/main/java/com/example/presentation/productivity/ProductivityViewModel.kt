package com.example.presentation.productivity

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.EventReminderEntity
import com.example.data.local.entity.Priority
import com.example.data.local.entity.TaskEntity
import com.example.data.repository.PersonalManagerRepository
import com.example.util.AlarmScheduler
import com.example.util.CalendarExportHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProductivityUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val reminders: List<EventReminderEntity> = emptyList()
)

class ProductivityViewModel(
    private val repository: PersonalManagerRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val uiState: StateFlow<ProductivityUiState> = combine(
        repository.tasksFlow,
        repository.remindersFlow
    ) { tasks, reminders ->
        ProductivityUiState(
            tasks = tasks,
            reminders = reminders.sortedBy { it.triggerTime }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProductivityUiState()
    )

    fun createTask(title: String, description: String?, priority: Priority, dueDate: Long?) {
        viewModelScope.launch {
            repository.createTask(title, description, priority, dueDate)
        }
    }

    fun toggleTaskCompletion(taskId: String, completed: Boolean) {
        viewModelScope.launch {
            repository.setTaskCompleted(taskId, completed)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun createReminder(
        context: Context,
        title: String,
        triggerTime: Long,
        isCritical: Boolean,
        exportToCalendar: Boolean
    ) {
        viewModelScope.launch {
            val reminder = repository.createReminder(
                title = title,
                triggerTime = triggerTime,
                isCritical = isCritical,
                exportedToCalendar = exportToCalendar
            )
            // Schedule offline alarm clock
            alarmScheduler.scheduleReminderAlarm(reminder)

            // Export to native calendar if requested
            if (exportToCalendar) {
                CalendarExportHelper.exportEventToCalendar(
                    context = context,
                    title = title,
                    description = "Personal Manager Reminder (Alarm governed in app)",
                    startTimeMillis = triggerTime
                )
            }
        }
    }

    fun dismissReminder(eventId: String) {
        viewModelScope.launch {
            repository.dismissReminder(eventId)
            alarmScheduler.cancelReminderAlarm(eventId)
        }
    }

    fun updateReminder(
        context: Context,
        reminder: EventReminderEntity,
        title: String,
        triggerTime: Long,
        isCritical: Boolean,
        exportToCalendar: Boolean
    ) {
        viewModelScope.launch {
            val updated = reminder.copy(
                title = title,
                triggerTime = triggerTime,
                isCritical = isCritical,
                exportedToCalendar = exportToCalendar,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateReminder(updated)
            alarmScheduler.scheduleReminderAlarm(updated)

            if (exportToCalendar && !reminder.exportedToCalendar) {
                CalendarExportHelper.exportEventToCalendar(
                    context = context,
                    title = title,
                    description = "Personal Manager Reminder (Alarm governed in app)",
                    startTimeMillis = triggerTime
                )
            }
        }
    }

    fun snoozeReminder(eventId: String, minutes: Int) {
        viewModelScope.launch {
            repository.snoozeReminder(eventId, minutes)
            val updated = repository.getPendingActiveReminders().firstOrNull { it.eventId == eventId }
            if (updated != null) {
                alarmScheduler.scheduleReminderAlarm(updated)
            }
        }
    }

    fun deleteReminder(reminder: EventReminderEntity) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
            alarmScheduler.cancelReminderAlarm(reminder.eventId)
        }
    }
}
