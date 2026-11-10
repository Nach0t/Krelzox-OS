package dev.nexium.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.nexium.launcher.R
import dev.nexium.launcher.core.data.AppEntry
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumSpacing
import dev.nexium.launcher.ui.AppArtLoader

private const val GRID_COLUMNS = 5

@Composable
fun HomeScreen(
    state: HomeUiState,
    artLoader: AppArtLoader,
    onLaunch: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexiumColors.Background)
            .padding(horizontal = NexiumSpacing.SafeHorizontal, vertical = NexiumSpacing.SafeVertical),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = NexiumColors.OnBackground,
        )
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.titleMedium,
            color = NexiumColors.OnSurfaceMuted,
        )
        Spacer(Modifier.height(NexiumSpacing.Large))

        when (state) {
            HomeUiState.Loading -> Message(stringResource(R.string.home_loading))
            HomeUiState.Error -> MessageWithSettings(stringResource(R.string.home_error), onOpenSettings)
            is HomeUiState.Ready ->
                if (state.apps.isEmpty()) {
                    MessageWithSettings(stringResource(R.string.home_empty), onOpenSettings)
                } else {
                    AppGrid(state.apps, artLoader, onLaunch)
                }
        }
    }
}

@Composable
private fun AppGrid(apps: List<AppEntry>, artLoader: AppArtLoader, onLaunch: (AppEntry) -> Unit) {
    val firstItem = remember { FocusRequester() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        // Vertical padding leaves room for the focus scale so cards are not clipped.
        contentPadding = PaddingValues(vertical = NexiumSpacing.Medium, horizontal = NexiumSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(NexiumSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(NexiumSpacing.Large),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(apps, key = { _, entry -> entry.key }) { index, entry ->
            AppCard(
                entry = entry,
                artLoader = artLoader,
                onClick = { onLaunch(entry) },
                modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { firstItem.requestFocus() } }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = NexiumColors.OnSurfaceMuted)
    }
}

/** Fallback that always offers a way out, so the user is never stuck on an empty home. */
@Composable
private fun MessageWithSettings(text: String, onOpenSettings: () -> Unit) {
    val button = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = NexiumColors.OnSurfaceMuted)
        Spacer(Modifier.height(NexiumSpacing.Medium))
        Button(onClick = onOpenSettings, modifier = Modifier.focusRequester(button)) {
            Text(stringResource(R.string.open_settings))
        }
    }
    LaunchedEffect(Unit) { runCatching { button.requestFocus() } }
}
