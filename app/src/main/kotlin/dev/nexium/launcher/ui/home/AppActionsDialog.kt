package dev.nexium.launcher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.nexium.launcher.R
import dev.nexium.launcher.core.data.AppEntry
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumDimens
import dev.nexium.launcher.core.designsystem.NexiumIcons
import dev.nexium.launcher.core.designsystem.NexiumSpacing

private val DialogWidth = 420.dp
private val DialogShape = RoundedCornerShape(20.dp)

/**
 * Per-app actions, reachable with long-press OK or the Menu key. Back closes it.
 * Move actions are shown only for favorites that can move in that direction.
 */
@Composable
fun AppActionsDialog(
    entry: AppEntry,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveLeft: (() -> Unit)?,
    onMoveRight: (() -> Unit)?,
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
                style = MaterialTheme.typography.titleLarge,
                color = NexiumColors.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = NexiumSpacing.Small),
            )
            ActionButton(
                text = stringResource(R.string.action_open),
                icon = NexiumIcons.Play,
                onClick = { onDismiss(); onOpen() },
                modifier = Modifier.focusRequester(first),
            )
            ActionButton(
                text = stringResource(
                    if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite,
                ),
                icon = NexiumIcons.Star,
                onClick = { onDismiss(); onToggleFavorite() },
            )
            if (onMoveLeft != null) {
                ActionButton(
                    text = stringResource(R.string.action_move_left),
                    icon = NexiumIcons.ArrowLeft,
                    onClick = { onDismiss(); onMoveLeft() },
                )
            }
            if (onMoveRight != null) {
                ActionButton(
                    text = stringResource(R.string.action_move_right),
                    icon = NexiumIcons.ArrowRight,
                    onClick = { onDismiss(); onMoveRight() },
                )
            }
            ActionButton(
                text = stringResource(R.string.action_app_info),
                icon = NexiumIcons.Info,
                onClick = { onDismiss(); onAppInfo() },
            )
            ActionButton(
                text = stringResource(R.string.action_cancel),
                icon = null,
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
private fun ActionButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ButtonDefaults.shape(RoundedCornerShape(NexiumDimens.ButtonCorner)),
        colors = ButtonDefaults.colors(
            containerColor = NexiumColors.Surface,
            contentColor = NexiumColors.OnBackground,
            focusedContainerColor = NexiumColors.Electric,
            focusedContentColor = NexiumColors.OnBackground,
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(BorderStroke(1.5.dp, NexiumColors.Cyan)),
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.02f),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(NexiumSpacing.Medium))
        } else {
            Spacer(Modifier.width(18.dp + NexiumSpacing.Medium))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
