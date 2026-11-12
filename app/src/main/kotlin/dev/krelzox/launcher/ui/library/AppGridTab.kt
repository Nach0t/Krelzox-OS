package dev.krelzox.launcher.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.krelzox.launcher.core.data.AppEntry
import dev.krelzox.launcher.core.designsystem.KrelzoxButtonDefaults
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxGrid
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.launcher.ui.AppArtLoader
import dev.krelzox.launcher.ui.home.AppActions
import dev.krelzox.launcher.ui.home.AppCard


/** Full-catalog grid used by Biblioteca and Multimedia. */
@Composable
fun AppGridTab(
    title: String,
    subtitle: String,
    entries: List<AppEntry>,
    favoriteKeys: Set<String>,
    artLoader: AppArtLoader,
    actions: AppActions,
    emptyIcon: ImageVector,
    emptyText: String,
    emptyActionText: String,
    onEmptyAction: () -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyState(emptyIcon, title, emptyText, emptyActionText, onEmptyAction)
        return
    }
    // Widened by the focus inset on both sides so card edges align with the title,
    // the top bar and the rows on Inicio (same 5-column grid).
    BoxWithConstraints(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(KrelzoxGrid.APP_COLUMNS),
        contentPadding = PaddingValues(
            start = KrelzoxGrid.FocusInset,
            end = KrelzoxGrid.FocusInset,
            bottom = KrelzoxSpacing.SafeVertical,
        ),
        horizontalArrangement = Arrangement.spacedBy(KrelzoxGrid.Gap),
        verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Medium),
        modifier = Modifier
            .requiredWidth(maxWidth + KrelzoxGrid.FocusInset * 2)
            .fillMaxHeight(),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
            Column(Modifier.padding(top = KrelzoxSpacing.Small, bottom = KrelzoxSpacing.XSmall)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = KrelzoxColors.OnBackground)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = KrelzoxColors.OnSurfaceMuted)
            }
        }
        items(entries, key = { it.key }, contentType = { "card" }) { entry ->
            AppCard(
                entry = entry,
                isFavorite = entry.key in favoriteKeys,
                artLoader = artLoader,
                onClick = { actions.onLaunch(entry) },
                onShowActions = { actions.onShowActions(entry) },
                modifier = Modifier.padding(top = KrelzoxSpacing.Small),
            )
        }
    }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = KrelzoxColors.Cyan, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(KrelzoxSpacing.Medium))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = KrelzoxColors.OnBackground)
        Spacer(Modifier.height(KrelzoxSpacing.Small))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = KrelzoxColors.OnSurfaceMuted)
        Spacer(Modifier.height(KrelzoxSpacing.Large))
        Button(
            onClick = onAction,
            colors = KrelzoxButtonDefaults.colors(),
            border = KrelzoxButtonDefaults.border(),
            shape = KrelzoxButtonDefaults.shape(),
        ) { Text(actionText) }
    }
}
