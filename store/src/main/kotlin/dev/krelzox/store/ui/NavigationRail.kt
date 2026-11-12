package dev.krelzox.store.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.store.R

private data class RailEntry(val section: Section, val label: Int, val icon: ImageVector)

private val Entries = listOf(
    RailEntry(Section.SEARCH, R.string.nav_search, StoreIcons.Search),
    RailEntry(Section.HOME, R.string.nav_home, KrelzoxIcons.Home),
    RailEntry(Section.APPS, R.string.nav_apps, KrelzoxIcons.Library),
    RailEntry(Section.GAMES, R.string.nav_games, StoreIcons.Gamepad),
    RailEntry(Section.CATEGORIES, R.string.nav_categories, StoreIcons.Category),
    RailEntry(Section.LIBRARY, R.string.nav_library, StoreIcons.Shelf),
    RailEntry(Section.INSTALLED, R.string.nav_installed, StoreIcons.Installed),
    RailEntry(Section.UPDATES, R.string.nav_updates, StoreIcons.Update),
    RailEntry(Section.FAVORITES, R.string.nav_favorites, KrelzoxIcons.Star),
    RailEntry(Section.SETTINGS, R.string.nav_settings, KrelzoxIcons.Settings),
)

/** Left navigation: the store brand and one entry per section. Selecting moves focus-follows. */
@Composable
fun NavigationRail(selected: Section, updatesCount: Int, onSelect: (Section) -> Unit) {
    // Focus starts on the selected section (not on the first entry) when the store opens.
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }
    Column(
        Modifier
            .fillMaxHeight()
            .width(240.dp)
            .background(KrelzoxColors.BackgroundDeep)
            .padding(start = KrelzoxSpacing.SafeHorizontal - 16.dp, end = 16.dp, top = KrelzoxSpacing.SafeVertical),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, bottom = KrelzoxSpacing.Large, top = 6.dp),
        ) {
            Image(rememberVectorPainter(KrelzoxIcons.LogoMark), contentDescription = null, modifier = Modifier.size(20.dp, 22.dp))
            Text(
                "STORE",
                style = MaterialTheme.typography.labelSmall,
                color = KrelzoxColors.OnBackground,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        for (entry in Entries) {
            RailItem(
                label = stringResource(entry.label),
                icon = entry.icon,
                selected = entry.section == selected,
                badge = if (entry.section == Section.UPDATES && updatesCount > 0) updatesCount.toString() else null,
                onClick = { onSelect(entry.section) },
                modifier = if (entry.section == selected) Modifier.focusRequester(selectedFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun RailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) KrelzoxColors.SurfaceRaised else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (selected) KrelzoxColors.OnBackground else KrelzoxColors.OnSurfaceMuted,
            focusedContainerColor = KrelzoxColors.Electric,
            focusedContentColor = KrelzoxColors.OnBackground,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (selected) KrelzoxColors.Cyan else LocalContentColor.current)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(start = 12.dp).weight(1f))
            if (badge != null) {
                Box(
                    Modifier.background(KrelzoxColors.Cyan, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
                ) {
                    Text(badge, style = MaterialTheme.typography.labelMedium, color = KrelzoxColors.Background)
                }
            }
        }
    }
}
