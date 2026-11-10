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
    val CardCorner = 12.dp
    val FocusBorder = 2.dp
    val AppIcon = 56.dp
}

/** Motion tokens. Focus is the brand signature: subtle scale + Ion border. */
object NexiumMotion {
    const val FOCUS_SCALE = 1.08f
    const val FOCUS_DURATION_MS = 180
    const val CROSSFADE_DURATION_MS = 300
}
