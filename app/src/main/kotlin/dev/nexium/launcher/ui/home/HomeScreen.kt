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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.nexium.launcher.R
import dev.nexium.launcher.core.data.AppEntry
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumSpacing
import dev.nexium.launcher.ui.AppArtLoader
import kotlinx.coroutines.flow.Flow

private const val GRID_COLUMNS = 5
private const val FAVORITE_PREFIX = "fav/"
private const val APP_PREFIX = "app/"

@Composable
fun HomeScreen(
    state: HomeUiState,
    artLoader: AppArtLoader,
    homeResets: Flow<Unit>,
    onLaunch: (AppEntry) -> Unit,
    onToggleFavorite: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
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
            text = stringResource(R.string.home_hint),
            style = MaterialTheme.typography.labelMedium,
            color = NexiumColors.OnSurfaceMuted,
        )
        Spacer(Modifier.height(NexiumSpacing.Medium))

        when (state) {
            HomeUiState.Loading -> Message(stringResource(R.string.home_loading))
            HomeUiState.Error -> MessageWithSettings(stringResource(R.string.home_error), onOpenSettings)
            is HomeUiState.Ready ->
                if (state.apps.isEmpty()) {
                    MessageWithSettings(stringResource(R.string.home_empty), onOpenSettings)
                } else {
                    HomeGrid(state, artLoader, homeResets, onLaunch, onToggleFavorite, onAppInfo)
                }
        }
    }
}

@Composable
private fun HomeGrid(
    state: HomeUiState.Ready,
    artLoader: AppArtLoader,
    homeResets: Flow<Unit>,
    onLaunch: (AppEntry) -> Unit,
    onToggleFavorite: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val primary = remember { FocusRequester() }
    var gridHasFocus by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<AppEntry?>(null) }

    // First card on screen: first favorite, or first app if there are none.
    val primaryKey = state.favorites.firstOrNull()?.let { FAVORITE_PREFIX + it.key }
        ?: state.apps.firstOrNull()?.let { APP_PREFIX + it.key }

    suspend fun focusPrimary() {
        gridState.scrollToItem(0)
        withFrameNanos { } // let the primary card be composed and attached
        runCatching { primary.requestFocus() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = gridState,
        // Padding leaves room for the focus scale so cards are never clipped.
        contentPadding = PaddingValues(vertical = NexiumSpacing.Medium, horizontal = NexiumSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(NexiumSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(NexiumSpacing.Large),
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { gridHasFocus = it.hasFocus },
    ) {
        if (state.favorites.isNotEmpty()) {
            sectionTitle("header/favorites", R.string.section_favorites)
            cards(
                entries = state.favorites,
                keyPrefix = FAVORITE_PREFIX,
                primaryKey = primaryKey,
                primary = primary,
                favoriteKeys = state.favoriteKeys,
                artLoader = artLoader,
                onLaunch = onLaunch,
                onActions = { actionsFor = it },
            )
        }
        sectionTitle("header/apps", R.string.section_apps)
        cards(
            entries = state.apps,
            keyPrefix = APP_PREFIX,
            primaryKey = primaryKey,
            primary = primary,
            favoriteKeys = state.favoriteKeys,
            artLoader = artLoader,
            onLaunch = onLaunch,
            onActions = { actionsFor = it },
        )
    }

    // Focus must never be lost: on first display, and whenever the focused card
    // disappears (uninstalled app, removed favorite), focus returns to the first card.
    val dialogOpen = actionsFor != null
    LaunchedEffect(gridHasFocus, dialogOpen, primaryKey) {
        if (!gridHasFocus && !dialogOpen && primaryKey != null) focusPrimary()
    }

    // HOME pressed while already in front: back to the top.
    LaunchedEffect(homeResets) {
        homeResets.collect { focusPrimary() }
    }

    // Free cached banners of uninstalled apps.
    LaunchedEffect(state.apps) {
        artLoader.retainOnly(state.apps.mapTo(HashSet()) { it.key })
    }

    actionsFor?.let { entry ->
        AppActionsDialog(
            entry = entry,
            isFavorite = entry.key in state.favoriteKeys,
            onOpen = { onLaunch(entry) },
            onToggleFavorite = { onToggleFavorite(entry) },
            onAppInfo = { onAppInfo(entry) },
            onDismiss = { actionsFor = null },
        )
    }
}

private fun LazyGridScope.sectionTitle(key: String, title: Int) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = NexiumColors.OnSurfaceMuted,
            modifier = Modifier.padding(top = NexiumSpacing.Small),
        )
    }
}

private fun LazyGridScope.cards(
    entries: List<AppEntry>,
    keyPrefix: String,
    primaryKey: String?,
    primary: FocusRequester,
    favoriteKeys: Set<String>,
    artLoader: AppArtLoader,
    onLaunch: (AppEntry) -> Unit,
    onActions: (AppEntry) -> Unit,
) {
    items(entries, key = { keyPrefix + it.key }, contentType = { "card" }) { entry ->
        AppCard(
            entry = entry,
            isFavorite = entry.key in favoriteKeys,
            artLoader = artLoader,
            onClick = { onLaunch(entry) },
            onShowActions = { onActions(entry) },
            modifier = if (keyPrefix + entry.key == primaryKey) Modifier.focusRequester(primary) else Modifier,
        )
    }
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
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { button.requestFocus() }
    }
}
