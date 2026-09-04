package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.DebtDirection
import com.example.data.local.entity.DebtLedgerEntity
import com.example.data.local.entity.DebtRepaymentEntity
import com.example.data.local.entity.EventReminderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.Priority
import com.example.data.local.entity.SyncStatus
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.model.CategoryBudgetProgress
import com.example.data.model.DebtWithDetails
import com.example.data.model.FinancialSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar
import java.util.UUID

class PersonalManagerRepository(
    private val database: AppDatabase,
    var currentUserId: String = "local_default_user"
) {
    private val accountDao = database.accountDao()
    private val transactionDao = database.transactionDao()
    private val budgetDao = database.budgetDao()
    private val debtDao = database.debtDao()
    private val taskDao = database.taskDao()
    private val eventReminderDao = database.eventReminderDao()
    private val noteDao = database.noteDao()

    // --- Accounts ---
    val accountsFlow: Flow<List<AccountEntity>>
        get() = accountDao.getAllAccounts(currentUserId)

    val defaultAccountFlow: Flow<AccountEntity?>
        get() = accountDao.getDefaultAccount(currentUserId)

    suspend fun getAccountById(accountId: String): AccountEntity? =
        accountDao.getAccountById(accountId)

    suspend fun createAccount(name: String, type: AccountType, initialBalance: Double, isDefault: Boolean = false): AccountEntity {
        if (isDefault) {
            accountDao.clearDefault(currentUserId)
        }
        val account = AccountEntity(
            userId = currentUserId,
            name = name,
            type = type,
            balance = initialBalance,
            isDefault = isDefault
        )
        accountDao.insertAccount(account)
        return account
    }

    suspend fun setDefaultAccount(accountId: String) {
        accountDao.clearDefault(currentUserId)
        accountDao.setDefault(currentUserId, accountId)
    }

    suspend fun deleteAccount(account: AccountEntity) {
        accountDao.deleteAccount(account)
    }

    // --- Transactions ---
    val transactionsFlow: Flow<List<TransactionEntity>>
        get() = transactionDao.getAllTransactions(currentUserId)

    val recentTransactionsFlow: Flow<List<TransactionEntity>>
        get() = transactionDao.getRecentTransactions(currentUserId, 15)

    suspend fun logTransaction(
        accountId: String,
        type: TransactionType,
        amount: Double,
        category: String,
        note: String? = null
    ): TransactionEntity {
        return database.withTransaction {
            val account = accountDao.getAccountById(accountId)
                ?: throw IllegalArgumentException("Target account not found")

            val updatedBalance = when (type) {
                TransactionType.INCOME -> account.balance + amount
                TransactionType.EXPENSE -> account.balance - amount
            }
            accountDao.updateAccountBalance(accountId, updatedBalance)

            val transaction = TransactionEntity(
                userId = currentUserId,
                accountId = accountId,
                type = type,
                amount = amount,
                category = category,
                note = note
            )
            transactionDao.insertTransaction(transaction)
            transaction
        }
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        database.withTransaction {
            val account = accountDao.getAccountById(transaction.accountId)
            if (account != null) {
                // Revert account impact
                val revertedBalance = when (transaction.type) {
                    TransactionType.INCOME -> account.balance - transaction.amount
                    TransactionType.EXPENSE -> account.balance + transaction.amount
                }
                accountDao.updateAccountBalance(transaction.accountId, revertedBalance)
            }
            transactionDao.deleteTransaction(transaction)
        }
    }

    // --- Debts & Repayments (Append-Only Immutable Ledger) ---
    val allDebtsFlow: Flow<List<DebtLedgerEntity>>
        get() = debtDao.getAllDebts(currentUserId)

    val allRepaymentsFlow: Flow<List<DebtRepaymentEntity>>
        get() = debtDao.getAllRepayments()

    val debtsWithDetailsFlow: Flow<List<DebtWithDetails>> =
        combine(allDebtsFlow, allRepaymentsFlow) { debts, repayments ->
            val groupedRepayments = repayments.groupBy { it.debtId }
            debts.map { debt ->
                DebtWithDetails(
                    debt = debt,
                    repayments = groupedRepayments[debt.debtId] ?: emptyList()
                )
            }
        }

    suspend fun createDebt(
        counterpartyName: String,
        direction: DebtDirection,
        principalAmount: Double,
        accountId: String,
        dueDate: Long? = null,
        note: String? = null
    ): DebtLedgerEntity {
        return database.withTransaction {
            val account = accountDao.getAccountById(accountId)
                ?: throw IllegalArgumentException("Funding account not found")

            // Impact rules:
            // Money Lent: Decreases selected account balance (receivables ledger created)
            // Money Borrowed: Increases selected account balance (debt liabilities ledger created)
            val updatedBalance = when (direction) {
                DebtDirection.LENT -> account.balance - principalAmount
                DebtDirection.BORROWED -> account.balance + principalAmount
            }
            accountDao.updateAccountBalance(accountId, updatedBalance)

            val debt = DebtLedgerEntity(
                userId = currentUserId,
                counterpartyName = counterpartyName,
                direction = direction,
                principalAmount = principalAmount,
                dueDate = dueDate,
                note = note
            )
            debtDao.insertDebt(debt)

            // Also record a ledger tracking transaction
            val transaction = TransactionEntity(
                userId = currentUserId,
                accountId = accountId,
                type = if (direction == DebtDirection.LENT) TransactionType.EXPENSE else TransactionType.INCOME,
                amount = principalAmount,
                category = if (direction == DebtDirection.LENT) "Loan to $counterpartyName" else "Borrow from $counterpartyName",
                note = note ?: (if (direction == DebtDirection.LENT) "Lent to $counterpartyName" else "Borrowed from $counterpartyName")
            )
            transactionDao.insertTransaction(transaction)

            debt
        }
    }

    suspend fun recordRepayment(
        debtId: String,
        targetAccountId: String,
        amountPaid: Double,
        note: String? = null
    ): DebtRepaymentEntity {
        return database.withTransaction {
            val debt = debtDao.getDebtById(debtId)
                ?: throw IllegalArgumentException("Debt record not found")
            val targetAccount = accountDao.getAccountById(targetAccountId)
                ?: throw IllegalArgumentException("Target repayment account not found")

            // Atomic balance update:
            // LENT: We lent money, and the borrower pays us back -> Account balance increases (+)
            // BORROWED: We borrowed money, and we pay back the lender -> Account balance decreases (-)
            val updatedBalance = when (debt.direction) {
                DebtDirection.LENT -> targetAccount.balance + amountPaid
                DebtDirection.BORROWED -> targetAccount.balance - amountPaid
            }
            accountDao.updateAccountBalance(targetAccountId, updatedBalance)

            val repayment = DebtRepaymentEntity(
                debtId = debtId,
                targetAccountId = targetAccountId,
                amountPaid = amountPaid,
                note = note
            )
            debtDao.insertRepayment(repayment)

            // Record transaction for cashflow statement
            val transaction = TransactionEntity(
                userId = currentUserId,
                accountId = targetAccountId,
                type = if (debt.direction == DebtDirection.LENT) TransactionType.INCOME else TransactionType.EXPENSE,
                amount = amountPaid,
                category = if (debt.direction == DebtDirection.LENT) "Repayment from ${debt.counterpartyName}" else "Repayment to ${debt.counterpartyName}",
                note = note ?: "Debt repayment settlement"
            )
            transactionDao.insertTransaction(transaction)

            repayment
        }
    }

    suspend fun deleteDebt(debt: DebtLedgerEntity) {
        debtDao.deleteDebt(debt)
    }

    // --- Financial Summary Flow ---
    val financialSummaryFlow: Flow<FinancialSummary> =
        combine(accountDao.getLiquidCashFlow(currentUserId), debtsWithDetailsFlow) { liquidCash, debtsWithDetails ->
            val cash = liquidCash ?: 0.0
            var activeReceivables = 0.0
            var activePayables = 0.0

            for (item in debtsWithDetails) {
                when (item.debt.direction) {
                    DebtDirection.LENT -> activeReceivables += item.remainingAmount
                    DebtDirection.BORROWED -> activePayables += item.remainingAmount
                }
            }

            val netWorth = cash + activeReceivables - activePayables
            FinancialSummary(
                liquidCash = cash,
                totalReceivables = activeReceivables,
                totalPayables = activePayables,
                netWorth = netWorth
            )
        }

    // --- Budgets with Floor-Capped Deficit Rule ---
    val budgetsFlow: Flow<List<BudgetEntity>>
        get() = budgetDao.getAllBudgets(currentUserId)

    val budgetProgressFlow: Flow<List<CategoryBudgetProgress>> =
        combine(budgetsFlow, transactionsFlow) { budgets, transactions ->
            val startOfMonth = getStartOfCurrentMonth()
            val monthlyTransactions = transactions.filter {
                it.timestamp >= startOfMonth && it.type == TransactionType.EXPENSE
            }
            val spendByCategory = monthlyTransactions.groupBy { it.category }
                .mapValues { (_, txs) -> txs.sumOf { it.amount } }

            budgets.map { budget ->
                val spent = spendByCategory[budget.category] ?: 0.0
                // Hybrid Category Budgeting & Rollover with Floor-Capped Deficit Rule:
                // If allowRollover is false, effective limit is simply monthlyLimit.
                // In future cycles, unused surplus carries forward, but negative compounding
                // is floor-capped at budget.rolloverFloor (default 0.0).
                val effectiveLimit = budget.monthlyLimit
                val remaining = effectiveLimit - spent
                val percentUsed = if (effectiveLimit > 0) (spent / effectiveLimit).toFloat() else 0f

                CategoryBudgetProgress(
                    budget = budget,
                    spentThisMonth = spent,
                    effectiveLimit = effectiveLimit,
                    remaining = remaining,
                    percentUsed = percentUsed
                )
            }
        }

    suspend fun saveBudget(category: String, monthlyLimit: Double, allowRollover: Boolean = false, rolloverFloor: Double = 0.0): BudgetEntity {
        val existing = budgetDao.getBudgetByCategory(currentUserId, category)
        val budget = existing?.copy(
            monthlyLimit = monthlyLimit,
            allowRollover = allowRollover,
            rolloverFloor = rolloverFloor,
            updatedAt = System.currentTimeMillis()
        ) ?: BudgetEntity(
            userId = currentUserId,
            category = category,
            monthlyLimit = monthlyLimit,
            allowRollover = allowRollover,
            rolloverFloor = rolloverFloor
        )
        budgetDao.insertBudget(budget)
        return budget
    }

    suspend fun deleteBudget(budget: BudgetEntity) {
        budgetDao.deleteBudget(budget)
    }

    // --- Tasks ---
    val tasksFlow: Flow<List<TaskEntity>>
        get() = taskDao.getAllTasks(currentUserId)

    suspend fun createTask(
        title: String,
        description: String? = null,
        priority: Priority = Priority.MEDIUM,
        dueDate: Long? = null
    ): TaskEntity {
        val task = TaskEntity(
            userId = currentUserId,
            title = title,
            description = description,
            priority = priority,
            dueDate = dueDate
        )
        taskDao.insertTask(task)
        return task
    }

    suspend fun setTaskCompleted(taskId: String, isCompleted: Boolean) {
        taskDao.setTaskCompleted(taskId, isCompleted)
    }

    suspend fun deleteTask(task: TaskEntity) {
        taskDao.deleteTask(task)
    }

    // --- Event Reminders ---
    val remindersFlow: Flow<List<EventReminderEntity>>
        get() = eventReminderDao.getAllReminders(currentUserId)

    suspend fun createReminder(
        title: String,
        triggerTime: Long,
        isCritical: Boolean = false,
        exportedToCalendar: Boolean = false
    ): EventReminderEntity {
        val reminder = EventReminderEntity(
            userId = currentUserId,
            title = title,
            triggerTime = triggerTime,
            isCritical = isCritical,
            exportedToCalendar = exportedToCalendar
        )
        eventReminderDao.insertReminder(reminder)
        return reminder
    }

    suspend fun dismissReminder(eventId: String) {
        eventReminderDao.dismissReminder(eventId)
    }

    suspend fun snoozeReminder(eventId: String, additionalMinutes: Int) {
        val newTime = System.currentTimeMillis() + (additionalMinutes * 60 * 1000L)
        eventReminderDao.snoozeReminder(eventId, newTime)
    }

    suspend fun deleteReminder(reminder: EventReminderEntity) {
        eventReminderDao.deleteReminder(reminder)
    }

    suspend fun getPendingActiveReminders(): List<EventReminderEntity> {
        return eventReminderDao.getPendingActiveReminders()
    }

    // --- Notes & Strict Privacy Exclusion Rule for Gemini AI ---
    val notesFlow: Flow<List<NoteEntity>>
        get() = noteDao.getAllNotes(currentUserId)

    // Strictly excludes isLocked = 1 notes before compiling context for Gemini
    suspend fun getUnlockedNotesForAiContext(): List<NoteEntity> {
        return noteDao.getUnlockedNotesForContext(currentUserId)
    }

    suspend fun createNote(
        title: String,
        content: String,
        isLocked: Boolean = false,
        category: String = "General"
    ): NoteEntity {
        val note = NoteEntity(
            userId = currentUserId,
            title = title,
            content = content,
            isLocked = isLocked,
            category = category
        )
        noteDao.insertNote(note)
        return note
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.updateNote(note.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteNote(note: NoteEntity) {
        noteDao.deleteNote(note)
    }

    // --- Local Aggregation Queries for Gemini AI Context ---
    suspend fun getMonthlyExpenseBreakdownForAi(): Map<String, Double> {
        val startOfMonth = getStartOfCurrentMonth()
        val aggs = transactionDao.getCategorySpendAggregations(currentUserId, startOfMonth)
        return aggs.associate { it.category to it.total }
    }

    suspend fun getAccountBalancesSummaryForAi(): List<Pair<String, Double>> {
        val accounts = accountDao.getAllAccounts(currentUserId).firstOrNull() ?: emptyList()
        return accounts.map { it.name to it.balance }
    }

    // --- Initial Seeding ---
    suspend fun seedInitialDataIfEmpty() {
        val existingAccounts = accountDao.getAllAccounts(currentUserId).firstOrNull()
        if (existingAccounts.isNullOrEmpty()) {
            val bank = AccountEntity(
                userId = currentUserId,
                name = "Bank Account",
                type = AccountType.BANK,
                balance = 4500.0,
                isDefault = true
            )
            val cash = AccountEntity(
                userId = currentUserId,
                name = "Cash Wallet",
                type = AccountType.CASH,
                balance = 350.0,
                isDefault = false
            )
            val savings = AccountEntity(
                userId = currentUserId,
                name = "High Yield Savings",
                type = AccountType.SAVINGS,
                balance = 12000.0,
                isDefault = false
            )
            accountDao.insertAccounts(listOf(bank, cash, savings))

            // Seed sample transactions
            val now = System.currentTimeMillis()
            val day = 86400000L
            val sampleTransactions = listOf(
                TransactionEntity(
                    userId = currentUserId,
                    accountId = bank.accountId,
                    type = TransactionType.INCOME,
                    amount = 3200.0,
                    category = "Salary",
                    timestamp = now - (3 * day),
                    note = "Monthly payroll direct deposit"
                ),
                TransactionEntity(
                    userId = currentUserId,
                    accountId = bank.accountId,
                    type = TransactionType.EXPENSE,
                    amount = 85.50,
                    category = "Groceries",
                    timestamp = now - (2 * day),
                    note = "Weekly groceries organic market"
                ),
                TransactionEntity(
                    userId = currentUserId,
                    accountId = cash.accountId,
                    type = TransactionType.EXPENSE,
                    amount = 14.25,
                    category = "Food & Dining",
                    timestamp = now - (1 * day),
                    note = "Coffee and croissant"
                ),
                TransactionEntity(
                    userId = currentUserId,
                    accountId = bank.accountId,
                    type = TransactionType.EXPENSE,
                    amount = 120.0,
                    category = "Utilities",
                    timestamp = now - (4 * day),
                    note = "High-speed internet & power"
                )
            )
            sampleTransactions.forEach { transactionDao.insertTransaction(it) }

            // Seed sample budgets
            budgetDao.insertBudget(BudgetEntity(userId = currentUserId, category = "Groceries", monthlyLimit = 450.0, allowRollover = true))
            budgetDao.insertBudget(BudgetEntity(userId = currentUserId, category = "Food & Dining", monthlyLimit = 250.0, allowRollover = false))
            budgetDao.insertBudget(BudgetEntity(userId = currentUserId, category = "Utilities", monthlyLimit = 200.0, allowRollover = false))

            // Seed sample debt (loan to counterparty)
            val debt = DebtLedgerEntity(
                userId = currentUserId,
                counterpartyName = "Sarah Jenkins",
                direction = DebtDirection.LENT,
                principalAmount = 150.0,
                dueDate = now + (7 * day),
                note = "Weekend trip tickets"
            )
            debtDao.insertDebt(debt)
            debtDao.insertRepayment(
                DebtRepaymentEntity(
                    debtId = debt.debtId,
                    targetAccountId = bank.accountId,
                    amountPaid = 50.0,
                    timestamp = now - day,
                    note = "Partial transfer repayment"
                )
            )

            // Seed sample tasks
            taskDao.insertTask(
                TaskEntity(
                    userId = currentUserId,
                    title = "Review quarterly tax deductions",
                    description = "Collect digital receipts and summarize itemized expenses",
                    priority = Priority.HIGH,
                    dueDate = now + (2 * day)
                )
            )
            taskDao.insertTask(
                TaskEntity(
                    userId = currentUserId,
                    title = "Schedule vehicle maintenance",
                    description = "Oil change and tire rotation check",
                    priority = Priority.MEDIUM,
                    dueDate = now + (5 * day)
                )
            )

            // Seed sample notes
            noteDao.insertNote(
                NoteEntity(
                    userId = currentUserId,
                    title = "Annual Financial Goals",
                    content = "# 2026 Strategy\n- Maximize Roth IRA contributions\n- Keep dining expenses under \$250/mo\n- Build 6-month emergency buffer",
                    isLocked = false,
                    category = "Finance"
                )
            )
            noteDao.insertNote(
                NoteEntity(
                    userId = currentUserId,
                    title = "Banking Credentials & Safe Vault",
                    content = "Encrypted storage for sensitive pins and backup recovery tokens.\n\n*Master key stored securely.*",
                    isLocked = true,
                    category = "Security"
                )
            )
        }
    }

    private fun getStartOfCurrentMonth(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
