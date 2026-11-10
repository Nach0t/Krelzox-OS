package dev.nexium.launcher.core.designsystem

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

/** Shape and size tokens. */
object NexiumDimens {
    val CardCorner = 16.dp
    val ButtonCorner = 12.dp
    val PillCorner = 50.dp
    val FocusBorder = 2.dp
    val FocusGlow = 14.dp
    val AppIcon = 56.dp
    val AppIconLarge = 72.dp

    /** Large favorite cards (16:9). */
    val FavoriteCardWidth = 212.dp

    /** Catalog / row cards (16:9). */
    val AppCardWidth = 180.dp

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
}
