package com.example.presentation.productivity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.Priority
import com.example.ui.theme.AmberPayable
import com.example.ui.theme.CrimsonExpense
import com.example.ui.theme.EmeraldIncome
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductivityScreen(
    viewModel: ProductivityViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Tasks", "Reminders & Agenda")

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Productivity & Agenda",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            if (selectedTab == 0) showAddTaskDialog = true
                            else showAddReminderDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Item")
                        }
                    }
                )
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) showAddTaskDialog = true
                    else showAddReminderDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("productivity_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> TasksTab(
                    state = state,
                    onToggleComplete = { id, comp -> viewModel.toggleTaskCompletion(id, comp) },
                    onDeleteTask = { viewModel.deleteTask(it) }
                )
                1 -> RemindersTab(
                    state = state,
                    onDismiss = { viewModel.dismissReminder(it) },
                    onSnooze = { id, mins -> viewModel.snoozeReminder(id, mins) },
                    onDelete = { viewModel.deleteReminder(it) }
                )
            }
        }
    }

    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, desc, priority ->
                viewModel.createTask(title, desc, priority, null)
                showAddTaskDialog = false
            }
        )
    }

    if (showAddReminderDialog) {
        AddReminderDialog(
            onDismiss = { showAddReminderDialog = false },
            onConfirm = { title, triggerTime, isCritical, exportToCalendar ->
                viewModel.createReminder(context, title, triggerTime, isCritical, exportToCalendar)
                showAddReminderDialog = false
            }
        )
    }
}

// --- Tab 1: Tasks ---
@Composable
fun TasksTab(
    state: ProductivityUiState,
    onToggleComplete: (String, Boolean) -> Unit,
    onDeleteTask: (com.example.data.local.entity.TaskEntity) -> Unit
) {
    var filterStatus by remember { mutableStateOf("All") }

    val filteredTasks = remember(state.tasks, filterStatus) {
        when (filterStatus) {
            "Pending" -> state.tasks.filter { !it.isCompleted }
            "Completed" -> state.tasks.filter { it.isCompleted }
            else -> state.tasks
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterStatus == "All",
                    onClick = { filterStatus = "All" },
                    label = { Text("All (${state.tasks.size})") }
                )
                FilterChip(
                    selected = filterStatus == "Pending",
                    onClick = { filterStatus = "Pending" },
                    label = { Text("Pending (${state.tasks.count { !it.isCompleted }})") }
                )
                FilterChip(
                    selected = filterStatus == "Completed",
                    onClick = { filterStatus = "Completed" },
                    label = { Text("Completed (${state.tasks.count { it.isCompleted }})") }
                )
            }
        }

        if (filteredTasks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No tasks found in this view.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredTasks) { task ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = task.isCompleted,
                            onCheckedChange = { onToggleComplete(task.taskId, it) }
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            task.description?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AssistChip(
                            onClick = {},
                            label = { Text(task.priority.name, style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = when (task.priority) {
                                    Priority.HIGH -> CrimsonExpense.copy(alpha = 0.15f)
                                    Priority.MEDIUM -> AmberPayable.copy(alpha = 0.15f)
                                    Priority.LOW -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                labelColor = when (task.priority) {
                                    Priority.HIGH -> CrimsonExpense
                                    Priority.MEDIUM -> AmberPayable
                                    Priority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        )

                        IconButton(onClick = { onDeleteTask(task) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// --- Tab 2: Reminders & Agenda ---
@Composable
fun RemindersTab(
    state: ProductivityUiState,
    onDismiss: (String) -> Unit,
    onSnooze: (String, Int) -> Unit,
    onDelete: (com.example.data.local.entity.EventReminderEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Offline Alarms & Google Calendar Authority",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The local Room database is the authoritative single source of truth. Alarms fire via Android AlarmManager even when offline. You can optionally export events to Google Calendar upon creation without calendar permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.reminders.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No reminders scheduled. Tap + to set an alarm or agenda alert.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(state.reminders) { reminder ->
                val isPast = reminder.triggerTime <= System.currentTimeMillis()
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (reminder.isDismissed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (reminder.isCritical) CrimsonExpense.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.primaryContainer
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (reminder.isCritical) Icons.Default.NotificationImportant else Icons.Default.Alarm,
                                        contentDescription = null,
                                        tint = if (reminder.isCritical) CrimsonExpense else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = reminder.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = SimpleDateFormat("EEEE, MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(reminder.triggerTime)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isPast && !reminder.isDismissed) CrimsonExpense else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(onClick = { onDelete(reminder) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (reminder.exportedToCalendar) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "Calendar Exported",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (reminder.isCritical) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = CrimsonExpense.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "High Priority",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CrimsonExpense,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (!reminder.isDismissed) {
                                Row {
                                    FilledTonalButton(
                                        onClick = { onSnooze(reminder.eventId, 10) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Snooze 10m", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = { onDismiss(reminder.eventId) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Done", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            } else {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Dismissed") },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldIncome) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// --- Dialogs ---

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String?, priority: Priority) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    placeholder = { Text("e.g. File tax returns") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Priority Level:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Priority.values().forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title.trim(), desc.ifBlank { null }, priority)
                    }
                }
            ) {
                Text("Add Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, triggerTime: Long, isCritical: Boolean, exportToCalendar: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedPresetMinutes by remember { mutableIntStateOf(60) }
    var isCritical by remember { mutableStateOf(false) }
    var exportToCalendar by remember { mutableStateOf(true) }

    val presetOptions = listOf(
        15 to "In 15 mins",
        60 to "In 1 hour",
        180 to "In 3 hours",
        720 to "In 12 hours",
        1440 to "Tomorrow"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Offline Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Reminder Title / Agenda Item") },
                    placeholder = { Text("e.g. Collect loan repayment from Sarah") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Alert Timing:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetOptions) { (mins, label) ->
                        FilterChip(
                            selected = selectedPresetMinutes == mins,
                            onClick = { selectedPresetMinutes = mins },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isCritical, onCheckedChange = { isCritical = it })
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Critical High-Priority Alarm", style = MaterialTheme.typography.bodyMedium)
                        Text("Overrides silent profile and sounds alarm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = exportToCalendar, onCheckedChange = { exportToCalendar = it })
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Export to Google Calendar", style = MaterialTheme.typography.bodyMedium)
                        Text("Fires native intent without invasive permissions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "ℹ️ Alarm firing times are strictly governed inside Personal Manager.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val trigger = System.currentTimeMillis() + (selectedPresetMinutes * 60 * 1000L)
                        onConfirm(title.trim(), trigger, isCritical, exportToCalendar)
                    }
                }
            ) {
                Text("Schedule Alarm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
