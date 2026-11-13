package dev.krelzox.launcher.ui.screensaver

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Where the clock rests, and where its glow is centered (fractions of the screen). */
private class Spot(val alignment: Alignment, val glowX: Float, val glowY: Float, val end: Boolean)

/** Each move goes to a distant spot, so no pixel keeps the same content for long. */
private val Spots = listOf(
    Spot(Alignment.TopStart, 0.24f, 0.30f, end = false),
    Spot(Alignment.BottomEnd, 0.76f, 0.70f, end = true),
    Spot(Alignment.TopEnd, 0.76f, 0.30f, end = true),
    Spot(Alignment.BottomStart, 0.24f, 0.70f, end = false),
    Spot(Alignment.CenterEnd, 0.76f, 0.50f, end = true),
    Spot(Alignment.CenterStart, 0.24f, 0.50f, end = false),
)

/**
 * Krelzox screensaver: time, date and brand on deep graphite, lit by the active theme.
 *
 * Burn-in protection: every [shiftIntervalMs] the whole block fades out and reappears in
 * another area of the TV safe zone. Cheap on the V3D GPU: it only redraws when the
 * minute changes or during a move; the fade is a layer alpha (no recomposition).
 *
 * Every size is relative to the available width, so the same composable is the
 * full-screen screensaver and the miniature in Ajustes › Apariencia.
 */
@Composable
fun KrelzoxScreensaver(modifier: Modifier = Modifier, shiftIntervalMs: Long = 60_000L, animate: Boolean = true) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            val ms = System.currentTimeMillis()
            delay(60_000L - ms % 60_000L + 50L)
        }
    }
    val is24h = DateFormat.is24HourFormat(context)
    val time = now.format(DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm", locale))
    val dayPeriod = if (is24h) null else now.format(DateTimeFormatter.ofPattern("a", locale))
    val date = now.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale))
        .replaceFirstChar { it.titlecase(locale) }

    var spotIndex by remember { mutableIntStateOf(0) }
    val fade = remember { Animatable(if (animate) 0f else 1f) }
    if (animate) {
        LaunchedEffect(shiftIntervalMs) {
            fade.animateTo(1f, tween(1_200))
            while (true) {
                delay(shiftIntervalMs)
                fade.animateTo(0f, tween(900))
                spotIndex = (spotIndex + 1) % Spots.size
                fade.animateTo(1f, tween(1_200))
            }
        }
    }
    val spot = Spots[spotIndex]
    val accent = KrelzoxColors.accent

    BoxWithConstraints(modifier.background(KrelzoxColors.BackgroundDeep)) {
        val w = maxWidth
        val unit = w.value / 960f // 1 at full TV width (960 dp)

        // Theme glow behind the clock, plus a faint counter-light: depth without blur.
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) {
            val center = Offset(size.width * spot.glowX, size.height * spot.glowY)
            val r = size.width * 0.42f
            drawCircle(
                Brush.radialGradient(listOf(accent.primary.copy(alpha = 0.20f), Color.Transparent), center, r),
                radius = r,
                center = center,
            )
            val counter = Offset(size.width * (1f - spot.glowX), size.height * (1f - spot.glowY))
            val r2 = size.width * 0.30f
            drawCircle(
                Brush.radialGradient(listOf(accent.secondary.copy(alpha = 0.05f), Color.Transparent), counter, r2),
                radius = r2,
                center = counter,
            )
        }

        Column(
            Modifier
                .align(spot.alignment)
                .padding(horizontal = w * 0.075f, vertical = w * 0.055f)
                .graphicsLayer { alpha = fade.value },
            horizontalAlignment = if (spot.end) Alignment.End else Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    time,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = (104f * unit).sp,
                        lineHeight = (104f * unit).sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.035).em,
                    ),
                    color = KrelzoxColors.OnBackground,
                )
                if (dayPeriod != null) {
                    Text(
                        dayPeriod,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = (24f * unit).sp),
                        color = KrelzoxColors.OnSurfaceMuted,
                        modifier = Modifier.padding(start = 10.dp * unit, bottom = 14.dp * unit),
                    )
                }
            }
            Text(
                date,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = (24f * unit).sp, fontWeight = FontWeight.Normal),
                color = KrelzoxColors.OnSurfaceMuted,
            )
            Spacer(Modifier.height(22.dp * unit))
            Spacer(
                Modifier
                    .width(56.dp * unit)
                    .height(max(1.dp, 3.dp * unit))
                    .background(accent.horizontal, CircleShape),
            )
            Spacer(Modifier.height(18.dp * unit))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp * unit)) {
                Image(
                    rememberVectorPainter(KrelzoxIcons.LogoMark),
                    contentDescription = null,
                    modifier = Modifier.size(13.dp * unit, 14.dp * unit),
                )
                Text(
                    "RELZOX",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = (11f * unit).sp, letterSpacing = 0.3.em),
                    color = KrelzoxColors.OnSurfaceSubtle,
                )
            }
        }
    }
}

/**
 * Full-screen preview inside Krelzox, identical to the system screensaver but moving every
 * few seconds so the burn-in shift can be seen. Any key closes it, like the real one.
 */
@Composable
fun ScreensaverPreview(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val focus = remember { FocusRequester() }
        KrelzoxScreensaver(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                // Consume every key; close on release so the key never reaches the screen below.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    true
                }
                .focusable(),
            shiftIntervalMs = 6_000L,
        )
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }
}
