package dev.nexium.launcher.ui.shell

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.nexium.launcher.R
import dev.nexium.launcher.core.data.NetworkStatus
import dev.nexium.launcher.core.data.metrics.SystemMetrics
import dev.nexium.launcher.core.designsystem.Inter
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumDimens
import dev.nexium.launcher.core.designsystem.NexiumIcons
import dev.nexium.launcher.core.designsystem.NexiumSpacing
import dev.nexium.launcher.ui.performance.PerformancePill
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

enum class NexiumTab(val label: Int, val icon: ImageVector) {
    Home(R.string.tab_home, NexiumIcons.Home),
    Library(R.string.tab_library, NexiumIcons.Library),
    Media(R.string.tab_media, NexiumIcons.Media),
    Settings(R.string.tab_settings, NexiumIcons.Settings),
}

private val PillShape = RoundedCornerShape(NexiumDimens.PillCorner)

/**
 * Brand + navigation (start) and system status (end): performance panel, network and
 * clock. Inactive tabs show only their icon and the active one adds its label, which
 * keeps the bar calm and leaves room for the always-on performance panel.
 */
@Composable
fun TopBar(
    selected: NexiumTab,
    tabRequesters: Map<NexiumTab, FocusRequester>,
    network: NetworkStatus,
    metrics: Flow<SystemMetrics>,
    onOpenPerformance: () -> Unit,
    onSelect: (NexiumTab) -> Unit,
    onEnterTab: (NexiumTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(NexiumDimens.TopBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo()
        Spacer(Modifier.width(NexiumSpacing.Large + 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NexiumTab.entries.forEach { tab ->
                NavPill(
                    tab = tab,
                    selected = tab == selected,
                    onFocused = { onSelect(tab) },
                    onClick = { onEnterTab(tab) },
                    modifier = Modifier.focusRequester(tabRequesters.getValue(tab)),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PerformancePill(metrics = metrics, onOpen = onOpenPerformance)
        Spacer(Modifier.width(NexiumSpacing.Medium - 2.dp))
        NetworkIndicator(network)
        Spacer(Modifier.width(NexiumSpacing.Medium - 2.dp))
        Clock()
    }
}

/**
 * Brand lockup: the "N" mark at cap-height × 1.5 and the spaced wordmark, optically
 * centered on each other (line-height trimmed so the caps sit on the mark's center).
 */
@Composable
private fun Logo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(rememberVectorPainter(NexiumIcons.LogoMark), contentDescription = null, modifier = Modifier.size(24.dp, 26.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = "NEXIUM",
            style = TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                letterSpacing = 0.26.em,
                lineHeight = 19.sp,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            color = NexiumColors.OnBackground,
        )
    }
}

/** Tabs are selected on focus (TV convention); OK moves into the tab's content. */
@Composable
private fun NavPill(
    tab: NexiumTab,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) NexiumColors.Surface else NexiumColors.Background.copy(alpha = 0f),
            contentColor = if (selected) NexiumColors.OnBackground else NexiumColors.OnSurfaceMuted,
            focusedContainerColor = NexiumColors.SurfaceRaised,
            focusedContentColor = NexiumColors.OnBackground,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.dp, NexiumColors.Outline), shape = PillShape) else Border.None,
            focusedBorder = Border(BorderStroke(1.5.dp, NexiumColors.IonBrush), shape = PillShape),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = if (selected) NexiumSpacing.Medium else 13.dp, vertical = NexiumSpacing.Small + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                tab.icon,
                contentDescription = if (selected) null else stringResource(tab.label),
                tint = if (selected) NexiumColors.Cyan else NexiumColors.OnSurfaceMuted,
                modifier = Modifier.size(20.dp),
            )
            if (selected) {
                Spacer(Modifier.width(NexiumSpacing.Small))
                Text(stringResource(tab.label), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun NetworkIndicator(status: NetworkStatus) {
    val (icon, label) = when (status) {
        NetworkStatus.Wifi -> NexiumIcons.Wifi to R.string.network_wifi
        NetworkStatus.Ethernet -> NexiumIcons.Ethernet to R.string.network_ethernet
        NetworkStatus.Other -> NexiumIcons.Wifi to R.string.network_connected
        NetworkStatus.Offline -> NexiumIcons.Offline to R.string.network_offline
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = stringResource(label),
            tint = if (status == NetworkStatus.Offline) NexiumColors.Error else NexiumColors.OnSurfaceMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Updates once per minute, aligned to the minute boundary; only this text recomposes. */
@Composable
private fun Clock() {
    val context = LocalContext.current
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L + 50L)
        }
    }
    val locale = LocalConfiguration.current.locales[0]
    val time = DateFormat.getTimeFormat(context).format(Date(now))
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    Column(horizontalAlignment = Alignment.End) {
        Text(time, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = NexiumColors.OnBackground)
        Text(
            date.replaceFirstChar { it.titlecase(locale) },
            style = MaterialTheme.typography.labelMedium,
            color = NexiumColors.OnSurfaceMuted,
        )
    }
}
