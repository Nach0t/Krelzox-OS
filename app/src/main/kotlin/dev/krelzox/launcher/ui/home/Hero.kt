package dev.krelzox.launcher.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.krelzox.launcher.R
import dev.krelzox.launcher.core.data.AppCategory
import dev.krelzox.launcher.core.data.AppEntry
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxDimens
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxMotion
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.launcher.ui.AppArt
import dev.krelzox.launcher.ui.AppArtLoader

private val HeroShape = RoundedCornerShape(24.dp)
private val ArtWidth = 264.dp
private val ArtShape = RoundedCornerShape(KrelzoxDimens.CardCorner)
private val HeroIconSize = 72.dp

/** What the hero is showing and why (drives the overline). */
data class HeroContent(val entry: AppEntry, val overline: HeroOverline)

enum class HeroOverline { Continue, Recent, Favorite, Featured, Category }

/** Carousel position, shown as dots when the hero is rotating on its own. */
data class HeroPage(val index: Int, val count: Int)

/**
 * Cinematic feature area: graphite field tinted with the app's accent (no blur),
 * title + description, and quick actions. Text and art cross-fade; the buttons
 * stay in place so D-pad focus is never torn down by a content change.
 */
@Composable
fun Hero(
    content: HeroContent,
    isFavorite: Boolean,
    page: HeroPage?,
    artLoader: AppArtLoader,
    openRequester: FocusRequester,
    onOpen: (AppEntry) -> Unit,
    onToggleFavorite: (AppEntry) -> Unit,
    onButtonsFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = content.entry
    val art by produceState(initialValue = artLoader.peek(entry), entry.key) {
        value = artLoader.peek(entry) ?: artLoader.load(entry)
    }
    val banner by produceState(initialValue = artLoader.peekBanner(entry), entry.key) {
        value = artLoader.peekBanner(entry) ?: artLoader.loadBanner(entry)
    }
    val accent by animateColorAsState(
        targetValue = art?.accent?.let { Color(it) } ?: KrelzoxColors.Electric,
        animationSpec = tween(KrelzoxMotion.CROSSFADE_DURATION_MS),
        label = "heroAccent",
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(KrelzoxDimens.HeroHeight)
            .clip(HeroShape)
            // Draw-phase only: accent animation never recomposes the hero.
            .drawBehind {
                drawRect(KrelzoxColors.Surface)
                drawRect(
                    Brush.horizontalGradient(
                        0f to KrelzoxColors.Surface,
                        0.42f to KrelzoxColors.Surface,
                        1f to lerp(KrelzoxColors.Surface, accent, 0.30f),
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.32f), Color.Transparent),
                        center = Offset(size.width * 0.80f, size.height * 0.5f),
                        radius = size.height * 1.15f,
                    ),
                    radius = size.height * 1.15f,
                    center = Offset(size.width * 0.80f, size.height * 0.5f),
                )
            },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = KrelzoxSpacing.XLarge, vertical = KrelzoxSpacing.Large - 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Crossfade(
                    targetState = content,
                    animationSpec = tween(KrelzoxMotion.CROSSFADE_DURATION_MS),
                    label = "heroText",
                ) { shown -> HeroText(shown) }
                Spacer(Modifier.height(KrelzoxSpacing.Medium - 2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.onFocusChanged { onButtonsFocusChanged(it.hasFocus) },
                ) {
                    HeroButton(
                        text = stringResource(R.string.hero_open),
                        icon = KrelzoxIcons.Play,
                        primary = true,
                        onClick = { onOpen(entry) },
                        modifier = Modifier.focusRequester(openRequester),
                    )
                    HeroButton(
                        text = stringResource(if (isFavorite) R.string.hero_unfavorite else R.string.hero_favorite),
                        icon = if (isFavorite) KrelzoxIcons.StarFilled else KrelzoxIcons.Star,
                        primary = false,
                        onClick = { onToggleFavorite(entry) },
                    )
                }
            }
            Spacer(Modifier.width(KrelzoxSpacing.XLarge))
            Crossfade(
                targetState = HeroArtState(art, banner),
                animationSpec = tween(KrelzoxMotion.CROSSFADE_DURATION_MS),
                label = "heroArt",
            ) { shown -> HeroArt(shown) }
        }
        // Carousel position: fixed under the art, so it never shifts with button widths.
        if (page != null && page.count > 1) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = KrelzoxSpacing.XLarge, bottom = 7.dp)
                    .width(ArtWidth),
                contentAlignment = Alignment.Center,
            ) { PageDots(page) }
        }
    }
}

