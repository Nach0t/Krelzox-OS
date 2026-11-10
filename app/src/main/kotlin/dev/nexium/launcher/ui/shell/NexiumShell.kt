package dev.nexium.launcher.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.nexium.launcher.R
import dev.nexium.launcher.core.data.AppEntry
import dev.nexium.launcher.core.data.NetworkStatus
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumIcons
import dev.nexium.launcher.core.designsystem.NexiumSpacing
import dev.nexium.launcher.ui.AppArtLoader
import dev.nexium.launcher.ui.home.AppActions
import dev.nexium.launcher.ui.home.AppActionsDialog
import dev.nexium.launcher.ui.home.HomeTab
import dev.nexium.launcher.ui.home.HomeUiState
import dev.nexium.launcher.ui.library.AppGridTab
import dev.nexium.launcher.ui.settings.AboutDialog
import dev.nexium.launcher.ui.settings.SettingsActions
import dev.nexium.launcher.ui.settings.SettingsTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Everything the shell can ask the outside world to do. */
data class ShellCallbacks(
    val onLaunch: (AppEntry) -> Unit,
    val onToggleFavorite: (AppEntry) -> Unit,
    val onMoveFavorite: (AppEntry, Int) -> Unit,
    val onAppInfo: (AppEntry) -> Unit,
    val onNetworkSettings: () -> Unit,
    val onDisplaySoundSettings: () -> Unit,
    val onHomeAppSettings: () -> Unit,
    val onSystemSettings: () -> Unit,
)

