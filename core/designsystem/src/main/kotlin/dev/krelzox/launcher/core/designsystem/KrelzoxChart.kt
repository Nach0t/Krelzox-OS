package dev.krelzox.launcher.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One line of a chart. Values are plotted right-aligned: the newest point is at the right edge. */
@Immutable
data class ChartSeries(val values: List<Float>, val color: Color, val fill: Boolean = true)

/**
 * Lightweight real-time line chart for TV: horizontal grid, optional area fill, a dot on
 * the latest value and large axis labels. Plain Canvas drawing (no animation, no blur):
 * it redraws only when its data changes, i.e. once per sample.
 *
 * [capacity] is the number of points the full width represents, so the chart scrolls in
 * from the right as history fills up instead of stretching.
 */
@Composable
fun KrelzoxLineChart(
    series: List<ChartSeries>,
    yMin: Float,
    yMax: Float,
    capacity: Int,
    modifier: Modifier = Modifier,
    gridLines: Int = 4,
    yLabel: ((Float) -> String)? = null,
    labelColor: Color = KrelzoxColors.OnSurfaceSubtle,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = remember(labelColor) { TextStyle(color = labelColor, fontSize = 12.sp, fontFamily = Inter) }
    Canvas(modifier) {
        val range = (yMax - yMin).takeIf { it > 0f } ?: 1f
        // Gutter sized to the widest label (at least 40 dp, so charts side by side align).
        val labels = if (yLabel != null) {
            (0..gridLines).map { i -> measurer.measure(yLabel(yMax - range * (i / gridLines.toFloat())), labelStyle) }
        } else {
            emptyList()
        }
        val gap = 8.dp.toPx()
        val labelWidth = if (yLabel != null) maxOf(40.dp.toPx(), (labels.maxOfOrNull { it.size.width } ?: 0) + gap) else 0f
        val left = labelWidth
        val width = size.width - left
        val height = size.height

        // Grid and labels.
        for (i in 0..gridLines) {
            val fraction = i / gridLines.toFloat()
            val y = height * fraction
            drawLine(
                color = KrelzoxColors.Outline.copy(alpha = if (i == gridLines) 0.9f else 0.45f),
                start = Offset(left, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
            if (yLabel != null) {
                val layout = labels[i]
                drawText(
                    layout,
                    topLeft = Offset(
                        labelWidth - layout.size.width - gap,
                        (y - layout.size.height / 2f).coerceIn(0f, height - layout.size.height),
                    ),
                )
            }
        }

        series.forEach { s -> drawSeries(s, left, width, height, yMin, range, capacity) }
    }
}

private fun DrawScope.drawSeries(
    s: ChartSeries,
    left: Float,
    width: Float,
    height: Float,
    yMin: Float,
    range: Float,
    capacity: Int,
) {
    if (s.values.isEmpty()) return
    val step = width / (capacity - 1).coerceAtLeast(1)
    val count = s.values.size.coerceAtMost(capacity)
    val values = s.values.takeLast(count)
    val startX = left + width - step * (count - 1)
    fun point(i: Int) = Offset(
        startX + step * i,
        height - ((values[i] - yMin) / range).coerceIn(0f, 1f) * height,
    )

    val line = Path().apply {
        moveTo(point(0).x, point(0).y)
        for (i in 1 until count) lineTo(point(i).x, point(i).y)
    }
    if (s.fill && count > 1) {
        val area = Path().apply {
            addPath(line)
            lineTo(point(count - 1).x, height)
            lineTo(point(0).x, height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.28f), s.color.copy(alpha = 0f)), 0f, height))
    }
    drawPath(
        line,
        color = s.color,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    val last = point(count - 1)
    drawCircle(KrelzoxColors.Background, radius = 5.dp.toPx(), center = last)
    drawCircle(s.color, radius = 3.5.dp.toPx(), center = last)
}

/** Tiny chart without axes, for chips and tiles. */
@Composable
fun KrelzoxSparkline(values: List<Float>, yMin: Float, yMax: Float, capacity: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawSeries(ChartSeries(values, color, fill = true), 0f, size.width, size.height, yMin, (yMax - yMin).takeIf { it > 0f } ?: 1f, capacity)
    }
}
