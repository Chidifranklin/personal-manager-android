package com.example.presentation.productivity

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import com.example.data.local.entity.EventReminderEntity
import com.example.data.local.entity.Priority
import com.example.ui.theme.AmberPayable
import com.example.ui.theme.CrimsonExpense
import com.example.ui.theme.EmeraldIncome
import com.example.util.CalendarExportHelper
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
    var editingReminder by remember { mutableStateOf<EventReminderEntity?>(null) }

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
                    onEdit = { editingReminder = it },
                    onDelete = { viewModel.deleteReminder(it) },
                    onOpenCalendar = { CalendarExportHelper.openCalendar(context) },
                    onExportToCalendar = { rem ->
                        viewModel.updateReminder(
                            context = context,
                            reminder = rem,
                            title = rem.title,
                            triggerTime = rem.triggerTime,
                            isCritical = rem.isCritical,
                            exportToCalendar = true
                        )
                    }
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

    editingReminder?.let { reminder ->
        EditReminderDialog(
            reminder = reminder,
            onDismiss = { editingReminder = null },
            onConfirm = { title, triggerTime, isCritical, exportToCalendar ->
                viewModel.updateReminder(
                    context = context,
                    reminder = reminder,
                    title = title,
                    triggerTime = triggerTime,
                    isCritical = isCritical,
                    exportToCalendar = exportToCalendar
                )
                editingReminder = null
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
    onEdit: (EventReminderEntity) -> Unit,
    onDelete: (EventReminderEntity) -> Unit,
    onOpenCalendar: () -> Unit,
    onExportToCalendar: (EventReminderEntity) -> Unit
) {
    var filterMode by remember { mutableStateOf("All") }

    val now = System.currentTimeMillis()
    val startOfToday = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val endOfToday = remember { startOfToday + (24 * 60 * 60 * 1000L) }

    val filteredReminders = remember(state.reminders, filterMode) {
        when (filterMode) {
            "Today" -> state.reminders.filter { it.triggerTime in startOfToday until endOfToday }
            "Upcoming" -> state.reminders.filter { it.triggerTime >= now && !it.isDismissed }
            "Dismissed" -> state.reminders.filter { it.isDismissed }
            else -> state.reminders
        }
    }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Calendar & Time Agenda",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        FilledTonalButton(
                            onClick = onOpenCalendar,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Calendar", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Alarms fire via Android AlarmManager even when offline. Integrated with calendar dates & times for seamless scheduling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val filterOptions = listOf("All", "Today", "Upcoming", "Dismissed")
                        items(filterOptions) { opt ->
                            FilterChip(
                                selected = filterMode == opt,
                                onClick = { filterMode = opt },
                                label = { Text(opt, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        if (filteredReminders.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (filterMode == "All") "No reminders scheduled. Tap + to set an alarm or agenda alert."
                               else "No reminders match the \"$filterMode\" filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredReminders) { reminder ->
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
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
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
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(reminder.triggerTime)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = if (isPast && !reminder.isDismissed) CrimsonExpense else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(reminder.triggerTime)),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isPast && !reminder.isDismissed) CrimsonExpense else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!reminder.exportedToCalendar) {
                                    IconButton(
                                        onClick = { onExportToCalendar(reminder) },
                                        modifier = Modifier.size(32.dp).testTag("export_cal_${reminder.eventId}")
                                    ) {
                                        Icon(
                                            Icons.Default.CalendarMonth,
                                            contentDescription = "Export to Calendar",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onEdit(reminder) },
                                    modifier = Modifier.size(32.dp).testTag("edit_reminder_${reminder.eventId}")
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Reminder",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { onDelete(reminder) },
                                    modifier = Modifier.size(32.dp).testTag("delete_reminder_${reminder.eventId}")
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

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
    val initialCal = remember {
        Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    var triggerCalendar by remember { mutableStateOf(initialCal) }
    var isCritical by remember { mutableStateOf(false) }
    var exportToCalendar by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val presetOptions = listOf(
        "In 15m" to {
            triggerCalendar = Calendar.getInstance().apply { add(Calendar.MINUTE, 15); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        },
        "In 1h" to {
            triggerCalendar = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        },
        "Tonight 8 PM" to {
            triggerCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
        },
        "Tomorrow 9 AM" to {
            triggerCalendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Reminder & Agenda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Reminder Title / Agenda Item") },
                    placeholder = { Text("e.g. Collect loan repayment from Sarah") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Date & Time Integration:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Date Selector Card
                    OutlinedCard(
                        onClick = {
                            val cur = triggerCalendar
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val newCal = Calendar.getInstance().apply {
                                        timeInMillis = triggerCalendar.timeInMillis
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d)
                                    }
                                    triggerCalendar = newCal
                                },
                                cur.get(Calendar.YEAR),
                                cur.get(Calendar.MONTH),
                                cur.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(triggerCalendar.time),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Time Selector Card
                    OutlinedCard(
                        onClick = {
                            val cur = triggerCalendar
                            TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    val newCal = Calendar.getInstance().apply {
                                        timeInMillis = triggerCalendar.timeInMillis
                                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                                        set(Calendar.MINUTE, minute)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    triggerCalendar = newCal
                                },
                                cur.get(Calendar.HOUR_OF_DAY),
                                cur.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(triggerCalendar.time),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text("Quick Presets:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetOptions) { (label, action) ->
                        SuggestionChip(
                            onClick = action,
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
                        Text("Adds event directly to your Google Calendar app", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title.trim(), triggerCalendar.timeInMillis, isCritical, exportToCalendar)
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

@Composable
fun EditReminderDialog(
    reminder: EventReminderEntity,
    onDismiss: () -> Unit,
    onConfirm: (title: String, triggerTime: Long, isCritical: Boolean, exportToCalendar: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(reminder.title) }
    var triggerCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply { timeInMillis = reminder.triggerTime })
    }
    var isCritical by remember { mutableStateOf(reminder.isCritical) }
    var exportToCalendar by remember { mutableStateOf(reminder.exportedToCalendar) }
    val context = LocalContext.current

    val presetOptions = listOf(
        "In 15m" to {
            triggerCalendar = Calendar.getInstance().apply { add(Calendar.MINUTE, 15); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        },
        "In 1h" to {
            triggerCalendar = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        },
        "Tonight 8 PM" to {
            triggerCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
        },
        "Tomorrow 9 AM" to {
            triggerCalendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Reminder & Agenda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Reminder Title / Agenda Item") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Date & Time Integration:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Date Selector Card
                    OutlinedCard(
                        onClick = {
                            val cur = triggerCalendar
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val newCal = Calendar.getInstance().apply {
                                        timeInMillis = triggerCalendar.timeInMillis
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d)
                                    }
                                    triggerCalendar = newCal
                                },
                                cur.get(Calendar.YEAR),
                                cur.get(Calendar.MONTH),
                                cur.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(triggerCalendar.time),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Time Selector Card
                    OutlinedCard(
                        onClick = {
                            val cur = triggerCalendar
                            TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    val newCal = Calendar.getInstance().apply {
                                        timeInMillis = triggerCalendar.timeInMillis
                                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                                        set(Calendar.MINUTE, minute)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    triggerCalendar = newCal
                                },
                                cur.get(Calendar.HOUR_OF_DAY),
                                cur.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(triggerCalendar.time),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text("Quick Presets:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetOptions) { (label, action) ->
                        SuggestionChip(
                            onClick = action,
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
                        Text("Sync / Export to Google Calendar", style = MaterialTheme.typography.bodyMedium)
                        Text("Keep Google Calendar synchronized", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title.trim(), triggerCalendar.timeInMillis, isCritical, exportToCalendar)
                    }
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
