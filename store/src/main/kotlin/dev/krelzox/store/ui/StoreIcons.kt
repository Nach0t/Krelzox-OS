package dev.krelzox.store.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Store-specific icons in the Krelzox line style (24×24 grid, 1.8 stroke, round caps).
 * Shared icons (home, grid, star, settings…) come from KrelzoxIcons.
 */
object StoreIcons {

    val Search: ImageVector by lazy {
        line("Search") {
            circle(10.5f, 10.5f, 6f)
            moveTo(15f, 15f); lineTo(20f, 20f)
        }
    }

    val Gamepad: ImageVector by lazy {
        line("Gamepad") {
            moveTo(7f, 7f); lineTo(17f, 7f)
            arcTo(5f, 5f, 0f, false, true, 21.5f, 13f)
            lineTo(21f, 16.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 16.6f, 17.6f)
            lineTo(15f, 15.5f); lineTo(9f, 15.5f); lineTo(7.4f, 17.6f)
            arcTo(2.5f, 2.5f, 0f, false, true, 3f, 16.5f)
            lineTo(2.5f, 13f)
            arcTo(5f, 5f, 0f, false, true, 7f, 7f)
            close()
            moveTo(7.5f, 10f); lineTo(7.5f, 13f)
            moveTo(6f, 11.5f); lineTo(9f, 11.5f)
            moveTo(15.5f, 10.5f); lineTo(15.51f, 10.5f)
            moveTo(17.5f, 12.5f); lineTo(17.51f, 12.5f)
        }
    }

    val Category: ImageVector by lazy {
        line("Category") {
            moveTo(4f, 4f); lineTo(11f, 4f); lineTo(20f, 13f); lineTo(13f, 20f); lineTo(4f, 11f); close()
            moveTo(8f, 8f); lineTo(8.01f, 8f)
        }
    }

    val Shelf: ImageVector by lazy {
        line("Shelf") {
            moveTo(5f, 4f); lineTo(5f, 20f)
            moveTo(9f, 4f); lineTo(9f, 20f)
            moveTo(13f, 5f); lineTo(17.5f, 19.5f)
            moveTo(3f, 20f); lineTo(21f, 20f)
        }
    }

    val Installed: ImageVector by lazy {
        line("Installed") {
            moveTo(4f, 15f); lineTo(4f, 19f); lineTo(20f, 19f); lineTo(20f, 15f)
            moveTo(8f, 10f); lineTo(11f, 13f); lineTo(16.5f, 6.5f)
        }
    }

    val Update: ImageVector by lazy {
        line("Update") {
            moveTo(12f, 4f); lineTo(12f, 15f)
            moveTo(7.5f, 10.5f); lineTo(12f, 15f); lineTo(16.5f, 10.5f)
            moveTo(4f, 19.5f); lineTo(20f, 19.5f)
        }
    }

    val Refresh: ImageVector by lazy {
        line("Refresh") {
            moveTo(19.5f, 12f); arcTo(7.5f, 7.5f, 0f, true, true, 17.3f, 6.7f)
            moveTo(19.5f, 4f); lineTo(19.5f, 8.5f); lineTo(15f, 8.5f)
        }
    }

    val Shield: ImageVector by lazy {
        line("Shield") {
            moveTo(12f, 3.5f); lineTo(19f, 6f); lineTo(19f, 11.5f)
            arcTo(9f, 9f, 0f, false, true, 12f, 20.5f)
            arcTo(9f, 9f, 0f, false, true, 5f, 11.5f)
            lineTo(5f, 6f); close()
            moveTo(8.8f, 12f); lineTo(11f, 14.2f); lineTo(15.3f, 9.8f)
        }
    }

    val Trash: ImageVector by lazy {
        line("Trash") {
            moveTo(4.5f, 7f); lineTo(19.5f, 7f)
            moveTo(9.5f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 7f)
            moveTo(6.5f, 7f); lineTo(7.5f, 20f); lineTo(16.5f, 20f); lineTo(17.5f, 7f)
        }
    }

    val Warning: ImageVector by lazy {
        line("Warning") {
            moveTo(12f, 4f); lineTo(21f, 19.5f); lineTo(3f, 19.5f); close()
            moveTo(12f, 10f); lineTo(12f, 14f)
            moveTo(12f, 17f); lineTo(12.01f, 17f)
        }
    }

    private fun line(name: String, block: PathBuilder.() -> Unit): ImageVector =
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

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, false, true, cx - r, cy)
        arcTo(r, r, 0f, false, true, cx + r, cy)
        close()
    }
}
