package dev.nexium.launcher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.nexium.launcher.core.data.AppEntry
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumDimens
import dev.nexium.launcher.core.designsystem.NexiumMotion
import dev.nexium.launcher.core.designsystem.NexiumSpacing
import dev.nexium.launcher.ui.AppArt
import dev.nexium.launcher.ui.AppArtLoader

private val CardShape = RoundedCornerShape(NexiumDimens.CardCorner)

@Composable
fun AppCard(
    entry: AppEntry,
    artLoader: AppArtLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val art by produceState<AppArt?>(initialValue = null, entry.key) {
        value = artLoader.load(entry)
    }

    Column(modifier) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = CardDefaults.shape(CardShape),
            colors = CardDefaults.colors(
                containerColor = NexiumColors.Surface,
                focusedContainerColor = NexiumColors.SurfaceRaised,
            ),
            scale = CardDefaults.scale(focusedScale = NexiumMotion.FOCUS_SCALE),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(NexiumDimens.FocusBorder, NexiumColors.IonBrush),
                    shape = CardShape,
                ),
            ),
        ) {
            when (val current = art) {
                is AppArt.Banner -> Image(
                    bitmap = current.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                is AppArt.Icon -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = current.image,
                        contentDescription = null,
                        modifier = Modifier.size(NexiumDimens.AppIcon),
                    )
                }
                null -> Unit
            }
        }
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelMedium,
            color = NexiumColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = NexiumSpacing.Small),
        )
    }
}
