package dev.nexium.launcher.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import dev.nexium.launcher.core.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Visual for an app: a TV banner when the app provides one, otherwise its icon. */
sealed interface AppArt {
    val image: ImageBitmap

    /** ARGB accent derived once from the artwork; tints the hero without any blur. */
    val accent: Int

    data class Banner(override val image: ImageBitmap, override val accent: Int) : AppArt
    data class Icon(override val image: ImageBitmap, override val accent: Int) : AppArt
}

/**
 * Loads banners/icons off the main thread and keeps them in a size-bounded memory cache.
 * Bitmaps are rasterized once at card resolution so drawing stays cheap on the V3D GPU.
 */
class AppArtLoader(context: Context) {

    private val packageManager: PackageManager = context.applicationContext.packageManager

    private val cache = object : LruCache<String, AppArt>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: AppArt): Int =
            value.image.width * value.image.height * 4
    }

    /** Synchronous cache lookup, so already-loaded art renders on the first frame. */
    fun peek(entry: AppEntry): AppArt? = cache.get(entry.key)

    suspend fun load(entry: AppEntry): AppArt? {
        cache.get(entry.key)?.let { return it }
        val art = withContext(Dispatchers.IO) {
            try {
                loadUncached(entry)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            } catch (e: RuntimeException) {
                Log.w(TAG, "Failed to load art for ${entry.key}", e)
                null
            }
        }
        art?.let { cache.put(entry.key, it) }
        return art
    }

    /** Drops cached art of apps that are no longer installed. */
    fun retainOnly(keys: Set<String>) {
        cache.snapshot().keys
            .filterNot { it in keys }
            .forEach { cache.remove(it) }
    }

    private fun loadUncached(entry: AppEntry): AppArt {
        val banner = packageManager.getActivityBanner(entry.component)
            ?: packageManager.getApplicationBanner(entry.packageName)
        if (banner != null) {
            val bitmap = banner.toBitmap(BANNER_WIDTH_PX, BANNER_HEIGHT_PX)
            return AppArt.Banner(bitmap.asImageBitmap(), AccentExtractor.accentOf(bitmap))
        }
        val icon = packageManager.getActivityIcon(entry.component)
        val bitmap = icon.toBitmap(ICON_PX, ICON_PX)
        return AppArt.Icon(bitmap.asImageBitmap(), AccentExtractor.accentOf(bitmap))
    }

    private companion object {
        const val TAG = "NexiumArt"
        const val BANNER_WIDTH_PX = 512
        const val BANNER_HEIGHT_PX = 288
        const val ICON_PX = 192
        const val CACHE_BYTES = 32 * 1024 * 1024
    }
}

/** Cheap dominant-color estimate: 8×8 downscale, saturation-weighted average. */
internal object AccentExtractor {

    /** Brand electric blue, used for neutral (grey/white) artwork. */
    const val FALLBACK: Int = 0xFF2F6BFF.toInt()

    fun accentOf(bitmap: Bitmap): Int {
        val small = bitmap.scale(SAMPLE, SAMPLE, filter = true)
        val pixels = IntArray(SAMPLE * SAMPLE)
        small.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
        if (small !== bitmap) small.recycle()
        return accentOf(pixels)
    }

    /** Pure part, unit tested. */
    fun accentOf(pixels: IntArray): Int {
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var weight = 0.0
        val hsv = FloatArray(3)
        for (c in pixels) {
            val alpha = (c ushr 24) and 0xFF
            if (alpha < 128) continue
            val cr = (c shr 16) and 0xFF
            val cg = (c shr 8) and 0xFF
            val cb = c and 0xFF
            rgbToHsv(cr, cg, cb, hsv)
            val w = 1.0 + hsv[1] * 4.0 // favor saturated pixels over white/grey backgrounds
            r += cr * w; g += cg * w; b += cb * w; weight += w
        }
        if (weight == 0.0) return FALLBACK
        rgbToHsv((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt(), hsv)
        if (hsv[1] < MIN_SATURATION) return FALLBACK
        hsv[1] = hsv[1].coerceIn(0.45f, 0.85f)
        hsv[2] = 0.85f
        return hsvToArgb(hsv)
    }

    private fun rgbToHsv(r: Int, g: Int, b: Int, out: FloatArray) {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min
        val h = when {
            delta == 0f -> 0f
            max == rf -> 60f * (((gf - bf) / delta) % 6f)
            max == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }
        out[0] = if (h < 0f) h + 360f else h
        out[1] = if (max == 0f) 0f else delta / max
        out[2] = max
    }

    private fun hsvToArgb(hsv: FloatArray): Int {
        val c = hsv[2] * hsv[1]
        val x = c * (1f - kotlin.math.abs((hsv[0] / 60f) % 2f - 1f))
        val m = hsv[2] - c
        val (r1, g1, b1) = when ((hsv[0] / 60f).toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private const val SAMPLE = 8
    private const val MIN_SATURATION = 0.18f
}
