package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.export.FinancialActivityScope
import com.example.data.export.ReportFormat
import com.example.data.export.ReportPeriodType
import com.example.data.export.WeeklyPreset
import com.example.ui.theme.CrimsonExpense
import com.example.ui.theme.EmeraldIncome
import com.example.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportReportBottomSheet(
    currencyCode: String,
    isExporting: Boolean,
    initialScope: FinancialActivityScope = FinancialActivityScope.FULL_STATEMENT,
    onDismiss: () -> Unit,
    onGenerateAndShare: (
        scope: FinancialActivityScope,
        periodType: ReportPeriodType,
        format: ReportFormat,
        startTimestamp: Long,
        endTimestamp: Long,
        dateRangeLabel: String
    ) -> Unit,
    onCopyToClipboard: ((
        scope: FinancialActivityScope,
        periodType: ReportPeriodType,
        startTimestamp: Long,
        endTimestamp: Long,
        dateRangeLabel: String
    ) -> Unit)? = null,
    onOpenGoogleSheets: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedScope by remember { mutableStateOf(initialScope) }
    var periodType by remember { mutableStateOf(ReportPeriodType.MONTHLY) }
    var selectedFormat by remember { mutableStateOf(ReportFormat.PDF) }
    var selectedWeeklyPreset by remember { mutableStateOf(WeeklyPreset.CURRENT_WEEK) }

    // Calendar for Monthly selection
    val currentCal = remember { Calendar.getInstance() }
    var selectedMonth by remember { mutableIntStateOf(currentCal.get(Calendar.MONTH)) }
    var selectedYear by remember { mutableIntStateOf(currentCal.get(Calendar.YEAR)) }

    val monthNames = remember {
        listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }

    val availableYears = remember {
        val y = currentCal.get(Calendar.YEAR)
        listOf(y - 2, y - 1, y, y + 1)
    }

    // Compute active date range timestamps and human-readable label
    val (startTimestamp, endTimestamp, dateRangeLabel) = remember(
        periodType,
        selectedWeeklyPreset,
        selectedMonth,
        selectedYear
    ) {
        val df = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        when (periodType) {
            ReportPeriodType.WEEKLY -> {
                val calNow = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                val endCal = Calendar.getInstance().apply {
                    timeInMillis = calNow.timeInMillis
                    add(Calendar.DAY_OF_YEAR, -selectedWeeklyPreset.daysAgoEnd)
                }

                val startCal = Calendar.getInstance().apply {
                    timeInMillis = calNow.timeInMillis
                    add(Calendar.DAY_OF_YEAR, -selectedWeeklyPreset.daysAgoStart)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val start = startCal.timeInMillis
                val end = endCal.timeInMillis
                val label = "${df.format(Date(start))} – ${df.format(Date(end))} (${selectedWeeklyPreset.displayName})"
                Triple(start, end, label)
            }
            ReportPeriodType.MONTHLY -> {
                val startCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val start = startCal.timeInMillis
                val end = endCal.timeInMillis
                val label = "${monthNames[selectedMonth]} $selectedYear (${df.format(Date(start))} – ${df.format(Date(end))})"
                Triple(start, end, label)
            }
            ReportPeriodType.ALL_TIME -> {
                Triple(0L, Long.MAX_VALUE, "All Historical Records (Complete History)")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("export_report_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Export Report",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Export Statements & Activities",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Generate PDF, Excel (.xlsx), CSV or Google Sheets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 1: Activity Scope
            Text(
                text = "EXPORT SCOPE & ACTIVITY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            FinancialActivityScope.values().forEach { scope ->
                val isSelected = selectedScope == scope
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedScope = scope },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedScope = scope },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = scope.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = scope.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 2: Time Period Selection
            Text(
                text = "TIME PERIOD",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = periodType == ReportPeriodType.WEEKLY,
                    onClick = { periodType = ReportPeriodType.WEEKLY },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    modifier = Modifier.testTag("export_period_weekly_tab")
                ) {
                    Text("Weekly")
                }
                SegmentedButton(
                    selected = periodType == ReportPeriodType.MONTHLY,
                    onClick = { periodType = ReportPeriodType.MONTHLY },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    modifier = Modifier.testTag("export_period_monthly_tab")
                ) {
                    Text("Monthly")
                }
                SegmentedButton(
                    selected = periodType == ReportPeriodType.ALL_TIME,
                    onClick = { periodType = ReportPeriodType.ALL_TIME },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    modifier = Modifier.testTag("export_period_all_time_tab")
                ) {
                    Text("All Time")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (periodType) {
                ReportPeriodType.WEEKLY -> {
                    Text(
                        text = "Select Range:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    WeeklyPreset.values().forEach { preset ->
                        val isSelected = selectedWeeklyPreset == preset
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedWeeklyPreset = preset },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedWeeklyPreset = preset },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                ReportPeriodType.MONTHLY -> {
                    Text(
                        text = "Select Year & Month:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableYears) { year ->
                            FilterChip(
                                selected = selectedYear == year,
                                onClick = { selectedYear = year },
                                label = { Text(year.toString(), fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(monthNames.indices.toList()) { index ->
                            FilterChip(
                                selected = selectedMonth == index,
                                onClick = { selectedMonth = index },
                                label = { Text(monthNames[index].take(3), fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }
                }
                ReportPeriodType.ALL_TIME -> {
                    Text(
                        text = "Complete archive of all transactions, debt entries, and accounts since inception.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Computed Range Display Box
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Coverage: $dateRangeLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Format Choice (PDF, Excel XLSX, Excel CSV, Google Sheets)
            Text(
                text = "EXPORT FORMAT",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportFormat.values().forEach { format ->
                    val isSelected = selectedFormat == format
                    val iconColor = when (format) {
                        ReportFormat.PDF -> CrimsonExpense
                        ReportFormat.EXCEL_XLSX -> Color(0xFF107C41) // Microsoft Excel Green
                        ReportFormat.CSV -> EmeraldIncome
                        ReportFormat.GOOGLE_SHEETS -> Color(0xFF0F9D58) // Google Sheets Green
                    }
                    val icon = when (format) {
                        ReportFormat.PDF -> Icons.Default.PictureAsPdf
                        ReportFormat.EXCEL_XLSX -> Icons.Default.TableChart
                        ReportFormat.CSV -> Icons.Default.Dataset
                        ReportFormat.GOOGLE_SHEETS -> Icons.Default.CloudSync
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedFormat = format }
                            .testTag("export_format_${format.name.lowercase()}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(iconColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = format.title,
                                    tint = iconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = format.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (format == ReportFormat.EXCEL_XLSX) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFF107C41).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "=SUM Formulas",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF107C41),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = format.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedFormat = format }
                            )
                        }
                    }
                }
            }

            // Google Sheets Quick Action Panel
            if (selectedFormat == ReportFormat.GOOGLE_SHEETS) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F9D58).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFF0F9D58).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color(0xFF0F9D58),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Google Sheets Quick Actions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F9D58)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Copy formatted tabular data to clipboard to paste straight into Google Sheets, or open a blank Google Sheet directly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onCopyToClipboard?.invoke(
                                        selectedScope,
                                        periodType,
                                        startTimestamp,
                                        endTimestamp,
                                        dateRangeLabel
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy TSV", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { onOpenGoogleSheets?.invoke() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Sheets", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Currency badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Base Currency: $currencyCode (${CurrencyFormatter.getCurrencySymbol(currencyCode)})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary "Generate & Export" Button
            Button(
                onClick = {
                    onGenerateAndShare(
                        selectedScope,
                        periodType,
                        selectedFormat,
                        startTimestamp,
                        endTimestamp,
                        dateRangeLabel
                    )
                },
                enabled = !isExporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("generate_and_share_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Exporting & Preparing...", style = MaterialTheme.typography.titleSmall)
                } else {
                    Icon(
                        when (selectedFormat) {
                            ReportFormat.PDF -> Icons.Default.PictureAsPdf
                            ReportFormat.EXCEL_XLSX -> Icons.Default.TableChart
                            ReportFormat.CSV -> Icons.Default.Dataset
                            ReportFormat.GOOGLE_SHEETS -> Icons.Default.CloudUpload
                        },
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Export ${selectedFormat.shortLabel}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
