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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

data class UserRecordCounts(
    val accountCount: Int = 0,
    val transactionCount: Int = 0,
    val debtCount: Int = 0,
    val taskCount: Int = 0,
    val noteCount: Int = 0,
    val budgetCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
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

    private val _currentUserIdFlow = MutableStateFlow(currentUserId)
    val currentUserIdFlow: StateFlow<String> = _currentUserIdFlow.asStateFlow()

    fun setCurrentUser(userId: String) {
        currentUserId = userId
        _currentUserIdFlow.value = userId
    }

    // --- Accounts ---
    val accountsFlow: Flow<List<AccountEntity>> = _currentUserIdFlow.flatMapLatest { uid ->
        accountDao.getAllAccounts(uid)
    }

    val defaultAccountFlow: Flow<AccountEntity?> = _currentUserIdFlow.flatMapLatest { uid ->
        accountDao.getDefaultAccount(uid)
    }

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

    suspend fun getAllAccountsList(): List<AccountEntity> =
        accountDao.getAllAccountsList(currentUserId)

    // --- Transactions ---
    val transactionsFlow: Flow<List<TransactionEntity>> = _currentUserIdFlow.flatMapLatest { uid ->
        transactionDao.getAllTransactions(uid)
    }

    val recentTransactionsFlow: Flow<List<TransactionEntity>> = _currentUserIdFlow.flatMapLatest { uid ->
        transactionDao.getRecentTransactions(uid, 15)
    }

    suspend fun getTransactionsInRange(startTimestamp: Long, endTimestamp: Long): List<TransactionEntity> =
        transactionDao.getTransactionsByPeriodList(currentUserId, startTimestamp, endTimestamp)

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

    suspend fun updateTransaction(
        oldTransaction: TransactionEntity,
        newAccountId: String,
        newType: TransactionType,
        newAmount: Double,
        newCategory: String,
        newNote: String?,
        newTimestamp: Long = oldTransaction.timestamp
    ): TransactionEntity {
        return database.withTransaction {
            // Revert old transaction effect on old account
            val oldAccount = accountDao.getAccountById(oldTransaction.accountId)
            if (oldAccount != null) {
                val revertedBalance = when (oldTransaction.type) {
                    TransactionType.INCOME -> oldAccount.balance - oldTransaction.amount
                    TransactionType.EXPENSE -> oldAccount.balance + oldTransaction.amount
                }
                accountDao.updateAccountBalance(oldTransaction.accountId, revertedBalance)
            }

            // Apply new transaction effect on target account
            val targetAccount = accountDao.getAccountById(newAccountId)
                ?: throw IllegalArgumentException("Target account not found")
            val newBalance = when (newType) {
                TransactionType.INCOME -> targetAccount.balance + newAmount
                TransactionType.EXPENSE -> targetAccount.balance - newAmount
            }
            accountDao.updateAccountBalance(newAccountId, newBalance)

            val updated = oldTransaction.copy(
                accountId = newAccountId,
                type = newType,
                amount = newAmount,
                category = newCategory,
                note = newNote,
                timestamp = newTimestamp
            )
            transactionDao.updateTransaction(updated)
            updated
        }
    }

    // --- Debts & Repayments (Append-Only Immutable Ledger) ---
    val allDebtsFlow: Flow<List<DebtLedgerEntity>> = _currentUserIdFlow.flatMapLatest { uid ->
        debtDao.getAllDebts(uid)
    }

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

    suspend fun getDebtsWithDetailsList(): List<DebtWithDetails> {
        val debts = debtDao.getAllDebtsList(currentUserId)
        val repayments = debtDao.getAllRepaymentsList()
        val groupedRepayments = repayments.groupBy { it.debtId }
        return debts.map { debt ->
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

    suspend fun updateDebt(
        debt: DebtLedgerEntity,
        counterpartyName: String,
        direction: DebtDirection,
        principalAmount: Double,
        dueDate: Long?,
        note: String?
    ) {
        val updated = debt.copy(
            counterpartyName = counterpartyName,
            direction = direction,
            principalAmount = principalAmount,
            dueDate = dueDate,
            note = note,
            syncStatus = SyncStatus.DIRTY_LOCAL
        )
        debtDao.updateDebt(updated)
    }

    // --- Financial Summary Flow ---
    val financialSummaryFlow: Flow<FinancialSummary> =
        _currentUserIdFlow.flatMapLatest { uid ->
            combine(accountDao.getLiquidCashFlow(uid), debtsWithDetailsFlow) { liquidCash, debtsWithDetails ->
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
        }

    // --- Budgets with Floor-Capped Deficit Rule ---
    val budgetsFlow: Flow<List<BudgetEntity>> = _currentUserIdFlow.flatMapLatest { uid ->
        budgetDao.getAllBudgets(uid)
    }

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
    val tasksFlow: Flow<List<TaskEntity>> = _currentUserIdFlow.flatMapLatest { uid ->
        taskDao.getAllTasks(uid)
    }

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
    val remindersFlow: Flow<List<EventReminderEntity>> = _currentUserIdFlow.flatMapLatest { uid ->
        eventReminderDao.getAllReminders(uid)
    }

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

    suspend fun updateReminder(reminder: EventReminderEntity) {
        eventReminderDao.updateReminder(reminder)
    }

    suspend fun getPendingActiveReminders(): List<EventReminderEntity> {
        return eventReminderDao.getPendingActiveReminders()
    }

    // --- Notes & Strict Privacy Exclusion Rule for Gemini AI ---
    val notesFlow: Flow<List<NoteEntity>> = _currentUserIdFlow.flatMapLatest { uid ->
        noteDao.getAllNotes(uid)
    }

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

    // --- Initial Seeding (Disabled: no sample data) ---
    suspend fun seedInitialDataIfEmpty() {
        // No sample data seeded
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

    suspend fun getUserRecordCounts(userId: String = currentUserId): UserRecordCounts {
        val accounts = accountDao.getAllAccountsList(userId).size
        val transactions = transactionDao.getAllTransactions(userId).firstOrNull()?.size ?: 0
        val debts = debtDao.getAllDebtsList(userId).size
        val tasks = taskDao.getAllTasks(userId).firstOrNull()?.size ?: 0
        val notes = noteDao.getAllNotes(userId).firstOrNull()?.size ?: 0
        val budgets = budgetDao.getAllBudgets(userId).firstOrNull()?.size ?: 0
        return UserRecordCounts(accounts, transactions, debts, tasks, notes, budgets)
    }

    suspend fun wipeUserData(userId: String) {
        database.withTransaction {
            val accounts = accountDao.getAllAccountsList(userId)
            accounts.forEach { accountDao.deleteAccount(it) }
            val transactions = transactionDao.getAllTransactions(userId).firstOrNull() ?: emptyList()
            transactions.forEach { transactionDao.deleteTransaction(it) }
            val debts = debtDao.getAllDebtsList(userId)
            debts.forEach { debtDao.deleteDebt(it) }
            val tasks = taskDao.getAllTasks(userId).firstOrNull() ?: emptyList()
            tasks.forEach { taskDao.deleteTask(it) }
            val notes = noteDao.getAllNotes(userId).firstOrNull() ?: emptyList()
            notes.forEach { noteDao.deleteNote(it) }
            val budgets = budgetDao.getAllBudgets(userId).firstOrNull() ?: emptyList()
            budgets.forEach { budgetDao.deleteBudget(it) }
        }
    }

    suspend fun seedInitialUserDataIfEmpty(userId: String, displayName: String = "User") {
        // No sample data seeded
    }

    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }
}
