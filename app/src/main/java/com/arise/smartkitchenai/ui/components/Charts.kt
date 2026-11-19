package com.arise.smartkitchenai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arise.smartkitchenai.ui.owner.ChartPoint
import com.arise.smartkitchenai.ui.theme.ChartGreenDark
import com.arise.smartkitchenai.ui.theme.ChartGreenLight
import com.arise.smartkitchenai.ui.theme.ChartTerracottaDark
import com.arise.smartkitchenai.ui.theme.ChartTerracottaLight
import com.arise.smartkitchenai.util.compact
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

enum class ChartColor { GREEN, TERRACOTTA }

@Composable
private fun ChartColor.resolve(): Color {
    val dark = isSystemInDarkTheme()
    return when (this) {
        ChartColor.GREEN -> if (dark) ChartGreenDark else ChartGreenLight
        ChartColor.TERRACOTTA -> if (dark) ChartTerracottaDark else ChartTerracottaLight
    }
}

/**
 * Single-series bar chart: thin bars anchored to the baseline with rounded tops, recessive
 * gridlines, compact y-axis labels, category + value labels underneath. Tap a bar to see its
 * exact value (the touch equivalent of a hover tooltip).
 */
@Composable
fun BarChart(
    points: List<ChartPoint>,
    color: ChartColor,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
    showValueLabels: Boolean = true,
) {
    val barColor = color.resolve()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisTextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    var selected by remember(points) { mutableStateOf<Int?>(null) }

    val maxValue = points.maxOfOrNull { it.values.firstOrNull() ?: 0.0 } ?: 0.0
    val axisMax = niceCeiling(maxValue)
    val ticks = listOf(0.0, axisMax / 3, axisMax * 2 / 3, axisMax)

    Column(modifier) {
        // Tooltip line for the tapped bar (kept in text ink, not the series colour).
        Text(
            selected?.let { "${points[it].label}: ${formatValue(points[it].values.first())}" } ?: "Tap a bar for details",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val axisWidth = 36.dp.toPx()
                        val slot = (size.width - axisWidth) / points.size.coerceAtLeast(1)
                        val index = ((offset.x - axisWidth) / slot).toInt()
                        selected = index.takeIf { it in points.indices && offset.x >= axisWidth }
                    }
                },
        ) {
            val axisWidth = 36.dp.toPx()
            val plotWidth = size.width - axisWidth
            val plotHeight = size.height - 4.dp.toPx()

            // Recessive gridlines + y labels.
            ticks.forEach { tick ->
                val y = plotHeight - (if (axisMax > 0) tick / axisMax else 0.0).toFloat() * plotHeight
                drawLine(gridColor, Offset(axisWidth, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                val layout = measurer.measure(tick.compact(), axisTextStyle)
                drawText(layout, topLeft = Offset(0f, (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)))
            }

            if (points.isEmpty() || axisMax <= 0) return@Canvas
            val slot = plotWidth / points.size
            val barWidth = (slot * 0.5f).coerceAtMost(28.dp.toPx())
            val radius = 4.dp.toPx()
            points.forEachIndexed { i, point ->
                val value = point.values.firstOrNull() ?: 0.0
                if (value <= 0) return@forEachIndexed
                val barHeight = (value / axisMax).toFloat() * plotHeight
                val left = axisWidth + slot * i + (slot - barWidth) / 2
                val top = plotHeight - barHeight
                val alpha = if (selected == null || selected == i) 1f else 0.45f
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left, top = top, right = left + barWidth, bottom = plotHeight,
                            topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                            bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                        )
                    )
                }
                drawPath(path, barColor.copy(alpha = alpha))
            }
            // Baseline.
            drawLine(gridColor, Offset(axisWidth, plotHeight), Offset(size.width, plotHeight), strokeWidth = 1.dp.toPx())
        }
        // Category (+ value) labels aligned with the bars.
        Row(Modifier.fillMaxWidth().padding(start = 36.dp, top = 4.dp)) {
            points.forEach { point ->
                Column(Modifier.weight(1f)) {
                    Text(
                        point.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                    )
                    if (showValueLabels) {
                        Text(
                            (point.values.firstOrNull() ?: 0.0).compact(),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Labelled horizontal bar for "where money is lost" breakdowns (share of the largest/total). */
@Composable
fun LossBar(label: String, value: String, fraction: Float, modifier: Modifier = Modifier) {
    val barColor = ChartColor.TERRACOTTA.resolve()
    Column(modifier) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** Rounds up to a "nice" axis maximum (1, 2, 2.5, 5 × 10^n) so gridlines land on round numbers. */
private fun niceCeiling(value: Double): Double {
    if (value <= 0) return 0.0
    val magnitude = 10.0.pow(ceil(log10(value)) - 1)
    val normalized = value / magnitude
    val nice = listOf(1.5, 3.0, 6.0, 7.5, 9.0, 15.0, 30.0, 60.0, 75.0, 90.0).first { it >= normalized }
    return nice * magnitude
}
