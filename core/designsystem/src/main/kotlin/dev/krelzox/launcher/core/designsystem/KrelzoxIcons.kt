package dev.krelzox.launcher.core.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Krelzox line-icon family: 24×24 grid, 1.8 stroke, round caps and joins.
 * Drawn in-house to keep the iconography consistent and avoid the large
 * material-icons-extended dependency. Tint with the `tint` of Icon().
 */
object KrelzoxIcons {

    val Home: ImageVector by lazy {
        lineIcon("Home") {
            moveTo(3.5f, 11f); lineTo(12f, 4f); lineTo(20.5f, 11f)
            moveTo(6f, 9.5f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 9.5f)
            moveTo(10f, 20f); lineTo(10f, 14.5f); lineTo(14f, 14.5f); lineTo(14f, 20f)
        }
    }

    val Library: ImageVector by lazy {
        lineIcon("Library") {
            rect(4f, 4f, 7f, 7f); rect(13f, 4f, 7f, 7f)
            rect(4f, 13f, 7f, 7f); rect(13f, 13f, 7f, 7f)
        }
    }

    val Media: ImageVector by lazy {
        lineIcon("Media") {
            rect(3f, 5.5f, 18f, 13f)
            moveTo(10f, 9f); lineTo(15f, 12f); lineTo(10f, 15f); close()
        }
    }

