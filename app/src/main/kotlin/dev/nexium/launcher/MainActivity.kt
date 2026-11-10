package dev.nexium.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.nexium.launcher.core.designsystem.NexiumTheme
import dev.nexium.launcher.safety.SafeModeView
import dev.nexium.launcher.ui.AppLauncher
import dev.nexium.launcher.ui.home.HomeScreen
import dev.nexium.launcher.ui.home.HomeViewModel
import dev.nexium.launcher.ui.shell.ShellCallbacks
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {

    /** Emits when HOME is pressed while Nexium is already in front: back to the top. */
    private val homeResets = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NexiumApp

        // As the home app, Back must never leave the user on an empty screen.
        // When launched as a normal app, Back exits as usual.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isDefaultHome()) finish()
            }
        })

        if (app.crashGuard.isInCrashLoop()) {
            showSafeMode(app)
            return
        }
        showHome(app.container)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeResets.tryEmit(Unit)
    }

    private fun showHome(container: AppContainer) {
        setContent {
            NexiumTheme {
                val viewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.factory(
                        container.appRepository,
                        container.favoritesRepository,
                        container.recentsRepository,
                        container.networkRepository,
                        heroExcludedPackages = remember { utilityPackages() },
                    ),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val network by viewModel.networkStatus.collectAsStateWithLifecycle()
                val callbacks = remember(viewModel) {
                    ShellCallbacks(
                        onLaunch = { entry ->
                            if (AppLauncher.launch(this, entry)) {
                                viewModel.recordLaunch(entry)
                            } else {
                                showLaunchError(entry.label)
                            }
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onMoveFavorite = viewModel::moveFavorite,
                        onAppInfo = { entry ->
                            if (!AppLauncher.openAppInfo(this, entry.packageName)) showLaunchError(entry.label)
                        },
                        onNetworkSettings = { AppLauncher.openSystemScreen(this, Settings.ACTION_WIFI_SETTINGS) },
                        onDisplaySoundSettings = { AppLauncher.openSystemScreen(this, Settings.ACTION_SOUND_SETTINGS) },
                        onHomeAppSettings = { AppLauncher.openSystemScreen(this, Settings.ACTION_HOME_SETTINGS) },
                        onSystemSettings = { AppLauncher.openSettings(this) },
                    )
                }
                HomeScreen(
                    state = state,
                    network = network,
                    artLoader = container.artLoader,
                    homeResets = homeResets,
                    callbacks = callbacks,
                )
            }
        }
    }

    /** Crash loop detected: plain-View recovery screen, no Compose, no data loading. */
    private fun showSafeMode(app: NexiumApp) {
        setContentView(
            SafeModeView.create(
                context = this,
                onChooseLauncher = {
                    if (!AppLauncher.openHomeSettings(this)) AppLauncher.openSettings(this)
                },
                onOpenSettings = { AppLauncher.openSettings(this) },
                onRetry = {
                    app.crashGuard.reset()
                    recreate()
                },
            ),
        )
    }

    /**
     * Utility apps kept out of the hero carousel (they have their own tab): the package
     * that handles system Settings. Resolved at runtime, so it works on any device.
     */
    private fun utilityPackages(): Set<String> = runCatching {
        packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)
            ?.activityInfo?.packageName
            ?.let { setOf(it) }
    }.getOrNull().orEmpty()

    private fun showLaunchError(label: String) {
        Toast.makeText(this, getString(R.string.launch_failed, label), Toast.LENGTH_SHORT).show()
    }

    private fun isDefaultHome(): Boolean =
        getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
}
