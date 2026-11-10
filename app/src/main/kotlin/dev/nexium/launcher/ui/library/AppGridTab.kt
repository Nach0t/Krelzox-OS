package dev.nexium.launcher.ui.library

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
import dev.nexium.launcher.core.data.AppEntry
import dev.nexium.launcher.core.designsystem.NexiumButtonDefaults
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumGrid
import dev.nexium.launcher.core.designsystem.NexiumSpacing
import dev.nexium.launcher.ui.AppArtLoader
import dev.nexium.launcher.ui.home.AppActions
import dev.nexium.launcher.ui.home.AppCard


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
        columns = GridCells.Fixed(NexiumGrid.APP_COLUMNS),
        contentPadding = PaddingValues(
            start = NexiumGrid.FocusInset,
            end = NexiumGrid.FocusInset,
            bottom = NexiumSpacing.SafeVertical,
        ),
        horizontalArrangement = Arrangement.spacedBy(NexiumGrid.Gap),
        verticalArrangement = Arrangement.spacedBy(NexiumSpacing.Medium),
        modifier = Modifier
            .requiredWidth(maxWidth + NexiumGrid.FocusInset * 2)
            .fillMaxHeight(),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
            Column(Modifier.padding(top = NexiumSpacing.Small, bottom = NexiumSpacing.XSmall)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = NexiumColors.OnBackground)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = NexiumColors.OnSurfaceMuted)
            }
        }
        items(entries, key = { it.key }, contentType = { "card" }) { entry ->
            AppCard(
                entry = entry,
                isFavorite = entry.key in favoriteKeys,
                artLoader = artLoader,
                onClick = { actions.onLaunch(entry) },
                onShowActions = { actions.onShowActions(entry) },
                modifier = Modifier.padding(top = NexiumSpacing.Small),
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
        Icon(icon, contentDescription = null, tint = NexiumColors.Cyan, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(NexiumSpacing.Medium))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = NexiumColors.OnBackground)
        Spacer(Modifier.height(NexiumSpacing.Small))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = NexiumColors.OnSurfaceMuted)
        Spacer(Modifier.height(NexiumSpacing.Large))
        Button(
            onClick = onAction,
            colors = NexiumButtonDefaults.colors(),
            border = NexiumButtonDefaults.border(),
            shape = NexiumButtonDefaults.shape(),
        ) { Text(actionText) }
    }
}
