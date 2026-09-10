package com.example.data.export

import com.example.data.local.entity.DebtDirection
import com.example.data.local.entity.TransactionType
import com.example.util.CurrencyFormatter
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pure Kotlin zero-dependency generator for Microsoft Excel (.xlsx) OpenXML packages.
 * Produces valid OpenXML workbooks compatible with Microsoft Excel, Google Sheets,
 * LibreOffice Calc, and Apple Numbers with automated =SUM() formulas and formatted columns.
 */
object ExcelXmlWriter {

    fun writeWorkbook(targetFile: File, data: FinancialReportData) {
        FileOutputStream(targetFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // 1. [Content_Types].xml
                writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml())

                // 2. _rels/.rels
                writeZipEntry(zos, "_rels/.rels", buildPackageRelsXml())

                // 3. xl/_rels/workbook.xml.rels
                writeZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml())

                // 4. xl/workbook.xml
                writeZipEntry(zos, "xl/workbook.xml", buildWorkbookXml(data.title))

                // 5. xl/styles.xml
                writeZipEntry(zos, "xl/styles.xml", buildStylesXml())

                // 6. xl/worksheets/sheet1.xml
                writeZipEntry(zos, "xl/worksheets/sheet1.xml", buildSheetXml(data))
            }
        }
    }

    private fun writeZipEntry(zos: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun buildContentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
            <Default Extension="xml" ContentType="application/xml"/>
            <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
            <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
        </Types>
    """.trimIndent()

    private fun buildPackageRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildWorkbookRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
            <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildWorkbookXml(sheetName: String): String {
        val cleanName = escapeXml(sheetName.take(30).ifBlank { "Statement" })
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <sheets>
                    <sheet name="$cleanName" sheetId="1" r:id="rId1"/>
                </sheets>
            </workbook>
        """.trimIndent()
    }

    private fun buildStylesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <fonts count="4">
                <font><name val="Calibri"/><sz val="11"/></font>
                <font><b/><name val="Calibri"/><sz val="11"/><color rgb="FFFFFFFF"/></font>
                <font><b/><name val="Calibri"/><sz val="14"/><color rgb="FF1E293B"/></font>
                <font><b/><name val="Calibri"/><sz val="11"/><color rgb="FF1E293B"/></font>
            </fonts>
            <fills count="4">
                <fill><patternFill patternType="none"/></fill>
                <fill><patternFill patternType="gray125"/></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FF2563EB"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FFF1F5F9"/></patternFill></fill>
            </fills>
            <borders count="2">
                <border><left/><right/><top/><bottom/><diagonal/></border>
                <border>
                    <left style="thin"><color rgb="FFE2E8F0"/></left>
                    <right style="thin"><color rgb="FFE2E8F0"/></right>
                    <top style="thin"><color rgb="FFE2E8F0"/></top>
                    <bottom style="thin"><color rgb="FFE2E8F0"/></bottom>
                    <diagonal/>
                </border>
            </borders>
            <cellStyleXfs count="1">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
            </cellStyleXfs>
            <cellXfs count="5">
                <!-- 0: Default -->
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                <!-- 1: Table Header (Bold white text, blue fill) -->
                <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
                <!-- 2: Title (Bold 14pt) -->
                <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
                <!-- 3: Subtotal / Summary (Bold with light gray fill) -->
                <xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
                <!-- 4: Bordered cell -->
                <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
            </cellXfs>
        </styleSheet>
    """.trimIndent()

    private fun buildSheetXml(data: FinancialReportData): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")

        // Column widths
        sb.append("  <cols>\n")
        sb.append("    <col min=\"1\" max=\"1\" width=\"22\" customWidth=\"1\"/>\n")
        sb.append("    <col min=\"2\" max=\"2\" width=\"20\" customWidth=\"1\"/>\n")
        sb.append("    <col min=\"3\" max=\"3\" width=\"22\" customWidth=\"1\"/>\n")
        sb.append("    <col min=\"4\" max=\"4\" width=\"16\" customWidth=\"1\"/>\n")
        sb.append("    <col min=\"5\" max=\"5\" width=\"18\" customWidth=\"1\"/>\n")
        sb.append("    <col min=\"6\" max=\"6\" width=\"35\" customWidth=\"1\"/>\n")
        sb.append("  </cols>\n")

        sb.append("  <sheetData>\n")

        var rowIndex = 1

        // Title Block
        sb.append("    <row r=\"$rowIndex\">\n")
        sb.append("      <c r=\"A$rowIndex\" s=\"2\" t=\"inlineStr\"><is><t>${escapeXml(data.title)}</t></is></c>\n")
        sb.append("    </row>\n")
        rowIndex++

        // Scope & Date Range
        sb.append("    <row r=\"$rowIndex\">\n")
        sb.append("      <c r=\"A$rowIndex\" t=\"inlineStr\"><is><t>Scope: ${escapeXml(data.scope.displayName)}</t></is></c>\n")
        sb.append("      <c r=\"C$rowIndex\" t=\"inlineStr\"><is><t>Period: ${escapeXml(data.dateRangeLabel)}</t></is></c>\n")
        sb.append("    </row>\n")
        rowIndex++

        // Base currency & timestamp
        sb.append("    <row r=\"$rowIndex\">\n")
        sb.append("      <c r=\"A$rowIndex\" t=\"inlineStr\"><is><t>Base Currency: ${escapeXml(data.currencyCode)} (${CurrencyFormatter.getCurrencySymbol(data.currencyCode)})</t></is></c>\n")
        sb.append("    </row>\n")
        rowIndex += 2

        // Section: Executive Summary (always for FULL_STATEMENT or TRANSACTIONS_ONLY)
        if (data.scope == FinancialActivityScope.FULL_STATEMENT || data.scope == FinancialActivityScope.TRANSACTIONS_ONLY) {
            sb.append("    <row r=\"$rowIndex\">\n")
            sb.append("      <c r=\"A$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>KPI Metric</t></is></c>\n")
            sb.append("      <c r=\"B$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Amount</t></is></c>\n")
            sb.append("      <c r=\"C$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Formatted Value</t></is></c>\n")
            sb.append("    </row>\n")
            rowIndex++

            val kpis = listOf(
                Triple("Total Inflow (Income)", data.totalIncome, CurrencyFormatter.format(data.totalIncome, data.currencyCode)),
                Triple("Total Outflow (Expense)", data.totalExpense, CurrencyFormatter.format(data.totalExpense, data.currencyCode)),
                Triple("Net Cash Flow (Savings)", data.netSavings, CurrencyFormatter.format(data.netSavings, data.currencyCode)),
                Triple("Receivables (Money Lent Out)", data.totalReceivablesLent, CurrencyFormatter.format(data.totalReceivablesLent, data.currencyCode)),
                Triple("Payables (Money Borrowed)", data.totalPayablesBorrowed, CurrencyFormatter.format(data.totalPayablesBorrowed, data.currencyCode))
            )

            for (kpi in kpis) {
                sb.append("    <row r=\"$rowIndex\">\n")
                sb.append("      <c r=\"A$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${escapeXml(kpi.first)}</t></is></c>\n")
                sb.append("      <c r=\"B$rowIndex\" s=\"4\"><v>${kpi.second}</v></c>\n")
                sb.append("      <c r=\"C$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${escapeXml(kpi.third)}</t></is></c>\n")
                sb.append("    </row>\n")
                rowIndex++
            }
            rowIndex += 2
        }

        // Section: Category Allocations (if available)
        if (data.categoryAllocations.isNotEmpty() && data.scope == FinancialActivityScope.FULL_STATEMENT) {
            sb.append("    <row r=\"$rowIndex\">\n")
            sb.append("      <c r=\"A$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Expense Category</t></is></c>\n")
            sb.append("      <c r=\"B$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Amount</t></is></c>\n")
            sb.append("      <c r=\"C$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Share (%)</t></is></c>\n")
            sb.append("      <c r=\"D$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Formatted Value</t></is></c>\n")
            sb.append("    </row>\n")
            rowIndex++

            for (cat in data.categoryAllocations) {
                sb.append("    <row r=\"$rowIndex\">\n")
                sb.append("      <c r=\"A$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${escapeXml(cat.category)}</t></is></c>\n")
                sb.append("      <c r=\"B$rowIndex\" s=\"4\"><v>${cat.amount}</v></c>\n")
                sb.append("      <c r=\"C$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${String.format(java.util.Locale.US, "%.2f%%", cat.percentage)}</t></is></c>\n")
                sb.append("      <c r=\"D$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${escapeXml(CurrencyFormatter.format(cat.amount, data.currencyCode))}</t></is></c>\n")
                sb.append("    </row>\n")
                rowIndex++
            }
            rowIndex += 2
        }

        // Section: Account Portfolio (if requested or in FULL_STATEMENT)
        if (data.accountsList.isNotEmpty() && (data.scope == FinancialActivityScope.FULL_STATEMENT || data.scope == FinancialActivityScope.ACCOUNTS_PORTFOLIO)) {
            sb.append("    <row r=\"$rowIndex\">\n")
            sb.append("      <c r=\"A$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Account Name</t></is></c>\n")
            sb.append("      <c r=\"B$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Type</t></is></c>\n")
            sb.append("      <c r=\"C$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Balance</t></is></c>\n")
            sb.append("      <c r=\"D$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Status</t></is></c>\n")
            sb.append("    </row>\n")
            val startAccountRow = rowIndex + 1
            rowIndex++

            for (acc in data.accountsList) {
                sb.append("    <row r=\"$rowIndex\">\n")
                sb.append("      <c r=\"A$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${escapeXml(acc.name)}</t></is></c>\n")
                sb.append("      <c r=\"B$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${escapeXml(acc.type.name)}</t></is></c>\n")
                sb.append("      <c r=\"C$rowIndex\" s=\"4\"><v>${acc.balance}</v></c>\n")
                sb.append("      <c r=\"D$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${if (acc.isDefault) "Default Primary" else "Active"}</t></is></c>\n")
                sb.append("    </row>\n")
                rowIndex++
            }

            // Total Portfolio Balance Formula
            val endAccountRow = rowIndex - 1
            sb.append("    <row r=\"$rowIndex\">\n")
            sb.append("      <c r=\"A$rowIndex\" s=\"3\" t=\"inlineStr\"><is><t>Total Liquid Portfolio</t></is></c>\n")
            sb.append("      <c r=\"B$rowIndex\" s=\"3\"><is><t/></is></c>\n")
            sb.append("      <c r=\"C$rowIndex\" s=\"3\"><f>SUM(C$startAccountRow:C$endAccountRow)</f></c>\n")
            sb.append("      <c r=\"D$rowIndex\" s=\"3\"><is><t/></is></c>\n")
            sb.append("    </row>\n")
            rowIndex += 2
        }

        // Section: Debt Ledger Activities (if requested or in FULL_STATEMENT)
        if (data.debtsList.isNotEmpty() && (data.scope == FinancialActivityScope.FULL_STATEMENT || data.scope == FinancialActivityScope.DEBT_ACTIVITIES)) {
            sb.append("    <row r=\"$rowIndex\">\n")
            sb.append("      <c r=\"A$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Counterparty</t></is></c>\n")
            sb.append("      <c r=\"B$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Direction</t></is></c>\n")
            sb.append("      <c r=\"C$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Principal</t></is></c>\n")
            sb.append("      <c r=\"D$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Repaid</t></is></c>\n")
            sb.append("      <c r=\"E$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Remaining Balance</t></is></c>\n")
            sb.append("      <c r=\"F$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Status / Due Date</t></is></c>\n")
            sb.append("    </row>\n")
            val startDebtRow = rowIndex + 1
            rowIndex++

            for (debt in data.debtsList) {
                val dirLabel = if (debt.direction == DebtDirection.LENT) "Lent (Receivable)" else "Borrowed (Payable)"
                val statusStr = if (debt.isSettled) "Settled" else (debt.dueDateFormatted?.let { "Due $it" } ?: "Active")
                sb.append("    <row r=\"$rowIndex\">\n")
                sb.append("      <c r=\"A$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${escapeXml(debt.counterpartyName)}</t></is></c>\n")
                sb.append("      <c r=\"B$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${escapeXml(dirLabel)}</t></is></c>\n")
                sb.append("      <c r=\"C$rowIndex\" s=\"4\"><v>${debt.principalAmount}</v></c>\n")
                sb.append("      <c r=\"D$rowIndex\" s=\"4\"><v>${debt.totalRepaid}</v></c>\n")
                sb.append("      <c r=\"E$rowIndex\" s=\"4\"><f>C$rowIndex-D$rowIndex</f><v>${debt.remainingBalance}</v></c>\n")
                sb.append("      <c r=\"F$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${escapeXml(statusStr)}</t></is></c>\n")
                sb.append("    </row>\n")
                rowIndex++
            }

            val endDebtRow = rowIndex - 1
            sb.append("    <row r=\"$rowIndex\">\n")
            sb.append("      <c r=\"A$rowIndex\" s=\"3\" t=\"inlineStr\"><is><t>Total Debt Balances</t></is></c>\n")
            sb.append("      <c r=\"B$rowIndex\" s=\"3\"><is><t/></is></c>\n")
            sb.append("      <c r=\"C$rowIndex\" s=\"3\"><f>SUM(C$startDebtRow:C$endDebtRow)</f></c>\n")
            sb.append("      <c r=\"D$rowIndex\" s=\"3\"><f>SUM(D$startDebtRow:D$endDebtRow)</f></c>\n")
            sb.append("      <c r=\"E$rowIndex\" s=\"3\"><f>SUM(E$startDebtRow:E$endDebtRow)</f></c>\n")
            sb.append("      <c r=\"F$rowIndex\" s=\"3\"><is><t/></is></c>\n")
            sb.append("    </row>\n")
            rowIndex += 2
        }

        // Section: Itemized Transactions (if requested or in FULL_STATEMENT)
        if (data.itemizedTransactions.isNotEmpty() && (data.scope == FinancialActivityScope.FULL_STATEMENT || data.scope == FinancialActivityScope.TRANSACTIONS_ONLY)) {
            sb.append("    <row r=\"$rowIndex\">\n")
            sb.append("      <c r=\"A$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Date &amp; Time</t></is></c>\n")
            sb.append("      <c r=\"B$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Account</t></is></c>\n")
            sb.append("      <c r=\"C$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Category</t></is></c>\n")
            sb.append("      <c r=\"D$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Type</t></is></c>\n")
            sb.append("      <c r=\"E$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Amount</t></is></c>\n")
            sb.append("      <c r=\"F$rowIndex\" s=\"1\" t=\"inlineStr\"><is><t>Notes</t></is></c>\n")
            sb.append("    </row>\n")
            val startTxRow = rowIndex + 1
            rowIndex++

            for (tx in data.itemizedTransactions) {
                val signedAmt = if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
                sb.append("    <row r=\"$rowIndex\">\n")
                sb.append("      <c r=\"A$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${escapeXml(tx.dateFormatted)}</t></is></c>\n")
                sb.append("      <c r=\"B$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${escapeXml(tx.accountName)}</t></is></c>\n")
                sb.append("      <c r=\"C$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${escapeXml(tx.category)}</t></is></c>\n")
                sb.append("      <c r=\"D$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${escapeXml(tx.type.name)}</t></is></c>\n")
                sb.append("      <c r=\"E$rowIndex\" s=\"4\"><v>$signedAmt</v></c>\n")
                sb.append("      <c r=\"F$rowIndex\" s=\"4\" t=\"inlineStr\"><is><t>${escapeXml(tx.note ?: "")}</t></is></c>\n")
                sb.append("    </row>\n")
                rowIndex++
            }

            // Net Balance SUM Formula for Transactions
            val endTxRow = rowIndex - 1
            sb.append("    <row r=\"$rowIndex\">\n")
            sb.append("      <c r=\"A$rowIndex\" s=\"3\" t=\"inlineStr\"><is><t>Net Activity Cash Flow</t></is></c>\n")
            sb.append("      <c r=\"B$rowIndex\" s=\"3\"><is><t/></is></c>\n")
            sb.append("      <c r=\"C$rowIndex\" s=\"3\"><is><t/></is></c>\n")
            sb.append("      <c r=\"D$rowIndex\" s=\"3\"><is><t/></is></c>\n")
            sb.append("      <c r=\"E$rowIndex\" s=\"3\"><f>SUM(E$startTxRow:E$endTxRow)</f><v>${data.netSavings}</v></c>\n")
            sb.append("      <c r=\"F$rowIndex\" s=\"3\"><is><t>${escapeXml(data.currencyCode)}</t></is></c>\n")
            sb.append("    </row>\n")
        }

        sb.append("  </sheetData>\n")
        sb.append("</worksheet>\n")
        return sb.toString()
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
