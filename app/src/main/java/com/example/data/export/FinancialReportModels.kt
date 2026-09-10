package com.example.data.export

import com.example.data.local.entity.AccountType
import com.example.data.local.entity.DebtDirection
import com.example.data.local.entity.TransactionType

enum class ReportPeriodType(val displayName: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    ALL_TIME("All Time")
}

enum class FinancialActivityScope(val displayName: String, val description: String) {
    FULL_STATEMENT("Full Financial Statement", "Executive summary KPIs, income vs. expense, debt ledger snapshot & transactions"),
    TRANSACTIONS_ONLY("Transaction Activities", "Itemized income and expense activity logs with accounts, categories & notes"),
    DEBT_ACTIVITIES("Debt Ledger Activities", "Receivables (lent) & payables (borrowed) with counterparty repayment history"),
    ACCOUNTS_PORTFOLIO("Account Portfolio & Balances", "Cash pools, bank balances, savings breakdown & liquidity status")
}

enum class WeeklyPreset(val displayName: String, val daysAgoStart: Int, val daysAgoEnd: Int) {
    CURRENT_WEEK("Current Week (Past 7 Days)", 6, 0),
    PREVIOUS_WEEK("Previous Week (Days 7–14)", 13, 7),
    PAST_14_DAYS("Past 14 Days", 13, 0),
    PAST_30_DAYS("Past 30 Days", 29, 0)
}

enum class ReportFormat(
    val extension: String,
    val mimeType: String,
    val title: String,
    val shortLabel: String,
    val description: String
) {
    PDF(
        extension = "pdf",
        mimeType = "application/pdf",
        title = "PDF Financial Statement (.pdf)",
        shortLabel = "PDF",
        description = "Executive paginated report with corporate header, KPIs, tables & signature block"
    ),
    EXCEL_XLSX(
        extension = "xlsx",
        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        title = "Microsoft Excel Workbook (.xlsx)",
        shortLabel = "Excel (.xlsx)",
        description = "Native OpenXML workbook with formatted columns, headers & auto-calculated SUM formulas"
    ),
    CSV(
        extension = "csv",
        mimeType = "text/csv",
        title = "Excel Comma-Separated (.csv)",
        shortLabel = "Excel (.csv)",
        description = "Standard CSV formatted with UTF-8 BOM encoding for seamless Excel import"
    ),
    GOOGLE_SHEETS(
        extension = "csv",
        mimeType = "application/vnd.google-apps.spreadsheet",
        title = "Google Sheets Export",
        shortLabel = "Google Sheets",
        description = "Formatted spreadsheet ready for Google Sheets app, Drive sync & instant clipboard paste"
    )
}

data class CategoryAllocation(
    val category: String,
    val amount: Double,
    val percentage: Double
)

data class TransactionReportItem(
    val timestamp: Long,
    val dateFormatted: String,
    val accountName: String,
    val category: String,
    val type: TransactionType,
    val amount: Double,
    val note: String?
)

data class DebtReportItem(
    val debtId: String,
    val counterpartyName: String,
    val direction: DebtDirection,
    val principalAmount: Double,
    val totalRepaid: Double,
    val remainingBalance: Double,
    val dueDateFormatted: String?,
    val isSettled: Boolean,
    val note: String?
)

data class AccountReportItem(
    val accountId: String,
    val name: String,
    val type: AccountType,
    val balance: Double,
    val isDefault: Boolean
)

data class FinancialReportData(
    val title: String,
    val scope: FinancialActivityScope,
    val periodType: ReportPeriodType,
    val dateRangeLabel: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val generatedAtMillis: Long,
    val currencyCode: String,
    val totalIncome: Double,
    val totalExpense: Double,
    val netSavings: Double,
    val totalReceivablesLent: Double,
    val totalPayablesBorrowed: Double,
    val categoryAllocations: List<CategoryAllocation>,
    val itemizedTransactions: List<TransactionReportItem>,
    val debtsList: List<DebtReportItem> = emptyList(),
    val accountsList: List<AccountReportItem> = emptyList()
)
