package dev.nexium.launcher

import android.app.role.RoleManager
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NexiumApp).container

        // As the home app, Back must never leave the user on an empty screen.
        // When launched as a normal app (stage 1 testing), Back exits as usual.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isDefaultHome()) finish()
            }
        })

        setContent {
            NexiumTheme {
                val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container.appRepository))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    artLoader = container.artLoader,
                    onLaunch = { entry ->
                        if (!AppLauncher.launch(this, entry)) {
                            Toast.makeText(this, getString(R.string.launch_failed, entry.label), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenSettings = { AppLauncher.openSettings(this) },
                )
            }
        }
    }

    private fun isDefaultHome(): Boolean =
        getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
}