    val Settings: ImageVector by lazy {
        lineIcon("Settings") {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 17f); lineTo(20f, 17f)
            circle(9f, 7f, 2f); circle(15f, 12f, 2f); circle(8f, 17f, 2f)
        }
    }

    val Wifi: ImageVector by lazy {
        lineIcon("Wifi") {
            moveTo(2.5f, 9f); arcTo(14f, 14f, 0f, false, true, 21.5f, 9f)
            moveTo(5.5f, 12.5f); arcTo(9.5f, 9.5f, 0f, false, true, 18.5f, 12.5f)
            moveTo(8.5f, 16f); arcTo(5f, 5f, 0f, false, true, 15.5f, 16f)
            moveTo(12f, 19.5f); lineTo(12.01f, 19.5f)
        }
    }

    val Ethernet: ImageVector by lazy {
        lineIcon("Ethernet") {
            moveTo(5f, 5f); lineTo(19f, 5f); lineTo(19f, 15f); lineTo(15.5f, 15f)
            lineTo(15.5f, 19f); lineTo(8.5f, 19f); lineTo(8.5f, 15f); lineTo(5f, 15f); close()
            moveTo(9.5f, 8.5f); lineTo(9.5f, 11f)
            moveTo(12f, 8.5f); lineTo(12f, 11f)
            moveTo(14.5f, 8.5f); lineTo(14.5f, 11f)
        }
    }

    val Offline: ImageVector by lazy {
        lineIcon("Offline") {
            moveTo(5.5f, 12.5f); arcTo(9.5f, 9.5f, 0f, false, true, 18.5f, 12.5f)
            moveTo(8.5f, 16f); arcTo(5f, 5f, 0f, false, true, 15.5f, 16f)
            moveTo(12f, 19.5f); lineTo(12.01f, 19.5f)
            moveTo(3.5f, 3.5f); lineTo(20.5f, 20.5f)
        }
    }

    val Play: ImageVector by lazy {
        lineIcon("Play") {
            moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
        }
    }

    val Star: ImageVector by lazy {
        lineIcon("Star") {
            moveTo(12f, 3.5f); lineTo(14.6f, 9f); lineTo(20.5f, 9.6f); lineTo(16f, 13.6f)
            lineTo(17.3f, 19.5f); lineTo(12f, 16.5f); lineTo(6.7f, 19.5f); lineTo(8f, 13.6f)
            lineTo(3.5f, 9.6f); lineTo(9.4f, 9f); close()
        }
    }

    /** Filled star (same outline as [Star]) for "is favorite" states. */
    val StarFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "StarFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3.5f); lineTo(14.6f, 9f); lineTo(20.5f, 9.6f); lineTo(16f, 13.6f)
                lineTo(17.3f, 19.5f); lineTo(12f, 16.5f); lineTo(6.7f, 19.5f); lineTo(8f, 13.6f)
                lineTo(3.5f, 9.6f); lineTo(9.4f, 9f); close()
            }
        }.build()
    }

    val Plus: ImageVector by lazy {
        lineIcon("Plus") { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
    }

    /** Horizontal reorder (↔). */
    val Reorder: ImageVector by lazy {
        lineIcon("Reorder") {
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(8f, 8f); lineTo(4f, 12f); lineTo(8f, 16f)
            moveTo(16f, 8f); lineTo(20f, 12f); lineTo(16f, 16f)
        }
    }

    /** Processor: a chip with pins. */
    val Cpu: ImageVector by lazy {
        lineIcon("Cpu") {
            rect(7f, 7f, 10f, 10f)
            rect(10f, 10f, 4f, 4f)
            moveTo(10f, 4f); lineTo(10f, 7f); moveTo(14f, 4f); lineTo(14f, 7f)
            moveTo(10f, 17f); lineTo(10f, 20f); moveTo(14f, 17f); lineTo(14f, 20f)
            moveTo(4f, 10f); lineTo(7f, 10f); moveTo(4f, 14f); lineTo(7f, 14f)
            moveTo(17f, 10f); lineTo(20f, 10f); moveTo(17f, 14f); lineTo(20f, 14f)
        }
    }

    /** Memory: a RAM module. */
    val Memory: ImageVector by lazy {
        lineIcon("Memory") {
            rect(3f, 7f, 18f, 9f)
            rect(6f, 10f, 3f, 3f); rect(10.5f, 10f, 3f, 3f); rect(15f, 10f, 3f, 3f)
            moveTo(6f, 16f); lineTo(6f, 19f); moveTo(10f, 16f); lineTo(10f, 19f)
            moveTo(14f, 16f); lineTo(14f, 19f); moveTo(18f, 16f); lineTo(18f, 19f)
        }
    }

    /** Storage: a drive. */
    val Storage: ImageVector by lazy {
        lineIcon("Storage") {
            rect(3f, 13f, 18f, 6f)
            moveTo(3f, 13f); lineTo(6f, 5f); lineTo(18f, 5f); lineTo(21f, 13f)
            moveTo(16.5f, 16f); lineTo(16.51f, 16f)
        }
    }

    val Thermometer: ImageVector by lazy {
        lineIcon("Thermometer") {
            moveTo(10f, 14.5f); lineTo(10f, 5f)
            arcTo(2f, 2f, 0f, false, true, 14f, 5f)
            lineTo(14f, 14.5f)
            arcTo(4f, 4f, 0f, true, true, 10f, 14.5f)
            close()
            moveTo(12f, 10f); lineTo(12f, 17f)
        }
    }

    /** Performance: a pulse line. */
    val Pulse: ImageVector by lazy {
        lineIcon("Pulse") {
            moveTo(3f, 12f); lineTo(7f, 12f); lineTo(10f, 5f); lineTo(14f, 19f); lineTo(17f, 12f); lineTo(21f, 12f)
        }
    }

    val Clock: ImageVector by lazy {
        lineIcon("Clock") {
            circle(12f, 12f, 9f)
            moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
        }
    }

    val Info: ImageVector by lazy {
        lineIcon("Info") {
            circle(12f, 12f, 9f)
            moveTo(12f, 11f); lineTo(12f, 16.5f)
            moveTo(12f, 7.8f); lineTo(12.01f, 7.8f)
        }
    }

    val ArrowLeft: ImageVector by lazy {
        lineIcon("ArrowLeft") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
    }

    val ArrowRight: ImageVector by lazy {
        lineIcon("ArrowRight") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
    }

    /** Brand "K" mark, filled with the active theme's accent gradient. Draw without tint. */
    val LogoMark: ImageVector get() = logoMarkFor(KrelzoxColors.accent)

    /** The brand mark in a given theme (theme previews). Built once per theme. */
    fun logoMarkFor(accent: KrelzoxAccent): ImageVector = logoMarks.getOrPut(accent) { logoMark(accent) }

    private val logoMarks = HashMap<KrelzoxAccent, ImageVector>()

    private fun logoMark(accent: KrelzoxAccent): ImageVector =
        ImageVector.Builder(
            name = "KrelzoxMark",
            defaultWidth = 20.dp,
            defaultHeight = 22.dp,
            viewportWidth = 40f,
            viewportHeight = 44f,
        ).apply {
            path(
                fill = Brush.linearGradient(
                    colors = listOf(accent.primary, accent.secondary),
                    start = Offset(0f, 0f),
                    end = Offset(40f, 44f),
                ),
            ) {
                moveTo(0f, 0f); horizontalLineTo(9f); verticalLineTo(18f); lineTo(28f, 0f); horizontalLineTo(40f)
                lineTo(21f, 20f); lineTo(40f, 44f); horizontalLineTo(28.5f); lineTo(14.5f, 25.5f); lineTo(9f, 31f)
                verticalLineTo(44f); horizontalLineTo(0f); close()
            }
        }.build()

    /** Appearance: a palette with three color wells. */
    val Palette: ImageVector by lazy {
        lineIcon("Palette") {
            moveTo(12f, 3f)
            arcTo(9f, 9f, 0f, false, false, 12f, 21f)
            arcTo(2f, 2f, 0f, false, false, 13.4f, 17.6f)
            arcTo(2f, 2f, 0f, false, true, 15f, 14.5f)
            lineTo(17f, 14.5f)
            arcTo(4f, 4f, 0f, false, false, 21f, 10.5f)
            arcTo(8f, 7.5f, 0f, false, false, 12f, 3f)
            close()
            circle(7.5f, 11f, 1.1f)
            circle(10f, 7f, 1.1f)
            circle(14.5f, 7f, 1.1f)
        }
    }

    /** Raspberry Pi: a board with a chip and pin headers. */
    val Board: ImageVector by lazy {
        lineIcon("Board") {
            rect(3f, 5f, 18f, 14f)
            rect(9f, 9f, 6f, 6f)
            moveTo(6f, 8f); lineTo(6f, 8.01f)
            moveTo(6f, 12f); lineTo(6f, 12.01f)
            moveTo(6f, 16f); lineTo(6f, 16.01f)
            moveTo(18f, 8f); lineTo(18f, 8.01f)
            moveTo(18f, 16f); lineTo(18f, 16.01f)
        }
    }

    /** Screensaver: a crescent moon. */
    val Moon: ImageVector by lazy {
        lineIcon("Moon") {
            moveTo(20f, 14.5f)
            arcTo(8.5f, 8.5f, 0f, true, true, 9.5f, 4f)
            arcTo(7f, 7f, 0f, false, false, 20f, 14.5f)
            close()
        }
    }

    /** Selected option: a check mark. */
    val Check: ImageVector by lazy {
        lineIcon("Check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
    }

    private fun lineIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun PathBuilder.rect(x: Float, y: Float, w: Float, h: Float) {
        moveTo(x, y); lineTo(x + w, y); lineTo(x + w, y + h); lineTo(x, y + h); close()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, false, true, cx - r, cy)
        arcTo(r, r, 0f, false, true, cx + r, cy)
        close()
    }
}
