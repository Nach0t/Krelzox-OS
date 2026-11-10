package dev.nexium.launcher.ui.performance

import android.view.Gravity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.nexium.launcher.R
import dev.nexium.launcher.core.data.metrics.SystemMetrics
import dev.nexium.launcher.core.designsystem.ChartSeries
import dev.nexium.launcher.core.designsystem.NexiumButtonDefaults
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumIcons
import dev.nexium.launcher.core.designsystem.NexiumLineChart
import dev.nexium.launcher.core.designsystem.NexiumSpacing
import dev.nexium.launcher.core.designsystem.NexiumSparkline
import kotlinx.coroutines.flow.Flow

private val SheetWidth = 600.dp
private val ChipShape = RoundedCornerShape(14.dp)

/**
 * Expanded real-time view, dropped under the top bar on the right (where the compact
 * panel lives). ←/→ choose the metric, ↓ reaches the performance center, Back closes.
 */
@Composable
fun PerformanceSheet(metrics: Flow<SystemMetrics>, onOpenCenter: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        val density = LocalDensity.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setGravity(Gravity.TOP or Gravity.END)
                window.attributes = window.attributes.apply {
                    x = with(density) { 48.dp.roundToPx() }
                    y = with(density) { 80.dp.roundToPx() }
                    dimAmount = 0.5f
                }
            }
        }

        val current by metrics.collectAsStateWithLifecycle(initialValue = null)
        val locale = LocalConfiguration.current.locales[0]
        var selected by remember { mutableStateOf(PerfMetric.Cpu) }
        val first = remember { FocusRequester() }

        Column(
            Modifier
                .width(SheetWidth)
                .background(NexiumColors.SurfaceRaised, RoundedCornerShape(20.dp))
                .border(1.dp, NexiumColors.Outline, RoundedCornerShape(20.dp))
                .padding(NexiumSpacing.Large - 4.dp),
            verticalArrangement = Arrangement.spacedBy(NexiumSpacing.Medium - 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(NexiumIcons.Pulse, contentDescription = null, tint = NexiumColors.Cyan, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(NexiumSpacing.Small + 2.dp))
                Text(stringResource(R.string.perf_title), style = MaterialTheme.typography.titleLarge, color = NexiumColors.OnBackground)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(7.dp).background(NexiumColors.Cyan, CircleShape))
                Spacer(Modifier.width(NexiumSpacing.Small))
                Text(stringResource(R.string.perf_live), style = MaterialTheme.typography.labelMedium, color = NexiumColors.OnSurfaceMuted)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(NexiumSpacing.Medium - 4.dp)) {
                PerfMetric.entries.forEach { metric ->
                    MetricChip(
                        metric = metric,
                        value = metric.valueText(current, locale, stringResource(R.string.not_available)),
                        history = current?.let { metric.history(it) }.orEmpty(),
                        capacity = current?.historyCapacity ?: 2,
                        selected = metric == selected,
                        onFocused = { selected = metric },
                        modifier = Modifier
                            .weight(1f)
                            .then(if (metric == PerfMetric.Cpu) Modifier.focusRequester(first) else Modifier),
                    )
                }
            }

            MetricChartCard(selected, current, locale, chartHeight = 110.dp)

            Button(
                onClick = onOpenCenter,
                modifier = Modifier.fillMaxWidth(),
                colors = NexiumButtonDefaults.colors(),
                border = NexiumButtonDefaults.border(),
                shape = NexiumButtonDefaults.shape(),
            ) {
                Icon(NexiumIcons.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(NexiumSpacing.Small))
                Text(stringResource(R.string.perf_open_center))
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
}

/** Selectable metric with its live value and a sparkline; focusing it selects it (TV convention). */
@Composable
private fun MetricChip(
    metric: PerfMetric,
    value: String,
    history: List<Float>,
    capacity: Int,
    selected: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onFocused,
        modifier = modifier
            .height(76.dp)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(ChipShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) NexiumColors.Electric.copy(alpha = 0.16f) else NexiumColors.Surface,
            contentColor = NexiumColors.OnBackground,
            focusedContainerColor = NexiumColors.Electric.copy(alpha = 0.22f),
            focusedContentColor = NexiumColors.OnBackground,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.dp, NexiumColors.Electric), shape = ChipShape) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, NexiumColors.IonBrush), shape = ChipShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(metric.icon, contentDescription = null, tint = metric.color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(metric.label), style = MaterialTheme.typography.labelMedium, color = NexiumColors.OnSurfaceMuted)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleMedium, color = NexiumColors.OnBackground, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                NexiumSparkline(
                    values = history,
                    yMin = metric.yMin,
                    yMax = metric.yMax(history),
                    capacity = capacity,
                    color = metric.color,
                    modifier = Modifier.weight(1f).height(26.dp),
                )
            }
        }
    }
}

