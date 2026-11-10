package dev.nexium.launcher.ui.performance

import android.app.role.RoleManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.nexium.launcher.R
import dev.nexium.launcher.core.data.NetworkDetails
import dev.nexium.launcher.core.data.NetworkStatus
import dev.nexium.launcher.core.data.metrics.MetricId
import dev.nexium.launcher.core.data.metrics.SystemMetrics
import dev.nexium.launcher.core.designsystem.ChartSeries
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumIcons
import dev.nexium.launcher.core.designsystem.NexiumLineChart
import dev.nexium.launcher.core.designsystem.NexiumSpacing
import kotlinx.coroutines.flow.Flow
import java.util.Locale

private enum class Category(val label: Int, val icon: ImageVector) {
    Processor(R.string.cat_processor, NexiumIcons.Cpu),
    Memory(R.string.cat_memory, NexiumIcons.Memory),
    Storage(R.string.cat_storage, NexiumIcons.Storage),
    System(R.string.cat_system, NexiumIcons.Info),
    Network(R.string.cat_network, NexiumIcons.Wifi),
}

private val RailWidth = 264.dp
private val CardShape = RoundedCornerShape(14.dp)

/**
 * "Sistema y rendimiento": category rail (↑/↓, selected on focus) + read-only detail.
 * Every value comes from the system; anything Android does not expose on this build is
 * shown as "No disponible".
 */
