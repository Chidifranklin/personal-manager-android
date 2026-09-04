package com.example.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.EventReminderEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.model.CategoryBudgetProgress
import com.example.data.model.DebtWithDetails
import com.example.data.model.FinancialSummary
import com.example.data.repository.PersonalManagerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val financialSummary: FinancialSummary = FinancialSummary(),
    val accounts: List<AccountEntity> = emptyList(),
    val defaultAccount: AccountEntity? = null,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val pendingTasks: List<TaskEntity> = emptyList(),
    val upcomingReminders: List<EventReminderEntity> = emptyList(),
    val debtsWithDetails: List<DebtWithDetails> = emptyList(),
    val budgetProgress: List<CategoryBudgetProgress> = emptyList()
)

class DashboardViewModel(private val repository: PersonalManagerRepository) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.financialSummaryFlow,
        repository.accountsFlow,
        repository.defaultAccountFlow,
        repository.recentTransactionsFlow,
        repository.tasksFlow,
        repository.remindersFlow,
        repository.debtsWithDetailsFlow,
        repository.budgetProgressFlow
    ) { args: Array<Any?> ->
        val summary = args[0] as FinancialSummary
        val accounts = args[1] as List<AccountEntity>
        val defaultAccount = args[2] as? AccountEntity
        val recentTxs = args[3] as List<TransactionEntity>
        val tasks = args[4] as List<TaskEntity>
        val reminders = args[5] as List<EventReminderEntity>
        val debts = args[6] as List<DebtWithDetails>
        val budgets = args[7] as List<CategoryBudgetProgress>

        DashboardUiState(
            financialSummary = summary,
            accounts = accounts,
            defaultAccount = defaultAccount,
            recentTransactions = recentTxs,
            pendingTasks = tasks.filter { !it.isCompleted }.take(5),
            upcomingReminders = reminders.filter { !it.isDismissed && it.triggerTime > System.currentTimeMillis() }.take(5),
            debtsWithDetails = debts,
            budgetProgress = budgets
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun toggleTaskCompletion(taskId: String, completed: Boolean) {
        viewModelScope.launch {
            repository.setTaskCompleted(taskId, completed)
        }
    }

    fun dismissReminder(eventId: String) {
        viewModelScope.launch {
            repository.dismissReminder(eventId)
        }
    }
}
