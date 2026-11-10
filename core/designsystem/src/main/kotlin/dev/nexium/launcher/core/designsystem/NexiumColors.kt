package dev.nexium.launcher.core.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** "Graphite & Ion" color tokens. See docs/identidad-visual.md. */
object NexiumColors {
    // Graphite foundation
    val Background = Color(0xFF0B0D12)
    val BackgroundDeep = Color(0xFF07080B)
    val Surface = Color(0xFF141821)
    val SurfaceRaised = Color(0xFF1C212C)
    val Outline = Color(0xFF2A303C)

    // Text
    val OnBackground = Color(0xFFF3F5F9)
    val OnSurfaceMuted = Color(0xFF9AA3B2)
    val OnSurfaceSubtle = Color(0xFF6B7385)

    // Identity: electric blue → cyan
    val Electric = Color(0xFF2F6BFF)
    val Cyan = Color(0xFF22D3EE)
    val Error = Color(0xFFFF6B7A)

    /** Ion accent: reserved for focus, selection and brand marks. */
    val IonBrush: Brush = Brush.linearGradient(listOf(Electric, Cyan))
    val IonHorizontal: Brush = Brush.horizontalGradient(listOf(Electric, Cyan))
}
