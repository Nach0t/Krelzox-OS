package dev.nexium.launcher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.nexium.launcher.R
import dev.nexium.launcher.core.data.AppEntry
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumDimens
import dev.nexium.launcher.core.designsystem.NexiumIcons
import dev.nexium.launcher.core.designsystem.NexiumMotion
import dev.nexium.launcher.core.designsystem.NexiumSpacing
import dev.nexium.launcher.ui.AppArtLoader
import kotlinx.coroutines.delay

private const val HOME_ROW_LIMIT = 12
private const val ROW_SHIFT_DURATION_MS = 260

/** Horizontal inset so focus scale and glow of edge cards are never clipped by the row. */
private val RowEdgeInset = 14.dp

/** Callbacks shared by every tab that shows app cards. */
data class AppActions(
    val onLaunch: (AppEntry) -> Unit,
    val onShowActions: (AppEntry) -> Unit,
    val onToggleFavorite: (AppEntry) -> Unit,
)

/** Data for one horizontal row on Inicio. */
private data class HomeRow(
    val key: String,
    val title: String,
    val entries: List<AppEntry>,
    val cardWidth: Dp,
    val iconSize: Dp,
    val hint: String?,
    val showSeeAll: Boolean,
)

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
    onHeroButtonsFocusChanged: (Boolean) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val byKey = remember(state.apps) { state.apps.associateBy { it.key } }

    // The hero follows the focused card after a short delay, so fast scrolling
    // does not thrash the cross-fade or art loading.
    LaunchedEffect(focusedCardKey) {
        if (focusedCardKey != null && focusedCardKey != heroKey) {
            delay(NexiumMotion.HERO_FOLLOW_DELAY_MS)
            onHeroKeyChange(focusedCardKey)
        }
    }

    val featured = state.featured ?: return
    val heroEntry = heroKey?.let(byKey::get)
    val heroContent = when {
        heroEntry == null || heroEntry.key == featured.entry.key -> HeroContent(
            entry = featured.entry,
            overline = when (featured.reason) {
                FeaturedReason.Recent -> HeroOverline.Continue
                FeaturedReason.Favorite -> HeroOverline.Favorite
                FeaturedReason.Suggested -> HeroOverline.Featured
            },
        )
        heroEntry.key in state.favoriteKeys -> HeroContent(heroEntry, HeroOverline.Favorite)
        else -> HeroContent(heroEntry, HeroOverline.Category)
    }

    val hint = stringResource(R.string.home_hint)
    val favoritesTitle = stringResource(R.string.section_favorites)
    val appsTitle = stringResource(R.string.section_apps)
    val rows = remember(state.favorites, state.apps, hint, favoritesTitle, appsTitle) {
        buildList {
            if (state.favorites.isNotEmpty()) {
                add(HomeRow("fav", favoritesTitle, state.favorites, NexiumDimens.FavoriteCardWidth, NexiumDimens.AppIconLarge, hint, showSeeAll = false))
            }
            add(
                HomeRow(
                    "app", appsTitle, state.apps.take(HOME_ROW_LIMIT), NexiumDimens.AppCardWidth, NexiumDimens.AppIcon,
                    hint = if (state.favorites.isEmpty()) hint else null, showSeeAll = true,
                ),
            )
        }
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
            artLoader = artLoader,
            openRequester = heroOpenRequester,
            onOpen = actions.onLaunch,
            onToggleFavorite = actions.onToggleFavorite,
            onButtonsFocusChanged = { focused ->
                if (focused) focusedRow = -1
                onHeroButtonsFocusChanged(focused)
            },
        )
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .graphicsLayer { translationY = -shift }
                    .padding(top = NexiumSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(NexiumSpacing.XSmall),
            ) {
                rows.forEachIndexed { index, row ->
                    AppRow(
                        row = row,
                        favoriteKeys = state.favoriteKeys,
                        artLoader = artLoader,
                        actions = actions,
                        onCardFocused = onCardFocused,
                        seeAllCount = state.apps.size,
                        onOpenLibrary = onOpenLibrary,
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                val top = coords.positionInParent().y
                                if (rowTops[index] != top) rowTops[index] = top
                            }
                            .onFocusChanged { if (it.hasFocus) focusedRow = index },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    row: HomeRow,
    favoriteKeys: Set<String>,
    artLoader: AppArtLoader,
    actions: AppActions,
    onCardFocused: (String) -> Unit,
    seeAllCount: Int,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = RowEdgeInset, end = RowEdgeInset, top = NexiumSpacing.Small),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(row.title, style = MaterialTheme.typography.titleMedium, color = NexiumColors.OnBackground)
            if (row.hint != null) {
                Spacer(Modifier.weight(1f))
                Text(row.hint, style = MaterialTheme.typography.labelMedium, color = NexiumColors.OnSurfaceSubtle)
            }
        }
        LazyRow(
            // Padding leaves room for focus scale and glow so they are never clipped.
            contentPadding = PaddingValues(horizontal = RowEdgeInset, vertical = NexiumSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(NexiumSpacing.Large - 4.dp),
            // Re-entering the row restores the last focused card instead of the nearest one.
            modifier = Modifier.focusRestorer(),
        ) {
            items(row.entries, key = { "${row.key}/${it.key}" }, contentType = { "card" }) { entry ->
                AppCard(
                    entry = entry,
                    isFavorite = entry.key in favoriteKeys,
                    artLoader = artLoader,
                    onClick = { actions.onLaunch(entry) },
                    onShowActions = { actions.onShowActions(entry) },
                    iconSize = row.iconSize,
                    modifier = Modifier
                        .width(row.cardWidth)
                        .onFocusChanged { if (it.hasFocus) onCardFocused(entry.key) },
                )
            }
            if (row.showSeeAll) {
                item(key = "${row.key}/see-all", contentType = "trailing") {
                    SeeAllCard(count = seeAllCount, width = row.cardWidth, onClick = onOpenLibrary)
                }
            }
        }
    }
}

@Composable
private fun SeeAllCard(count: Int, width: Dp, onClick: () -> Unit) {
    Column(Modifier.width(width)) {
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
            border = nexiumCardBorder(),
            glow = nexiumCardGlow(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(NexiumColors.Electric.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(NexiumIcons.Library, contentDescription = null, tint = NexiumColors.Cyan, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(NexiumSpacing.Small))
                    Icon(NexiumIcons.ArrowRight, contentDescription = null, tint = NexiumColors.OnSurfaceMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(
            text = stringResource(R.string.see_library, count),
            style = MaterialTheme.typography.labelLarge,
            color = NexiumColors.OnSurfaceMuted,
            modifier = Modifier.padding(top = NexiumSpacing.Small + 2.dp, start = 2.dp),
        )
    }
}
