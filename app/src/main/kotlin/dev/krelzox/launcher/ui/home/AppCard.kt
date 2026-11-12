package dev.krelzox.launcher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.krelzox.launcher.R
import dev.krelzox.launcher.core.data.AppEntry
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxDimens
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxMotion
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.launcher.ui.AppArtLoader

internal val CardShape = RoundedCornerShape(KrelzoxDimens.CardCorner)
private val FavoriteDotSize = 8.dp

/** Shared Krelzox focus treatment for 16:9 tiles: scale, Ion border and a moderate blue glow. */
@Composable
internal fun krelzoxCardBorder() = CardDefaults.border(
    focusedBorder = Border(
        border = BorderStroke(KrelzoxDimens.FocusBorder, KrelzoxColors.IonBrush),
        shape = CardShape,
    ),
)

@Composable
internal fun krelzoxCardGlow() = CardDefaults.glow(
    focusedGlow = Glow(elevationColor = KrelzoxColors.Electric.copy(alpha = 0.45f), elevation = KrelzoxDimens.FocusGlow),
)

/** Uniform icon plate: graphite with a faint tint of the app's own color. Drawn, never blurred. */
internal fun Modifier.iconPlate(accent: Color?): Modifier = drawBehind {
    val tint = accent ?: KrelzoxColors.Electric
    drawRect(
        Brush.linearGradient(
            colors = listOf(lerp(KrelzoxColors.SurfaceRaised, tint, 0.20f), KrelzoxColors.Surface),
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
        ),
    )
}

/** Reorder-mode callbacks for a favorite card. */
class MoveControls(val onMove: (Int) -> Unit, val onDone: () -> Unit)

/**
 * App tile: the app icon on a uniform plate, identical proportions in every row and grid.
 * OK launches; long-press OK or Menu opens the actions dialog. In reorder mode
 * ([moveControls] not null) ←/→ move the card, OK or Back finish, ↑/↓ are blocked.
 */
@Composable
fun AppCard(
    entry: AppEntry,
    isFavorite: Boolean,
    artLoader: AppArtLoader,
    onClick: () -> Unit,
    onShowActions: () -> Unit,
    modifier: Modifier = Modifier,
    moveControls: MoveControls? = null,
) {
    val art by produceState(initialValue = artLoader.peek(entry), entry.key) {
        if (value == null) value = artLoader.load(entry)
    }
    val description = if (isFavorite) {
        stringResource(R.string.card_description_favorite, entry.label)
    } else {
        entry.label
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val moving = moveControls != null

    Column(modifier) {
        Card(
            onClick = onClick,
            onLongClick = onShowActions,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onPreviewKeyEvent { event ->
                    if (moveControls != null) {
                        return@onPreviewKeyEvent handleMoveKey(event.key, event.type, moveControls)
                    }
                    if (event.key != Key.Menu) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp) onShowActions()
                    true
                }
                .semantics { contentDescription = description },
            shape = CardDefaults.shape(CardShape),
            colors = CardDefaults.colors(
                containerColor = KrelzoxColors.Surface,
                focusedContainerColor = KrelzoxColors.SurfaceRaised,
            ),
            scale = CardDefaults.scale(focusedScale = KrelzoxMotion.FOCUS_SCALE),
            border = krelzoxCardBorder(),
            glow = krelzoxCardGlow(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .iconPlate(art?.accent?.let { Color(it) }),
                contentAlignment = Alignment.Center,
            ) {
                art?.let { current ->
                    Image(
                        bitmap = current.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxHeight(KrelzoxDimens.CARD_ICON_FRACTION)
                            .aspectRatio(1f),
                    )
                }
                if (isFavorite && !moving) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(KrelzoxSpacing.Small + 2.dp)
                            .size(FavoriteDotSize)
                            .background(KrelzoxColors.Cyan, CircleShape),
                    )
                }
                if (moving) MoveOverlay()
            }
        }
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused || moving) KrelzoxColors.OnBackground else KrelzoxColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = KrelzoxSpacing.Small + 2.dp, start = 2.dp),
        )
    }
}

/** Reorder affordance: cyan frame and ← → chevrons at the card edges. */
@Composable
private fun MoveOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .border(BorderStroke(2.dp, KrelzoxColors.Cyan), CardShape)
            .background(KrelzoxColors.BackgroundDeep.copy(alpha = 0.25f)),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = KrelzoxSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KrelzoxIcons.ArrowLeft, contentDescription = null, tint = KrelzoxColors.Cyan, modifier = Modifier.size(22.dp))
            Box(Modifier.weight(1f))
            Icon(KrelzoxIcons.ArrowRight, contentDescription = null, tint = KrelzoxColors.Cyan, modifier = Modifier.size(22.dp))
        }
    }
}

private fun handleMoveKey(key: Key, type: KeyEventType, controls: MoveControls): Boolean = when (key) {
    Key.DirectionLeft -> {
        if (type == KeyEventType.KeyDown) controls.onMove(-1)
        true
    }
    Key.DirectionRight -> {
        if (type == KeyEventType.KeyDown) controls.onMove(+1)
        true
    }
    Key.DirectionUp, Key.DirectionDown, Key.Menu -> true // stay on the card while moving
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back, Key.Escape -> {
        if (type == KeyEventType.KeyUp) controls.onDone()
        true
    }
    else -> false
}
