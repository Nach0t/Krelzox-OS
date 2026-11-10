package dev.nexium.launcher.ui.home

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
import dev.nexium.launcher.R
import dev.nexium.launcher.core.data.NetworkStatus
import dev.nexium.launcher.core.designsystem.NexiumButtonDefaults
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumSpacing
import dev.nexium.launcher.ui.AppArtLoader
import dev.nexium.launcher.ui.shell.NexiumShell
import dev.nexium.launcher.ui.shell.ShellCallbacks
import kotlinx.coroutines.flow.Flow

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
) {
    when (state) {
        HomeUiState.Loading -> Message(stringResource(R.string.home_loading))
        HomeUiState.Error -> MessageWithSettings(stringResource(R.string.home_error), callbacks.onSystemSettings)
        is HomeUiState.Ready ->
            if (state.apps.isEmpty()) {
                MessageWithSettings(stringResource(R.string.home_empty), callbacks.onSystemSettings)
            } else {
                NexiumShell(state, network, artLoader, homeResets, callbacks)
            }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().background(NexiumColors.Background), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = NexiumColors.OnSurfaceMuted)
    }
}

/** Fallback that always offers a way out, so the user is never stuck on an empty home. */
@Composable
private fun MessageWithSettings(text: String, onOpenSettings: () -> Unit) {
    val button = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxSize().background(NexiumColors.Background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = NexiumColors.OnSurfaceMuted)
        Spacer(Modifier.height(NexiumSpacing.Medium))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.focusRequester(button),
            colors = NexiumButtonDefaults.colors(),
            border = NexiumButtonDefaults.border(),
            shape = NexiumButtonDefaults.shape(),
        ) {
            Text(stringResource(R.string.open_settings))
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { button.requestFocus() }
    }
}