@Composable
fun NexiumShell(
    state: HomeUiState.Ready,
    network: NetworkStatus,
    artLoader: AppArtLoader,
    homeResets: Flow<Unit>,
    callbacks: ShellCallbacks,
) {
    var selected by rememberSaveable { mutableStateOf(NexiumTab.Home) }
    val tabRequesters = remember { NexiumTab.entries.associateWith { FocusRequester() } }
    val heroOpen = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var focusedCardKey by remember { mutableStateOf<String?>(null) }
    var heroKey by remember { mutableStateOf<String?>(null) }
    var heroButtonsFocused by remember { mutableStateOf(false) }
    var topBarFocused by remember { mutableStateOf(false) }
    var rootHasFocus by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<AppEntry?>(null) }
    var aboutOpen by remember { mutableStateOf(false) }
    val dialogOpen = actionsFor != null || aboutOpen

    fun focusHero() {
        scope.launch {
            withFrameNanos { } // let the Home tab compose if we just switched to it
            withFrameNanos { }
            runCatching { heroOpen.requestFocus() }
        }
    }

    fun goHome() {
        selected = NexiumTab.Home
        focusHero()
    }

    fun focusTab(tab: NexiumTab) {
        scope.launch {
            withFrameNanos { }
            runCatching { tabRequesters.getValue(tab).requestFocus() }
        }
    }

    // Focus must never be lost: on first display, or when the focused element disappears
    // (uninstalled app, removed favorite), go to the hero, else to the selected tab.
    LaunchedEffect(rootHasFocus, dialogOpen) {
        if (rootHasFocus || dialogOpen) return@LaunchedEffect
        withFrameNanos { }
        if (selected == NexiumTab.Home) runCatching { heroOpen.requestFocus() }
        withFrameNanos { }
        if (!rootHasFocus) runCatching { tabRequesters.getValue(selected).requestFocus() }
    }

    // HOME while in front: close dialogs, reset the hero and go back to the top of Inicio.
    LaunchedEffect(homeResets) {
        homeResets.collect {
            actionsFor = null
            aboutOpen = false
            focusedCardKey = null
            heroKey = null
            goHome()
        }
    }

    // Back is hierarchical: content → its tab → Inicio → hero. On the hero it is not
    // consumed, so MainActivity applies its rule (no-op as HOME, exit as a normal app).
    val atRoot = selected == NexiumTab.Home && heroButtonsFocused
    BackHandler(enabled = !dialogOpen && !atRoot) {
        when {
            selected != NexiumTab.Home && !topBarFocused -> focusTab(selected)
            selected != NexiumTab.Home -> goHome()
            else -> focusHero()
        }
    }

    // Free cached art of uninstalled apps.
    LaunchedEffect(state.apps) {
        artLoader.retainOnly(state.apps.mapTo(HashSet()) { it.key })
    }

    val appActions = remember(callbacks) {
        AppActions(
            onLaunch = callbacks.onLaunch,
            onShowActions = { actionsFor = it },
            onToggleFavorite = callbacks.onToggleFavorite,
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(NexiumColors.Background)
                // Static ambient brand light, top-left. Drawn, never blurred.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NexiumColors.Electric.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.width * 0.55f,
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(0f, 0f),
                )
            }
            .onFocusChanged { rootHasFocus = it.hasFocus },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = NexiumSpacing.SafeHorizontal, end = NexiumSpacing.SafeHorizontal, top = NexiumSpacing.Large - 4.dp),
        ) {
            TopBar(
                selected = selected,
                tabRequesters = tabRequesters,
                network = network,
                onSelect = { selected = it },
                onEnterTab = { tab ->
                    selected = tab
                    if (tab == NexiumTab.Home) focusHero() else focusManager.moveFocus(FocusDirection.Down)
                },
                modifier = Modifier.onFocusChanged { topBarFocused = it.hasFocus },
            )
            Spacer(Modifier.height(NexiumSpacing.Medium))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (selected) {
                    NexiumTab.Home -> HomeTab(
                        state = state,
                        artLoader = artLoader,
                        actions = appActions,
                        heroOpenRequester = heroOpen,
                        focusedCardKey = focusedCardKey,
                        onCardFocused = { focusedCardKey = it },
                        heroKey = heroKey,
                        onHeroKeyChange = { heroKey = it },
                        onHeroButtonsFocusChanged = { heroButtonsFocused = it },
                        onOpenLibrary = {
                            selected = NexiumTab.Library
                            focusTab(NexiumTab.Library)
                        },
                    )
                    NexiumTab.Library -> AppGridTab(
                        title = stringResource(R.string.tab_library),
                        subtitle = pluralStringResource(R.plurals.apps_count, state.apps.size, state.apps.size),
                        entries = state.apps,
                        favoriteKeys = state.favoriteKeys,
                        artLoader = artLoader,
                        actions = appActions,
                        emptyIcon = NexiumIcons.Library,
                        emptyText = stringResource(R.string.home_empty),
                        emptyActionText = stringResource(R.string.open_settings),
                        onEmptyAction = callbacks.onSystemSettings,
                    )
                    NexiumTab.Media -> AppGridTab(
                        title = stringResource(R.string.tab_media),
                        subtitle = stringResource(R.string.media_subtitle),
                        entries = state.media,
                        favoriteKeys = state.favoriteKeys,
                        artLoader = artLoader,
                        actions = appActions,
                        emptyIcon = NexiumIcons.Media,
                        emptyText = stringResource(R.string.media_empty),
                        emptyActionText = stringResource(R.string.see_library_short),
                        onEmptyAction = {
                            selected = NexiumTab.Library
                            focusTab(NexiumTab.Library)
                        },
                    )
                    NexiumTab.Settings -> SettingsTab(
                        SettingsActions(
                            onNetwork = callbacks.onNetworkSettings,
                            onDisplaySound = callbacks.onDisplaySoundSettings,
                            onHomeApp = callbacks.onHomeAppSettings,
                            onSystemSettings = callbacks.onSystemSettings,
                            onAbout = { aboutOpen = true },
                        ),
                    )
                }
            }
        }
    }

    actionsFor?.let { entry ->
        val index = state.favorites.indexOfFirst { it.key == entry.key }
        AppActionsDialog(
            entry = entry,
            isFavorite = index >= 0,
            onOpen = { callbacks.onLaunch(entry) },
            onToggleFavorite = { callbacks.onToggleFavorite(entry) },
            onMoveLeft = if (index > 0) ({ callbacks.onMoveFavorite(entry, -1) }) else null,
            onMoveRight = if (index in 0 until state.favorites.lastIndex) ({ callbacks.onMoveFavorite(entry, +1) }) else null,
            onAppInfo = { callbacks.onAppInfo(entry) },
            onDismiss = { actionsFor = null },
        )
    }
    if (aboutOpen) {
        AboutDialog(appCount = state.apps.size, onDismiss = { aboutOpen = false })
    }
}

