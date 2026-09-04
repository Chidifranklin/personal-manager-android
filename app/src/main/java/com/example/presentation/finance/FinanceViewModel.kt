package com.example.presentation.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.DebtDirection
import com.example.data.local.entity.DebtLedgerEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.model.CategoryBudgetProgress
import com.example.data.model.DebtWithDetails
import com.example.data.model.FinancialSummary
import com.example.data.preferences.PreferenceManager
import com.example.data.repository.PersonalManagerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FinanceUiState(
    val financialSummary: FinancialSummary = FinancialSummary(),
    val accounts: List<AccountEntity> = emptyList(),
    val defaultAccount: AccountEntity? = null,
    val transactions: List<TransactionEntity> = emptyList(),
    val debtsWithDetails: List<DebtWithDetails> = emptyList(),
    val budgetProgress: List<CategoryBudgetProgress> = emptyList(),
    val currencyCode: String = PreferenceManager.getDefaultCurrencyCode()
)

class FinanceViewModel(
    private val repository: PersonalManagerRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    val uiState: StateFlow<FinanceUiState> = combine(
        repository.financialSummaryFlow,
        repository.accountsFlow,
        repository.defaultAccountFlow,
        repository.transactionsFlow,
        repository.debtsWithDetailsFlow,
        repository.budgetProgressFlow,
        preferenceManager.currencyCodeFlow
    ) { args: Array<Any?> ->
        FinanceUiState(
            financialSummary = args[0] as FinancialSummary,
            accounts = args[1] as List<AccountEntity>,
            defaultAccount = args[2] as? AccountEntity,
            transactions = args[3] as List<TransactionEntity>,
            debtsWithDetails = args[4] as List<DebtWithDetails>,
            budgetProgress = args[5] as List<CategoryBudgetProgress>,
            currencyCode = args[6] as String
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinanceUiState()
    )

    fun setCurrencyCode(currencyCode: String) {
        viewModelScope.launch {
            preferenceManager.setCurrencyCode(currencyCode)
        }
    }

    fun createAccount(name: String, type: AccountType, balance: Double, isDefault: Boolean) {
        viewModelScope.launch {
            repository.createAccount(name, type, balance, isDefault)
        }
    }

    fun setDefaultAccount(accountId: String) {
        viewModelScope.launch {
            repository.setDefaultAccount(accountId)
        }
    }

    fun logTransaction(accountId: String, type: TransactionType, amount: Double, category: String, note: String?) {
        viewModelScope.launch {
            repository.logTransaction(accountId, type, amount, category, note)
        }
    }

    fun createDebt(
        counterparty: String,
        direction: DebtDirection,
        amount: Double,
        accountId: String,
        dueDate: Long?,
        note: String?
    ) {
        viewModelScope.launch {
            repository.createDebt(counterparty, direction, amount, accountId, dueDate, note)
        }
    }

    fun recordRepayment(debtId: String, targetAccountId: String, amountPaid: Double, note: String?) {
        viewModelScope.launch {
            repository.recordRepayment(debtId, targetAccountId, amountPaid, note)
        }
    }

    fun setBudget(category: String, limit: Double, allowRollover: Boolean, rolloverFloor: Double) {
        viewModelScope.launch {
            repository.saveBudget(category, limit, allowRollover, rolloverFloor)
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun deleteDebt(debt: DebtLedgerEntity) {
        viewModelScope.launch {
            repository.deleteDebt(debt)
        }
    }
}
