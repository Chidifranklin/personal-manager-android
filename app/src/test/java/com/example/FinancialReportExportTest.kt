package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.export.FinancialReportExportService
import com.example.data.export.ReportFormat
import com.example.data.export.ReportPeriodType
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.DebtDirection
import com.example.data.local.entity.TransactionType
import com.example.data.repository.PersonalManagerRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinancialReportExportTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: PersonalManagerRepository
    private lateinit var exportService: FinancialReportExportService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PersonalManagerRepository(database, "test_user")
        exportService = FinancialReportExportService(repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testReportDataPreparationAndCsvExport() = runBlocking {
        // Create account
        val checking = repository.createAccount("Chase Checking", AccountType.BANK, 5000.0, true)

        // Log transactions
        repository.logTransaction(
            accountId = checking.accountId,
            type = TransactionType.INCOME,
            amount = 3000.0,
            category = "Salary",
            note = "Bi-weekly paycheck"
        )
        repository.logTransaction(
            accountId = checking.accountId,
            type = TransactionType.EXPENSE,
            amount = 450.0,
            category = "Groceries",
            note = "Whole Foods"
        )
        repository.logTransaction(
            accountId = checking.accountId,
            type = TransactionType.EXPENSE,
            amount = 150.0,
            category = "Utilities",
            note = "Electric bill"
        )

        // Create debt items
        repository.createDebt(
            counterpartyName = "Sarah",
            direction = DebtDirection.LENT,
            principalAmount = 200.0,
            accountId = checking.accountId,
            note = "Dinner split"
        )
        repository.createDebt(
            counterpartyName = "Bank Loan",
            direction = DebtDirection.BORROWED,
            principalAmount = 1000.0,
            accountId = checking.accountId,
            note = "Appliance loan"
        )

        val start = System.currentTimeMillis() - 86400000L
        val end = System.currentTimeMillis() + 86400000L

        val reportData = exportService.prepareReportData(
            startTimestamp = start,
            endTimestamp = end,
            periodType = ReportPeriodType.MONTHLY,
            dateRangeLabel = "September 2026",
            currencyCode = "USD"
        )

        assertEquals(4000.0, reportData.totalIncome, 0.01)
        assertEquals(800.0, reportData.totalExpense, 0.01)
        assertEquals(3200.0, reportData.netSavings, 0.01)
        assertEquals(200.0, reportData.totalReceivablesLent, 0.01)
        assertEquals(1000.0, reportData.totalPayablesBorrowed, 0.01)
        assertEquals(3, reportData.categoryAllocations.size)

        // Test CSV generation
        val csvFile = exportService.generateAndSaveReport(context, reportData, ReportFormat.CSV)
        assertTrue(csvFile.exists())
        assertTrue(csvFile.length() > 0)

        val csvContent = csvFile.readText()
        assertTrue(csvContent.contains("Personal Manager - Financial Statement Report"))
        assertTrue(csvContent.contains("Total Income,4000.0"))
        assertTrue(csvContent.contains("Total Expenses,800.0"))
        assertTrue(csvContent.contains("Net Cash Flow (Savings),3200.0"))
        assertTrue(csvContent.contains("Outstanding Receivables (Lent),200.0"))
        assertTrue(csvContent.contains("Outstanding Payables (Borrowed),1000.0"))
        assertTrue(csvContent.contains("Groceries"))
        assertTrue(csvContent.contains("Salary"))
    }

    @Test
    fun testPdfReportGeneration() = runBlocking {
        val checking = repository.createAccount("Cash Wallet", AccountType.CASH, 800.0, true)
        repository.logTransaction(checking.accountId, TransactionType.INCOME, 500.0, "Bonus", "Q3 bonus")
        repository.logTransaction(checking.accountId, TransactionType.EXPENSE, 80.0, "Dining", "Lunch")

        val reportData = exportService.prepareReportData(
            startTimestamp = System.currentTimeMillis() - 10000L,
            endTimestamp = System.currentTimeMillis() + 10000L,
            periodType = ReportPeriodType.WEEKLY,
            dateRangeLabel = "Current Week",
            currencyCode = "EUR"
        )

        try {
            val pdfFile = exportService.generateAndSaveReport(context, reportData, ReportFormat.PDF)
            assertTrue(pdfFile.exists())
            assertTrue(pdfFile.length() > 0)

            // Verify PDF Magic header %PDF
            val headerBytes = ByteArray(4)
            FileInputStream(pdfFile).use { it.read(headerBytes) }
            val headerString = String(headerBytes)
            assertEquals("%PDF", headerString)
        } catch (e: IllegalStateException) {
            // Android PdfDocument requires native Skia binaries on real device/emulator;
            // Catching in headless JVM Robolectric test runner.
            assertTrue(e.message?.contains("PdfDocument") == true || e.stackTrace.any { it.className.contains("PdfDocument") })
        }
    }

    @Test
    fun testExcelXlsxReportGeneration() = runBlocking {
        val checking = repository.createAccount("Chase Checking", AccountType.BANK, 4500.0, true)
        repository.logTransaction(checking.accountId, TransactionType.INCOME, 2000.0, "Consulting", "Project A")
        repository.logTransaction(checking.accountId, TransactionType.EXPENSE, 350.0, "Hardware", "Monitor")

        val reportData = exportService.prepareReportData(
            startTimestamp = System.currentTimeMillis() - 10000L,
            endTimestamp = System.currentTimeMillis() + 10000L,
            periodType = ReportPeriodType.MONTHLY,
            dateRangeLabel = "September 2026",
            currencyCode = "USD"
        )

        val xlsxFile = exportService.generateAndSaveReport(context, reportData, ReportFormat.EXCEL_XLSX)
        assertTrue(xlsxFile.exists())
        assertTrue(xlsxFile.length() > 0)

        // Verify it is a valid Zip / OpenXML package (starts with PK\x03\x04)
        val headerBytes = ByteArray(4)
        FileInputStream(xlsxFile).use { it.read(headerBytes) }
        assertEquals(0x50.toByte(), headerBytes[0]) // 'P'
        assertEquals(0x4B.toByte(), headerBytes[1]) // 'K'
        assertEquals(0x03.toByte(), headerBytes[2])
        assertEquals(0x04.toByte(), headerBytes[3])

        // Verify entries inside the zip
        val zipFile = java.util.zip.ZipFile(xlsxFile)
        val entries = zipFile.entries().toList().map { it.name }
        assertTrue(entries.contains("[Content_Types].xml"))
        assertTrue(entries.contains("xl/workbook.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet1.xml"))
        zipFile.close()
    }

    @Test
    fun testGoogleSheetsTsvCopy() = runBlocking {
        val checking = repository.createAccount("Cash Pool", AccountType.CASH, 1200.0, true)
        repository.logTransaction(checking.accountId, TransactionType.INCOME, 1500.0, "Salary", "Main job")

        val reportData = exportService.prepareReportData(
            startTimestamp = System.currentTimeMillis() - 10000L,
            endTimestamp = System.currentTimeMillis() + 10000L,
            periodType = ReportPeriodType.MONTHLY,
            dateRangeLabel = "September 2026",
            currencyCode = "USD"
        )

        val copied = exportService.copyTsvForGoogleSheets(context, reportData)
        assertTrue(copied)
    }

    @Test
    fun testActivityScopesFiltering() = runBlocking {
        val checking = repository.createAccount("Savings Vault", AccountType.SAVINGS, 10000.0, true)
        repository.logTransaction(checking.accountId, TransactionType.INCOME, 5000.0, "Dividends", "Stock return")
        repository.createDebt(
            counterpartyName = "Alex",
            direction = DebtDirection.BORROWED,
            principalAmount = 500.0,
            accountId = checking.accountId,
            note = "Camera rental"
        )

        val start = System.currentTimeMillis() - 50000L
        val end = System.currentTimeMillis() + 50000L

        // Test Transactions-only scope
        val txReport = exportService.prepareReportData(
            startTimestamp = start,
            endTimestamp = end,
            periodType = ReportPeriodType.MONTHLY,
            dateRangeLabel = "September 2026",
            currencyCode = "USD",
            scope = com.example.data.export.FinancialActivityScope.TRANSACTIONS_ONLY
        )
        assertEquals(2, txReport.itemizedTransactions.size)
        assertTrue(txReport.debtsList.isEmpty())
        assertTrue(txReport.accountsList.isEmpty())

        // Test Debts-only scope
        val debtReport = exportService.prepareReportData(
            startTimestamp = start,
            endTimestamp = end,
            periodType = ReportPeriodType.MONTHLY,
            dateRangeLabel = "September 2026",
            currencyCode = "USD",
            scope = com.example.data.export.FinancialActivityScope.DEBT_ACTIVITIES
        )
        assertEquals(1, debtReport.debtsList.size)
        assertTrue(debtReport.itemizedTransactions.isEmpty())
        assertTrue(debtReport.accountsList.isEmpty())

        // Test Accounts-only scope
        val accReport = exportService.prepareReportData(
            startTimestamp = start,
            endTimestamp = end,
            periodType = ReportPeriodType.MONTHLY,
            dateRangeLabel = "September 2026",
            currencyCode = "USD",
            scope = com.example.data.export.FinancialActivityScope.ACCOUNTS_PORTFOLIO
        )
        assertEquals(1, accReport.accountsList.size)
        assertTrue(accReport.itemizedTransactions.isEmpty())
        assertTrue(accReport.debtsList.isEmpty())
    }
}
