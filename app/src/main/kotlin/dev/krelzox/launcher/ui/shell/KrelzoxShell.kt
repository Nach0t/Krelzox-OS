package dev.krelzox.launcher.ui.shell

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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.krelzox.launcher.R
import dev.krelzox.launcher.core.data.AppEntry
import dev.krelzox.launcher.core.data.NetworkStatus
import dev.krelzox.launcher.core.designsystem.KrelzoxAccent
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.launcher.ui.AppArtLoader
import dev.krelzox.launcher.ui.home.AppActions
import dev.krelzox.launcher.ui.home.AppActionsDialog
import dev.krelzox.launcher.ui.home.FavoritesPickerDialog
import dev.krelzox.launcher.ui.home.HomeTab
import dev.krelzox.launcher.ui.home.HomeUiState
import dev.krelzox.launcher.ui.home.InteractionClock
import dev.krelzox.launcher.ui.library.AppGridTab
import dev.krelzox.launcher.ui.performance.PerformanceCenter
import dev.krelzox.launcher.ui.performance.PerformanceSheet
import dev.krelzox.launcher.ui.performance.SystemSources
import dev.krelzox.launcher.ui.screensaver.ScreensaverPreview
import dev.krelzox.launcher.ui.settings.AboutDialog
import dev.krelzox.launcher.ui.settings.AppearancePage
import dev.krelzox.launcher.ui.settings.RaspberrySettingsDialog
import dev.krelzox.launcher.ui.settings.SettingsActions
import dev.krelzox.launcher.ui.settings.SettingsTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Pages inside the Ajustes tab. */
enum class SettingsPage { Overview, Performance, Appearance }

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
    /** Applies and saves a theme (Ajustes › Apariencia). */
    val onSelectAccent: (KrelzoxAccent) -> Unit = {},
    /** KonstaKANG's Raspberry Pi settings exist on this device: show their tile. */
    val showRaspberrySettings: Boolean = false,
)

