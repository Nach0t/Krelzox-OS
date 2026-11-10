package dev.nexium.launcher.core.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** "Obsidian & Ion" color tokens. See docs/identidad-visual.md. */
object NexiumColors {
    val Background = Color(0xFF07080C)
    val Surface = Color(0xFF10131A)
    val SurfaceRaised = Color(0xFF171B24)
    val OnBackground = Color(0xFFF2F4F8)
    val OnSurfaceMuted = Color(0xFF9AA3B2)
    val IonStart = Color(0xFF5B8CFF)
    val IonEnd = Color(0xFF8A6BFF)
    val Champagne = Color(0xFFE8C48A)
    val Error = Color(0xFFFF6B7A)

    /** Ion accent: reserved for focus and selection only. */
    val IonBrush: Brush = Brush.linearGradient(listOf(IonStart, IonEnd))
}
