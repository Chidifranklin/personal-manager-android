package com.example.presentation.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.DebtDirection
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.export.FinancialActivityScope
import android.widget.Toast
import com.example.data.model.DebtWithDetails
import com.example.presentation.components.CurrencySelectionBottomSheet
import com.example.presentation.components.DonutSlice
import com.example.presentation.components.ExpenseDonutChart
import com.example.presentation.components.ExportReportBottomSheet
import com.example.presentation.components.LentVsBorrowedRatioBar
import com.example.ui.theme.AmberPayable
import com.example.ui.theme.CrimsonExpense
import com.example.ui.theme.EmeraldIncome
import com.example.ui.theme.VioletReceivable
import com.example.util.BiometricAuthManager
import com.example.util.CurrencyFormatter
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Accounts", "Transactions", "Debt Ledger", "Budgets")

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showLogTransactionDialog by remember { mutableStateOf(false) }
    var showCreateDebtDialog by remember { mutableStateOf(false) }
    var showRepaymentDialogForDebt by remember { mutableStateOf<DebtWithDetails?>(null) }
    var showSetBudgetDialog by remember { mutableStateOf(false) }
    var showCurrencySheet by remember { mutableStateOf(false) }
    var showExportReportSheet by remember { mutableStateOf(false) }

    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var deletingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var editingDebt by remember { mutableStateOf<DebtWithDetails?>(null) }
    var deletingDebt by remember { mutableStateOf<DebtWithDetails?>(null) }
    var editingBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    var deletingBudget by remember { mutableStateOf<BudgetEntity?>(null) }

    val isExporting by viewModel.isExporting.collectAsState()
    val biometricProtectFinance by viewModel.biometricProtectFinance.collectAsState()
    var isFinanceUnlocked by rememberSaveable { mutableStateOf(false) }
    val isFinanceLocked = biometricProtectFinance && !isFinanceUnlocked
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Financial Ledger",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        if (biometricProtectFinance) {
                            IconButton(
                                onClick = {
                                    if (isFinanceUnlocked) {
                                        isFinanceUnlocked = false
                                    } else {
                                        val activity = context as? FragmentActivity
                                        if (activity != null) {
                                            BiometricAuthManager.authenticate(
                                                activity = activity,
                                                title = "Unlock Financial Ledger",
                                                subtitle = "Verify Fingerprint or Face",
                                                description = "Confirm identity to view financial balances and accounts.",
                                                onSuccess = { isFinanceUnlocked = true },
                                                onError = { _, err ->
                                                    Toast.makeText(context, "Authentication: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        } else {
                                            isFinanceUnlocked = true
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("finance_biometric_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isFinanceUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = if (isFinanceUnlocked) "Lock Financial Data" else "Unlock Financial Data",
                                    tint = if (isFinanceUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        AssistChip(
                            onClick = { showCurrencySheet = true },
                            label = {
                                Text(
                                    text = "${state.currencyCode} (${CurrencyFormatter.getCurrencySymbol(state.currencyCode)})",
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
                                .testTag("finance_currency_selector_chip")
                        )
                        IconButton(
                            onClick = { showExportReportSheet = true },
                            modifier = Modifier.testTag("finance_export_report_button")
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Export Report"
                            )
                        }
                        if (!isFinanceLocked) {
                            IconButton(onClick = {
                                when (selectedTab) {
                                    0 -> showAddAccountDialog = true
                                    1 -> showLogTransactionDialog = true
                                    2 -> showCreateDebtDialog = true
                                    3 -> showSetBudgetDialog = true
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Item")
                            }
                        }
                    }
                )
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, maxLines = 1) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isFinanceLocked) {
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> showAddAccountDialog = true
                            1 -> showLogTransactionDialog = true
                            2 -> showCreateDebtDialog = true
                            3 -> showSetBudgetDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("finance_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isFinanceLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 440.dp)
                            .testTag("finance_biometric_locked_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldIncome.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Biometric Lock",
                                    modifier = Modifier.size(40.dp),
                                    tint = EmeraldIncome
                                )
                            }

                            Text(
                                text = "Financial Ledger Protected",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Biometric authentication (fingerprint or face unlock) is enabled to protect your account balances, debts, and transaction history.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Button(
                                onClick = {
                                    val activity = context as? FragmentActivity
                                    if (activity != null) {
                                        BiometricAuthManager.authenticate(
                                            activity = activity,
                                            title = "Unlock Financial Ledger",
                                            subtitle = "Verify Fingerprint or Face",
                                            description = "Confirm identity to access your private financial accounts.",
                                            onSuccess = {
                                                isFinanceUnlocked = true
                                            },
                                            onError = { _, err ->
                                                Toast.makeText(context, "Unlock: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } else {
                                        isFinanceUnlocked = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("unlock_finance_biometric_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Unlock Financial Data")
                            }
                        }
                    }
                }
            } else {
                when (selectedTab) {
                    0 -> AccountsTab(
                        state = state,
                        onSetDefault = { viewModel.setDefaultAccount(it) },
                        onAddAccount = { showAddAccountDialog = true }
                    )
                    1 -> TransactionsTab(
                        state = state,
                        onEditTransaction = { editingTransaction = it },
                        onDeleteTransaction = { deletingTransaction = it }
                    )
                    2 -> DebtLedgerTab(
                        state = state,
                        onRecordRepayment = { showRepaymentDialogForDebt = it },
                        onEditDebt = { editingDebt = it },
                        onDeleteDebt = { deletingDebt = it }
                    )
                    3 -> BudgetsTab(
                        state = state,
                        onAddBudget = { showSetBudgetDialog = true },
                        onEditBudget = { editingBudget = it },
                        onDeleteBudget = { deletingBudget = it }
                    )
                }
            }
        }
    }

    // --- Dialogs ---
    if (showAddAccountDialog) {
        AddAccountDialog(
            currencyCode = state.currencyCode,
            onDismiss = { showAddAccountDialog = false },
            onConfirm = { name, type, balance, isDefault ->
                viewModel.createAccount(name, type, balance, isDefault)
                showAddAccountDialog = false
            }
        )
    }

    if (showLogTransactionDialog) {
        LogTransactionDialog(
            accounts = state.accounts,
            currencyCode = state.currencyCode,
            onDismiss = { showLogTransactionDialog = false },
            onConfirm = { accountId, type, amount, category, note ->
                viewModel.logTransaction(accountId, type, amount, category, note)
                showLogTransactionDialog = false
            }
        )
    }

    if (showCreateDebtDialog) {
        CreateDebtDialog(
            accounts = state.accounts,
            currencyCode = state.currencyCode,
            onDismiss = { showCreateDebtDialog = false },
            onConfirm = { counterparty, direction, amount, accountId, dueDate, note ->
                viewModel.createDebt(counterparty, direction, amount, accountId, dueDate, note)
                showCreateDebtDialog = false
            }
        )
    }

    showRepaymentDialogForDebt?.let { debtWithDetails ->
        RecordRepaymentDialog(
            debtWithDetails = debtWithDetails,
            accounts = state.accounts,
            currencyCode = state.currencyCode,
            onDismiss = { showRepaymentDialogForDebt = null },
            onConfirm = { targetAccountId, amountPaid, note ->
                viewModel.recordRepayment(debtWithDetails.debt.debtId, targetAccountId, amountPaid, note)
                showRepaymentDialogForDebt = null
            }
        )
    }

    if (showSetBudgetDialog) {
        SetBudgetDialog(
            currencyCode = state.currencyCode,
            onDismiss = { showSetBudgetDialog = false },
            onConfirm = { category, limit, allowRollover, rolloverFloor ->
                viewModel.setBudget(category, limit, allowRollover, rolloverFloor)
                showSetBudgetDialog = false
            }
        )
    }

    editingTransaction?.let { tx ->
        EditTransactionDialog(
            transaction = tx,
            accounts = state.accounts,
            currencyCode = state.currencyCode,
            onDismiss = { editingTransaction = null },
            onConfirm = { accountId, type, amount, category, note ->
                viewModel.updateTransaction(
                    oldTransaction = tx,
                    newAccountId = accountId,
                    newType = type,
                    newAmount = amount,
                    newCategory = category,
                    newNote = note
                )
                editingTransaction = null
            }
        )
    }

    deletingTransaction?.let { tx ->
        AlertDialog(
            onDismissRequest = { deletingTransaction = null },
            title = { Text("Delete Transaction") },
            text = {
                Text(
                    "Are you sure you want to delete this ${tx.category} transaction for ${CurrencyFormatter.format(tx.amount, state.currencyCode)}? The amount will be automatically reverted on your account balance."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(tx)
                        deletingTransaction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTransaction = null }) { Text("Cancel") }
            }
        )
    }

    editingDebt?.let { item ->
        EditDebtDialog(
            item = item,
            currencyCode = state.currencyCode,
            onDismiss = { editingDebt = null },
            onConfirm = { counterparty, direction, amount, dueDate, note ->
                viewModel.updateDebt(
                    debt = item.debt,
                    counterparty = counterparty,
                    direction = direction,
                    amount = amount,
                    dueDate = dueDate,
                    note = note
                )
                editingDebt = null
            }
        )
    }

    deletingDebt?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingDebt = null },
            title = { Text("Delete Debt Record") },
            text = {
                Text(
                    "Are you sure you want to delete the debt record with ${item.debt.counterpartyName} (${CurrencyFormatter.format(item.debt.principalAmount, state.currencyCode)})? This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDebt(item.debt)
                        deletingDebt = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingDebt = null }) { Text("Cancel") }
            }
        )
    }

    editingBudget?.let { budget ->
        EditBudgetDialog(
            budget = budget,
            currencyCode = state.currencyCode,
            onDismiss = { editingBudget = null },
            onConfirm = { limit, allowRollover, rolloverFloor ->
                viewModel.setBudget(
                    category = budget.category,
                    limit = limit,
                    allowRollover = allowRollover,
                    rolloverFloor = rolloverFloor
                )
                editingBudget = null
            }
        )
    }

    deletingBudget?.let { budget ->
        AlertDialog(
            onDismissRequest = { deletingBudget = null },
            title = { Text("Delete Budget") },
            text = {
                Text(
                    "Are you sure you want to delete the monthly budget for \"${budget.category}\"?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBudget(budget)
                        deletingBudget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingBudget = null }) { Text("Cancel") }
            }
        )
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

    if (showExportReportSheet) {
        val initialScope = when (selectedTab) {
            0 -> FinancialActivityScope.ACCOUNTS_PORTFOLIO
            1 -> FinancialActivityScope.TRANSACTIONS_ONLY
            2 -> FinancialActivityScope.DEBT_ACTIVITIES
            else -> FinancialActivityScope.FULL_STATEMENT
        }

        ExportReportBottomSheet(
            currencyCode = state.currencyCode,
            isExporting = isExporting,
            initialScope = initialScope,
            onDismiss = { showExportReportSheet = false },
            onGenerateAndShare = { scope, periodType, format, startTimestamp, endTimestamp, dateRangeLabel ->
                viewModel.generateAndShareReport(
                    context = context,
                    scope = scope,
                    periodType = periodType,
                    format = format,
                    startTimestamp = startTimestamp,
                    endTimestamp = endTimestamp,
                    dateRangeLabel = dateRangeLabel
                ) { success, errorMsg ->
                    if (success) {
                        showExportReportSheet = false
                    } else if (errorMsg != null) {
                        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCopyToClipboard = { scope, periodType, startTimestamp, endTimestamp, dateRangeLabel ->
                viewModel.copyReportForGoogleSheets(
                    context = context,
                    scope = scope,
                    periodType = periodType,
                    startTimestamp = startTimestamp,
                    endTimestamp = endTimestamp,
                    dateRangeLabel = dateRangeLabel
                ) { _, msg ->
                    if (msg != null) {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onOpenGoogleSheets = {
                viewModel.openGoogleSheets(context)
            }
        )
    }
}

// --- Tab 1: Accounts ---
@Composable
fun AccountsTab(
    state: FinanceUiState,
    onSetDefault: (String) -> Unit,
    onAddAccount: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Total Liquid Cash",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = CurrencyFormatter.format(state.financialSummary.liquidCash, state.currencyCode),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Available spending money across ${state.accounts.size} accounts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Configured Accounts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onAddAccount) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Account")
                }
            }
        }

        if (state.accounts.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "No accounts configured yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Add your first checking, savings, or cash wallet to begin tracking your finances.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = onAddAccount,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create Account")
                        }
                    }
                }
            }
        }

        items(state.accounts) { account ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (account.isDefault) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (account.type) {
                        AccountType.BANK -> Icons.Default.AccountBalance
                        AccountType.CASH -> Icons.Default.Payments
                        AccountType.SAVINGS -> Icons.Default.Savings
                    }
                    FilledTonalIconButton(
                        onClick = {},
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(icon, contentDescription = null)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (account.isDefault) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = EmeraldIncome.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "DEFAULT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldIncome,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = account.type.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = CurrencyFormatter.format(account.balance, state.currencyCode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!account.isDefault) {
                            TextButton(
                                onClick = { onSetDefault(account.accountId) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Make Default", style = MaterialTheme.typography.labelSmall)
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

// --- Tab 2: Transactions ---
@Composable
fun TransactionsTab(
    state: FinanceUiState,
    onEditTransaction: (com.example.data.local.entity.TransactionEntity) -> Unit,
    onDeleteTransaction: (com.example.data.local.entity.TransactionEntity) -> Unit
) {
    var filterType by remember { mutableStateOf<TransactionType?>(null) }

    val filteredTransactions = remember(state.transactions, filterType) {
        if (filterType == null) state.transactions
        else state.transactions.filter { it.type == filterType }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterType == null,
                    onClick = { filterType = null },
                    label = { Text("All (${state.transactions.size})") }
                )
                FilterChip(
                    selected = filterType == TransactionType.INCOME,
                    onClick = { filterType = TransactionType.INCOME },
                    label = { Text("Income") },
                    leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = EmeraldIncome) }
                )
                FilterChip(
                    selected = filterType == TransactionType.EXPENSE,
                    onClick = { filterType = TransactionType.EXPENSE },
                    label = { Text("Expense") },
                    leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = CrimsonExpense) }
                )
            }
        }

        if (filteredTransactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No transactions found in this view.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredTransactions) { tx ->
                val account = state.accounts.firstOrNull { it.accountId == tx.accountId }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (tx.type == TransactionType.INCOME) EmeraldIncome.copy(alpha = 0.15f)
                                    else CrimsonExpense.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (tx.type == TransactionType.INCOME) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (tx.type == TransactionType.INCOME) EmeraldIncome else CrimsonExpense
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.category,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(tx.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (account != null) {
                                    Text(
                                        text = " • ${account.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            tx.note?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = (if (tx.type == TransactionType.INCOME) "+" else "-") +
                                        CurrencyFormatter.format(tx.amount, state.currencyCode),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (tx.type == TransactionType.INCOME) EmeraldIncome else CrimsonExpense
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onEditTransaction(tx) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("edit_tx_${tx.transactionId}")
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Transaction",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteTransaction(tx) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("delete_tx_${tx.transactionId}")
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Transaction",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
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

// --- Tab 3: Debt Ledger (Append-Only Immutable Ledger) ---
@Composable
fun DebtLedgerTab(
    state: FinanceUiState,
    onRecordRepayment: (DebtWithDetails) -> Unit,
    onEditDebt: (DebtWithDetails) -> Unit,
    onDeleteDebt: (DebtWithDetails) -> Unit
) {
    val lentDebts = remember(state.debtsWithDetails) {
        state.debtsWithDetails.filter { it.debt.direction == DebtDirection.LENT }
    }
    val borrowedDebts = remember(state.debtsWithDetails) {
        state.debtsWithDetails.filter { it.debt.direction == DebtDirection.BORROWED }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            LentVsBorrowedRatioBar(
                totalLent = state.financialSummary.totalReceivables,
                totalBorrowed = state.financialSummary.totalPayables
            )
        }

        // Section: Money Lent Out (Receivables)
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(VioletReceivable))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Receivables (Money Lent Out)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (lentDebts.isEmpty()) {
            item {
                Text(
                    text = "No active loans. Tap + to record money you lent to someone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(lentDebts) { item ->
                DebtLedgerItemCard(
                    item = item,
                    currencyCode = state.currencyCode,
                    onRecordRepayment = { onRecordRepayment(item) },
                    onEditDebt = { onEditDebt(item) },
                    onDeleteDebt = { onDeleteDebt(item) }
                )
            }
        }

        // Section: Money Borrowed (Payables)
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(AmberPayable))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Liabilities (Money Borrowed)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (borrowedDebts.isEmpty()) {
            item {
                Text(
                    text = "No borrowed debts. Tap + to record money you borrowed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(borrowedDebts) { item ->
                DebtLedgerItemCard(
                    item = item,
                    currencyCode = state.currencyCode,
                    onRecordRepayment = { onRecordRepayment(item) },
                    onEditDebt = { onEditDebt(item) },
                    onDeleteDebt = { onDeleteDebt(item) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun DebtLedgerItemCard(
    item: DebtWithDetails,
    currencyCode: String = "USD",
    onRecordRepayment: () -> Unit,
    onEditDebt: () -> Unit,
    onDeleteDebt: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.debt.counterpartyName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (item.debt.direction == DebtDirection.LENT) "Lent to Counterparty" else "Borrowed from Counterparty",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.debt.dueDate != null) {
                        Text(
                            text = "Due: ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(item.debt.dueDate))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Remaining: ${CurrencyFormatter.format(item.remainingAmount, currencyCode)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (item.isFullySettled) EmeraldIncome else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Principal: ${CurrencyFormatter.format(item.debt.principalAmount, currencyCode)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onEditDebt,
                        modifier = Modifier.size(32.dp).testTag("edit_debt_${item.debt.debtId}")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Debt",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDeleteDebt,
                        modifier = Modifier.size(32.dp).testTag("delete_debt_${item.debt.debtId}")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Debt",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (item.repayments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Repayments (${item.repayments.size} synced records):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                item.repayments.forEach { rep ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "• ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(rep.timestamp))}: ${rep.note ?: "Partial Payment"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "+${CurrencyFormatter.format(rep.amountPaid, currencyCode)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldIncome
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!item.isFullySettled) {
                    Button(
                        onClick = onRecordRepayment,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.debt.direction == DebtDirection.LENT) VioletReceivable else AmberPayable
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (item.debt.direction == DebtDirection.LENT) "Collect Repayment" else "Pay Repayment")
                    }
                } else {
                    AssistChip(
                        onClick = {},
                        label = { Text("Settled in Full") },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldIncome) }
                    )
                }
            }
        }
    }
}

// --- Tab 4: Budgets ---
@Composable
fun BudgetsTab(
    state: FinanceUiState,
    onAddBudget: () -> Unit,
    onEditBudget: (com.example.data.local.entity.BudgetEntity) -> Unit,
    onDeleteBudget: (com.example.data.local.entity.BudgetEntity) -> Unit
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
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Floor-Capped Deficit Rule",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Unused surpluses carry forward if rollover is enabled. Overspending deficits are bounded by a minimum floor (default ${CurrencyFormatter.format(0.0, state.currencyCode)}) to prevent negative compounding spirals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.budgetProgress.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No category budgets set. Tap + to define a budget limit.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(state.budgetProgress) { bp ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = bp.budget.category,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${CurrencyFormatter.format(bp.spentThisMonth, state.currencyCode)} / ${CurrencyFormatter.format(bp.effectiveLimit, state.currencyCode)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onEditBudget(bp.budget) },
                                    modifier = Modifier.size(32.dp).testTag("edit_budget_${bp.budget.budgetId}")
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Budget",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteBudget(bp.budget) },
                                    modifier = Modifier.size(32.dp).testTag("delete_budget_${bp.budget.budgetId}")
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Budget",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { bp.percentUsed.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = if (bp.percentUsed > 1f) CrimsonExpense else if (bp.percentUsed > 0.8f) AmberPayable else EmeraldIncome,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (bp.remaining >= 0) "${CurrencyFormatter.format(bp.remaining, state.currencyCode)} left" else "${CurrencyFormatter.format(-bp.remaining, state.currencyCode)} over budget",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (bp.remaining >= 0) EmeraldIncome else CrimsonExpense,
                                fontWeight = FontWeight.Bold
                            )
                            if (bp.budget.allowRollover) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "Rollover Active (Floor: ${CurrencyFormatter.format(bp.budget.rolloverFloor, state.currencyCode)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
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

// --- Dialog Implementations ---

@Composable
fun AddAccountDialog(
    currencyCode: String = "USD",
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: AccountType, balance: Double, isDefault: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.BANK) }
    var balanceText by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }
    val symbol = CurrencyFormatter.getCurrencySymbol(currencyCode)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name") },
                    placeholder = { Text("e.g. Chase Checking, Cash") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Starting Balance ($symbol)") },
                    placeholder = { Text("0.00") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Type: ", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    AccountType.values().forEach { acctType ->
                        FilterChip(
                            selected = type == acctType,
                            onClick = { type = acctType },
                            label = { Text(acctType.name, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Set as Default Spending Account")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bal = balanceText.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), type, bal, isDefault)
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun LogTransactionDialog(
    accounts: List<AccountEntity>,
    currencyCode: String = "USD",
    onDismiss: () -> Unit,
    onConfirm: (accountId: String, type: TransactionType, amount: Double, category: String, note: String?) -> Unit
) {
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Groceries") }
    var note by remember { mutableStateOf("") }
    var selectedAccountId by remember {
        mutableStateOf(accounts.firstOrNull { it.isDefault }?.accountId ?: accounts.firstOrNull()?.accountId ?: "")
    }
    val symbol = CurrencyFormatter.getCurrencySymbol(currencyCode)

    val standardCategories = listOf("Groceries", "Food & Dining", "Utilities", "Salary", "Transport", "Entertainment", "Health", "Shopping")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row {
                    FilterChip(
                        selected = type == TransactionType.EXPENSE,
                        onClick = { type = TransactionType.EXPENSE },
                        label = { Text("Expense") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = type == TransactionType.INCOME,
                        onClick = { type = TransactionType.INCOME },
                        label = { Text("Income") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($symbol)") },
                    placeholder = { Text("0.00") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick category chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(standardCategories) { cat ->
                        SuggestionChip(
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Deduct / Credit Account:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(accounts) { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.accountId,
                            onClick = { selectedAccountId = acc.accountId },
                            label = { Text("${acc.name} (${CurrencyFormatter.format(acc.balance, currencyCode)})") }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Optional Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0 && selectedAccountId.isNotBlank()) {
                        onConfirm(selectedAccountId, type, amt, category.trim(), note.ifBlank { null })
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CreateDebtDialog(
    accounts: List<AccountEntity>,
    currencyCode: String = "USD",
    onDismiss: () -> Unit,
    onConfirm: (counterparty: String, direction: DebtDirection, amount: Double, accountId: String, dueDate: Long?, note: String?) -> Unit
) {
    var counterparty by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(DebtDirection.LENT) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedAccountId by remember {
        mutableStateOf(accounts.firstOrNull { it.isDefault }?.accountId ?: accounts.firstOrNull()?.accountId ?: "")
    }
    val symbol = CurrencyFormatter.getCurrencySymbol(currencyCode)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (direction == DebtDirection.LENT) "Lend Money (Receivable)" else "Borrow Money (Liability)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row {
                    FilterChip(
                        selected = direction == DebtDirection.LENT,
                        onClick = { direction = DebtDirection.LENT },
                        label = { Text("I Lent Money") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = direction == DebtDirection.BORROWED,
                        onClick = { direction = DebtDirection.BORROWED },
                        label = { Text("I Borrowed") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it },
                    label = { Text("Person / Counterparty Name") },
                    placeholder = { Text("e.g. Alex Smith") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Principal Amount ($symbol)") },
                    placeholder = { Text("0.00") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = if (direction == DebtDirection.LENT) "Deduct funds from:" else "Deposit funds to:",
                    style = MaterialTheme.typography.labelMedium
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(accounts) { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.accountId,
                            onClick = { selectedAccountId = acc.accountId },
                            label = { Text(acc.name) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Purpose") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (counterparty.isNotBlank() && amt > 0 && selectedAccountId.isNotBlank()) {
                        onConfirm(counterparty.trim(), direction, amt, selectedAccountId, null, note.ifBlank { null })
                    }
                }
            ) {
                Text("Record Debt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditTransactionDialog(
    transaction: TransactionEntity,
    accounts: List<AccountEntity>,
    currencyCode: String = "USD",
    onDismiss: () -> Unit,
    onConfirm: (accountId: String, type: TransactionType, amount: Double, category: String, note: String?) -> Unit
) {
    var type by remember { mutableStateOf(transaction.type) }
    var amountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", transaction.amount)) }
    var category by remember { mutableStateOf(transaction.category) }
    var note by remember { mutableStateOf(transaction.note ?: "") }
    var selectedAccountId by remember {
        mutableStateOf(
            if (accounts.any { it.accountId == transaction.accountId }) transaction.accountId
            else accounts.firstOrNull()?.accountId ?: ""
        )
    }
    val symbol = CurrencyFormatter.getCurrencySymbol(currencyCode)
    val standardCategories = listOf("Groceries", "Food & Dining", "Utilities", "Salary", "Transport", "Entertainment", "Health", "Shopping")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row {
                    FilterChip(
                        selected = type == TransactionType.EXPENSE,
                        onClick = { type = TransactionType.EXPENSE },
                        label = { Text("Expense") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = type == TransactionType.INCOME,
                        onClick = { type = TransactionType.INCOME },
                        label = { Text("Income") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($symbol)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth()
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(standardCategories) { cat ->
                        SuggestionChip(
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Account:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(accounts) { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.accountId,
                            onClick = { selectedAccountId = acc.accountId },
                            label = { Text(acc.name) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0 && selectedAccountId.isNotBlank()) {
                        onConfirm(selectedAccountId, type, amt, category.trim(), note.ifBlank { null })
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

@Composable
fun EditDebtDialog(
    item: DebtWithDetails,
    currencyCode: String = "USD",
    onDismiss: () -> Unit,
    onConfirm: (counterparty: String, direction: DebtDirection, amount: Double, dueDate: Long?, note: String?) -> Unit
) {
    var counterparty by remember { mutableStateOf(item.debt.counterpartyName) }
    var direction by remember { mutableStateOf(item.debt.direction) }
    var amountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", item.debt.principalAmount)) }
    var note by remember { mutableStateOf(item.debt.note ?: "") }
    var dueDate by remember { mutableStateOf(item.debt.dueDate) }
    val symbol = CurrencyFormatter.getCurrencySymbol(currencyCode)
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Debt Record") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row {
                    FilterChip(
                        selected = direction == DebtDirection.LENT,
                        onClick = { direction = DebtDirection.LENT },
                        label = { Text("I Lent") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = direction == DebtDirection.BORROWED,
                        onClick = { direction = DebtDirection.BORROWED },
                        label = { Text("I Borrowed") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it },
                    label = { Text("Person / Counterparty") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Principal Amount ($symbol)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Date Picker for Due Date
                OutlinedCard(
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            dueDate?.let { timeInMillis = it }
                        }
                        android.app.DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val selectedCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, y)
                                    set(Calendar.MONTH, m)
                                    set(Calendar.DAY_OF_MONTH, d)
                                }
                                dueDate = selectedCal.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Due Date (Optional)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = dueDate?.let { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it)) } ?: "No due date set (Tap to choose)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (dueDate != null) {
                            IconButton(onClick = { dueDate = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear date", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Purpose") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (counterparty.isNotBlank() && amt > 0) {
                        onConfirm(counterparty.trim(), direction, amt, dueDate, note.ifBlank { null })
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

@Composable
fun EditBudgetDialog(
    budget: BudgetEntity,
    currencyCode: String = "USD",
    onDismiss: () -> Unit,
    onConfirm: (limit: Double, allowRollover: Boolean, rolloverFloor: Double) -> Unit
) {
    var limitText by remember { mutableStateOf(String.format(Locale.US, "%.2f", budget.monthlyLimit)) }
    var allowRollover by remember { mutableStateOf(budget.allowRollover) }
    val symbol = CurrencyFormatter.getCurrencySymbol(currencyCode)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Budget: ${budget.category}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Category: ${budget.category}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Monthly Limit ($symbol)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allowRollover, onCheckedChange = { allowRollover = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Enable Surplus Rollover", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Carry forward surplus funds into next month",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = limitText.toDoubleOrNull() ?: 0.0
                    if (limit > 0) {
                        onConfirm(limit, allowRollover, 0.0)
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

@Composable
fun RecordRepaymentDialog(
    debtWithDetails: DebtWithDetails,
    accounts: List<AccountEntity>,
    currencyCode: String = "USD",
    onDismiss: () -> Unit,
    onConfirm: (targetAccountId: String, amountPaid: Double, note: String?) -> Unit
) {
    var amountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", debtWithDetails.remainingAmount)) }
    var selectedAccountId by remember {
        mutableStateOf(accounts.firstOrNull { it.isDefault }?.accountId ?: accounts.firstOrNull()?.accountId ?: "")
    }
    var note by remember { mutableStateOf("") }
    val symbol = CurrencyFormatter.getCurrencySymbol(currencyCode)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (debtWithDetails.debt.direction == DebtDirection.LENT)
                    "Collect Repayment from ${debtWithDetails.debt.counterpartyName}"
                else
                    "Pay Back ${debtWithDetails.debt.counterpartyName}"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Remaining Outstanding: ${CurrencyFormatter.format(debtWithDetails.remainingAmount, currencyCode)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Repayment Amount ($symbol)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = if (debtWithDetails.debt.direction == DebtDirection.LENT) "Deposit repayment into:" else "Pay repayment from:",
                    style = MaterialTheme.typography.labelMedium
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(accounts) { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.accountId,
                            onClick = { selectedAccountId = acc.accountId },
                            label = { Text(acc.name) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Repayment Reference / Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0 && selectedAccountId.isNotBlank()) {
                        onConfirm(selectedAccountId, amt, note.ifBlank { null })
                    }
                }
            ) {
                Text("Confirm Repayment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SetBudgetDialog(
    currencyCode: String = "USD",
    onDismiss: () -> Unit,
    onConfirm: (category: String, limit: Double, allowRollover: Boolean, rolloverFloor: Double) -> Unit
) {
    var category by remember { mutableStateOf("") }
    var limitText by remember { mutableStateOf("") }
    var allowRollover by remember { mutableStateOf(false) }
    val symbol = CurrencyFormatter.getCurrencySymbol(currencyCode)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Category Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    placeholder = { Text("e.g. Groceries, Dining") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Monthly Limit ($symbol)") },
                    placeholder = { Text("400.00") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allowRollover, onCheckedChange = { allowRollover = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Enable Surplus Rollover", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Unspent funds carry over; deficits capped at $symbol" + "0.00 floor",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = limitText.toDoubleOrNull() ?: 0.0
                    if (category.isNotBlank() && limit > 0) {
                        onConfirm(category.trim(), limit, allowRollover, 0.0)
                    }
                }
            ) {
                Text("Save Budget")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
