package dev.nexium.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.nexium.launcher.core.designsystem.NexiumTheme
import dev.nexium.launcher.ui.AppLauncher
import dev.nexium.launcher.ui.home.HomeScreen
import dev.nexium.launcher.ui.home.HomeViewModel
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
        val container = (application as NexiumApp).container

        // As the home app, Back must never leave the user on an empty screen.
        // When launched as a normal app (current stage), Back exits as usual.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isDefaultHome()) finish()
            }
        })

        setContent {
            NexiumTheme {
                val viewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.factory(container.appRepository, container.favoritesRepository),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    artLoader = container.artLoader,
                    homeResets = homeResets,
                    onLaunch = { entry ->
                        if (!AppLauncher.launch(this, entry)) showLaunchError(entry.label)
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onAppInfo = { entry ->
                        if (!AppLauncher.openAppInfo(this, entry.packageName)) showLaunchError(entry.label)
                    },
                    onOpenSettings = { AppLauncher.openSettings(this) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeResets.tryEmit(Unit)
    }

    private fun showLaunchError(label: String) {
        Toast.makeText(this, getString(R.string.launch_failed, label), Toast.LENGTH_SHORT).show()
    }

    private fun isDefaultHome(): Boolean =
        getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
}
