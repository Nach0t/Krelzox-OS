package dev.krelzox.launcher.ui.performance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.krelzox.launcher.R
import dev.krelzox.launcher.core.data.metrics.MetricId
import dev.krelzox.launcher.core.data.metrics.SystemMetrics
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import kotlinx.coroutines.flow.Flow
import java.util.Locale

private val PillShape = RoundedCornerShape(14.dp)

/**
 * Compact always-on system panel for the top bar: CPU · RAM · TEMP · FREE.
 * It collects the shared metrics flow itself, so only this panel recomposes on each
 * sample (every 2 s), never the rest of the bar or the screen.
 */
@Composable
fun PerformancePill(metrics: Flow<SystemMetrics>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val current by metrics.collectAsStateWithLifecycle(initialValue = null)
    val locale = LocalConfiguration.current.locales[0]
    val s = current?.snapshot
    val na = stringResource(R.string.not_available_short)
    val pending = "…"

    val cpu = when {
        s == null -> pending
        !s.isAvailable(MetricId.Cpu) -> na
        else -> s.cpuPercent?.let { PerformanceFormat.percent(it, locale) } ?: pending
    }
    val ram = s?.let { PerformanceFormat.percent(it.ramPercent, locale) } ?: pending
    val ramMb = s?.let { PerformanceFormat.megabytes(it.ramUsedBytes, locale) } ?: ""
    val temp = when {
        s == null -> pending
        !s.isAvailable(MetricId.Temperature) -> na
        else -> s.temperatureC?.let { PerformanceFormat.temperatureShort(it, locale) } ?: pending
    }
    val free = s?.takeIf { it.storageTotalBytes > 0 }?.let { PerformanceFormat.size(it.storageFreeBytes, locale) } ?: pending
    val description = stringResource(R.string.perf_pill_description, cpu, ram, ramMb, temp, free)

    Surface(
        onClick = onOpen,
        modifier = modifier
            .height(48.dp)
            .semantics { contentDescription = description },
        shape = ClickableSurfaceDefaults.shape(PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = KrelzoxColors.Surface.copy(alpha = 0.85f),
            contentColor = KrelzoxColors.OnBackground,
            focusedContainerColor = KrelzoxColors.SurfaceRaised,
            focusedContentColor = KrelzoxColors.OnBackground,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, KrelzoxColors.Outline), shape = PillShape),
            focusedBorder = Border(BorderStroke(1.5.dp, KrelzoxColors.IonBrush), shape = PillShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = KrelzoxSpacing.Medium - 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Cell(cpu, stringResource(R.string.perf_cpu), warn = (s?.cpuPercent ?: 0f) >= 85f)
            Divider()
            Cell(ram, ramMb, warn = (s?.ramPercent ?: 0f) >= 90f)
            Divider()
            Cell(temp, stringResource(R.string.perf_temp_short), warn = (s?.temperatureC ?: 0f) >= 75f)
            Divider()
            Cell(free, stringResource(R.string.perf_free), warn = (s?.storagePercent ?: 0f) >= 95f)
        }
    }
}

@Composable
private fun Cell(value: String, caption: String, warn: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (warn) KrelzoxColors.Error else KrelzoxColors.OnBackground,
            maxLines = 1,
        )
        Text(
            caption.uppercase(Locale.ROOT),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = KrelzoxColors.OnSurfaceSubtle,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .width(1.dp)
            .height(22.dp)
            .background(KrelzoxColors.Outline),
    )
}
