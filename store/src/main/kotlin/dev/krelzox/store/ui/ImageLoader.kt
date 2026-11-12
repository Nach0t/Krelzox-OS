package dev.krelzox.store.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.krelzox.store.index.IndexVerifier
import dev.krelzox.store.net.Http
import java.io.File
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Loads repository images (HTTPS only) with a memory cache and a disk cache. Images are
 * decoded down-sampled to the size they are shown at, and at most four download at once.
 */
class ImageLoader(private val dir: File) {

    private val memory = object : LruCache<String, ImageBitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val network = Semaphore(4)

    fun peek(url: String, maxSidePx: Int): ImageBitmap? = memory.get(key(url, maxSidePx))

    suspend fun load(url: String, maxSidePx: Int): ImageBitmap? {
        val key = key(url, maxSidePx)
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = File(dir, IndexVerifier.sha256Hex(url.toByteArray()))
                if (!file.exists()) network.withPermit { download(url, file) }
                decode(file, maxSidePx)?.asImageBitmap()?.also { memory.put(key, it) }
            }.getOrNull()
        }
    }

    private fun download(url: String, file: File) {
        val response = Http.get(url)
        try {
            if (response.code != HttpURLConnection.HTTP_OK) return
            dir.mkdirs()
            val tmp = File(file.path + ".tmp")
            response.connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_IMAGE_BYTES) {
                            tmp.delete()
                            return
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            tmp.renameTo(file)
        } finally {
            response.connection.disconnect()
        }
    }

    private fun decode(file: File, maxSidePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            file.delete()
            return null
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun key(url: String, maxSidePx: Int) = "$maxSidePx|$url"

    private companion object {
        const val MEMORY_BYTES = 48 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 8L * 1024 * 1024
    }
}
