package dev.krelzox.launcher.ui.home

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.krelzox.launcher.R
import dev.krelzox.launcher.core.data.AppEntry
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxGrid
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxMotion
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.launcher.ui.AppArtLoader
import kotlinx.coroutines.delay

private const val HOME_ROW_LIMIT = 12
private const val ROW_SHIFT_DURATION_MS = 260

/** Callbacks shared by every tab that shows app cards. */
data class AppActions(
    val onLaunch: (AppEntry) -> Unit,
    val onShowActions: (AppEntry) -> Unit,
    val onToggleFavorite: (AppEntry) -> Unit,
    /** Actions opened from the favorites row (offers live reorder). */
    val onShowFavoriteActions: (AppEntry) -> Unit = onShowActions,
    val onAddFavorites: () -> Unit = {},
    /** Favorite currently in reorder mode, if any. */
    val movingKey: String? = null,
    val onMoveFavorite: (AppEntry, Int) -> Unit = { _, _ -> },
    val onMoveDone: () -> Unit = {},
)

/** Time of the last key press, read by the hero carousel (plain field: never recomposes). */
class InteractionClock {
    @Volatile
    var lastKeyAt: Long = 0L

    fun touch() {
        lastKeyAt = SystemClock.uptimeMillis()
    }
}

@Composable
fun HomeTab(
    state: HomeUiState.Ready,
    artLoader: AppArtLoader,
    actions: AppActions,
    heroOpenRequester: FocusRequester,
    focusedCardKey: String?,
    onCardFocused: (String) -> Unit,
    heroKey: String?,
    onHeroKeyChange: (String?) -> Unit,
    heroIndex: Int,
    onHeroIndexChange: (Int) -> Unit,
    interaction: InteractionClock,
    onHeroButtonsFocusChanged: (Boolean) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val byKey = remember(state.apps) { state.apps.associateBy { it.key } }
    var rowsHaveFocus by remember { mutableStateOf(false) }

    // The hero follows the focused card after a short delay, so fast scrolling
    // does not thrash the cross-fade or art loading.
    LaunchedEffect(focusedCardKey) {
        if (focusedCardKey != null && focusedCardKey != heroKey) {
            delay(KrelzoxMotion.HERO_FOLLOW_DELAY_MS)
            onHeroKeyChange(focusedCardKey)
        }
    }

    // Carousel: while Krelzox is in front, no row is focused and the remote is idle,
    // the hero advances through the relevant apps. Any key press pauses it.
    val candidates = state.heroCandidates
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Read the latest values inside the long-running loop (not the ones captured at launch).
    val currentIndex by rememberUpdatedState(heroIndex)
    val currentHeroKey by rememberUpdatedState(heroKey)
    LaunchedEffect(candidates, rowsHaveFocus) {
        if (rowsHaveFocus || candidates.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastAdvance = SystemClock.uptimeMillis()
            while (true) {
                delay(1_000)
                val now = SystemClock.uptimeMillis()
                val idle = now - interaction.lastKeyAt >= KrelzoxMotion.HERO_ROTATE_MS
                if (idle && now - lastAdvance >= KrelzoxMotion.HERO_ROTATE_MS && (candidates.size > 1 || currentHeroKey != null)) {
                    // Leaving a followed card resumes the carousel where it was; otherwise advance.
                    if (currentHeroKey != null) {
                        onHeroKeyChange(null)
                    } else {
                        onHeroIndexChange((currentIndex + 1) % candidates.size)
                    }
                    lastAdvance = now
                }
            }
        }
    }

    if (candidates.isEmpty()) return
    val followed = heroKey?.let(byKey::get)
    val carouselIndex = heroIndex.coerceIn(0, candidates.lastIndex)
    val heroContent = if (followed != null) {
        HeroContent(followed, if (followed.key in state.favoriteKeys) HeroOverline.Favorite else HeroOverline.Category)
    } else {
        val featured = candidates[carouselIndex]
        HeroContent(
            entry = featured.entry,
            overline = when (featured.reason) {
                FeaturedReason.Continue -> HeroOverline.Continue
                FeaturedReason.Recent -> HeroOverline.Recent
                FeaturedReason.Favorite -> HeroOverline.Favorite
                FeaturedReason.Suggested -> HeroOverline.Featured
            },
        )
    }

    // Row-level "pivot" scrolling: the focused row is aligned to the top of the area.
    // Rows live in a plain Column (nothing scrolls by itself) shifted by an animated
    // translation read in the draw phase, so the shift never recomposes or relayouts.
    var focusedRow by remember { mutableIntStateOf(-1) }
    val rowTops = remember { mutableStateMapOf<Int, Float>() }
    val targetShift = if (focusedRow <= 0) 0f else (rowTops[focusedRow] ?: 0f) - (rowTops[0] ?: 0f)
    val shift by animateFloatAsState(targetShift, tween(ROW_SHIFT_DURATION_MS), label = "rowShift")

    Column(Modifier.fillMaxSize()) {
        Hero(
            content = heroContent,
            isFavorite = heroContent.entry.key in state.favoriteKeys,
            page = if (followed == null) HeroPage(carouselIndex, candidates.size) else null,
            artLoader = artLoader,
            openRequester = heroOpenRequester,
            onOpen = actions.onLaunch,
            onToggleFavorite = actions.onToggleFavorite,
            onButtonsFocusChanged = { focused ->
                if (focused) focusedRow = -1
                onHeroButtonsFocusChanged(focused)
            },
        )
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .clipVerticalOnly(),
        ) {
            val favoriteWidth = KrelzoxGrid.columnWidth(maxWidth, KrelzoxGrid.FAVORITE_COLUMNS)
            val appWidth = KrelzoxGrid.columnWidth(maxWidth, KrelzoxGrid.APP_COLUMNS)
            val contentWidth = maxWidth
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .graphicsLayer { translationY = -shift }
                    .onFocusChanged { rowsHaveFocus = it.hasFocus }
                    .padding(top = KrelzoxSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.XSmall),
            ) {
                RowShell(
                    index = 0,
                    title = stringResource(R.string.section_favorites),
                    hint = stringResource(
                        when {
                            actions.movingKey != null -> R.string.move_hint
                            state.favorites.isEmpty() -> R.string.favorites_empty_hint
                            else -> R.string.home_hint
                        },
                    ),
                    contentWidth = contentWidth,
                    rowTops = rowTops,
                    onFocused = { focusedRow = 0 },
                ) {
                    items(state.favorites, key = { "fav/${it.key}" }, contentType = { "card" }) { entry ->
                        AppCard(
                            entry = entry,
                            isFavorite = true,
                            artLoader = artLoader,
                            onClick = { actions.onLaunch(entry) },
                            onShowActions = { actions.onShowFavoriteActions(entry) },
                            moveControls = if (entry.key == actions.movingKey) {
                                MoveControls(onMove = { actions.onMoveFavorite(entry, it) }, onDone = actions.onMoveDone)
                            } else {
                                null
                            },
                            modifier = Modifier
                                .width(favoriteWidth)
                                .onFocusChanged { if (it.hasFocus) onCardFocused(entry.key) },
                        )
                    }
                    item(key = "fav/add", contentType = "action") {
                        ActionCard(
                            icon = KrelzoxIcons.Plus,
                            label = stringResource(R.string.favorites_add),
                            width = favoriteWidth,
                            onClick = actions.onAddFavorites,
                        )
                    }
                }
                RowShell(
                    index = 1,
                    title = stringResource(R.string.section_apps),
                    hint = null,
                    contentWidth = contentWidth,
                    rowTops = rowTops,
                    onFocused = { focusedRow = 1 },
                ) {
                    items(state.apps.take(HOME_ROW_LIMIT), key = { "app/${it.key}" }, contentType = { "card" }) { entry ->
                        AppCard(
                            entry = entry,
                            isFavorite = entry.key in state.favoriteKeys,
                            artLoader = artLoader,
                            onClick = { actions.onLaunch(entry) },
                            onShowActions = { actions.onShowActions(entry) },
                            modifier = Modifier
                                .width(appWidth)
                                .onFocusChanged { if (it.hasFocus) onCardFocused(entry.key) },
                        )
                    }
                    item(key = "app/see-all", contentType = "action") {
                        ActionCard(
                            icon = KrelzoxIcons.Library,
                            label = stringResource(R.string.see_library, state.apps.size),
                            width = appWidth,
                            onClick = onOpenLibrary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clips content that scrolls under the hero (vertical) but leaves horizontal room, so
 * the focus scale and glow of edge cards are never cut at the content edges.
 */
private fun Modifier.clipVerticalOnly(): Modifier = drawWithContent {
    val room = KrelzoxGrid.FocusInset.toPx() * 2
    clipRect(left = -room, top = 0f, right = size.width + room, bottom = size.height) {
        this@drawWithContent.drawContent()
    }
}

/**
 * A titled horizontal row. The list is widened by [KrelzoxGrid.FocusInset] on both sides
 * (room for focus scale/glow) so card edges still align with the title and the hero.
 */
@Composable
private fun RowShell(
    index: Int,
    title: String,
    hint: String?,
    contentWidth: Dp,
    rowTops: MutableMap<Int, Float>,
    onFocused: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val top = coords.positionInParent().y
                if (rowTops[index] != top) rowTops[index] = top
            }
            .onFocusChanged { if (it.hasFocus) onFocused() },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = KrelzoxSpacing.Small),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = KrelzoxColors.OnBackground)
            if (hint != null) {
                Spacer(Modifier.weight(1f))
                Text(hint, style = MaterialTheme.typography.labelMedium, color = KrelzoxColors.OnSurfaceSubtle)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = KrelzoxGrid.FocusInset, vertical = KrelzoxSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(KrelzoxGrid.Gap),
            // Re-entering the row restores the last focused card instead of the nearest one.
            modifier = Modifier
                .requiredWidth(contentWidth + KrelzoxGrid.FocusInset * 2)
                .focusRestorer(),
            content = content,
        )
    }
}

/** Utility tile in a row ("+ Add", "See all"): same size and focus treatment as an app card. */
@Composable
internal fun ActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, width: Dp, onClick: () -> Unit) {
    Column(Modifier.width(width)) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
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
                    .iconPlate(null),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = KrelzoxColors.Cyan, modifier = Modifier.size(28.dp))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = KrelzoxColors.OnSurfaceMuted,
            maxLines = 1,
            modifier = Modifier.padding(top = KrelzoxSpacing.Small + 2.dp, start = 2.dp),
        )
    }
}
