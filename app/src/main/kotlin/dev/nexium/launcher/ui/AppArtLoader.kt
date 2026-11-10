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
import java.util.Collections

/**
 * Uniform visual for an app tile: its launcher icon plus an accent color derived once
 * from that icon (tints card plates and the hero, without any blur).
 */
data class AppArt(val icon: ImageBitmap, val accent: Int)

/**
 * Loads icons (always) and TV banners (on demand, only for the hero) off the main
 * thread, each in its own size-bounded memory cache. Bitmaps are rasterized once at
 * display resolution so drawing stays cheap on the V3D GPU.
 */
class AppArtLoader(context: Context) {

    private val packageManager: PackageManager = context.applicationContext.packageManager

    private val icons = object : LruCache<String, AppArt>(ICON_CACHE_BYTES) {
        override fun sizeOf(key: String, value: AppArt): Int = value.icon.width * value.icon.height * 4
    }

    private val banners = object : LruCache<String, ImageBitmap>(BANNER_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** Apps known to have no banner, so the lookup is not repeated. */
    private val noBanner: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** Synchronous cache lookup, so already-loaded art renders on the first frame. */
    fun peek(entry: AppEntry): AppArt? = icons.get(entry.key)

    suspend fun load(entry: AppEntry): AppArt? {
        icons.get(entry.key)?.let { return it }
        val art = io(entry) {
            val bitmap = packageManager.getActivityIcon(entry.component).toBitmap(ICON_PX, ICON_PX)
            AppArt(bitmap.asImageBitmap(), AccentExtractor.accentOf(bitmap))
        }
        art?.let { icons.put(entry.key, it) }
        return art
    }

    fun peekBanner(entry: AppEntry): ImageBitmap? = banners.get(entry.key)

    /** TV banner for the hero, or null if the app has none. */
    suspend fun loadBanner(entry: AppEntry): ImageBitmap? {
        banners.get(entry.key)?.let { return it }
        if (entry.key in noBanner) return null
        val banner = io(entry) {
            val drawable = packageManager.getActivityBanner(entry.component)
                ?: packageManager.getApplicationBanner(entry.packageName)
            drawable?.toBitmap(BANNER_WIDTH_PX, BANNER_HEIGHT_PX)?.asImageBitmap()
        }
        if (banner != null) banners.put(entry.key, banner) else noBanner.add(entry.key)
        return banner
    }

    /** Drops cached art of apps that are no longer installed. */
    fun retainOnly(keys: Set<String>) {
        icons.snapshot().keys.filterNot { it in keys }.forEach { icons.remove(it) }
        banners.snapshot().keys.filterNot { it in keys }.forEach { banners.remove(it) }
        noBanner.retainAll(keys)
    }

    private suspend fun <T> io(entry: AppEntry, block: () -> T?): T? = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Failed to load art for ${entry.key}", e)
            null
        }
    }

    private companion object {
        const val TAG = "NexiumArt"
        const val ICON_PX = 192
        const val BANNER_WIDTH_PX = 512
        const val BANNER_HEIGHT_PX = 288
        const val ICON_CACHE_BYTES = 16 * 1024 * 1024
        const val BANNER_CACHE_BYTES = 8 * 1024 * 1024
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
