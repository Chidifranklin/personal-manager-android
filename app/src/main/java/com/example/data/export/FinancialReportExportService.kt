package com.example.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.local.entity.DebtDirection
import com.example.data.local.entity.TransactionType
import com.example.data.repository.PersonalManagerRepository
import com.example.util.CurrencyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FinancialReportExportService(
    private val repository: PersonalManagerRepository
) {

    suspend fun prepareReportData(
        startTimestamp: Long,
        endTimestamp: Long,
        periodType: ReportPeriodType,
        dateRangeLabel: String,
        currencyCode: String,
        scope: FinancialActivityScope = FinancialActivityScope.FULL_STATEMENT
    ): FinancialReportData = withContext(Dispatchers.IO) {
        val transactions = repository.getTransactionsInRange(startTimestamp, endTimestamp)
        val accountsMap = repository.getAllAccountsList().associateBy { it.accountId }
        val debts = repository.getDebtsWithDetailsList()

        var totalIncome = 0.0
        var totalExpense = 0.0

        val expenseCategoryMap = mutableMapOf<String, Double>()

        val transactionReportItems = transactions.map { tx ->
            when (tx.type) {
                TransactionType.INCOME -> totalIncome += tx.amount
                TransactionType.EXPENSE -> {
                    totalExpense += tx.amount
                    val currentCatTotal = expenseCategoryMap[tx.category] ?: 0.0
                    expenseCategoryMap[tx.category] = currentCatTotal + tx.amount
                }
            }

            val accountName = accountsMap[tx.accountId]?.name ?: "Account"
            val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(tx.timestamp))

            TransactionReportItem(
                timestamp = tx.timestamp,
                dateFormatted = dateFormatted,
                accountName = accountName,
                category = tx.category,
                type = tx.type,
                amount = tx.amount,
                note = tx.note
            )
        }

        val netSavings = totalIncome - totalExpense

        // Debt snapshot: active receivables and payables at export time
        var totalReceivables = 0.0
        var totalPayables = 0.0
        val debtReportItems = debts.map { item ->
            when (item.debt.direction) {
                DebtDirection.LENT -> totalReceivables += item.remainingAmount
                DebtDirection.BORROWED -> totalPayables += item.remainingAmount
            }
            val dueDateFormatted = item.debt.dueDate?.let {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
            }
            DebtReportItem(
                debtId = item.debt.debtId,
                counterpartyName = item.debt.counterpartyName,
                direction = item.debt.direction,
                principalAmount = item.debt.principalAmount,
                totalRepaid = item.totalRepaid,
                remainingBalance = item.remainingAmount,
                dueDateFormatted = dueDateFormatted,
                isSettled = item.isFullySettled,
                note = item.debt.note
            )
        }

        val accountReportItems = accountsMap.values.map { acc ->
            AccountReportItem(
                accountId = acc.accountId,
                name = acc.name,
                type = acc.type,
                balance = acc.balance,
                isDefault = acc.isDefault
            )
        }

        // Category breakdown
        val categoryAllocations = expenseCategoryMap.entries
            .sortedByDescending { it.value }
            .map { (cat, amount) ->
                val pct = if (totalExpense > 0) (amount / totalExpense) * 100.0 else 0.0
                CategoryAllocation(category = cat, amount = amount, percentage = pct)
            }

        val finalTransactions = when (scope) {
            FinancialActivityScope.FULL_STATEMENT, FinancialActivityScope.TRANSACTIONS_ONLY -> transactionReportItems
            else -> emptyList()
        }
        val finalDebts = when (scope) {
            FinancialActivityScope.FULL_STATEMENT, FinancialActivityScope.DEBT_ACTIVITIES -> debtReportItems
            else -> emptyList()
        }
        val finalAccounts = when (scope) {
            FinancialActivityScope.FULL_STATEMENT, FinancialActivityScope.ACCOUNTS_PORTFOLIO -> accountReportItems
            else -> emptyList()
        }

        FinancialReportData(
            title = "Personal Manager Financial Statement",
            scope = scope,
            periodType = periodType,
            dateRangeLabel = dateRangeLabel,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            generatedAtMillis = System.currentTimeMillis(),
            currencyCode = currencyCode,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netSavings = netSavings,
            totalReceivablesLent = totalReceivables,
            totalPayablesBorrowed = totalPayables,
            categoryAllocations = categoryAllocations,
            itemizedTransactions = finalTransactions,
            debtsList = finalDebts,
            accountsList = finalAccounts
        )
    }

    suspend fun generateAndSaveReport(
        context: Context,
        reportData: FinancialReportData,
        format: ReportFormat
    ): File = withContext(Dispatchers.IO) {
        val reportsDir = File(context.cacheDir, "reports").apply {
            if (!exists()) mkdirs()
        }
        cleanOldCachedReports(reportsDir)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Financial_${reportData.scope.name}_${timestamp}.${format.extension}"
        val targetFile = File(reportsDir, fileName)

        when (format) {
            ReportFormat.PDF -> generatePdf(targetFile, reportData)
            ReportFormat.EXCEL_XLSX -> ExcelXmlWriter.writeWorkbook(targetFile, reportData)
            ReportFormat.CSV,
            ReportFormat.GOOGLE_SHEETS -> generateCsv(targetFile, reportData)
        }

        targetFile
    }

    private fun generatePdf(targetFile: File, data: FinancialReportData) {
        val pdfDoc = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842
        val marginLeft = 36f
        val marginRight = 559f
        val marginTop = 36f
        val marginBottom = 806f
        val contentWidth = marginRight - marginLeft

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42) // Slate 900
            textSize = 10f
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
        }
        val subheaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(37, 99, 235) // Blue 600
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139) // Slate 500
            textSize = 8.5f
        }
        val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240) // Slate 200
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(248, 250, 252) // Slate 50
            style = Paint.Style.FILL
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var currentPage = pdfDoc.startPage(pageInfo)
        var canvas = currentPage.canvas

        fun drawFooter(pNum: Int) {
            val footerLineY = marginBottom - 12f
            canvas.drawLine(marginLeft, footerLineY, marginRight, footerLineY, linePaint)
            canvas.drawText("Personal Manager Statement", marginLeft, marginBottom, mutedPaint)
            val pageStr = "Page $pNum"
            val pWidth = mutedPaint.measureText(pageStr)
            canvas.drawText(pageStr, marginRight - pWidth, marginBottom, mutedPaint)
        }

        var currentY = marginTop

        // Page 1 Header Banner
        canvas.drawText("PERSONAL MANAGER", marginLeft, currentY + 16f, headerPaint)
        currentY += 28f
        canvas.drawText("FINANCIAL AUDIT & STATEMENT REPORT", marginLeft, currentY + 10f, subheaderPaint)

        val genDateStr = "Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(data.generatedAtMillis))}"
        val genWidth = mutedPaint.measureText(genDateStr)
        canvas.drawText(genDateStr, marginRight - genWidth, currentY + 10f, mutedPaint)

        currentY += 18f
        val periodText = "Period: ${data.dateRangeLabel}"
        canvas.drawText(periodText, marginLeft, currentY + 10f, boldTextPaint)

        val curText = "Currency: ${data.currencyCode} (${CurrencyFormatter.getCurrencySymbol(data.currencyCode)})"
        val curWidth = boldTextPaint.measureText(curText)
        canvas.drawText(curText, marginRight - curWidth, currentY + 10f, boldTextPaint)

        currentY += 20f
        canvas.drawLine(marginLeft, currentY, marginRight, currentY, linePaint)
        currentY += 14f

        // Section: Executive Summary (4 Cards in a 2x2 grid)
        val cardWidth = (contentWidth - 12f) / 2f
        val cardHeight = 46f

        fun drawSummaryCard(x: Float, y: Float, label: String, value: String, valueColor: Int, subValue: String? = null) {
            val rect = RectF(x, y, x + cardWidth, y + cardHeight)
            canvas.drawRoundRect(rect, 6f, 6f, cardFillPaint)
            canvas.drawRoundRect(rect, 6f, 6f, linePaint)

            canvas.drawText(label, x + 10f, y + 15f, mutedPaint)
            val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = valueColor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 12f
            }
            canvas.drawText(value, x + 10f, y + 33f, valPaint)

            if (subValue != null) {
                val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(100, 116, 139)
                    textSize = 8f
                }
                canvas.drawText(subValue, x + 10f, y + 42f, subPaint)
            }
        }

        // Top Row: Income and Expenses
        val incColor = Color.rgb(16, 185, 129) // Emerald
        val expColor = Color.rgb(239, 68, 68) // Crimson
        val savingsColor = if (data.netSavings >= 0) incColor else expColor

        val formattedIncome = "+${CurrencyFormatter.format(data.totalIncome, data.currencyCode)}"
        val formattedExpense = "-${CurrencyFormatter.format(data.totalExpense, data.currencyCode)}"
        val formattedSavings = "${if (data.netSavings >= 0) "+" else ""}${CurrencyFormatter.format(data.netSavings, data.currencyCode)}"

        drawSummaryCard(marginLeft, currentY, "TOTAL INCOME", formattedIncome, incColor)
        drawSummaryCard(marginLeft + cardWidth + 12f, currentY, "TOTAL EXPENSES", formattedExpense, expColor)

        currentY += cardHeight + 8f

        // Bottom Row: Net Savings and Debt Position
        val debtSummaryStr = "Lent: ${CurrencyFormatter.format(data.totalReceivablesLent, data.currencyCode)} | Borrowed: ${CurrencyFormatter.format(data.totalPayablesBorrowed, data.currencyCode)}"
        drawSummaryCard(marginLeft, currentY, "NET CASH FLOW / SAVINGS", formattedSavings, savingsColor)
        drawSummaryCard(marginLeft + cardWidth + 12f, currentY, "DEBT SNAPSHOT (OUTSTANDING)", "Position", Color.rgb(79, 70, 229), debtSummaryStr)

        currentY += cardHeight + 18f

        // Section: Expense Category Breakdown Table
        if (data.categoryAllocations.isNotEmpty()) {
            canvas.drawText("EXPENSE CATEGORY BREAKDOWN", marginLeft, currentY + 10f, boldTextPaint)
            currentY += 16f

            // Table Header
            val tableHeadFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(241, 245, 249)
                style = Paint.Style.FILL
            }
            canvas.drawRect(marginLeft, currentY, marginRight, currentY + 18f, tableHeadFill)
            canvas.drawLine(marginLeft, currentY + 18f, marginRight, currentY + 18f, linePaint)

            canvas.drawText("Category", marginLeft + 8f, currentY + 13f, boldTextPaint)
            canvas.drawText("Amount", marginLeft + 240f, currentY + 13f, boldTextPaint)
            canvas.drawText("Share (%)", marginLeft + 390f, currentY + 13f, boldTextPaint)
            currentY += 18f

            val displayedAllocations = data.categoryAllocations.take(6)
            for (alloc in displayedAllocations) {
                currentY += 16f
                canvas.drawText(truncateText(alloc.category, 210f, textPaint), marginLeft + 8f, currentY, textPaint)
                canvas.drawText(CurrencyFormatter.format(alloc.amount, data.currencyCode), marginLeft + 240f, currentY, textPaint)
                val pctStr = String.format(Locale.US, "%.1f%%", alloc.percentage)
                canvas.drawText(pctStr, marginLeft + 390f, currentY, textPaint)
                canvas.drawLine(marginLeft, currentY + 4f, marginRight, currentY + 4f, linePaint)
            }
            currentY += 12f
        }

        currentY += 10f

        // Section: Itemized Transactions Ledger
        val ledgerTitle = "ITEMIZED TRANSACTION LEDGER (${data.itemizedTransactions.size} Records)"
        canvas.drawText(ledgerTitle, marginLeft, currentY + 10f, boldTextPaint)
        currentY += 16f

        fun drawLedgerHeader() {
            val tableHeadFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(241, 245, 249)
                style = Paint.Style.FILL
            }
            canvas.drawRect(marginLeft, currentY, marginRight, currentY + 18f, tableHeadFill)
            canvas.drawLine(marginLeft, currentY + 18f, marginRight, currentY + 18f, linePaint)

            canvas.drawText("Date", marginLeft + 4f, currentY + 13f, boldTextPaint)
            canvas.drawText("Account", marginLeft + 90f, currentY + 13f, boldTextPaint)
            canvas.drawText("Category", marginLeft + 180f, currentY + 13f, boldTextPaint)
            canvas.drawText("Type", marginLeft + 270f, currentY + 13f, boldTextPaint)
            canvas.drawText("Amount", marginLeft + 335f, currentY + 13f, boldTextPaint)
            canvas.drawText("Note", marginLeft + 425f, currentY + 13f, boldTextPaint)
            currentY += 18f
        }

        drawLedgerHeader()

        if (data.itemizedTransactions.isEmpty()) {
            currentY += 24f
            canvas.drawText("No transactions recorded during this period.", marginLeft + 8f, currentY, mutedPaint)
        } else {
            val rowHeight = 18f
            for (tx in data.itemizedTransactions) {
                if (currentY + rowHeight > marginBottom - 25f) {
                    drawFooter(pageNumber)
                    pdfDoc.finishPage(currentPage)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    currentPage = pdfDoc.startPage(pageInfo)
                    canvas = currentPage.canvas

                    currentY = marginTop
                    canvas.drawText("ITEMIZED TRANSACTION LEDGER (CONTINUED)", marginLeft, currentY + 10f, boldTextPaint)
                    currentY += 18f
                    drawLedgerHeader()
                }

                currentY += rowHeight
                val shortDate = tx.dateFormatted.take(10)
                canvas.drawText(shortDate, marginLeft + 4f, currentY - 4f, textPaint)
                canvas.drawText(truncateText(tx.accountName, 80f, textPaint), marginLeft + 90f, currentY - 4f, textPaint)
                canvas.drawText(truncateText(tx.category, 80f, textPaint), marginLeft + 180f, currentY - 4f, textPaint)

                val typePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 8.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = if (tx.type == TransactionType.INCOME) incColor else expColor
                }
                canvas.drawText(tx.type.name, marginLeft + 270f, currentY - 4f, typePaint)

                val amtSign = if (tx.type == TransactionType.INCOME) "+" else "-"
                val amtStr = "$amtSign${CurrencyFormatter.format(tx.amount, data.currencyCode)}"
                canvas.drawText(amtStr, marginLeft + 335f, currentY - 4f, typePaint)

                val noteStr = tx.note ?: "—"
                canvas.drawText(truncateText(noteStr, 125f, mutedPaint), marginLeft + 425f, currentY - 4f, mutedPaint)

                canvas.drawLine(marginLeft, currentY, marginRight, currentY, linePaint)
            }
        }

        drawFooter(pageNumber)
        pdfDoc.finishPage(currentPage)

        FileOutputStream(targetFile).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()
    }

    private fun generateCsv(targetFile: File, data: FinancialReportData) {
        val genDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(data.generatedAtMillis))

        FileOutputStream(targetFile).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                // UTF-8 BOM for Microsoft Excel compatibility
                writer.write("\uFEFF")

                // Metadata header
                writer.append("Personal Manager - Financial Statement Report\n")
                writer.append("Generated At,").append(escapeCsv(genDateStr)).append("\n")
                writer.append("Period Type,").append(escapeCsv(data.periodType.name)).append("\n")
                writer.append("Date Range,").append(escapeCsv(data.dateRangeLabel)).append("\n")
                writer.append("Base Currency,").append(escapeCsv("${data.currencyCode} (${CurrencyFormatter.getCurrencySymbol(data.currencyCode)})")).append("\n\n")

                // Summary totals
                writer.append("EXECUTIVE SUMMARY\n")
                writer.append("Metric,Amount,Formatted Value\n")
                writer.append("Total Income,").append(data.totalIncome.toString()).append(",\"").append(CurrencyFormatter.format(data.totalIncome, data.currencyCode)).append("\"\n")
                writer.append("Total Expenses,").append(data.totalExpense.toString()).append(",\"").append(CurrencyFormatter.format(data.totalExpense, data.currencyCode)).append("\"\n")
                writer.append("Net Cash Flow (Savings),").append(data.netSavings.toString()).append(",\"").append(CurrencyFormatter.format(data.netSavings, data.currencyCode)).append("\"\n")
                writer.append("Debt Snapshot - Outstanding Receivables (Lent),").append(data.totalReceivablesLent.toString()).append(",\"").append(CurrencyFormatter.format(data.totalReceivablesLent, data.currencyCode)).append("\"\n")
                writer.append("Debt Snapshot - Outstanding Payables (Borrowed),").append(data.totalPayablesBorrowed.toString()).append(",\"").append(CurrencyFormatter.format(data.totalPayablesBorrowed, data.currencyCode)).append("\"\n\n")

                // Category Allocation
                writer.append("EXPENSE CATEGORY ALLOCATION\n")
                writer.append("Category,Total Amount,Share (%),Formatted Value\n")
                for (cat in data.categoryAllocations) {
                    writer.append(escapeCsv(cat.category)).append(",")
                        .append(cat.amount.toString()).append(",")
                        .append(String.format(Locale.US, "%.2f%%", cat.percentage)).append(",")
                        .append("\"").append(CurrencyFormatter.format(cat.amount, data.currencyCode)).append("\"\n")
                }
                writer.append("\n")

                // Itemized transactions
                if (data.itemizedTransactions.isNotEmpty() && (data.scope == FinancialActivityScope.FULL_STATEMENT || data.scope == FinancialActivityScope.TRANSACTIONS_ONLY)) {
                    writer.append("ITEMIZED TRANSACTION LEDGER\n")
                    writer.append("Date,Account,Category,Type,Amount,Currency,Note\n")
                    for (tx in data.itemizedTransactions) {
                        val signedAmt = if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
                        writer.append(escapeCsv(tx.dateFormatted)).append(",")
                            .append(escapeCsv(tx.accountName)).append(",")
                            .append(escapeCsv(tx.category)).append(",")
                            .append(escapeCsv(tx.type.name)).append(",")
                            .append(signedAmt.toString()).append(",")
                            .append(escapeCsv(data.currencyCode)).append(",")
                            .append(escapeCsv(tx.note ?: "")).append("\n")
                    }
                    writer.append("\n")
                }

                // Debt Ledger activities
                if (data.debtsList.isNotEmpty() && (data.scope == FinancialActivityScope.FULL_STATEMENT || data.scope == FinancialActivityScope.DEBT_ACTIVITIES)) {
                    writer.append("DEBT LEDGER ACTIVITIES\n")
                    writer.append("Counterparty,Direction,Principal Amount,Total Repaid,Remaining Balance,Due Date,Status,Notes\n")
                    for (debt in data.debtsList) {
                        val dirLabel = if (debt.direction == DebtDirection.LENT) "Lent (Receivable)" else "Borrowed (Payable)"
                        val status = if (debt.isSettled) "Settled" else (debt.dueDateFormatted?.let { "Due $it" } ?: "Active")
                        writer.append(escapeCsv(debt.counterpartyName)).append(",")
                            .append(escapeCsv(dirLabel)).append(",")
                            .append(debt.principalAmount.toString()).append(",")
                            .append(debt.totalRepaid.toString()).append(",")
                            .append(debt.remainingBalance.toString()).append(",")
                            .append(escapeCsv(debt.dueDateFormatted ?: "-")).append(",")
                            .append(escapeCsv(status)).append(",")
                            .append(escapeCsv(debt.note ?: "")).append("\n")
                    }
                    writer.append("\n")
                }

                // Accounts Portfolio
                if (data.accountsList.isNotEmpty() && (data.scope == FinancialActivityScope.FULL_STATEMENT || data.scope == FinancialActivityScope.ACCOUNTS_PORTFOLIO)) {
                    writer.append("ACCOUNTS PORTFOLIO & BALANCES\n")
                    writer.append("Account Name,Account Type,Balance,Currency,Status\n")
                    for (acc in data.accountsList) {
                        writer.append(escapeCsv(acc.name)).append(",")
                            .append(escapeCsv(acc.type.name)).append(",")
                            .append(acc.balance.toString()).append(",")
                            .append(escapeCsv(data.currencyCode)).append(",")
                            .append(if (acc.isDefault) "Default Primary" else "Active").append("\n")
                    }
                    writer.append("\n")
                }
            }
        }
    }

    fun copyTsvForGoogleSheets(context: Context, data: FinancialReportData): Boolean {
        return try {
            val sb = StringBuilder()
            sb.append("Personal Manager - Financial Export\t").append(data.title).append("\n")
            sb.append("Coverage:\t").append(data.dateRangeLabel).append("\n")
            sb.append("Currency:\t").append(data.currencyCode).append("\n\n")

            // KPI Summary
            sb.append("Metric\tAmount\tFormatted\n")
            sb.append("Total Income\t").append(data.totalIncome).append("\t").append(CurrencyFormatter.format(data.totalIncome, data.currencyCode)).append("\n")
            sb.append("Total Expenses\t").append(data.totalExpense).append("\t").append(CurrencyFormatter.format(data.totalExpense, data.currencyCode)).append("\n")
            sb.append("Net Cash Flow\t").append(data.netSavings).append("\t").append(CurrencyFormatter.format(data.netSavings, data.currencyCode)).append("\n")
            sb.append("Outstanding Receivables\t").append(data.totalReceivablesLent).append("\t").append(CurrencyFormatter.format(data.totalReceivablesLent, data.currencyCode)).append("\n")
            sb.append("Outstanding Payables\t").append(data.totalPayablesBorrowed).append("\t").append(CurrencyFormatter.format(data.totalPayablesBorrowed, data.currencyCode)).append("\n\n")

            // Itemized Transactions
            if (data.itemizedTransactions.isNotEmpty()) {
                sb.append("Date & Time\tAccount\tCategory\tType\tAmount\tNotes\n")
                for (tx in data.itemizedTransactions) {
                    val signedAmt = if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
                    sb.append(tx.dateFormatted).append("\t")
                        .append(tx.accountName).append("\t")
                        .append(tx.category).append("\t")
                        .append(tx.type.name).append("\t")
                        .append(signedAmt).append("\t")
                        .append(tx.note ?: "").append("\n")
                }
                sb.append("\n")
            }

            // Debt Activities
            if (data.debtsList.isNotEmpty()) {
                sb.append("Counterparty\tDirection\tPrincipal\tRepaid\tRemaining\tDue Date\tNotes\n")
                for (d in data.debtsList) {
                    sb.append(d.counterpartyName).append("\t")
                        .append(d.direction.name).append("\t")
                        .append(d.principalAmount).append("\t")
                        .append(d.totalRepaid).append("\t")
                        .append(d.remainingBalance).append("\t")
                        .append(d.dueDateFormatted ?: "-").append("\t")
                        .append(d.note ?: "").append("\n")
                }
                sb.append("\n")
            }

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Google Sheets Data", sb.toString())
            clipboard.setPrimaryClip(clip)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openGoogleSheetsAppOrWeb(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://docs.google.com/spreadsheets/create")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun shareReportFile(
        context: Context,
        file: File,
        format: ReportFormat,
        dateRangeLabel: String
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (format == ReportFormat.GOOGLE_SHEETS) "text/csv" else format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Financial Statement - $dateRangeLabel")
            putExtra(
                Intent.EXTRA_TEXT,
                "Attached is the financial statement generated from Personal Manager for $dateRangeLabel."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserTitle = when (format) {
            ReportFormat.PDF -> "Share Financial Statement (PDF)"
            ReportFormat.EXCEL_XLSX -> "Open in Microsoft Excel / Spreadsheets (.xlsx)"
            ReportFormat.CSV -> "Share CSV Statement"
            ReportFormat.GOOGLE_SHEETS -> "Open with Google Sheets / Drive"
        }

        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun cleanOldCachedReports(reportsDir: File) {
        try {
            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            reportsDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
        return if (needsQuotes) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun truncateText(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated…"
    }
}
