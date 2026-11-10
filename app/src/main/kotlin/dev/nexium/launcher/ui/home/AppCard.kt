package dev.nexium.launcher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.nexium.launcher.R
import dev.nexium.launcher.core.data.AppEntry
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumDimens
import dev.nexium.launcher.core.designsystem.NexiumMotion
import dev.nexium.launcher.core.designsystem.NexiumSpacing
import dev.nexium.launcher.ui.AppArt
import dev.nexium.launcher.ui.AppArtLoader

internal val CardShape = RoundedCornerShape(NexiumDimens.CardCorner)
private val FavoriteDotSize = 8.dp
private val IconFieldBrush = Brush.verticalGradient(listOf(NexiumColors.SurfaceRaised, NexiumColors.Surface))

/** Shared Nexium focus treatment for 16:9 tiles: scale, Ion border and a moderate blue glow. */
@Composable
internal fun nexiumCardBorder() = CardDefaults.border(
    focusedBorder = Border(
        border = BorderStroke(NexiumDimens.FocusBorder, NexiumColors.IonBrush),
        shape = CardShape,
    ),
)

@Composable
internal fun nexiumCardGlow() = CardDefaults.glow(
    focusedGlow = Glow(elevationColor = NexiumColors.Electric.copy(alpha = 0.45f), elevation = NexiumDimens.FocusGlow),
)

/**
 * App tile. OK launches; long-press OK or the Menu key opens the actions dialog.
 */
@Composable
fun AppCard(
    entry: AppEntry,
    isFavorite: Boolean,
    artLoader: AppArtLoader,
    onClick: () -> Unit,
    onShowActions: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = NexiumDimens.AppIcon,
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

    Column(modifier) {
        Card(
            onClick = onClick,
            onLongClick = onShowActions,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onPreviewKeyEvent { event ->
                    if (event.key != Key.Menu) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp) onShowActions()
                    true
                }
                .semantics { contentDescription = description },
            shape = CardDefaults.shape(CardShape),
            colors = CardDefaults.colors(
                containerColor = NexiumColors.Surface,
                focusedContainerColor = NexiumColors.SurfaceRaised,
            ),
            scale = CardDefaults.scale(focusedScale = NexiumMotion.FOCUS_SCALE),
            border = nexiumCardBorder(),
            glow = nexiumCardGlow(),
        ) {
            Box(Modifier.fillMaxSize()) {
                when (val current = art) {
                    is AppArt.Banner -> Image(
                        bitmap = current.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is AppArt.Icon -> Box(
                        Modifier
                            .fillMaxSize()
                            .background(IconFieldBrush),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = current.image,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                    null -> Unit
                }
                if (isFavorite) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(NexiumSpacing.Small + 2.dp)
                            .size(FavoriteDotSize)
                            .background(NexiumColors.Cyan, CircleShape),
                    )
                }
            }
        }
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) NexiumColors.OnBackground else NexiumColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = NexiumSpacing.Small + 2.dp, start = 2.dp),
        )
    }
}
