package dev.nexium.launcher.ui

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.nexium.launcher.core.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Visual for an app card: a TV banner when the app provides one, otherwise its icon. */
sealed interface AppArt {
    val image: ImageBitmap

    data class Banner(override val image: ImageBitmap) : AppArt
    data class Icon(override val image: ImageBitmap) : AppArt
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

    private fun loadUncached(entry: AppEntry): AppArt {
        val banner = packageManager.getActivityBanner(entry.component)
            ?: packageManager.getApplicationBanner(entry.packageName)
        if (banner != null) {
            return AppArt.Banner(banner.toBitmap(BANNER_WIDTH_PX, BANNER_HEIGHT_PX).asImageBitmap())
        }
        val icon = packageManager.getActivityIcon(entry.component)
        return AppArt.Icon(icon.toBitmap(ICON_PX, ICON_PX).asImageBitmap())
    }

    private companion object {
        const val TAG = "NexiumArt"
        const val BANNER_WIDTH_PX = 384
        const val BANNER_HEIGHT_PX = 216
        const val ICON_PX = 144
        const val CACHE_BYTES = 24 * 1024 * 1024
    }
}
