package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.DebtDirection
import com.example.data.local.entity.DebtLedgerEntity
import com.example.data.local.entity.DebtRepaymentEntity
import com.example.data.local.entity.EventReminderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY isDefault DESC, name ASC")
    fun getAllAccounts(userId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY isDefault DESC, name ASC")
    suspend fun getAllAccountsList(userId: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE userId = :userId AND isDefault = 1 LIMIT 1")
    fun getDefaultAccount(userId: String): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE accountId = :accountId LIMIT 1")
    suspend fun getAccountById(accountId: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET balance = :newBalance, updatedAt = :updatedAt WHERE accountId = :accountId")
    suspend fun updateAccountBalance(accountId: String, newBalance: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE accounts SET isDefault = 0 WHERE userId = :userId")
    suspend fun clearDefault(userId: String)

    @Query("UPDATE accounts SET isDefault = 1 WHERE accountId = :accountId AND userId = :userId")
    suspend fun setDefault(userId: String, accountId: String)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("SELECT SUM(balance) FROM accounts WHERE userId = :userId")
    fun getLiquidCashFlow(userId: String): Flow<Double?>
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllTransactions(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(userId: String, limit: Int = 10): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND timestamp >= :startTimestamp AND timestamp <= :endTimestamp ORDER BY timestamp DESC")
    fun getTransactionsByPeriod(userId: String, startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND timestamp >= :startTimestamp AND timestamp <= :endTimestamp ORDER BY timestamp DESC")
    suspend fun getTransactionsByPeriodList(userId: String, startTimestamp: Long, endTimestamp: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND category = :category AND timestamp >= :startTimestamp AND timestamp <= :endTimestamp")
    suspend fun getTransactionsForCategoryPeriod(userId: String, category: String, startTimestamp: Long, endTimestamp: Long): List<TransactionEntity>

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE userId = :userId AND type = 'EXPENSE' AND timestamp >= :startTimestamp GROUP BY category")
    suspend fun getCategorySpendAggregations(userId: String, startTimestamp: Long): List<CategorySpend>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)
}

data class CategorySpend(
    val category: String,
    val total: Double
)

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE userId = :userId ORDER BY category ASC")
    fun getAllBudgets(userId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE userId = :userId AND category = :category LIMIT 1")
    suspend fun getBudgetByCategory(userId: String, category: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Delete
    suspend fun deleteBudget(budget: BudgetEntity)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debt_ledger WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllDebts(userId: String): Flow<List<DebtLedgerEntity>>

    @Query("SELECT * FROM debt_ledger WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getAllDebtsList(userId: String): List<DebtLedgerEntity>

    @Query("SELECT * FROM debt_ledger WHERE debtId = :debtId LIMIT 1")
    suspend fun getDebtById(debtId: String): DebtLedgerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtLedgerEntity)

    @Query("SELECT * FROM debt_repayments WHERE debtId = :debtId ORDER BY timestamp DESC")
    fun getRepaymentsForDebt(debtId: String): Flow<List<DebtRepaymentEntity>>

    @Query("SELECT * FROM debt_repayments ORDER BY timestamp DESC")
    fun getAllRepayments(): Flow<List<DebtRepaymentEntity>>

    @Query("SELECT * FROM debt_repayments ORDER BY timestamp DESC")
    suspend fun getAllRepaymentsList(): List<DebtRepaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepayment(repayment: DebtRepaymentEntity)

    @Delete
    suspend fun deleteDebt(debt: DebtLedgerEntity)

    @Delete
    suspend fun deleteRepayment(repayment: DebtRepaymentEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE userId = :userId ORDER BY isCompleted ASC, priority DESC, dueDate ASC, updatedAt DESC")
    fun getAllTasks(userId: String): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET isCompleted = :isCompleted, updatedAt = :updatedAt WHERE taskId = :taskId")
    suspend fun setTaskCompleted(taskId: String, isCompleted: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteTask(task: TaskEntity)
}

@Dao
interface EventReminderDao {
    @Query("SELECT * FROM event_reminders WHERE userId = :userId ORDER BY triggerTime ASC")
    fun getAllReminders(userId: String): Flow<List<EventReminderEntity>>

    @Query("SELECT * FROM event_reminders WHERE triggerTime > :now AND isDismissed = 0 ORDER BY triggerTime ASC")
    suspend fun getPendingActiveReminders(now: Long = System.currentTimeMillis()): List<EventReminderEntity>

    @Query("SELECT * FROM event_reminders WHERE eventId = :eventId LIMIT 1")
    suspend fun getReminderById(eventId: String): EventReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: EventReminderEntity)

    @Update
    suspend fun updateReminder(reminder: EventReminderEntity)

    @Query("UPDATE event_reminders SET isDismissed = 1, updatedAt = :updatedAt WHERE eventId = :eventId")
    suspend fun dismissReminder(eventId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE event_reminders SET triggerTime = :newTime, isDismissed = 0, updatedAt = :updatedAt WHERE eventId = :eventId")
    suspend fun snoozeReminder(eventId: String, newTime: Long, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteReminder(reminder: EventReminderEntity)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE userId = :userId ORDER BY updatedAt DESC")
    fun getAllNotes(userId: String): Flow<List<NoteEntity>>

    // Strict Privacy Exclusion Rule: for Gemini AI prompts, strictly filter WHERE isLocked = 0
    @Query("SELECT * FROM notes WHERE userId = :userId AND isLocked = 0 ORDER BY updatedAt DESC")
    suspend fun getUnlockedNotesForContext(userId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE noteId = :noteId LIMIT 1")
    suspend fun getNoteById(noteId: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)
}