private data class HeroArtState(val art: AppArt?, val banner: ImageBitmap?)

@Composable
private fun HeroText(content: HeroContent) {
    Column {
        Text(
            text = overlineText(content).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = KrelzoxColors.Cyan,
        )
        Spacer(Modifier.height(KrelzoxSpacing.XSmall + 2.dp))
        Text(
            text = content.entry.label,
            style = MaterialTheme.typography.displaySmall,
            color = KrelzoxColors.OnBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(KrelzoxSpacing.XSmall))
        Text(
            text = descriptionText(content.entry),
            style = MaterialTheme.typography.bodyLarge,
            color = KrelzoxColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** TV banner when the app has one; otherwise its icon on the same plate used by cards. */
@Composable
private fun HeroArt(state: HeroArtState) {
    Box(
        Modifier
            .width(ArtWidth)
            .height(ArtWidth * 9 / 16)
            .clip(ArtShape)
            .iconPlate(state.art?.accent?.let { Color(it) }),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.banner != null -> Image(
                bitmap = state.banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            state.art != null -> Image(
                bitmap = state.art.icon,
                contentDescription = null,
                modifier = Modifier.size(HeroIconSize),
            )
        }
    }
}

@Composable
private fun PageDots(page: HeroPage) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(page.count) { i ->
            Box(
                Modifier
                    .size(width = if (i == page.index) 18.dp else 6.dp, height = 6.dp)
                    .background(
                        if (i == page.index) KrelzoxColors.Cyan else KrelzoxColors.OnSurfaceSubtle.copy(alpha = 0.6f),
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun HeroButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = ButtonDefaults.shape(RoundedCornerShape(KrelzoxDimens.ButtonCorner)),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) KrelzoxColors.Electric.copy(alpha = 0.22f) else KrelzoxColors.SurfaceRaised,
            contentColor = KrelzoxColors.OnBackground,
            focusedContainerColor = KrelzoxColors.Electric,
            focusedContentColor = KrelzoxColors.OnBackground,
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(BorderStroke(KrelzoxDimens.FocusBorder, KrelzoxColors.Cyan)),
        ),
        glow = ButtonDefaults.glow(
            focusedGlow = Glow(KrelzoxColors.Electric.copy(alpha = 0.5f), 12.dp),
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.04f),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(KrelzoxSpacing.Small))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun overlineText(content: HeroContent): String = when (content.overline) {
    HeroOverline.Continue -> stringResource(R.string.hero_overline_continue)
    HeroOverline.Recent -> stringResource(R.string.hero_overline_recent)
    HeroOverline.Favorite -> stringResource(R.string.hero_overline_favorite)
    HeroOverline.Featured -> stringResource(R.string.hero_overline_featured)
    HeroOverline.Category -> stringResource(R.string.category_app)
}

@Composable
internal fun categoryLabel(category: AppCategory): String = stringResource(
    when (category) {
        AppCategory.Video -> R.string.category_video
        AppCategory.Audio -> R.string.category_audio
        AppCategory.Image -> R.string.category_image
        AppCategory.Game -> R.string.category_game
        AppCategory.News -> R.string.category_news
        AppCategory.Maps -> R.string.category_maps
        AppCategory.Productivity -> R.string.category_productivity
        AppCategory.Social -> R.string.category_social
        AppCategory.Accessibility -> R.string.category_accessibility
        AppCategory.Other -> R.string.category_app
    },
)

@Composable
private fun descriptionText(entry: AppEntry): String = entry.description
    ?: if (entry.isSystem) {
        stringResource(R.string.hero_description_system, categoryLabel(entry.category))
    } else {
        stringResource(R.string.hero_description_installed, categoryLabel(entry.category))
    }
