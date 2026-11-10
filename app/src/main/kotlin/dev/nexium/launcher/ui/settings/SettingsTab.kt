package dev.nexium.launcher.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import dev.nexium.launcher.core.designsystem.NexiumAccent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nexium.launcher.core.data.metrics.MetricId
import dev.nexium.launcher.core.data.metrics.SystemMetrics
import dev.nexium.launcher.core.designsystem.NexiumSparkline
import dev.nexium.launcher.ui.performance.PerformanceFormat
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.nexium.launcher.R
import dev.nexium.launcher.core.designsystem.NexiumButtonDefaults
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumDimens
import dev.nexium.launcher.core.designsystem.NexiumGrid
import dev.nexium.launcher.core.designsystem.NexiumIcons
import dev.nexium.launcher.core.designsystem.NexiumMotion
import dev.nexium.launcher.core.designsystem.NexiumSpacing

private val TileShape = RoundedCornerShape(NexiumDimens.CardCorner)

data class SettingsActions(
    val onPerformance: () -> Unit,
    val onAppearance: () -> Unit,
    /** Null when KonstaKANG's Raspberry Pi settings are not installed: the tile is hidden. */
    val onRaspberry: (() -> Unit)?,
    val onNetwork: () -> Unit,
    val onDisplaySound: () -> Unit,
    val onHomeApp: () -> Unit,
    val onSystemSettings: () -> Unit,
    val onAbout: () -> Unit,
)

@Composable
fun SettingsTab(
    actions: SettingsActions,
    metrics: Flow<SystemMetrics>,
    performanceTile: FocusRequester,
    appearanceTile: FocusRequester,
) {
    Column(Modifier.fillMaxSize().padding(top = NexiumSpacing.Small)) {
        Text(
            stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = NexiumColors.OnBackground,
        )
        Text(
            stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = NexiumColors.OnSurfaceMuted,
        )
        Spacer(Modifier.height(NexiumSpacing.Large))
        // Same 5-column grid as Biblioteca and the rows on Inicio. "Sistema y rendimiento"
        // and "Apariencia" span two columns: the two Nexium pages, with live content.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tile = NexiumGrid.columnWidth(maxWidth, NexiumGrid.APP_COLUMNS)
            Column(verticalArrangement = Arrangement.spacedBy(NexiumGrid.Gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(NexiumGrid.Gap)) {
                    PerformanceTile(tile * 2 + NexiumGrid.Gap, metrics, actions.onPerformance, Modifier.focusRequester(performanceTile))
                    SettingsTile(tile, NexiumIcons.Wifi, R.string.settings_network, R.string.settings_network_desc, actions.onNetwork)
                    SettingsTile(tile, NexiumIcons.Media, R.string.settings_display_sound, R.string.settings_display_sound_desc, actions.onDisplaySound)
                    SettingsTile(tile, NexiumIcons.Home, R.string.settings_home_app, R.string.settings_home_app_desc, actions.onHomeApp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(NexiumGrid.Gap)) {
                    AppearanceTile(tile * 2 + NexiumGrid.Gap, actions.onAppearance, Modifier.focusRequester(appearanceTile))
                    actions.onRaspberry?.let { onRaspberry ->
                        SettingsTile(tile, NexiumIcons.Board, R.string.settings_raspberry, R.string.settings_raspberry_desc, onRaspberry)
                    }
                    SettingsTile(tile, NexiumIcons.Settings, R.string.settings_android, R.string.settings_system_desc, actions.onSystemSettings)
                    SettingsTile(tile, NexiumIcons.Info, R.string.settings_about, R.string.settings_about_desc, actions.onAbout)
                }
            }
        }
    }
}

