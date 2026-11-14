package dev.krelzox.launcher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.krelzox.launcher.R
import dev.krelzox.launcher.core.data.AppEntry
import dev.krelzox.launcher.core.designsystem.KrelzoxButtonDefaults
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxDimens
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.launcher.ui.AppArtLoader
import dev.krelzox.launcher.core.designsystem.touchClick

private val PickerWidth = 520.dp
private val RowShape = RoundedCornerShape(KrelzoxDimens.ButtonCorner)

/**
 * Add/remove favorites in one place: every app with a star. OK toggles without closing,
 * so several apps can be managed at once. Back or "Done" closes.
 */
@Composable
fun FavoritesPickerDialog(
    apps: List<AppEntry>,
    favoriteKeys: Set<String>,
    artLoader: AppArtLoader,
    onToggle: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        Column(
            Modifier
                .width(PickerWidth)
                .background(KrelzoxColors.SurfaceRaised, RoundedCornerShape(20.dp))
                .padding(KrelzoxSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Small),
        ) {
            Text(stringResource(R.string.favorites_picker_title), style = MaterialTheme.typography.titleLarge, color = KrelzoxColors.OnBackground)
            Text(
                stringResource(R.string.favorites_picker_subtitle, favoriteKeys.size),
                style = MaterialTheme.typography.bodyMedium,
                color = KrelzoxColors.OnSurfaceMuted,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                contentPadding = PaddingValues(vertical = KrelzoxSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.XSmall),
            ) {
                itemsIndexed(apps, key = { _, e -> e.key }) { index, entry ->
                    PickerRow(
                        entry = entry,
                        isFavorite = entry.key in favoriteKeys,
                        artLoader = artLoader,
                        onClick = { onToggle(entry) },
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.touchClick(onDismiss).fillMaxWidth(),
                colors = KrelzoxButtonDefaults.colors(),
                border = KrelzoxButtonDefaults.border(),
                shape = KrelzoxButtonDefaults.shape(),
            ) { Text(stringResource(R.string.action_done)) }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
}

@Composable
private fun PickerRow(
    entry: AppEntry,
    isFavorite: Boolean,
    artLoader: AppArtLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val art by produceState(initialValue = artLoader.peek(entry), entry.key) {
        if (value == null) value = artLoader.load(entry)
    }
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier.touchClick(onClick).fillMaxWidth(),
        headlineContent = {
            Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        },
        leadingContent = {
            art?.let { Image(it.icon, contentDescription = null, modifier = Modifier.size(KrelzoxDimens.AppIcon)) }
        },
        trailingContent = {
            Icon(
                if (isFavorite) KrelzoxIcons.StarFilled else KrelzoxIcons.Star,
                contentDescription = stringResource(if (isFavorite) R.string.hero_unfavorite else R.string.hero_favorite),
                tint = if (isFavorite) KrelzoxColors.Cyan else KrelzoxColors.OnSurfaceMuted,
                modifier = Modifier.size(22.dp),
            )
        },
        shape = ListItemDefaults.shape(RowShape),
        colors = ListItemDefaults.colors(
            containerColor = KrelzoxColors.Surface,
            contentColor = KrelzoxColors.OnBackground,
            focusedContainerColor = KrelzoxColors.Electric.copy(alpha = 0.35f),
            focusedContentColor = KrelzoxColors.OnBackground,
        ),
        border = ListItemDefaults.border(
            focusedBorder = Border(BorderStroke(1.5.dp, KrelzoxColors.Cyan), shape = RowShape),
        ),
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
    )
}
