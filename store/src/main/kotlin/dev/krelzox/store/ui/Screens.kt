package dev.krelzox.store.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxGrid
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxMotion
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.store.R
import dev.krelzox.store.data.CatalogState
import dev.krelzox.store.data.Operation
import dev.krelzox.store.data.Outcome
import dev.krelzox.store.install.Task
import dev.krelzox.store.model.CatalogApp
import dev.krelzox.launcher.core.designsystem.touchClick

private const val ROW_LIMIT = 20
private val RowTileWidth = 196.dp

/** Shows loading / error states until the catalog has apps, then [content]. */
@Composable
private fun WithCatalog(viewModel: StoreViewModel, content: @Composable (CatalogState) -> Unit) {
    val state by viewModel.catalog.collectAsStateWithLifecycle()
    when {
        state.catalog.apps.isNotEmpty() -> content(state)
        state.loading || state.refreshing -> EmptyState(StoreIcons.Refresh, stringResource(R.string.loading_catalog))
        else -> EmptyState(StoreIcons.Warning, stringResource(R.string.catalog_error), stringResource(R.string.action_retry), viewModel::refreshCatalog)
    }
}

@Composable
fun HomeScreen(viewModel: StoreViewModel) = WithCatalog(viewModel) { state ->
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val apps = state.catalog.apps
    val rows = remember(apps, installed.keys) {
        val usedCategories = apps.filter { it.packageName in installed }.flatMap { it.categories }.toSet()
        listOf(
            R.string.section_tv to apps.filter { it.hasTvAssets }.sortedByDescending { it.lastUpdated },
            R.string.section_for_you to apps.filter { it.packageName !in installed && it.categories.any(usedCategories::contains) }
                .sortedByDescending { it.lastUpdated },
            R.string.section_updated to apps.sortedByDescending { it.lastUpdated },
            R.string.section_new to apps.sortedByDescending { it.added },
            R.string.section_games to apps.filter { it.isGame }.sortedByDescending { it.lastUpdated },
        ).map { (title, list) -> title to list.take(ROW_LIMIT) }.filter { it.second.isNotEmpty() }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Large),
        contentPadding = PaddingValues(bottom = KrelzoxSpacing.XLarge),
    ) {
        item {
            PageHeader(stringResource(R.string.home_title), pluralStringResource(R.plurals.home_subtitle, apps.size, apps.size))
        }
        items(rows, key = { it.first }) { (title, list) ->
            Column {
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.titleLarge,
                    color = KrelzoxColors.OnBackground,
                    modifier = Modifier.padding(start = KrelzoxGrid.FocusInset, bottom = KrelzoxSpacing.Small),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(KrelzoxGrid.Gap),
                    contentPadding = PaddingValues(KrelzoxGrid.FocusInset),
                ) {
                    items(list, key = { it.packageName }) { app ->
                        AppTile(
                            app = app,
                            images = viewModel.images,
                            installed = app.packageName in installed,
                            task = tasks[app.packageName],
                            onClick = { viewModel.open(Route.App(app.packageName)) },
                            modifier = Modifier.width(RowTileWidth),
                        )
                    }
                }
            }
        }
    }
}

/** Apps or Games: the whole catalog section, most recently updated first. */
@Composable
fun BrowseScreen(viewModel: StoreViewModel, games: Boolean) = WithCatalog(viewModel) { state ->
    val apps = remember(state.catalog, games) {
        state.catalog.apps.filter { it.isGame == games }.sortedByDescending { it.lastUpdated }
    }
    CatalogGrid(viewModel, apps) {
        PageHeader(
            stringResource(if (games) R.string.nav_games else R.string.nav_apps),
            pluralStringResource(R.plurals.section_apps_count, apps.size, apps.size),
        )
    }
}

@Composable
private fun CatalogGrid(viewModel: StoreViewModel, apps: List<CatalogApp>, header: @Composable () -> Unit) {
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    AppGrid(
        apps = apps,
        images = viewModel.images,
        installed = installed.keys,
        tasks = tasks,
        onOpen = { viewModel.open(Route.App(it.packageName)) },
        header = header,
    )
}