@Composable
fun KrelzoxShell(
    state: HomeUiState.Ready,
    network: NetworkStatus,
    artLoader: AppArtLoader,
    homeResets: Flow<Unit>,
    callbacks: ShellCallbacks,
    system: SystemSources,
) {
    var selected by rememberSaveable { mutableStateOf(KrelzoxTab.Home) }
    val tabRequesters = remember { KrelzoxTab.entries.associateWith { FocusRequester() } }
    val heroOpen = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var focusedCardKey by remember { mutableStateOf<String?>(null) }
    var heroKey by remember { mutableStateOf<String?>(null) }
    var heroButtonsFocused by remember { mutableStateOf(false) }
    var topBarFocused by remember { mutableStateOf(false) }
    var rootHasFocus by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<AppEntry?>(null) }
    var actionsFromFavorites by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var movingKey by remember { mutableStateOf<String?>(null) }
    var heroIndex by remember { mutableIntStateOf(0) }
    val interaction = remember { InteractionClock() }
    var perfSheetOpen by remember { mutableStateOf(false) }
    var raspberryOpen by remember { mutableStateOf(false) }
    var screensaverPreviewOpen by remember { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.Overview) }
    val performanceTile = remember { FocusRequester() }
    val appearanceTile = remember { FocusRequester() }
    val dialogOpen = actionsFor != null || aboutOpen || pickerOpen || perfSheetOpen || raspberryOpen || screensaverPreviewOpen

    fun focusHero() {
        scope.launch {
            withFrameNanos { } // let the Home tab compose if we just switched to it
            withFrameNanos { }
            runCatching { heroOpen.requestFocus() }
        }
    }

    fun goHome() {
        selected = KrelzoxTab.Home
        focusHero()
    }

    fun focusTab(tab: KrelzoxTab) {
        scope.launch {
            withFrameNanos { }
            runCatching { tabRequesters.getValue(tab).requestFocus() }
        }
    }

    // Opening a sub-page (performance center, appearance) removes the focused tile. Park focus
    // on the Ajustes tab (present on every page) BEFORE swapping the page, or Compose hands
    // focus to the first focusable (the Inicio tab) and select-on-focus switches tabs. The
    // page then focuses its own first element.
    fun openSettingsPage(page: SettingsPage) {
        runCatching { tabRequesters.getValue(KrelzoxTab.Settings).requestFocus() }
        selected = KrelzoxTab.Settings
        settingsPage = page
    }

    // Focus must never be lost: on first display, or when the focused element disappears
    // (uninstalled app, removed favorite), go to the hero, else to the selected tab.
    LaunchedEffect(rootHasFocus, dialogOpen) {
        if (rootHasFocus || dialogOpen) return@LaunchedEffect
        withFrameNanos { }
        if (selected == KrelzoxTab.Home) runCatching { heroOpen.requestFocus() }
        withFrameNanos { }
        if (!rootHasFocus) runCatching { tabRequesters.getValue(selected).requestFocus() }
    }

    // HOME while in front: close dialogs, reset the hero and go back to the top of Inicio.
    LaunchedEffect(homeResets) {
        homeResets.collect {
            actionsFor = null
            aboutOpen = false
            pickerOpen = false
            perfSheetOpen = false
            raspberryOpen = false
            screensaverPreviewOpen = false
            settingsPage = SettingsPage.Overview
            movingKey = null
            focusedCardKey = null
            heroKey = null
            heroIndex = 0
            goHome()
        }
    }

    // Back is hierarchical: content → its tab → Inicio → hero. On the hero it is not
    // consumed, so MainActivity applies its rule (no-op as HOME, exit as a normal app).
    val atRoot = selected == KrelzoxTab.Home && heroButtonsFocused && movingKey == null
    BackHandler(enabled = !dialogOpen && !atRoot) {
        when {
            movingKey != null -> movingKey = null
            selected == KrelzoxTab.Settings && settingsPage != SettingsPage.Overview && !topBarFocused -> {
                // Park focus on the Ajustes tab (present on every page) BEFORE swapping the page:
                // otherwise the focused element disappears, Compose gives focus to the first
                // focusable (the Inicio tab) and select-on-focus would switch tabs. Then return
                // to the tile that opened the page.
                val tile = if (settingsPage == SettingsPage.Appearance) appearanceTile else performanceTile
                runCatching { tabRequesters.getValue(KrelzoxTab.Settings).requestFocus() }
                settingsPage = SettingsPage.Overview
                scope.launch {
                    withFrameNanos { }
                    runCatching { tile.requestFocus() }
                }
            }
            selected != KrelzoxTab.Home && !topBarFocused -> focusTab(selected)
            selected != KrelzoxTab.Home -> goHome()
            else -> focusHero()
        }
    }

    // The performance center is a sub-page of Ajustes: leaving the tab returns to its overview.
    LaunchedEffect(selected) {
        if (selected != KrelzoxTab.Settings) settingsPage = SettingsPage.Overview
    }

    // Free cached art of uninstalled apps.
    LaunchedEffect(state.apps) {
        artLoader.retainOnly(state.apps.mapTo(HashSet()) { it.key })
    }

    // A reorder in progress ends if its favorite disappears (uninstalled, removed).
    LaunchedEffect(state.favoriteKeys) {
        if (movingKey != null && movingKey !in state.favoriteKeys) movingKey = null
    }

    val appActions = remember(callbacks, movingKey) {
        AppActions(
            onLaunch = callbacks.onLaunch,
            onShowActions = { actionsFromFavorites = false; actionsFor = it },
            onShowFavoriteActions = { actionsFromFavorites = true; actionsFor = it },
            onToggleFavorite = callbacks.onToggleFavorite,
            onAddFavorites = { pickerOpen = true },
            movingKey = movingKey,
            onMoveFavorite = callbacks.onMoveFavorite,
            onMoveDone = { movingKey = null },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(KrelzoxColors.Background)
                // Static ambient brand light, top-left. Drawn, never blurred.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(KrelzoxColors.Electric.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.width * 0.55f,
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(0f, 0f),
                )
            }
            .onFocusChanged { rootHasFocus = it.hasFocus }
            // Remote activity pauses the hero carousel. Observes only; never consumes keys.
            .onPreviewKeyEvent { interaction.touch(); false },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = KrelzoxSpacing.SafeHorizontal, end = KrelzoxSpacing.SafeHorizontal, top = KrelzoxSpacing.Large - 4.dp),
        ) {
            TopBar(
                selected = selected,
                tabRequesters = tabRequesters,
                network = network,
                metrics = system.metrics,
                onOpenPerformance = { perfSheetOpen = true },
                onSelect = { selected = it },
                onEnterTab = { tab ->
                    selected = tab
                    if (tab == KrelzoxTab.Home) focusHero() else focusManager.moveFocus(FocusDirection.Down)
                },
                modifier = Modifier.onFocusChanged { topBarFocused = it.hasFocus },
                // In the Ajustes grid, ↓ (or OK) from the tab goes to "Sistema y rendimiento":
                // otherwise the tile under the tab (Red) gets focus and OK opens Android settings.
                contentEntry = if (selected == KrelzoxTab.Settings && settingsPage == SettingsPage.Overview) performanceTile else null,
            )
            Spacer(Modifier.height(KrelzoxSpacing.Medium))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (selected) {
                    KrelzoxTab.Home -> HomeTab(
                        state = state,
                        artLoader = artLoader,
                        actions = appActions,
                        heroOpenRequester = heroOpen,
                        focusedCardKey = focusedCardKey,
                        onCardFocused = { focusedCardKey = it },
                        heroKey = heroKey,
                        onHeroKeyChange = { heroKey = it },
                        heroIndex = heroIndex,
                        onHeroIndexChange = { heroIndex = it },
                        interaction = interaction,
                        onHeroButtonsFocusChanged = { heroButtonsFocused = it },
                        onOpenLibrary = {
                            selected = KrelzoxTab.Library
                            focusTab(KrelzoxTab.Library)
                        },
                    )
                    KrelzoxTab.Library -> AppGridTab(
                        title = stringResource(R.string.tab_library),
                        subtitle = pluralStringResource(R.plurals.apps_count, state.apps.size, state.apps.size),
                        entries = state.apps,
                        favoriteKeys = state.favoriteKeys,
                        artLoader = artLoader,
                        actions = appActions,
                        emptyIcon = KrelzoxIcons.Library,
                        emptyText = stringResource(R.string.home_empty),
                        emptyActionText = stringResource(R.string.open_settings),
                        onEmptyAction = callbacks.onSystemSettings,
                    )
                    KrelzoxTab.Media -> AppGridTab(
                        title = stringResource(R.string.tab_media),
                        subtitle = stringResource(R.string.media_subtitle),
                        entries = state.media,
                        favoriteKeys = state.favoriteKeys,
                        artLoader = artLoader,
                        actions = appActions,
                        emptyIcon = KrelzoxIcons.Media,
                        emptyText = stringResource(R.string.media_empty),
                        emptyActionText = stringResource(R.string.see_library_short),
                        onEmptyAction = {
                            selected = KrelzoxTab.Library
                            focusTab(KrelzoxTab.Library)
                        },
                    )
                    KrelzoxTab.Settings -> when (settingsPage) {
                        SettingsPage.Overview -> SettingsTab(
                            actions = SettingsActions(
                                onPerformance = { openSettingsPage(SettingsPage.Performance) },
                                onAppearance = { openSettingsPage(SettingsPage.Appearance) },
                                onRaspberry = if (callbacks.showRaspberrySettings) {
                                    { raspberryOpen = true }
                                } else {
                                    null
                                },
                                onNetwork = callbacks.onNetworkSettings,
                                onDisplaySound = callbacks.onDisplaySoundSettings,
                                onHomeApp = callbacks.onHomeAppSettings,
                                onSystemSettings = callbacks.onSystemSettings,
                                onAbout = { aboutOpen = true },
                            ),
                            metrics = system.metrics,
                            performanceTile = performanceTile,
                            appearanceTile = appearanceTile,
                        )
                        SettingsPage.Performance -> PerformanceCenter(
                            metrics = system.metrics,
                            network = system.network,
                            appCount = state.apps.size,
                        )
                        SettingsPage.Appearance -> AppearancePage(
                            current = KrelzoxColors.accent,
                            onSelectAccent = callbacks.onSelectAccent,
                            onPreviewScreensaver = { screensaverPreviewOpen = true },
                            onOpenSystemSettings = callbacks.onSystemSettings,
                        )
                    }
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
            // Live reorder needs the focus on the card in the favorites row.
            onReorder = if (actionsFromFavorites && index >= 0 && state.favorites.size > 1) {
                { movingKey = entry.key }
            } else {
                null
            },
            onManageFavorites = { pickerOpen = true },
            onAppInfo = { callbacks.onAppInfo(entry) },
            onDismiss = { actionsFor = null },
        )
    }
    if (aboutOpen) {
        AboutDialog(appCount = state.apps.size, onDismiss = { aboutOpen = false })
    }
    if (perfSheetOpen) {
        PerformanceSheet(
            metrics = system.metrics,
            onOpenCenter = {
                perfSheetOpen = false
                selected = KrelzoxTab.Settings
                settingsPage = SettingsPage.Performance
            },
            onDismiss = { perfSheetOpen = false },
        )
    }
    if (raspberryOpen) {
        RaspberrySettingsDialog(
            onOpenSettings = {
                raspberryOpen = false
                callbacks.onSystemSettings()
            },
            onDismiss = { raspberryOpen = false },
        )
    }
    if (screensaverPreviewOpen) {
        ScreensaverPreview(onDismiss = { screensaverPreviewOpen = false })
    }
    if (pickerOpen) {
        FavoritesPickerDialog(
            apps = state.apps,
            favoriteKeys = state.favoriteKeys,
            artLoader = artLoader,
            onToggle = callbacks.onToggleFavorite,
            onDismiss = { pickerOpen = false },
        )
    }
}

