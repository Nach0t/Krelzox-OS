package dev.nexium.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.nexium.launcher.R
import dev.nexium.launcher.core.data.AppEntry
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumSpacing

private val DialogWidth = 400.dp
private val DialogShape = RoundedCornerShape(16.dp)

/** Per-app actions, reachable with long-press OK or the Menu key. Back closes it. */
@Composable
fun AppActionsDialog(
    entry: AppEntry,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        Column(
            modifier = Modifier
                .width(DialogWidth)
                .background(NexiumColors.SurfaceRaised, DialogShape)
                .padding(NexiumSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(NexiumSpacing.Small),
        ) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleMedium,
                color = NexiumColors.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = NexiumSpacing.Small),
            )
            ActionButton(
                text = stringResource(R.string.action_open),
                onClick = { onDismiss(); onOpen() },
                modifier = Modifier.focusRequester(first),
            )
            ActionButton(
                text = stringResource(
                    if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite,
                ),
                onClick = { onDismiss(); onToggleFavorite() },
            )
            ActionButton(
                text = stringResource(R.string.action_app_info),
                onClick = { onDismiss(); onAppInfo() },
            )
            ActionButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
            )
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ButtonDefaults.colors(
            containerColor = NexiumColors.Surface,
            contentColor = NexiumColors.OnBackground,
            focusedContainerColor = NexiumColors.IonStart,
            focusedContentColor = NexiumColors.OnBackground,
        ),
    ) {
        Text(text)
    }
}
