package dev.krelzox.launcher.ui.shell

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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import dev.krelzox.launcher.R
import dev.krelzox.launcher.core.data.NetworkStatus
import dev.krelzox.launcher.core.data.metrics.SystemMetrics
import dev.krelzox.launcher.core.designsystem.Inter
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxDimens
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.launcher.ui.performance.PerformancePill
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import dev.krelzox.launcher.core.designsystem.touchClick

enum class KrelzoxTab(val label: Int, val icon: ImageVector) {
    Home(R.string.tab_home, KrelzoxIcons.Home),
    Library(R.string.tab_library, KrelzoxIcons.Library),
    Media(R.string.tab_media, KrelzoxIcons.Media),
    Settings(R.string.tab_settings, KrelzoxIcons.Settings),
}

private val PillShape = RoundedCornerShape(KrelzoxDimens.PillCorner)

/**
 * Brand + navigation (start) and system status (end): performance panel, network and
 * clock. Inactive tabs show only their icon and the active one adds its label, which
 * keeps the bar calm and leaves room for the always-on performance panel.
 */
@Composable
fun TopBar(
    selected: KrelzoxTab,
    tabRequesters: Map<KrelzoxTab, FocusRequester>,
    network: NetworkStatus,
    metrics: Flow<SystemMetrics>,
    onOpenPerformance: () -> Unit,
    onSelect: (KrelzoxTab) -> Unit,
    onEnterTab: (KrelzoxTab) -> Unit,
    modifier: Modifier = Modifier,
    contentEntry: FocusRequester? = null,
) {
    Row(
        modifier.fillMaxWidth().height(KrelzoxDimens.TopBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo()
        Spacer(Modifier.width(KrelzoxSpacing.Large + 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KrelzoxTab.entries.forEach { tab ->
                NavPill(
                    tab = tab,
                    selected = tab == selected,
                    onFocused = { onSelect(tab) },
                    onClick = { onEnterTab(tab) },
                    modifier = Modifier
                        .focusRequester(tabRequesters.getValue(tab))
                        // ↓ from the selected tab lands on its content's first element, not on
                        // whatever sits geometrically below the tab.
                        .then(
                            if (tab == selected && contentEntry != null) {
                                Modifier.focusProperties { down = contentEntry }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PerformancePill(metrics = metrics, onOpen = onOpenPerformance)
        Spacer(Modifier.width(KrelzoxSpacing.Medium - 2.dp))
        NetworkIndicator(network)
        Spacer(Modifier.width(KrelzoxSpacing.Medium - 2.dp))
        Clock()
    }
}

/**
 * Brand lockup: the "K" mark at cap-height × 1.5 is the wordmark's first letter, followed by
 * "RELZOX" one tracking step away, optically centered on each other (line-height trimmed so
 * the caps sit on the mark's center). Read as a whole as "Krelzox".
 */
@Composable
private fun Logo(modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Krelzox" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(rememberVectorPainter(KrelzoxIcons.LogoMark), contentDescription = null, modifier = Modifier.size(24.dp, 26.dp))
        // Same gap as the wordmark's tracking (0.26 em × 19 sp ≈ 5 dp), so the mark reads as its "K".
        Spacer(Modifier.width(5.dp))
        Text(
            text = "RELZOX",
            style = TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                letterSpacing = 0.26.em,
                lineHeight = 19.sp,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            color = KrelzoxColors.OnBackground,
        )
    }
}

/** Tabs are selected on focus (TV convention); OK moves into the tab's content. */
@Composable
private fun NavPill(
    tab: KrelzoxTab,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.touchClick(onClick).onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) KrelzoxColors.Surface else KrelzoxColors.Background.copy(alpha = 0f),
            contentColor = if (selected) KrelzoxColors.OnBackground else KrelzoxColors.OnSurfaceMuted,
            focusedContainerColor = KrelzoxColors.SurfaceRaised,
            focusedContentColor = KrelzoxColors.OnBackground,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.dp, KrelzoxColors.Outline), shape = PillShape) else Border.None,
            focusedBorder = Border(BorderStroke(1.5.dp, KrelzoxColors.IonBrush), shape = PillShape),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = if (selected) KrelzoxSpacing.Medium else 13.dp, vertical = KrelzoxSpacing.Small + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                tab.icon,
                contentDescription = if (selected) null else stringResource(tab.label),
                tint = if (selected) KrelzoxColors.Cyan else KrelzoxColors.OnSurfaceMuted,
                modifier = Modifier.size(20.dp),
            )
            if (selected) {
                Spacer(Modifier.width(KrelzoxSpacing.Small))
                Text(stringResource(tab.label), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun NetworkIndicator(status: NetworkStatus) {
    val (icon, label) = when (status) {
        NetworkStatus.Wifi -> KrelzoxIcons.Wifi to R.string.network_wifi
        NetworkStatus.Ethernet -> KrelzoxIcons.Ethernet to R.string.network_ethernet
        NetworkStatus.Other -> KrelzoxIcons.Wifi to R.string.network_connected
        NetworkStatus.Offline -> KrelzoxIcons.Offline to R.string.network_offline
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = stringResource(label),
            tint = if (status == NetworkStatus.Offline) KrelzoxColors.Error else KrelzoxColors.OnSurfaceMuted,
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
        Text(time, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = KrelzoxColors.OnBackground)
        Text(
            date.replaceFirstChar { it.titlecase(locale) },
            style = MaterialTheme.typography.labelMedium,
            color = KrelzoxColors.OnSurfaceMuted,
        )
    }
}
