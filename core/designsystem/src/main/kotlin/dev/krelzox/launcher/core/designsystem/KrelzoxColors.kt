package dev.krelzox.launcher.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The light that marks focus, selection and the brand on the graphite base. Each theme
 * is a pair: [primary] (filled surfaces with white text, WCAG AA ≥ 4.5:1 except the
 * original Ion) and [secondary] (icons, overlines and lines on graphite, ≥ 10:1).
 * Everything else in "Graphite & Ion" stays the same across themes.
 */
enum class KrelzoxAccent(val key: String, val primary: Color, val secondary: Color) {
    /** Original identity: electric blue → cyan. */
    Ion("ion", Color(0xFF2F6BFF), Color(0xFF22D3EE)),
    Aurora("aurora", Color(0xFF7C3AED), Color(0xFFC4B5FD)),
    Amber("amber", Color(0xFFB45309), Color(0xFFFBBF24)),
    Jade("jade", Color(0xFF047857), Color(0xFF34D399)),
    ;

    /** Built once per theme: focus borders and bars read them on every frame. */
    val brush: Brush = Brush.linearGradient(listOf(primary, secondary))
    val horizontal: Brush = Brush.horizontalGradient(listOf(primary, secondary))

    companion object {
        val Default = Ion
        fun fromKey(key: String?): KrelzoxAccent = entries.firstOrNull { it.key == key } ?: Default
    }
}

/** "Graphite & Ion" color tokens. See docs/identidad-visual.md. */
object KrelzoxColors {
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

    val Error = Color(0xFFFF6B7A)

    /**
     * Active theme. Snapshot state: every composable or draw block that reads an accent
     * token below is invalidated when it changes, so a new theme applies everywhere at
     * once without restarting. Set it only through the app's appearance store.
     */
    var accent: KrelzoxAccent by mutableStateOf(KrelzoxAccent.Default)

    // Identity accent (names kept from the original Ion palette: primary / secondary).
    val Electric: Color get() = accent.primary
    val Cyan: Color get() = accent.secondary

    /** Accent gradient: reserved for focus, selection and brand marks. */
    val IonBrush: Brush get() = accent.brush
    val IonHorizontal: Brush get() = accent.horizontal
}