/** Wide entry to the performance center with live CPU / RAM / temperature and a CPU sparkline. */
@Composable
private fun PerformanceTile(width: Dp, metrics: Flow<SystemMetrics>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val current by metrics.collectAsStateWithLifecycle(initialValue = null)
    val locale = LocalConfiguration.current.locales[0]
    val s = current?.snapshot
    val na = stringResource(R.string.not_available_short)
    val pending = "\u2026"
    SettingsSurface(width, onClick, modifier) {
        Column(Modifier.fillMaxSize().padding(NexiumSpacing.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(NexiumIcons.Pulse, contentDescription = null, tint = NexiumColors.Cyan, modifier = Modifier.size(26.dp))
                Spacer(Modifier.weight(1f))
                MiniValue(
                    stringResource(R.string.perf_cpu),
                    when {
                        s == null -> pending
                        !s.isAvailable(MetricId.Cpu) -> na
                        else -> s.cpuPercent?.let { PerformanceFormat.percent(it, locale) } ?: pending
                    },
                )
                Spacer(Modifier.width(NexiumSpacing.Medium))
                MiniValue(stringResource(R.string.perf_ram), s?.let { PerformanceFormat.percent(it.ramPercent, locale) } ?: pending)
                Spacer(Modifier.width(NexiumSpacing.Medium))
                MiniValue(
                    stringResource(R.string.perf_temp_short),
                    when {
                        s == null -> pending
                        !s.isAvailable(MetricId.Temperature) -> na
                        else -> s.temperatureC?.let { PerformanceFormat.temperatureShort(it, locale) } ?: pending
                    },
                )
            }
            NexiumSparkline(
                values = current?.cpuHistory.orEmpty(),
                yMin = 0f,
                yMax = 100f,
                capacity = current?.historyCapacity ?: 2,
                color = NexiumColors.Cyan,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp),
            )
            Text(stringResource(R.string.perf_tile_title), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(
                stringResource(R.string.perf_tile_desc),
                style = MaterialTheme.typography.labelMedium,
                color = NexiumColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Wide entry to Apariencia: the four themes as swatches (the one in use ringed). */
@Composable
private fun AppearanceTile(width: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val current = NexiumColors.accent
    SettingsSurface(width, onClick, modifier) {
        Column(Modifier.fillMaxSize().padding(NexiumSpacing.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(NexiumIcons.Palette, contentDescription = null, tint = NexiumColors.Cyan, modifier = Modifier.size(26.dp))
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NexiumAccent.entries.forEach { accent ->
                        val ring = if (accent == current) {
                            Modifier.border(2.dp, NexiumColors.OnBackground, CircleShape).padding(4.dp)
                        } else {
                            Modifier.padding(4.dp)
                        }
                        Box(ring) { Box(Modifier.size(18.dp).background(accent.brush, CircleShape)) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(
                stringResource(R.string.settings_appearance_desc, stringResource(current.label())),
                style = MaterialTheme.typography.labelMedium,
                color = NexiumColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MiniValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(value, style = MaterialTheme.typography.labelLarge, color = NexiumColors.OnBackground)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = NexiumColors.OnSurfaceSubtle)
    }
}

@Composable
private fun SettingsTile(width: Dp, icon: ImageVector, title: Int, subtitle: Int, onClick: () -> Unit) {
    SettingsSurface(width, onClick) {
        Column(Modifier.fillMaxSize().padding(NexiumSpacing.Medium)) {
            Icon(icon, contentDescription = null, tint = NexiumColors.Cyan, modifier = Modifier.size(26.dp))
            Spacer(Modifier.weight(1f))
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(subtitle),
                style = MaterialTheme.typography.labelMedium,
                color = NexiumColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsSurface(width: Dp, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(width).height(138.dp),
        shape = ClickableSurfaceDefaults.shape(TileShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NexiumColors.Surface,
            contentColor = NexiumColors.OnBackground,
            focusedContainerColor = NexiumColors.SurfaceRaised,
            focusedContentColor = NexiumColors.OnBackground,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = NexiumMotion.FOCUS_SCALE),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(NexiumDimens.FocusBorder, NexiumColors.IonBrush), shape = TileShape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(NexiumColors.Electric.copy(alpha = 0.45f), NexiumDimens.FocusGlow),
        ),
    ) { content() }
}

@Composable
fun AboutDialog(appCount: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    Dialog(onDismissRequest = onDismiss) {
        val close = remember { FocusRequester() }
        Column(
            Modifier
                .width(440.dp)
                .background(NexiumColors.SurfaceRaised, RoundedCornerShape(20.dp))
                .padding(NexiumSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(NexiumSpacing.Small),
        ) {
            Text("Nexium-OS", style = MaterialTheme.typography.headlineMedium, color = NexiumColors.OnBackground)
            AboutLine(stringResource(R.string.about_version), version)
            AboutLine(stringResource(R.string.about_android), "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            AboutLine(stringResource(R.string.about_device), Build.MODEL)
            AboutLine(stringResource(R.string.about_apps), appCount.toString())
            Text(
                stringResource(R.string.about_font_license),
                style = MaterialTheme.typography.labelMedium,
                color = NexiumColors.OnSurfaceSubtle,
                modifier = Modifier.padding(top = NexiumSpacing.Small),
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = NexiumSpacing.Small).focusRequester(close),
                colors = NexiumButtonDefaults.colors(),
                border = NexiumButtonDefaults.border(),
                shape = NexiumButtonDefaults.shape(),
            ) { Text(stringResource(R.string.action_close)) }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { close.requestFocus() }
        }
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = NexiumColors.OnSurfaceMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = NexiumColors.OnBackground)
    }
}

/**
 * KonstaKANG's Raspberry Pi settings are not exported to other apps: Android only opens
 * them from its own Settings. Say exactly where they are, then open Settings.
 */
@Composable
fun RaspberrySettingsDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val open = remember { FocusRequester() }
        Column(
            Modifier
                .width(460.dp)
                .background(NexiumColors.SurfaceRaised, RoundedCornerShape(20.dp))
                .padding(NexiumSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(NexiumSpacing.Small),
        ) {
            Icon(NexiumIcons.Board, contentDescription = null, tint = NexiumColors.Cyan, modifier = Modifier.size(28.dp))
            Text(stringResource(R.string.raspberry_title), style = MaterialTheme.typography.headlineMedium, color = NexiumColors.OnBackground)
            Text(stringResource(R.string.raspberry_body), style = MaterialTheme.typography.bodyMedium, color = NexiumColors.OnSurfaceMuted)
            Text(
                stringResource(R.string.raspberry_path),
                style = MaterialTheme.typography.labelLarge,
                color = NexiumColors.OnBackground,
                modifier = Modifier
                    .padding(vertical = NexiumSpacing.XSmall)
                    .border(1.dp, NexiumColors.IonBrush, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = NexiumSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(NexiumSpacing.Small),
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f).focusRequester(open),
                    colors = NexiumButtonDefaults.colors(),
                    border = NexiumButtonDefaults.border(),
                    shape = NexiumButtonDefaults.shape(),
                ) { Text(stringResource(R.string.action_open_settings)) }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = NexiumButtonDefaults.colors(),
                    border = NexiumButtonDefaults.border(),
                    shape = NexiumButtonDefaults.shape(),
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { open.requestFocus() }
        }
    }
}