/** Large chart with current / max / average over the history window. Shared with the center. */
@Composable
internal fun MetricChartCard(
    metric: PerfMetric,
    metrics: SystemMetrics?,
    locale: java.util.Locale,
    chartHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    /** Half-width cards show only current and max. */
    compact: Boolean = false,
) {
    val history = metrics?.let { metric.history(it) }.orEmpty()
    val na = stringResource(R.string.not_available)
    Column(
        modifier
            .fillMaxWidth()
            .background(NexiumColors.Surface, RoundedCornerShape(16.dp))
            .padding(NexiumSpacing.Medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(metric.chartTitle), style = MaterialTheme.typography.labelLarge, color = NexiumColors.OnBackground)
            Spacer(Modifier.weight(1f))
            Stat(stringResource(R.string.perf_current), metric.valueText(metrics, locale, na))
            Spacer(Modifier.width(NexiumSpacing.Large))
            Stat(stringResource(R.string.perf_max), history.maxOrNull()?.let { metric.format(it, locale) } ?: "—")
            if (!compact) {
                Spacer(Modifier.width(NexiumSpacing.Large))
                Stat(stringResource(R.string.perf_avg), history.takeIf { it.isNotEmpty() }?.average()?.toFloat()?.let { metric.format(it, locale) } ?: "—")
            }
        }
        Spacer(Modifier.height(NexiumSpacing.Small + 2.dp))
        if (metrics != null && !metric.isAvailable(metrics)) {
            Box(Modifier.fillMaxWidth().height(chartHeight), contentAlignment = Alignment.Center) {
                Text(na, style = MaterialTheme.typography.bodyLarge, color = NexiumColors.OnSurfaceMuted)
            }
        } else {
            NexiumLineChart(
                series = listOf(ChartSeries(history, metric.color)),
                yMin = metric.yMin,
                yMax = metric.yMax(history),
                capacity = metrics?.historyCapacity ?: 2,
                yLabel = { metric.axisLabel(it) },
                modifier = Modifier.fillMaxWidth().height(chartHeight),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Spacer(Modifier.width(40.dp))
            Text(stringResource(R.string.perf_axis_start), style = MaterialTheme.typography.labelSmall, color = NexiumColors.OnSurfaceSubtle)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.perf_axis_now), style = MaterialTheme.typography.labelSmall, color = NexiumColors.OnSurfaceSubtle)
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = NexiumColors.OnSurfaceSubtle)
        Text(value, style = MaterialTheme.typography.labelLarge, color = NexiumColors.OnBackground)
    }
}

/** Metrics with history, shared by the sheet and the center. */
internal enum class PerfMetric(
    val label: Int,
    val chartTitle: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val yMin: Float,
) {
    Cpu(R.string.perf_cpu, R.string.cpu_usage, NexiumIcons.Cpu, 0f),
    Ram(R.string.perf_ram, R.string.ram_usage, NexiumIcons.Memory, 0f),
    Temperature(R.string.perf_temp, R.string.perf_temp, NexiumIcons.Thermometer, 30f);

    /** Read on use, not stored: CPU and RAM follow the active theme's accent. */
    val color: Color
        get() = when (this) {
            Cpu -> NexiumColors.Cyan
            Ram -> NexiumColors.Electric
            Temperature -> Color(0xFF7DD3FC)
        }

    fun history(m: SystemMetrics): List<Float> = when (this) {
        Cpu -> m.cpuHistory
        Ram -> m.ramHistory
        Temperature -> m.temperatureHistory
    }

    /** Percentages use a fixed 0–100 scale; temperature 30–90 °C (the Pi 5 throttles at 85 °C). */
    fun yMax(history: List<Float>): Float = when (this) {
        Temperature -> maxOf(90f, (history.maxOrNull() ?: 0f) + 5f)
        else -> 100f
    }

    fun isAvailable(m: SystemMetrics): Boolean = when (this) {
        Cpu -> m.snapshot.isAvailable(dev.nexium.launcher.core.data.metrics.MetricId.Cpu)
        Ram -> true
        Temperature -> m.snapshot.isAvailable(dev.nexium.launcher.core.data.metrics.MetricId.Temperature)
    }

    fun format(value: Float, locale: java.util.Locale): String = when (this) {
        Temperature -> PerformanceFormat.temperature(value, locale)
        else -> PerformanceFormat.percent(value, locale)
    }

    fun axisLabel(value: Float): String = when (this) {
        Temperature -> "${value.toInt()}°"
        else -> "${value.toInt()}%"
    }

    fun valueText(m: SystemMetrics?, locale: java.util.Locale, notAvailable: String): String {
        if (m == null) return "…"
        if (!isAvailable(m)) return notAvailable
        val v = when (this) {
            Cpu -> m.snapshot.cpuPercent
            Ram -> m.snapshot.ramPercent
            Temperature -> m.snapshot.temperatureC
        } ?: return "…"
        return format(v, locale)
    }
}