@Composable
fun PerformanceCenter(
    metrics: Flow<SystemMetrics>,
    network: Flow<NetworkDetails>,
    appCount: Int,
    modifier: Modifier = Modifier,
) {
    val current by metrics.collectAsStateWithLifecycle(initialValue = null)
    val net by network.collectAsStateWithLifecycle(initialValue = null)
    val locale = LocalConfiguration.current.locales[0]
    var selected by remember { mutableStateOf(Category.Processor) }
    val first = remember { FocusRequester() }

    Column(modifier.fillMaxSize().padding(top = NexiumSpacing.Small)) {
        Text(stringResource(R.string.perf_center_title), style = MaterialTheme.typography.headlineMedium, color = NexiumColors.OnBackground)
        Text(stringResource(R.string.perf_center_subtitle), style = MaterialTheme.typography.bodyMedium, color = NexiumColors.OnSurfaceMuted)
        Spacer(Modifier.height(NexiumSpacing.Medium))
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(RailWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Category.entries.forEachIndexed { i, category ->
                    RailItem(
                        icon = category.icon,
                        label = stringResource(category.label),
                        value = railValue(category, current, net, locale),
                        selected = category == selected,
                        onFocused = { selected = category },
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
            Spacer(Modifier.width(NexiumSpacing.Large - 4.dp))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (selected) {
                    Category.Processor -> ProcessorDetail(current, locale)
                    Category.Memory -> MemoryDetail(current, locale)
                    Category.Storage -> StorageDetail(current, locale)
                    Category.System -> SystemDetail(current, net, appCount)
                    Category.Network -> NetworkDetail(current, net, locale)
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
}

@Composable
private fun railValue(c: Category, m: SystemMetrics?, net: NetworkDetails?, locale: Locale): String {
    val s = m?.snapshot ?: return "…"
    return when (c) {
        Category.Processor -> if (!s.isAvailable(MetricId.Cpu)) stringResource(R.string.not_available_short)
        else s.cpuPercent?.let { PerformanceFormat.percent(it, locale) } ?: "…"
        Category.Memory -> PerformanceFormat.percent(s.ramPercent, locale)
        Category.Storage -> PerformanceFormat.size(s.storageFreeBytes, locale)
        Category.System -> PerformanceFormat.duration(s.uptimeMs)
        Category.Network -> net?.let { stringResource(networkTypeLabel(it.status)) } ?: "…"
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, value: String, selected: Boolean, onFocused: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onFocused,
        modifier = modifier.fillMaxWidth().height(52.dp).onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) NexiumColors.Surface else NexiumColors.Background.copy(alpha = 0f),
            contentColor = if (selected) NexiumColors.OnBackground else NexiumColors.OnSurfaceMuted,
            focusedContainerColor = NexiumColors.Electric.copy(alpha = 0.28f),
            focusedContentColor = NexiumColors.OnBackground,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.5.dp, NexiumColors.Cyan), shape = CardShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(20.dp).background(if (selected) NexiumColors.Cyan else NexiumColors.Background.copy(alpha = 0f), CircleShape))
            Spacer(Modifier.width(10.dp))
            Icon(icon, contentDescription = null, tint = if (selected) NexiumColors.Cyan else NexiumColors.OnSurfaceMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.labelMedium, color = NexiumColors.OnSurfaceMuted, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- details

@Composable
private fun ProcessorDetail(m: SystemMetrics?, locale: Locale) {
    val s = m?.snapshot
    val na = stringResource(R.string.not_available)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(stringResource(R.string.cpu_usage), PerfMetric.Cpu.valueText(m, locale, na), stringResource(R.string.cpu_cores, s?.cpuCores ?: 0), weight = 0.85f)
            StatTile(stringResource(R.string.perf_temp), PerfMetric.Temperature.valueText(m, locale, na), fanText(m, locale))
            StatTile(
                stringResource(R.string.cpu_freq),
                when {
                    s == null -> "…"
                    !s.isAvailable(MetricId.Frequency) -> na
                    else -> s.cpuFreqMHz?.let { PerformanceFormat.frequency(it, locale) } ?: "…"
                },
                s?.cpuMaxFreqMHz?.let { stringResource(R.string.cpu_freq_max, PerformanceFormat.frequency(it, locale)) },
            )
            StatTile(
                stringResource(R.string.cpu_load),
                when {
                    s == null -> "…"
                    !s.isAvailable(MetricId.Load) -> na
                    else -> s.load?.firstOrNull()?.let { PerformanceFormat.decimal(it, 2, locale) } ?: "…"
                },
                // The big value is the 1-minute average; the detail carries the 5- and 15-minute ones.
                s?.load?.drop(1)?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.cpu_load_detail, PerformanceFormat.load(it, locale)) },
                weight = 1.15f,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricChartCard(PerfMetric.Cpu, m, locale, chartHeight = 118.dp, compact = true, modifier = Modifier.weight(1f))
            MetricChartCard(PerfMetric.Temperature, m, locale, chartHeight = 118.dp, compact = true, modifier = Modifier.weight(1f))
        }
        SourceNote(stringResource(R.string.source_processor))
    }
}

@Composable
private fun fanText(m: SystemMetrics?, locale: Locale): String? {
    val s = m?.snapshot ?: return null
    if (!s.isAvailable(MetricId.Fan)) return null
    return s.fanRpm?.let { stringResource(R.string.cpu_fan, java.text.NumberFormat.getIntegerInstance(locale).format(it)) }
}

@Composable
private fun MemoryDetail(m: SystemMetrics?, locale: Locale) {
    val s = m?.snapshot
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(stringResource(R.string.ram_total), s?.let { PerformanceFormat.size(it.ramTotalBytes, locale) } ?: "…", null)
            StatTile(stringResource(R.string.ram_used), s?.let { PerformanceFormat.size(it.ramUsedBytes, locale) } ?: "…", s?.let { PerformanceFormat.megabytes(it.ramUsedBytes, locale) })
            StatTile(stringResource(R.string.ram_available), s?.let { PerformanceFormat.size(it.ramAvailableBytes, locale) } ?: "…", null)
            StatTile(
                stringResource(R.string.ram_usage),
                s?.let { PerformanceFormat.percent(it.ramPercent, locale) } ?: "…",
                s?.let { stringResource(if (it.lowMemory) R.string.ram_low_yes else R.string.ram_low_no) },
            )
        }
        MetricChartCard(PerfMetric.Ram, m, locale, chartHeight = 118.dp)
        SourceNote(stringResource(R.string.source_memory))
    }
}