@Composable
fun CategoriesScreen(viewModel: StoreViewModel) = WithCatalog(viewModel) { state ->
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(KrelzoxGrid.FocusInset),
        horizontalArrangement = Arrangement.spacedBy(KrelzoxGrid.Gap),
        verticalArrangement = Arrangement.spacedBy(KrelzoxGrid.Gap),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            PageHeader(stringResource(R.string.nav_categories), pluralStringResource(R.plurals.section_apps_count, state.catalog.apps.size, state.catalog.apps.size))
        }
        items(state.catalog.categories, key = { it.first }) { (name, count) ->
            Card(
                onClick = { viewModel.open(Route.Category(name)) },
                modifier = Modifier.touchClick({ viewModel.open(Route.Category(name)) }).fillMaxWidth().aspectRatio(2.6f),
                shape = CardDefaults.shape(TileShape),
                colors = CardDefaults.colors(containerColor = KrelzoxColors.Surface, focusedContainerColor = KrelzoxColors.SurfaceRaised),
                scale = CardDefaults.scale(focusedScale = KrelzoxMotion.FOCUS_SCALE),
                border = tileBorder(),
                glow = tileGlow(),
            ) {
                // Icon beside the text: the tile is too short (~62 dp) to stack both.
                Row(
                    Modifier.fillMaxSize().padding(horizontal = KrelzoxSpacing.Medium, vertical = KrelzoxSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (CatalogApp.isGameCategory(name)) StoreIcons.Gamepad else StoreIcons.Category,
                        contentDescription = null,
                        tint = KrelzoxColors.Cyan,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(KrelzoxSpacing.Small))
                    Column {
                        Text(name, style = MaterialTheme.typography.titleMedium, color = KrelzoxColors.OnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pluralStringResource(R.plurals.section_apps_count, count, count), style = MaterialTheme.typography.bodyMedium, color = KrelzoxColors.OnSurfaceMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryScreen(viewModel: StoreViewModel, name: String) = WithCatalog(viewModel) { state ->
    val apps = remember(state.catalog, name) {
        state.catalog.apps.filter { name in it.categories }.sortedByDescending { it.lastUpdated }
    }
    CatalogGrid(viewModel, apps) { PageHeader(name, pluralStringResource(R.plurals.section_apps_count, apps.size, apps.size)) }
}

@Composable
fun SearchScreen(viewModel: StoreViewModel) = WithCatalog(viewModel) { _ ->
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    CatalogGrid(viewModel, results) {
        Column {
            PageHeader(stringResource(R.string.nav_search))
            Surface(
                modifier = Modifier.padding(start = KrelzoxGrid.FocusInset).width(720.dp),
                shape = RoundedCornerShape(50),
                colors = SurfaceDefaults.colors(containerColor = KrelzoxColors.SurfaceRaised),
            ) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(StoreIcons.Search, contentDescription = null, tint = KrelzoxColors.Cyan, modifier = Modifier.size(22.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = KrelzoxColors.OnBackground),
                        cursorBrush = SolidColor(KrelzoxColors.Cyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            focusManager.moveFocus(FocusDirection.Down)
                        }),
                        modifier = Modifier
                            .padding(start = 14.dp)
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onPreviewKeyEvent { event -> tvTextFieldKeys(event, keyboard, focusManager) },
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.titleMedium, color = KrelzoxColors.OnSurfaceSubtle)
                            }
                            inner()
                        },
                    )
                }
            }
            val message = when {
                query.isBlank() -> null
                results.isEmpty() -> stringResource(R.string.search_none, query)
                else -> pluralStringResource(R.plurals.search_results, results.size, results.size)
            }
            if (message != null) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = KrelzoxColors.OnSurfaceMuted,
                    modifier = Modifier.padding(start = KrelzoxGrid.FocusInset, top = KrelzoxSpacing.Medium),
                )
            }
        }
    }
}

@Composable
fun InstalledScreen(viewModel: StoreViewModel) = WithCatalog(viewModel) { _ ->
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    if (apps.isEmpty()) {
        EmptyState(StoreIcons.Installed, stringResource(R.string.installed_none))
    } else {
        CatalogGrid(viewModel, apps) { PageHeader(stringResource(R.string.nav_installed), pluralStringResource(R.plurals.section_apps_count, apps.size, apps.size)) }
    }
}

@Composable
fun FavoritesScreen(viewModel: StoreViewModel) = WithCatalog(viewModel) { _ ->
    val apps by viewModel.favoriteApps.collectAsStateWithLifecycle()
    if (apps.isEmpty()) {
        EmptyState(KrelzoxIcons.Star, stringResource(R.string.favorites_none))
    } else {
        CatalogGrid(viewModel, apps) { PageHeader(stringResource(R.string.nav_favorites), pluralStringResource(R.plurals.section_apps_count, apps.size, apps.size)) }
    }
}

