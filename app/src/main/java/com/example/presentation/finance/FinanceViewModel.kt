package com.example.presentation.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.data.export.FinancialReportData
import com.example.data.export.FinancialReportExportService
import com.example.data.export.ReportFormat
import com.example.data.export.ReportPeriodType
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val preferenceManager: PreferenceManager,
    private val exportService: FinancialReportExportService = FinancialReportExportService(repository)
) : ViewModel() {

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

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

    fun generateAndShareReport(
        context: Context,
        scope: com.example.data.export.FinancialActivityScope = com.example.data.export.FinancialActivityScope.FULL_STATEMENT,
        periodType: ReportPeriodType,
        format: ReportFormat,
        startTimestamp: Long,
        endTimestamp: Long,
        dateRangeLabel: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (_isExporting.value) return
        _isExporting.value = true
        viewModelScope.launch {
            try {
                val reportData = exportService.prepareReportData(
                    startTimestamp = startTimestamp,
                    endTimestamp = endTimestamp,
                    periodType = periodType,
                    dateRangeLabel = dateRangeLabel,
                    currencyCode = uiState.value.currencyCode,
                    scope = scope
                )
                val file = exportService.generateAndSaveReport(context, reportData, format)
                exportService.shareReportFile(context, file, format, dateRangeLabel)
                _isExporting.value = false
                onComplete(true, null)
            } catch (e: Exception) {
                _isExporting.value = false
                onComplete(false, e.message ?: "Failed to generate report")
            }
        }
    }

    fun copyReportForGoogleSheets(
        context: Context,
        scope: com.example.data.export.FinancialActivityScope = com.example.data.export.FinancialActivityScope.FULL_STATEMENT,
        periodType: ReportPeriodType,
        startTimestamp: Long,
        endTimestamp: Long,
        dateRangeLabel: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val reportData = exportService.prepareReportData(
                    startTimestamp = startTimestamp,
                    endTimestamp = endTimestamp,
                    periodType = periodType,
                    dateRangeLabel = dateRangeLabel,
                    currencyCode = uiState.value.currencyCode,
                    scope = scope
                )
                val copied = exportService.copyTsvForGoogleSheets(context, reportData)
                if (copied) {
                    onComplete(true, "Copied formatted financial data to clipboard! Open Google Sheets and paste.")
                } else {
                    onComplete(false, "Failed to copy to clipboard.")
                }
            } catch (e: Exception) {
                onComplete(false, e.message ?: "Failed to copy report data")
            }
        }
    }

    fun openGoogleSheets(context: Context) {
        exportService.openGoogleSheetsAppOrWeb(context)
    }
}