@Composable
private fun StorageDetail(m: SystemMetrics?, locale: Locale) {
    val s = m?.snapshot
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(stringResource(R.string.storage_total), s?.let { PerformanceFormat.size(it.storageTotalBytes, locale) } ?: "…", null)
            StatTile(stringResource(R.string.storage_used), s?.let { PerformanceFormat.size(it.storageUsedBytes, locale) } ?: "…", null)
            StatTile(stringResource(R.string.storage_free), s?.let { PerformanceFormat.size(it.storageFreeBytes, locale) } ?: "…", null)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(NexiumColors.Surface, CardShape)
                .padding(NexiumSpacing.Medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.storage_internal), style = MaterialTheme.typography.labelLarge, color = NexiumColors.OnBackground)
                Spacer(Modifier.weight(1f))
                Text(
                    s?.let { stringResource(R.string.storage_used_percent, PerformanceFormat.percent(it.storagePercent, locale)) } ?: "…",
                    style = MaterialTheme.typography.labelLarge,
                    color = NexiumColors.OnSurfaceMuted,
                )
            }
            Spacer(Modifier.height(NexiumSpacing.Medium))
            UsageBar(fraction = (s?.storagePercent ?: 0f) / 100f)
        }
        SourceNote(stringResource(R.string.source_storage))
    }
}

@Composable
private fun UsageBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(NexiumColors.Background),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(NexiumColors.IonHorizontal, RoundedCornerShape(11.dp)),
        )
    }
}

@Composable
private fun SystemDetail(m: SystemMetrics?, net: NetworkDetails?, appCount: Int) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    val isHome = remember { context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true }
    val na = stringResource(R.string.not_available)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(stringResource(R.string.sys_device), Modifier.weight(1f)) {
            InfoLine(stringResource(R.string.sys_uptime), m?.snapshot?.let { PerformanceFormat.duration(it.uptimeMs) } ?: "…")
            InfoLine(stringResource(R.string.sys_android), "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            InfoLine(stringResource(R.string.sys_patch), Build.VERSION.SECURITY_PATCH.ifBlank { na })
            InfoLine(stringResource(R.string.sys_nexium), version ?: na)
            InfoLine(stringResource(R.string.sys_model), Build.MODEL)
            InfoLine(stringResource(R.string.sys_manufacturer), Build.MANUFACTURER)
            InfoLine(stringResource(R.string.sys_build), Build.ID.replace(".", ".​"))
        }
        InfoCard(stringResource(R.string.sys_services), Modifier.weight(1f)) {
            ServiceLine(stringResource(R.string.svc_home), stringResource(if (isHome) R.string.status_yes else R.string.status_no), ok = isHome)
            ServiceLine(stringResource(R.string.svc_crash), stringResource(R.string.status_active), ok = true)
            ServiceLine(stringResource(R.string.svc_apps), stringResource(R.string.svc_apps_value, appCount), ok = appCount > 0)
            ServiceLine(
                stringResource(R.string.svc_monitor),
                if (m != null) stringResource(R.string.svc_monitor_value) else "…",
                ok = m != null,
            )
            ServiceLine(
                stringResource(R.string.svc_network),
                when {
                    net == null -> "…"
                    net.status == NetworkStatus.Offline -> stringResource(R.string.net_offline)
                    net.validated -> stringResource(R.string.net_validated)
                    else -> stringResource(R.string.net_not_validated)
                },
                ok = net?.validated == true,
            )
        }
    }
}

