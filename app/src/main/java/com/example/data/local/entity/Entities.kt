package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class AccountType { CASH, BANK, SAVINGS }
enum class TransactionType { INCOME, EXPENSE }
enum class DebtDirection { LENT, BORROWED }
enum class Priority { LOW, MEDIUM, HIGH }
enum class SyncStatus { SYNCED, DIRTY_LOCAL }

// Accounts (Liquid Pools)
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val accountId: String = UUID.randomUUID().toString(),
    val userId: String,
    val name: String,
    val type: AccountType,
    val balance: Double,
    val isDefault: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.DIRTY_LOCAL,
    val updatedAt: Long = System.currentTimeMillis()
)

// Transactions (Income / Expense)
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("accountId")]
)
data class TransactionEntity(
    @PrimaryKey val transactionId: String = UUID.randomUUID().toString(),
    val userId: String,
    val accountId: String,
    val type: TransactionType,
    val amount: Double,
    val category: String,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,
    val syncStatus: SyncStatus = SyncStatus.DIRTY_LOCAL
)

// Budgets & Hybrid Rollover
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val budgetId: String = UUID.randomUUID().toString(),
    val userId: String,
    val category: String,
    val monthlyLimit: Double,
    val allowRollover: Boolean = false,
    val rolloverFloor: Double = 0.0, // Prevents compounding negative balances
    val syncStatus: SyncStatus = SyncStatus.DIRTY_LOCAL,
    val updatedAt: Long = System.currentTimeMillis()
)

// Immutable Debt Ledger Root
@Entity(tableName = "debt_ledger")
data class DebtLedgerEntity(
    @PrimaryKey val debtId: String = UUID.randomUUID().toString(),
    val userId: String,
    val counterpartyName: String,
    val direction: DebtDirection,
    val principalAmount: Double,
    val dueDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String? = null,
    val syncStatus: SyncStatus = SyncStatus.DIRTY_LOCAL
)

// Append-Only Debt Repayments
@Entity(
    tableName = "debt_repayments",
    foreignKeys = [
        ForeignKey(
            entity = DebtLedgerEntity::class,
            parentColumns = ["debtId"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["targetAccountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("debtId"), Index("targetAccountId")]
)
data class DebtRepaymentEntity(
    @PrimaryKey val repaymentId: String = UUID.randomUUID().toString(),
    val debtId: String,
    val targetAccountId: String,
    val amountPaid: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,
    val syncStatus: SyncStatus = SyncStatus.DIRTY_LOCAL
)

// Tasks
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val taskId: String = UUID.randomUUID().toString(),
    val userId: String,
    val title: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val priority: Priority = Priority.MEDIUM,
    val dueDate: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.DIRTY_LOCAL,
    val updatedAt: Long = System.currentTimeMillis()
)

// Event Reminders & Appointments
@Entity(tableName = "event_reminders")
data class EventReminderEntity(
    @PrimaryKey val eventId: String = UUID.randomUUID().toString(),
    val userId: String,
    val title: String,
    val triggerTime: Long,
    val isCritical: Boolean = false,
    val exportedToCalendar: Boolean = false,
    val isDismissed: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.DIRTY_LOCAL,
    val updatedAt: Long = System.currentTimeMillis()
)

// Notes
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val noteId: String = UUID.randomUUID().toString(),
    val userId: String,
    val title: String,
    val content: String,
    val isLocked: Boolean = false,
    val category: String = "General",
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.DIRTY_LOCAL
)
