package dev.krelzox.store.ui

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.krelzox.launcher.core.designsystem.KrelzoxButtonDefaults
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxDimens
import dev.krelzox.launcher.core.designsystem.KrelzoxGrid
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxMotion
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.store.install.Task
import dev.krelzox.store.model.CatalogApp

internal val TileShape = RoundedCornerShape(KrelzoxDimens.CardCorner)

/** Same focus signature as the launcher: scale, accent border and a soft glow. */
@Composable
internal fun tileBorder() = CardDefaults.border(
    focusedBorder = Border(BorderStroke(KrelzoxDimens.FocusBorder, KrelzoxColors.IonBrush), shape = TileShape),
)

@Composable
internal fun tileGlow() = CardDefaults.glow(
    focusedGlow = Glow(elevationColor = KrelzoxColors.Electric.copy(alpha = 0.45f), elevation = KrelzoxDimens.FocusGlow),
)

/** Graphite plate with a faint accent tint behind an app icon. */
internal fun Modifier.iconPlate(): Modifier = drawBehind {
    drawRect(
        Brush.linearGradient(
            colors = listOf(lerp(KrelzoxColors.SurfaceRaised, KrelzoxColors.Electric, 0.16f), KrelzoxColors.Surface),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
    )
}

/** Remote image (icon, banner, screenshot) loaded through the store's cache. */
@Composable
fun RemoteImage(
    url: String?,
    images: ImageLoader,
    maxSide: Dp,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    if (url == null) return
    val px = with(LocalDensity.current) { maxSide.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(images.peek(url, px), url, px) {
        if (value == null) value = images.load(url, px)
    }
    bitmap?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = contentScale) }
}

/**
 * Catalog tile: icon on a plate (16:9), name and one line of summary. A small cyan dot
 * marks installed apps; a progress bar shows a running operation.
 */
@Composable
fun AppTile(
    app: CatalogApp,
    images: ImageLoader,
    installed: Boolean,
    task: Task?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier) {
        Card(
            onClick = onClick,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = CardDefaults.shape(TileShape),
            colors = CardDefaults.colors(containerColor = KrelzoxColors.Surface, focusedContainerColor = KrelzoxColors.SurfaceRaised),
            scale = CardDefaults.scale(focusedScale = KrelzoxMotion.FOCUS_SCALE),
            border = tileBorder(),
            glow = tileGlow(),
        ) {
            Box(Modifier.fillMaxSize().iconPlate(), contentAlignment = Alignment.Center) {
                RemoteImage(
                    url = app.iconUrl,
                    images = images,
                    maxSide = 96.dp,
                    modifier = Modifier.fillMaxHeight(KrelzoxDimens.CARD_ICON_FRACTION).aspectRatio(1f).clip(RoundedCornerShape(14.dp)),
                )
                if (installed) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(10.dp).size(8.dp)
                            .background(KrelzoxColors.Cyan, RoundedCornerShape(50)),
                    )
                }
                if (task != null && task !is Task.Failed) {
                    ProgressBar(
                        fraction = (task as? Task.Downloading)?.fraction,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                    )
                }
            }
        }
        Text(
            text = app.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) KrelzoxColors.OnBackground else KrelzoxColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp),
        )
        Text(
            text = app.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = KrelzoxColors.OnSurfaceSubtle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

/** Thin accent progress bar; [fraction] null draws an indeterminate (full, dimmed) bar. */
@Composable
fun ProgressBar(fraction: Float?, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(KrelzoxColors.Outline)) {
        Box(
            Modifier.fillMaxHeight()
                .fillMaxWidth(fraction ?: 1f)
                .background(if (fraction == null) Brush.horizontalGradient(listOf(KrelzoxColors.Outline, KrelzoxColors.Cyan, KrelzoxColors.Outline)) else KrelzoxColors.IonHorizontal),
        )
    }
}

/** Grid of app tiles sharing the launcher's column grid. */
@Composable
fun AppGrid(
    apps: List<CatalogApp>,
    images: ImageLoader,
    installed: Set<String>,
    tasks: Map<String, Task>,
    onOpen: (CatalogApp) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(KrelzoxGrid.APP_COLUMNS - 1),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(KrelzoxGrid.FocusInset),
        horizontalArrangement = Arrangement.spacedBy(KrelzoxGrid.Gap),
        verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Large),
    ) {
        if (header != null) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
        }
        items(apps, key = { it.packageName }) { app ->
            AppTile(
                app = app,
                images = images,
                installed = app.packageName in installed,
                task = tasks[app.packageName],
                onClick = { onOpen(app) },
            )
        }
    }
}

/** Page title with an optional muted subtitle. */
@Suppress("ModifierParameter") // subtitle is passed positionally everywhere
@Composable
fun PageHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = KrelzoxGrid.FocusInset, bottom = KrelzoxSpacing.Medium)) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = KrelzoxColors.OnBackground)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = KrelzoxColors.OnSurfaceMuted)
        }
    }
}

/** Centered message for empty or failed states, with an optional action. */
@Composable
fun EmptyState(icon: ImageVector, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = KrelzoxColors.Cyan, modifier = Modifier.size(48.dp))
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = KrelzoxColors.OnSurfaceMuted,
            modifier = Modifier.padding(top = KrelzoxSpacing.Medium).width(520.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) {
            StoreButton(action, onClick = onAction, modifier = Modifier.padding(top = KrelzoxSpacing.Large))
        }
    }
}

@Composable
fun StoreButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = KrelzoxButtonDefaults.shape(),
        colors = if (primary) {
            androidx.tv.material3.ButtonDefaults.colors(
                containerColor = KrelzoxColors.Electric,
                contentColor = KrelzoxColors.OnBackground,
                focusedContainerColor = KrelzoxColors.Electric,
                focusedContentColor = KrelzoxColors.OnBackground,
            )
        } else {
            KrelzoxButtonDefaults.colors()
        },
        border = KrelzoxButtonDefaults.border(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Box(Modifier.width(KrelzoxSpacing.Small))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun formatSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/** Thin divider line in the outline color. */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(KrelzoxColors.Outline))
}

/** Small label + value pair used in the facts row of the app page. */
@Composable
fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = KrelzoxColors.OnSurfaceSubtle)
        Text(value, style = MaterialTheme.typography.titleMedium, color = KrelzoxColors.OnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Remote handling for text fields on TV: OK opens the on-screen keyboard (focus alone does
 * not), and ↑/↓ leave the field instead of being swallowed by it.
 */
internal fun tvTextFieldKeys(
    event: androidx.compose.ui.input.key.KeyEvent,
    keyboard: androidx.compose.ui.platform.SoftwareKeyboardController?,
    focusManager: androidx.compose.ui.focus.FocusManager,
): Boolean {
    val type = event.type
    return when (event.key) {
        Key.DirectionCenter, Key.Enter -> {
            if (type == KeyEventType.KeyUp) keyboard?.show()
            false
        }
        Key.DirectionDown -> {
            if (type == KeyEventType.KeyDown) focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
            true
        }
        Key.DirectionUp -> {
            if (type == KeyEventType.KeyDown) focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Up)
            true
        }
        else -> false
    }
}