@Composable
private fun NetworkDetail(m: SystemMetrics?, net: NetworkDetails?, locale: Locale) {
    val na = stringResource(R.string.not_available)
    val s = m?.snapshot
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                stringResource(R.string.net_status),
                when {
                    net == null -> "…"
                    net.status == NetworkStatus.Offline -> stringResource(R.string.net_offline)
                    net.validated -> stringResource(R.string.net_connected)
                    else -> stringResource(R.string.net_not_validated)
                },
                null,
                valueSize = 20,
                weight = 1.1f,
            )
            StatTile(stringResource(R.string.net_type), net?.let { stringResource(networkTypeLabel(it.status)) } ?: "…", net?.interfaceName, weight = 0.8f)
            StatTile(stringResource(R.string.net_ip), net?.let { it.ipv4 ?: na } ?: "…", null, valueSize = 18, weight = 1.4f)
            StatTile(
                stringResource(R.string.net_link),
                when {
                    net == null -> "…"
                    net.status != NetworkStatus.Wifi -> "—"
                    else -> net.wifiLinkMbps?.let { "$it Mbps" } ?: na
                },
                stringResource(R.string.net_link_note),
                valueSize = 20,
                weight = 1.1f,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(NexiumColors.Surface, CardShape)
                .padding(NexiumSpacing.Medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.net_traffic), style = MaterialTheme.typography.labelLarge, color = NexiumColors.OnBackground)
                Spacer(Modifier.weight(1f))
                if (s != null && !s.isAvailable(MetricId.Traffic)) {
                    Text(na, style = MaterialTheme.typography.labelLarge, color = NexiumColors.OnSurfaceMuted)
                } else {
                    Legend(stringResource(R.string.net_down), s?.netRxBytesPerSec?.let { PerformanceFormat.rate(it, locale) } ?: "…", NexiumColors.Cyan)
                    Spacer(Modifier.width(NexiumSpacing.Large))
                    Legend(stringResource(R.string.net_up), s?.netTxBytesPerSec?.let { PerformanceFormat.rate(it, locale) } ?: "…", NexiumColors.Electric)
                }
            }
            Spacer(Modifier.height(NexiumSpacing.Small + 2.dp))
            val rx = m?.netRxHistory.orEmpty()
            val tx = m?.netTxHistory.orEmpty()
            val top = maxOf(rx.maxOrNull() ?: 0f, tx.maxOrNull() ?: 0f, 10_000f) * 1.2f
            NexiumLineChart(
                series = listOf(ChartSeries(rx, NexiumColors.Cyan), ChartSeries(tx, NexiumColors.Electric, fill = false)),
                yMin = 0f,
                yMax = top,
                capacity = m?.historyCapacity ?: 2,
                yLabel = { PerformanceFormat.rate(it.toLong(), locale).replace(" ", "") },
                modifier = Modifier.fillMaxWidth().height(116.dp),
            )
        }
        Column {
            SourceNote(stringResource(R.string.net_ipv6, net?.ipv6 ?: na))
            SourceNote(stringResource(R.string.source_network))
        }
    }
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun RowScope.StatTile(label: String, value: String, sub: String?, valueSize: Int = 24, weight: Float = 1f) {
    Column(
        Modifier
            .weight(weight)
            .height(88.dp)
            .background(NexiumColors.Surface, CardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = NexiumColors.OnSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = valueSize.sp, fontWeight = FontWeight.SemiBold, color = NexiumColors.OnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) {
            Text(sub, fontSize = 12.sp, color = NexiumColors.OnSurfaceSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InfoCard(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .background(NexiumColors.Surface, CardShape)
            .padding(NexiumSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = NexiumColors.Cyan)
        content()
    }
}

@Composable
private fun InfoLine(label: String, value: String, small: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = NexiumColors.OnSurfaceMuted, modifier = Modifier.width(138.dp))
        Text(
            value,
            style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
            color = NexiumColors.OnBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ServiceLine(label: String, value: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(if (ok) NexiumColors.Cyan else NexiumColors.Error, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = NexiumColors.OnSurfaceMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(10.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = NexiumColors.OnBackground, maxLines = 1)
    }
}

@Composable
private fun Legend(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 3.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text("$label ", style = MaterialTheme.typography.labelMedium, color = NexiumColors.OnSurfaceMuted)
        Text(value, style = MaterialTheme.typography.labelLarge, color = NexiumColors.OnBackground)
    }
}

@Composable
private fun SourceNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = NexiumColors.OnSurfaceSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

internal fun networkTypeLabel(status: NetworkStatus): Int = when (status) {
    NetworkStatus.Wifi -> R.string.net_type_wifi
    NetworkStatus.Ethernet -> R.string.net_type_ethernet
    NetworkStatus.Other -> R.string.net_type_other
    NetworkStatus.Offline -> R.string.net_offline
}