@Composable
fun UpdatesScreen(viewModel: StoreViewModel) = WithCatalog(viewModel) { _ ->
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    if (updates.isEmpty()) {
        EmptyState(StoreIcons.Installed, stringResource(R.string.updates_none))
        return@WithCatalog
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Small)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PageHeader(stringResource(R.string.nav_updates), modifier = Modifier.weight(1f))
                StoreButton(stringResource(R.string.updates_all, updates.size), onClick = viewModel::updateAll, icon = StoreIcons.Update, primary = true)
            }
        }
        items(updates, key = { it.first.packageName }) { (app, status) ->
            ListRow(
                viewModel = viewModel,
                app = app,
                line = stringResource(R.string.status_update, status.installed.versionName, status.version.versionName),
                task = tasks[app.packageName],
            )
        }
    }
}

/** One app per line: icon, name, a status line and the running task. Opens the app page. */
@Composable
private fun ListRow(viewModel: StoreViewModel, app: CatalogApp, line: String, task: Task?) {
    Surface(
        onClick = { viewModel.open(Route.App(app.packageName)) },
        modifier = Modifier.touchClick({ viewModel.open(Route.App(app.packageName)) }).fillMaxWidth().padding(horizontal = KrelzoxGrid.FocusInset),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(TileShape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = KrelzoxColors.Surface,
            focusedContainerColor = KrelzoxColors.SurfaceRaised,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, KrelzoxColors.IonBrush), shape = TileShape),
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
    ) {
        Row(Modifier.padding(KrelzoxSpacing.Medium), verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(app.iconUrl, viewModel.images, 56.dp, Modifier.size(48.dp))
            Column(Modifier.padding(start = KrelzoxSpacing.Medium).weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, color = KrelzoxColors.OnBackground)
                Text(line, style = MaterialTheme.typography.bodyMedium, color = KrelzoxColors.OnSurfaceMuted)
                if (task != null) TaskLine(task, Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
fun LibraryScreen(viewModel: StoreViewModel) = WithCatalog(viewModel) { state ->
    val history by viewModel.history.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteApps.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Small)) {
        item {
            PageHeader(
                stringResource(R.string.nav_library),
                stringResource(R.string.library_summary, installedApps.size, updates.size, favorites.size),
            )
        }
        item {
            Text(
                stringResource(R.string.library_history),
                style = MaterialTheme.typography.titleLarge,
                color = KrelzoxColors.OnBackground,
                modifier = Modifier.padding(start = KrelzoxGrid.FocusInset, top = KrelzoxSpacing.Small),
            )
        }
        if (history.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.empty_list),
                    style = MaterialTheme.typography.bodyLarge,
                    color = KrelzoxColors.OnSurfaceMuted,
                    modifier = Modifier.padding(start = KrelzoxGrid.FocusInset),
                )
            }
        }
        items(history, key = { "${it.timestamp}-${it.packageName}-${it.operation}" }) { entry ->
            val op = stringResource(
                when (entry.operation) {
                    Operation.INSTALL -> R.string.op_install
                    Operation.UPDATE -> R.string.op_update
                    Operation.UNINSTALL -> R.string.op_uninstall
                },
            )
            val outcome = stringResource(
                when (entry.outcome) {
                    Outcome.SUCCESS -> R.string.outcome_success
                    Outcome.FAILED -> R.string.outcome_failed
                    Outcome.CANCELLED -> R.string.outcome_cancelled
                },
            )
            val app = state.catalog.byPackage[entry.packageName]
            val when_ = DateUtils.getRelativeTimeSpanString(entry.timestamp).toString()
            val version = entry.versionName?.let { " $it" }.orEmpty()
            if (app != null) {
                ListRow(viewModel, app, "$op$version · $outcome · $when_", null)
            } else {
                Text(
                    "${entry.appName} — $op$version · $outcome · $when_",
                    style = MaterialTheme.typography.bodyLarge,
                    color = KrelzoxColors.OnSurfaceMuted,
                    modifier = Modifier.padding(start = KrelzoxGrid.FocusInset + KrelzoxSpacing.Medium, top = 4.dp, bottom = 4.dp),
                )
            }
        }
    }
}
