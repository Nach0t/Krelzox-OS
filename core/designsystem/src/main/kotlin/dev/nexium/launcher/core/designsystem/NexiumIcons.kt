package dev.nexium.launcher.core.designsystem

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
 * Nexium line-icon family: 24×24 grid, 1.8 stroke, round caps and joins.
 * Drawn in-house to keep the iconography consistent and avoid the large
 * material-icons-extended dependency. Tint with the `tint` of Icon().
 */
object NexiumIcons {

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

    /** Brand "N" mark, filled with the electric → cyan gradient. Draw without tint. */
    val LogoMark: ImageVector by lazy {
        ImageVector.Builder(
            name = "NexiumMark",
            defaultWidth = 20.dp,
            defaultHeight = 22.dp,
            viewportWidth = 40f,
            viewportHeight = 44f,
        ).apply {
            path(
                fill = Brush.linearGradient(
                    colors = listOf(NexiumColors.Electric, NexiumColors.Cyan),
                    start = Offset(0f, 0f),
                    end = Offset(40f, 44f),
                ),
            ) {
                moveTo(0f, 0f); horizontalLineTo(9f); lineTo(32f, 30f); verticalLineTo(0f)
                horizontalLineTo(40f); verticalLineTo(44f); horizontalLineTo(31f); lineTo(8f, 14f)
                verticalLineTo(44f); horizontalLineTo(0f); close()
            }
        }.build()
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
