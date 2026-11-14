package dev.krelzox.launcher.ui.home

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
import dev.krelzox.launcher.R
import dev.krelzox.launcher.core.data.AppEntry
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxDimens
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.launcher.core.designsystem.touchClick

private val DialogWidth = 420.dp
private val DialogShape = RoundedCornerShape(20.dp)

/**
 * Per-app actions, reachable with long-press OK or the Menu key. Back closes it.
 * "Reorder" (live ←/→ move mode) is shown only for favorites when there are at least two.
 */
@Composable
fun AppActionsDialog(
    entry: AppEntry,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onReorder: (() -> Unit)?,
    onManageFavorites: () -> Unit,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        Column(
            modifier = Modifier
                .width(DialogWidth)
                .background(KrelzoxColors.SurfaceRaised, DialogShape)
                .padding(KrelzoxSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Small),
        ) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleLarge,
                color = KrelzoxColors.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = KrelzoxSpacing.Small),
            )
            ActionButton(
                text = stringResource(R.string.action_open),
                icon = KrelzoxIcons.Play,
                onClick = { onDismiss(); onOpen() },
                modifier = Modifier.focusRequester(first),
            )
            ActionButton(
                text = stringResource(
                    if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite,
                ),
                icon = KrelzoxIcons.Star,
                onClick = { onDismiss(); onToggleFavorite() },
            )
            if (onReorder != null) {
                ActionButton(
                    text = stringResource(R.string.action_reorder),
                    icon = KrelzoxIcons.Reorder,
                    onClick = { onDismiss(); onReorder() },
                )
            }
            ActionButton(
                text = stringResource(R.string.action_manage_favorites),
                icon = KrelzoxIcons.Plus,
                onClick = { onDismiss(); onManageFavorites() },
            )
            ActionButton(
                text = stringResource(R.string.action_app_info),
                icon = KrelzoxIcons.Info,
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
        modifier = modifier.touchClick(onClick).fillMaxWidth(),
        shape = ButtonDefaults.shape(RoundedCornerShape(KrelzoxDimens.ButtonCorner)),
        colors = ButtonDefaults.colors(
            containerColor = KrelzoxColors.Surface,
            contentColor = KrelzoxColors.OnBackground,
            focusedContainerColor = KrelzoxColors.Electric,
            focusedContentColor = KrelzoxColors.OnBackground,
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(BorderStroke(1.5.dp, KrelzoxColors.Cyan)),
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.02f),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(KrelzoxSpacing.Medium))
        } else {
            Spacer(Modifier.width(18.dp + KrelzoxSpacing.Medium))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
