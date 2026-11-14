package dev.krelzox.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.krelzox.launcher.R
import dev.krelzox.launcher.core.data.NetworkStatus
import dev.krelzox.launcher.core.designsystem.KrelzoxButtonDefaults
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.launcher.ui.AppArtLoader
import dev.krelzox.launcher.ui.performance.SystemSources
import dev.krelzox.launcher.ui.shell.KrelzoxShell
import dev.krelzox.launcher.ui.shell.ShellCallbacks
import kotlinx.coroutines.flow.Flow
import dev.krelzox.launcher.core.designsystem.touchClick

/**
 * Entry point of the home UI. Loading, error and empty states keep the minimal
 * recovery layout (always offering Settings); a ready catalog gets the full shell.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    network: NetworkStatus,
    artLoader: AppArtLoader,
    homeResets: Flow<Unit>,
    callbacks: ShellCallbacks,
    system: SystemSources,
) {
    when (state) {
        HomeUiState.Loading -> Message(stringResource(R.string.home_loading))
        HomeUiState.Error -> MessageWithSettings(stringResource(R.string.home_error), callbacks.onSystemSettings)
        is HomeUiState.Ready ->
            if (state.apps.isEmpty()) {
                MessageWithSettings(stringResource(R.string.home_empty), callbacks.onSystemSettings)
            } else {
                KrelzoxShell(state, network, artLoader, homeResets, callbacks, system)
            }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().background(KrelzoxColors.Background), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = KrelzoxColors.OnSurfaceMuted)
    }
}

/** Fallback that always offers a way out, so the user is never stuck on an empty home. */
@Composable
private fun MessageWithSettings(text: String, onOpenSettings: () -> Unit) {
    val button = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxSize().background(KrelzoxColors.Background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = KrelzoxColors.OnSurfaceMuted)
        Spacer(Modifier.height(KrelzoxSpacing.Medium))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.touchClick(onOpenSettings).focusRequester(button),
            colors = KrelzoxButtonDefaults.colors(),
            border = KrelzoxButtonDefaults.border(),
            shape = KrelzoxButtonDefaults.shape(),
        ) {
            Text(stringResource(R.string.open_settings))
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { button.requestFocus() }
    }
}
