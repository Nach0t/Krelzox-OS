package dev.nexium.launcher.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing tokens. TV safe area: 48 dp horizontal, 27 dp vertical. */
object NexiumSpacing {
    val SafeHorizontal = 48.dp
    val SafeVertical = 27.dp
    val XSmall = 4.dp
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val XLarge = 32.dp
}

/**
 * Column grid shared by every screen, so cards, tiles, titles and the hero line up
 * on the same edges. Card widths are derived from the available width, never fixed.
 */
object NexiumGrid {
    /** Gap between columns (and between cards in a row). */
    val Gap = 20.dp

    /**
     * Room kept around scrolling rows/grids so focus scale and glow are never clipped.
     * Lists are widened by this amount on both sides, so card edges still align with
     * the content edges.
     */
    val FocusInset = 14.dp

    const val FAVORITE_COLUMNS = 4
    const val APP_COLUMNS = 5

    /** Width of one column when [columns] share [total] with [Gap] between them. */
    fun columnWidth(total: Dp, columns: Int): Dp = (total - Gap * (columns - 1)) / columns
}

/** Shape and size tokens. */
object NexiumDimens {
    val CardCorner = 16.dp
    val ButtonCorner = 12.dp
    val PillCorner = 50.dp
    val FocusBorder = 2.dp
    val FocusGlow = 14.dp

    /** Icon size in lists and dialogs. */
    val AppIcon = 40.dp

    /** App icons on cards take this fraction of the card height: identical proportions everywhere. */
    const val CARD_ICON_FRACTION = 0.46f

    val TopBarHeight = 56.dp
    val HeroHeight = 188.dp
}

/** Motion tokens. Focus is the brand signature: subtle scale + Ion border + soft glow. */
object NexiumMotion {
    const val FOCUS_SCALE = 1.06f
    const val FOCUS_DURATION_MS = 160
    const val CROSSFADE_DURATION_MS = 280

    /** Delay before the hero follows focus, so fast D-pad scrolling does not thrash it. */
    const val HERO_FOLLOW_DELAY_MS = 140L

    /** Hero carousel interval; it also pauses this long after any key press. */
    const val HERO_ROTATE_MS = 9_000L
}
