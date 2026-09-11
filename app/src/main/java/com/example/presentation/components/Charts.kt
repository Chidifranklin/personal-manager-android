package com.example.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberPayable
import com.example.ui.theme.EmeraldIncome
import com.example.ui.theme.VioletReceivable
import com.example.util.CurrencyFormatter
import java.util.Locale

data class DonutSlice(
    val label: String,
    val value: Double,
    val color: Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseDonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    totalLabel: String = "Total Spent",
    currencyCode: String = "USD"
) {
    val total = remember(slices) { slices.sumOf { it.value } }
    var selectedSlice by remember { mutableStateOf<DonutSlice?>(null) }
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(slices) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, animationSpec = tween(750))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 32.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val centerOffset = Offset(size.width / 2, size.height / 2)
                val topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius)
                val arcSize = Size(radius * 2, radius * 2)

                if (total <= 0.001) {
                    drawArc(
                        color = Color.LightGray.copy(alpha = 0.3f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    var currentStartAngle = -90f
                    for (slice in slices) {
                        val sweepAngle = ((slice.value / total).toFloat() * 360f) * animatedProgress.value
                        drawArc(
                            color = slice.color,
                            startAngle = currentStartAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                        currentStartAngle += sweepAngle
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = selectedSlice?.label ?: totalLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val displayedAmount = selectedSlice?.value ?: total
                Text(
                    text = CurrencyFormatter.format(displayedAmount, currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (total > 0 && selectedSlice != null) {
                    val pct = (selectedSlice!!.value / total * 100).toInt()
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Legend chips
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slices.forEach { slice ->
                val isSelected = selectedSlice == slice
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) slice.color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clickable { selectedSlice = if (isSelected) null else slice }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(slice.color)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = slice.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

data class TrendDataPoint(
    val timestamp: Long,
    val dateLabel: String,
    val amount: Double,
    val note: String? = null
)

private fun formatCompactAmount(amount: Double, currencyCode: String): String {
    val symbol = CurrencyFormatter.getCurrencySymbol(currencyCode)
    val absVal = kotlin.math.abs(amount)
    val sign = if (amount < 0) "-" else ""
    return when {
        absVal >= 1_000_000 -> String.format(Locale.getDefault(), "%s%s%.1fM", sign, symbol, absVal / 1_000_000)
        absVal >= 10_000 -> String.format(Locale.getDefault(), "%s%s%.0fk", sign, symbol, absVal / 1_000)
        absVal >= 1_000 -> String.format(Locale.getDefault(), "%s%s%.1fk", sign, symbol, absVal / 1_000)
        else -> String.format(Locale.getDefault(), "%s%s%.0f", sign, symbol, absVal)
    }
}

@Composable
fun TrendLineChart(
    dataPoints: List<TrendDataPoint>,
    currencyCode: String = "USD",
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    if (dataPoints.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    // Interactive selected point (defaults to the latest point on the far right)
    var selectedIndex by remember(dataPoints) {
        mutableStateOf<Int?>(dataPoints.indices.lastOrNull())
    }

    val maxVal = remember(dataPoints) {
        val highest = dataPoints.maxOfOrNull { it.amount } ?: 100.0
        if (highest <= 0.0) 100.0 else highest * 1.12 // 12% headroom
    }
    val minVal = remember(dataPoints) {
        val lowest = dataPoints.minOfOrNull { it.amount } ?: 0.0
        if (lowest >= 0.0) 0.0 else lowest * 1.12
    }
    val range = (maxVal - minVal).coerceAtLeast(1.0)

    Column(modifier = modifier.fillMaxWidth()) {
        // Top Selected Point Summary Pill
        selectedIndex?.let { idx ->
            if (idx in dataPoints.indices) {
                val pt = dataPoints[idx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(lineColor)
                        )
                        Text(
                            text = "Date: ${pt.dateLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = CurrencyFormatter.format(pt.amount, currencyCode),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // Main Chart Canvas with X and Y Axes
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(dataPoints) {
                    detectTapGestures { tapOffset ->
                        val leftMargin = 58.dp.toPx()
                        val rightMargin = 16.dp.toPx()
                        val plotWidth = size.width - leftMargin - rightMargin
                        if (plotWidth > 0 && dataPoints.size > 1) {
                            val relativeX = (tapOffset.x - leftMargin).coerceIn(0f, plotWidth)
                            val fraction = relativeX / plotWidth
                            val closestIndex = (fraction * (dataPoints.size - 1)).toInt()
                                .coerceIn(0, dataPoints.size - 1)
                            selectedIndex = closestIndex
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height

            val leftMargin = 58.dp.toPx()
            val rightMargin = 16.dp.toPx()
            val topMargin = 16.dp.toPx()
            val bottomMargin = 28.dp.toPx()

            val plotWidth = width - leftMargin - rightMargin
            val plotHeight = height - topMargin - bottomMargin
            val plotLeft = leftMargin
            val plotRight = width - rightMargin
            val plotTop = topMargin
            val plotBottom = height - bottomMargin

            // 1. Draw Y-Axis & Horizontal Grid Lines (4 steps: 0%, 33%, 66%, 100%)
            val ySteps = 4
            for (step in 0 until ySteps) {
                val frac = step.toFloat() / (ySteps - 1)
                val yVal = minVal + (frac * range)
                val yPos = plotBottom - (frac * plotHeight)

                // Dotted grid line extending across plot area
                drawLine(
                    color = gridColor,
                    start = Offset(plotLeft, yPos),
                    end = Offset(plotRight, yPos),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                )

                // Tick mark on Y axis
                drawLine(
                    color = axisColor,
                    start = Offset(plotLeft - 4.dp.toPx(), yPos),
                    end = Offset(plotLeft, yPos),
                    strokeWidth = 1.2.dp.toPx()
                )

                // Formatted Amount Label on Y Axis
                val formattedAmount = formatCompactAmount(yVal, currencyCode)
                val textLayout = textMeasurer.measure(
                    text = formattedAmount,
                    style = TextStyle(fontSize = 10.sp, color = labelColor, fontWeight = FontWeight.Medium)
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(
                        x = plotLeft - textLayout.size.width - 6.dp.toPx(),
                        y = yPos - (textLayout.size.height / 2)
                    )
                )
            }

            // 2. Solid Axis Lines (Vertical Y-Axis and Horizontal X-Axis)
            // Y-Axis line
            drawLine(
                color = axisColor,
                start = Offset(plotLeft, plotTop),
                end = Offset(plotLeft, plotBottom),
                strokeWidth = 1.5.dp.toPx()
            )
            // X-Axis line
            drawLine(
                color = axisColor,
                start = Offset(plotLeft, plotBottom),
                end = Offset(plotRight, plotBottom),
                strokeWidth = 1.5.dp.toPx()
            )

            // 3. Compute Data Coordinates moving chronologically from Left to Right
            val coordinates = dataPoints.mapIndexed { index, point ->
                val x = if (dataPoints.size > 1) {
                    plotLeft + (index.toFloat() / (dataPoints.size - 1)) * plotWidth
                } else {
                    plotLeft + (plotWidth / 2)
                }
                val normalizedY = ((point.amount - minVal) / range).toFloat().coerceIn(0f, 1f)
                val y = plotBottom - (normalizedY * plotHeight)
                Offset(x, y)
            }

            // 4. Draw X-Axis Dates and Tick Marks (Moving Left to Right)
            val stepSize = when {
                dataPoints.size <= 7 -> 1
                dataPoints.size <= 14 -> 2
                else -> (dataPoints.size / 6).coerceAtLeast(1)
            }

            dataPoints.forEachIndexed { index, point ->
                if (index % stepSize == 0 || index == dataPoints.size - 1) {
                    val ptX = coordinates[index].x

                    // Tick mark on X axis
                    drawLine(
                        color = axisColor,
                        start = Offset(ptX, plotBottom),
                        end = Offset(ptX, plotBottom + 4.dp.toPx()),
                        strokeWidth = 1.2.dp.toPx()
                    )

                    // Date label below tick mark
                    val textLayout = textMeasurer.measure(
                        text = point.dateLabel,
                        style = TextStyle(fontSize = 10.sp, color = labelColor, fontWeight = FontWeight.Normal)
                    )
                    // Clamp X to avoid clipping outside canvas bounds
                    val textX = (ptX - (textLayout.size.width / 2))
                        .coerceIn(plotLeft - 4.dp.toPx(), width - textLayout.size.width)
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(x = textX, y = plotBottom + 6.dp.toPx())
                    )
                }
            }

            // 5. Draw Trend Curve and Gradient Fill (Left to Right)
            if (coordinates.isNotEmpty()) {
                val path = Path()
                val fillPath = Path()

                path.moveTo(coordinates[0].x, coordinates[0].y)
                fillPath.moveTo(coordinates[0].x, plotBottom)
                fillPath.lineTo(coordinates[0].x, coordinates[0].y)

                for (i in 1 until coordinates.size) {
                    val prev = coordinates[i - 1]
                    val curr = coordinates[i]
                    val cx = (prev.x + curr.x) / 2
                    path.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                    fillPath.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                }

                fillPath.lineTo(coordinates.last().x, plotBottom)
                fillPath.close()

                // Gradient area under the line
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                        startY = plotTop,
                        endY = plotBottom
                    )
                )

                // Smooth trend stroke
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                )

                // Data points circles
                coordinates.forEachIndexed { index, pt ->
                    val isSelected = (index == selectedIndex)
                    if (isSelected) {
                        // Vertical guideline for selected point
                        drawLine(
                            color = lineColor.copy(alpha = 0.4f),
                            start = Offset(pt.x, plotBottom),
                            end = Offset(pt.x, pt.y),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                        // Outer glowing halo
                        drawCircle(color = lineColor.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = pt)
                        drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = pt)
                        drawCircle(color = lineColor, radius = 3.8.dp.toPx(), center = pt)
                    } else {
                        drawCircle(color = Color.White, radius = 4.dp.toPx(), center = pt)
                        drawCircle(color = lineColor, radius = 2.5.dp.toPx(), center = pt)
                    }
                }
            }
        }
    }
}

@Composable
fun TrendLineChart(
    points: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    val dataPoints = remember(points, labels) {
        points.mapIndexed { idx, value ->
            TrendDataPoint(
                timestamp = System.currentTimeMillis() - ((points.size - 1 - idx) * 86400000L),
                dateLabel = labels.getOrElse(idx) { "P$idx" },
                amount = value
            )
        }
    }
    TrendLineChart(
        dataPoints = dataPoints,
        currencyCode = "USD",
        modifier = modifier,
        lineColor = lineColor
    )
}

@Composable
fun LentVsBorrowedRatioBar(
    totalLent: Double,
    totalBorrowed: Double,
    modifier: Modifier = Modifier,
    currencyCode: String = "USD"
) {
    val total = (totalLent + totalBorrowed).coerceAtLeast(0.01)
    val lentRatio = (totalLent / total).toFloat()
    val borrowedRatio = (totalBorrowed / total).toFloat()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(VioletReceivable))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Lent (Receivables): ${CurrencyFormatter.format(totalLent, currencyCode)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AmberPayable))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Borrowed: ${CurrencyFormatter.format(totalBorrowed, currencyCode)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (lentRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(lentRatio.coerceAtLeast(0.01f))
                            .background(VioletReceivable)
                    )
                }
                if (borrowedRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(borrowedRatio.coerceAtLeast(0.01f))
                            .background(AmberPayable)
                    )
                }
            }
        }
    }
}
