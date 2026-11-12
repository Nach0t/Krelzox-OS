package dev.krelzox.store.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.launcher.core.designsystem.KrelzoxTheme

class StoreActivity : ComponentActivity() {

    private val viewModel: StoreViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KrelzoxTheme {
                StoreRoot(
                    viewModel = viewModel,
                    actions = StoreActions(
                        openApp = { pkg -> viewModel.launchIntent(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                        allowInstalls = {
                            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri()))
                        },
                        exit = ::finish,
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Installs, uninstalls or permission changes may have happened outside the store.
        viewModel.onResume()
    }
}

/** Platform actions the UI needs, provided by the activity. */
class StoreActions(
    val openApp: (String) -> Unit,
    val allowInstalls: () -> Unit,
    val exit: () -> Unit,
)

@Composable
fun StoreRoot(viewModel: StoreViewModel, actions: StoreActions) {
    val section by viewModel.section.collectAsStateWithLifecycle()
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()

    BackHandler {
        when {
            viewModel.back() -> Unit
            section != Section.HOME -> viewModel.select(Section.HOME)
            else -> actions.exit()
        }
    }

    Row(Modifier.fillMaxSize().background(KrelzoxColors.Background)) {
        NavigationRail(
            selected = section,
            updatesCount = updates.size,
            onSelect = viewModel::select,
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = KrelzoxSpacing.SafeVertical, end = KrelzoxSpacing.SafeHorizontal),
        ) {
            when (val route = routes.lastOrNull()) {
                is Route.App -> AppDetailScreen(viewModel, route.packageName, actions)
                is Route.Category -> CategoryScreen(viewModel, route.name)
                null -> SectionScreen(viewModel, section, actions)
            }
        }
    }
}

@Composable
private fun SectionScreen(viewModel: StoreViewModel, section: Section, actions: StoreActions) {
    when (section) {
        Section.SEARCH -> SearchScreen(viewModel)
        Section.HOME -> HomeScreen(viewModel)
        Section.APPS -> BrowseScreen(viewModel, games = false)
        Section.GAMES -> BrowseScreen(viewModel, games = true)
        Section.CATEGORIES -> CategoriesScreen(viewModel)
        Section.LIBRARY -> LibraryScreen(viewModel)
        Section.INSTALLED -> InstalledScreen(viewModel)
        Section.UPDATES -> UpdatesScreen(viewModel)
        Section.FAVORITES -> FavoritesScreen(viewModel)
        Section.SETTINGS -> SettingsScreen(viewModel, actions)
    }
}
