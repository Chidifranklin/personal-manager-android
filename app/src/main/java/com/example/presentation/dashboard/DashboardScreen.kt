package com.example.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.Priority
import com.example.data.local.entity.TransactionType
import com.example.presentation.components.CurrencySelectionBottomSheet
import com.example.presentation.components.DonutSlice
import com.example.presentation.components.ExpenseDonutChart
import com.example.presentation.components.LentVsBorrowedRatioBar
import com.example.presentation.components.TrendLineChart
import com.example.presentation.components.TrendDataPoint
import com.example.presentation.components.AppBrandLogo
import com.example.ui.theme.AmberPayable
import com.example.ui.theme.CrimsonExpense
import com.example.ui.theme.EmeraldIncome
import com.example.ui.theme.VioletReceivable
import com.example.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToFinance: () -> Unit,
    onNavigateToProductivity: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onOpenQuickAdd: () -> Unit,
    onLockApp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTrendPeriod by remember { mutableStateOf("Weekly") }
    var showCurrencySheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppBrandLogo(size = 36.dp)
                        Column {
                            Text(
                                text = "Personal Manager",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Executive Ledger & Cloud Sync",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    if (onLockApp != null) {
                        IconButton(
                            onClick = onLockApp,
                            modifier = Modifier
                                .padding(end = 2.dp)
                                .testTag("dashboard_quick_lock_button")
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Lock App with Biometrics",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    AssistChip(
                        onClick = { showCurrencySheet = true },
                        label = {
                            Text(
                                text = state.currencyCode,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CurrencyExchange,
                                contentDescription = "Select Currency",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("dashboard_currency_selector_chip")
                    )
                    IconButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("dashboard_profile_button")
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "User Profile & Security",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenQuickAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = "Quick Add") },
                text = { Text("Quick Action") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("quick_action_fab")
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Net Worth & Liquid Cash Cards
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().testTag("hero_net_worth_card")
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Net Worth",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "Double-Entry Derived",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = CurrencyFormatter.format(state.financialSummary.netWorth, state.currencyCode),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Three breakdown metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Liquid Cash",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = CurrencyFormatter.format(state.financialSummary.liquidCash, state.currencyCode),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Column {
                                Text(
                                    text = "+ Lent Out",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VioletReceivable
                                )
                                Text(
                                    text = CurrencyFormatter.format(state.financialSummary.totalReceivables, state.currencyCode),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = VioletReceivable
                                )
                            }

                            Column {
                                Text(
                                    text = "- Borrowed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberPayable
                                )
                                Text(
                                    text = CurrencyFormatter.format(state.financialSummary.totalPayables, state.currencyCode),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberPayable
                                )
                            }
                        }

                        if (state.defaultAccount != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Default Spending: ${state.defaultAccount!!.name} (${CurrencyFormatter.format(state.defaultAccount!!.balance, state.currencyCode)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Quick Action Icons Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickActionButton(
                        icon = Icons.Default.Send,
                        label = "Lend Money",
                        color = VioletReceivable,
                        onClick = onNavigateToFinance
                    )
                    QuickActionButton(
                        icon = Icons.Default.CallReceived,
                        label = "Borrow",
                        color = AmberPayable,
                        onClick = onNavigateToFinance
                    )
                    QuickActionButton(
                        icon = Icons.Default.ReceiptLong,
                        label = "Log Expense",
                        color = CrimsonExpense,
                        onClick = onNavigateToFinance
                    )
                    QuickActionButton(
                        icon = Icons.Default.AutoAwesome,
                        label = "AI Assistant",
                        color = MaterialTheme.colorScheme.primary,
                        onClick = onNavigateToAi
                    )
                }
            }

            // 3. Ratio Bar: Lent vs Borrowed
            if (state.financialSummary.totalReceivables > 0 || state.financialSummary.totalPayables > 0) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Debt & Receivables Balance",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LentVsBorrowedRatioBar(
                                totalLent = state.financialSummary.totalReceivables,
                                totalBorrowed = state.financialSummary.totalPayables,
                                currencyCode = state.currencyCode
                            )
                        }
                    }
                }
            }

            // 4. Trend Line Chart: Cash Flow & Net Worth
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cash Flow Trends",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Row {
                                FilterChip(
                                    selected = selectedTrendPeriod == "Weekly",
                                    onClick = { selectedTrendPeriod = "Weekly" },
                                    label = { Text("7 Days", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(32.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                FilterChip(
                                    selected = selectedTrendPeriod == "Monthly",
                                    onClick = { selectedTrendPeriod = "Monthly" },
                                    label = { Text("30 Days", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (state.recentTransactions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No transaction trends yet. Log transactions to view trends.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                        val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
                        val now = System.currentTimeMillis()
                        val dayMs = 86400000L

                        // Compute chronological trend data points moving left to right
                        val trendDataPoints = if (selectedTrendPeriod == "Weekly") {
                            // 7 Days from 6 days ago (left) to today (right)
                            (6 downTo 0).map { daysAgo ->
                                val milestoneTime = now - (daysAgo * dayMs)
                                val dateLabel = if (daysAgo == 0) "Today" else dateFormat.format(Date(milestoneTime))
                                val netCumulativeChange = state.recentTransactions
                                    .filter { it.timestamp <= milestoneTime }
                                    .sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
                                val balance = (state.financialSummary.netWorth + netCumulativeChange).coerceAtLeast(0.0)
                                TrendDataPoint(
                                    timestamp = milestoneTime,
                                    dateLabel = dateLabel,
                                    amount = balance
                                )
                            }
                        } else {
                            // 30 Days: 5 milestone dates across the period (left to right)
                            listOf(28, 21, 14, 7, 0).map { daysAgo ->
                                val milestoneTime = now - (daysAgo * dayMs)
                                val dateLabel = if (daysAgo == 0) "Today" else dateFormat.format(Date(milestoneTime))
                                val netCumulativeChange = state.recentTransactions
                                    .filter { it.timestamp <= milestoneTime }
                                    .sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
                                val balance = (state.financialSummary.netWorth + netCumulativeChange).coerceAtLeast(0.0)
                                TrendDataPoint(
                                    timestamp = milestoneTime,
                                    dateLabel = dateLabel,
                                    amount = balance
                                )
                            }
                        }

                        TrendLineChart(
                            dataPoints = trendDataPoints,
                            currencyCode = state.currencyCode
                        )
                        }
                    }
                }
            }

            // 5. Expense Breakdown Donut Chart
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Monthly Expense Categories",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val expenses = state.recentTransactions
                            .filter { it.type == TransactionType.EXPENSE }
                            .groupBy { it.category }
                            .mapValues { it.value.sumOf { tx -> tx.amount } }

                        val colors = listOf(
                            Color(0xFF3B82F6),
                            Color(0xFF10B981),
                            Color(0xFFF59E0B),
                            Color(0xFF8B5CF6),
                            Color(0xFFEC4899),
                            Color(0xFF06B6D4)
                        )

                        val slices = if (expenses.isNotEmpty()) {
                            expenses.entries.mapIndexed { idx, entry ->
                                DonutSlice(
                                    label = entry.key,
                                    value = entry.value,
                                    color = colors[idx % colors.size]
                                )
                            }
                        } else {
                            emptyList()
                        }

                        ExpenseDonutChart(slices = slices, currencyCode = state.currencyCode)
                        if (slices.isEmpty()) {
                            Text(
                                text = "No expense transactions recorded yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            // 6. Upcoming Reminders & Offline Alarms
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Upcoming Agenda & Alarms",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onNavigateToProductivity) {
                        Text("View All")
                    }
                }
            }

            if (state.upcomingReminders.isEmpty()) {
                item {
                    Text(
                        text = "No pending event alarms. Tap Quick Action to schedule an offline reminder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.upcomingReminders) { reminder ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (reminder.isCritical) Icons.Default.NotificationImportant else Icons.Default.Alarm,
                                contentDescription = null,
                                tint = if (reminder.isCritical) CrimsonExpense else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = reminder.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()).format(Date(reminder.triggerTime)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.dismissReminder(reminder.eventId) }) {
                                Icon(Icons.Default.Check, contentDescription = "Dismiss", tint = EmeraldIncome)
                            }
                        }
                    }
                }
            }

            // 7. Active Tasks Preview
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tasks & Checklists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onNavigateToProductivity) {
                        Text("Manage")
                    }
                }
            }

            if (state.pendingTasks.isEmpty()) {
                item {
                    Text(
                        text = "All tasks completed! Great job.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.pendingTasks) { task ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { viewModel.toggleTaskCompletion(task.taskId, it) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                task.description?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
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
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }

    if (showCurrencySheet) {
        CurrencySelectionBottomSheet(
            currentCurrencyCode = state.currencyCode,
            onSelectCurrency = { newCode ->
                viewModel.setCurrencyCode(newCode)
            },
            onDismiss = { showCurrencySheet = false }
        )
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp)
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(54.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = color.copy(alpha = 0.15f),
                contentColor = color
            )
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
