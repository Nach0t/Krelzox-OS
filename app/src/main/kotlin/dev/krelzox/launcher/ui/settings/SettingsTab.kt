package dev.krelzox.launcher.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import dev.krelzox.launcher.core.designsystem.KrelzoxAccent
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
import dev.krelzox.launcher.core.data.metrics.MetricId
import dev.krelzox.launcher.core.data.metrics.SystemMetrics
import dev.krelzox.launcher.core.designsystem.KrelzoxSparkline
import dev.krelzox.launcher.ui.performance.PerformanceFormat
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
import dev.krelzox.launcher.R
import dev.krelzox.launcher.core.designsystem.KrelzoxButtonDefaults
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxDimens
import dev.krelzox.launcher.core.designsystem.KrelzoxGrid
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxMotion
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing

private val TileShape = RoundedCornerShape(KrelzoxDimens.CardCorner)

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
    Column(Modifier.fillMaxSize().padding(top = KrelzoxSpacing.Small)) {
        Text(
            stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = KrelzoxColors.OnBackground,
        )
        Text(
            stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = KrelzoxColors.OnSurfaceMuted,
        )
        Spacer(Modifier.height(KrelzoxSpacing.Large))
        // Same 5-column grid as Biblioteca and the rows on Inicio. "Sistema y rendimiento"
        // and "Apariencia" span two columns: the two Krelzox pages, with live content.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tile = KrelzoxGrid.columnWidth(maxWidth, KrelzoxGrid.APP_COLUMNS)
            Column(verticalArrangement = Arrangement.spacedBy(KrelzoxGrid.Gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(KrelzoxGrid.Gap)) {
                    PerformanceTile(tile * 2 + KrelzoxGrid.Gap, metrics, actions.onPerformance, Modifier.focusRequester(performanceTile))
                    SettingsTile(tile, KrelzoxIcons.Wifi, R.string.settings_network, R.string.settings_network_desc, actions.onNetwork)
                    SettingsTile(tile, KrelzoxIcons.Media, R.string.settings_display_sound, R.string.settings_display_sound_desc, actions.onDisplaySound)
                    SettingsTile(tile, KrelzoxIcons.Home, R.string.settings_home_app, R.string.settings_home_app_desc, actions.onHomeApp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(KrelzoxGrid.Gap)) {
                    AppearanceTile(tile * 2 + KrelzoxGrid.Gap, actions.onAppearance, Modifier.focusRequester(appearanceTile))
                    actions.onRaspberry?.let { onRaspberry ->
                        SettingsTile(tile, KrelzoxIcons.Board, R.string.settings_raspberry, R.string.settings_raspberry_desc, onRaspberry)
                    }
                    SettingsTile(tile, KrelzoxIcons.Settings, R.string.settings_android, R.string.settings_system_desc, actions.onSystemSettings)
                    SettingsTile(tile, KrelzoxIcons.Info, R.string.settings_about, R.string.settings_about_desc, actions.onAbout)
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
        Column(Modifier.fillMaxSize().padding(KrelzoxSpacing.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(KrelzoxIcons.Pulse, contentDescription = null, tint = KrelzoxColors.Cyan, modifier = Modifier.size(26.dp))
                Spacer(Modifier.weight(1f))
                MiniValue(
                    stringResource(R.string.perf_cpu),
                    when {
                        s == null -> pending
                        !s.isAvailable(MetricId.Cpu) -> na
                        else -> s.cpuPercent?.let { PerformanceFormat.percent(it, locale) } ?: pending
                    },
                )
                Spacer(Modifier.width(KrelzoxSpacing.Medium))
                MiniValue(stringResource(R.string.perf_ram), s?.let { PerformanceFormat.percent(it.ramPercent, locale) } ?: pending)
                Spacer(Modifier.width(KrelzoxSpacing.Medium))
                MiniValue(
                    stringResource(R.string.perf_temp_short),
                    when {
                        s == null -> pending
                        !s.isAvailable(MetricId.Temperature) -> na
                        else -> s.temperatureC?.let { PerformanceFormat.temperatureShort(it, locale) } ?: pending
                    },
                )
            }
            KrelzoxSparkline(
                values = current?.cpuHistory.orEmpty(),
                yMin = 0f,
                yMax = 100f,
                capacity = current?.historyCapacity ?: 2,
                color = KrelzoxColors.Cyan,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp),
            )
            Text(stringResource(R.string.perf_tile_title), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(
                stringResource(R.string.perf_tile_desc),
                style = MaterialTheme.typography.labelMedium,
                color = KrelzoxColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Wide entry to Apariencia: the four themes as swatches (the one in use ringed). */
@Composable
private fun AppearanceTile(width: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val current = KrelzoxColors.accent
    SettingsSurface(width, onClick, modifier) {
        Column(Modifier.fillMaxSize().padding(KrelzoxSpacing.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(KrelzoxIcons.Palette, contentDescription = null, tint = KrelzoxColors.Cyan, modifier = Modifier.size(26.dp))
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    KrelzoxAccent.entries.forEach { accent ->
                        val ring = if (accent == current) {
                            Modifier.border(2.dp, KrelzoxColors.OnBackground, CircleShape).padding(4.dp)
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
                color = KrelzoxColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MiniValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(value, style = MaterialTheme.typography.labelLarge, color = KrelzoxColors.OnBackground)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = KrelzoxColors.OnSurfaceSubtle)
    }
}

@Composable
private fun SettingsTile(width: Dp, icon: ImageVector, title: Int, subtitle: Int, onClick: () -> Unit) {
    SettingsSurface(width, onClick) {
        Column(Modifier.fillMaxSize().padding(KrelzoxSpacing.Medium)) {
            Icon(icon, contentDescription = null, tint = KrelzoxColors.Cyan, modifier = Modifier.size(26.dp))
            Spacer(Modifier.weight(1f))
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(subtitle),
                style = MaterialTheme.typography.labelMedium,
                color = KrelzoxColors.OnSurfaceMuted,
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
            containerColor = KrelzoxColors.Surface,
            contentColor = KrelzoxColors.OnBackground,
            focusedContainerColor = KrelzoxColors.SurfaceRaised,
            focusedContentColor = KrelzoxColors.OnBackground,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = KrelzoxMotion.FOCUS_SCALE),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(KrelzoxDimens.FocusBorder, KrelzoxColors.IonBrush), shape = TileShape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(KrelzoxColors.Electric.copy(alpha = 0.45f), KrelzoxDimens.FocusGlow),
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
                .background(KrelzoxColors.SurfaceRaised, RoundedCornerShape(20.dp))
                .padding(KrelzoxSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Small),
        ) {
            Text("Krelzox-OS", style = MaterialTheme.typography.headlineMedium, color = KrelzoxColors.OnBackground)
            AboutLine(stringResource(R.string.about_version), version)
            AboutLine(stringResource(R.string.about_android), "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            AboutLine(stringResource(R.string.about_device), Build.MODEL)
            AboutLine(stringResource(R.string.about_apps), appCount.toString())
            Text(
                stringResource(R.string.about_font_license),
                style = MaterialTheme.typography.labelMedium,
                color = KrelzoxColors.OnSurfaceSubtle,
                modifier = Modifier.padding(top = KrelzoxSpacing.Small),
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = KrelzoxSpacing.Small).focusRequester(close),
                colors = KrelzoxButtonDefaults.colors(),
                border = KrelzoxButtonDefaults.border(),
                shape = KrelzoxButtonDefaults.shape(),
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
        Text(label, style = MaterialTheme.typography.bodyMedium, color = KrelzoxColors.OnSurfaceMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = KrelzoxColors.OnBackground)
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
                .background(KrelzoxColors.SurfaceRaised, RoundedCornerShape(20.dp))
                .padding(KrelzoxSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Small),
        ) {
            Icon(KrelzoxIcons.Board, contentDescription = null, tint = KrelzoxColors.Cyan, modifier = Modifier.size(28.dp))
            Text(stringResource(R.string.raspberry_title), style = MaterialTheme.typography.headlineMedium, color = KrelzoxColors.OnBackground)
            Text(stringResource(R.string.raspberry_body), style = MaterialTheme.typography.bodyMedium, color = KrelzoxColors.OnSurfaceMuted)
            Text(
                stringResource(R.string.raspberry_path),
                style = MaterialTheme.typography.labelLarge,
                color = KrelzoxColors.OnBackground,
                modifier = Modifier
                    .padding(vertical = KrelzoxSpacing.XSmall)
                    .border(1.dp, KrelzoxColors.IonBrush, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = KrelzoxSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Small),
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f).focusRequester(open),
                    colors = KrelzoxButtonDefaults.colors(),
                    border = KrelzoxButtonDefaults.border(),
                    shape = KrelzoxButtonDefaults.shape(),
                ) { Text(stringResource(R.string.action_open_settings)) }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = KrelzoxButtonDefaults.colors(),
                    border = KrelzoxButtonDefaults.border(),
                    shape = KrelzoxButtonDefaults.shape(),
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { open.requestFocus() }
        }
    }
}
