package com.lucho314.spotter.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucho314.spotter.core.designsystem.theme.SpotterColors

/** One labeled point in a [LineChart]. */
data class ChartPoint(val label: String, val value: Float)

/**
 * A minimal line chart drawn with [Canvas] (ADR A10: no charting library dependency). Callers
 * should only call this with a non-empty [points] list; an empty chart isn't meaningfully drawable
 * (no axis range), so screens show an empty state instead of calling this.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = SpotterColors.Secondary,
    maxLabels: Int = 8,
) {
    if (points.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = SpotterColors.OnSurfaceVariant, fontSize = 10.sp)
    val axisColor = SpotterColors.OutlineVariant
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { 2.dp.toPx() }
    val pointRadiusPx = with(density) { 4.dp.toPx() }
    val horizontalPaddingPx = with(density) { 8.dp.toPx() }
    val labelPaddingPx = with(density) { 4.dp.toPx() }

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val left = horizontalPaddingPx
        val right = size.width - horizontalPaddingPx
        val top = 0f
        val bottom = size.height - 24f // leaves room for the x-axis labels

        val values = points.map { it.value }
        val min = values.min()
        val max = values.max()

        val xs = LineChartGeometry.xPositions(points.size, left, right)
        val ys = values.map { LineChartGeometry.yPosition(it, min, max, top, bottom) }

        drawLine(color = axisColor, start = Offset(left, bottom), end = Offset(right, bottom), strokeWidth = 1f)
        drawLine(color = axisColor, start = Offset(left, top), end = Offset(left, bottom), strokeWidth = 1f)

        val path = Path()
        xs.forEachIndexed { index, x ->
            val y = ys[index]
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = strokeWidthPx))

        xs.forEachIndexed { index, x ->
            drawCircle(color = lineColor, radius = pointRadiusPx, center = Offset(x, ys[index]))
        }

        val labelIndices = LineChartGeometry.labelIndices(points.size, maxLabels)
        labelIndices.forEach { index ->
            val layout = textMeasurer.measure(points[index].label, labelStyle)
            val x = (xs[index] - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, bottom + labelPaddingPx))
        }
    }
}
