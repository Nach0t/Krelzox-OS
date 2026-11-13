package dev.krelzox.remote.capture

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import dev.krelzox.remote.net.Frame
import dev.krelzox.remote.net.FrameHub
import java.io.ByteArrayOutputStream

/** Stream presets, chosen on the TV. JPEG because the Pi 5 has no hardware H.264 encoder. */
enum class Quality(val key: String, val scale: Float, val jpeg: Int, val fps: Int) {
    LOW("low", 0.5f, 55, 15),
    MEDIUM("medium", 0.67f, 65, 24),
    HIGH("high", 1f, 75, 30),
    ;

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

/**
 * Mirrors the screen through MediaProjection into an ImageReader and publishes JPEG
 * frames to [hub]. A virtual display only produces images when the screen changes, so
 * the newest image is always held (one at most): it is encoded as soon as someone starts
 * watching, and an image arriving faster than [Quality.fps] is encoded a moment later
 * instead of being dropped, so a still screen is never shown out of date.
 */
class ScreenCapture(
    private val projection: MediaProjection,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val densityDpi: Int,
    private val hub: FrameHub,
    private val onStopped: () -> Unit,
) {
    /** True while someone is watching; encoding only happens then. */
    var active = false
        set(value) {
            field = value
            if (value) handler.post { schedule() }
        }
    @Volatile private var quality = Quality.MEDIUM

    private val thread = HandlerThread("krx-capture").apply { start() }
    private val handler = Handler(thread.looper)
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var seq = 0L
    private var lastFrameAt = 0L
    private var bitmap: Bitmap? = null
    private val jpeg = ByteArrayOutputStream(256 * 1024)
    /** Newest image not yet encoded (owned by the capture thread). */
    private var held: Image? = null
    private val encodeHeld = Runnable { encode() }

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            handler.post { release() }
            onStopped()
        }
    }

    /** Starts mirroring. The callback must be registered before the display (Android 14+). */
    fun start(initial: Quality) {
        quality = initial
        projection.registerCallback(callback, handler)
        handler.post { createDisplay() }
    }

    fun setQuality(q: Quality) {
        if (q == quality) return
        quality = q
        handler.post {
            val (w, h) = size()
            held?.close()
            held = null
            reader?.close()
            reader = newReader(w, h)
            display?.resize(w, h, densityDpi)
            display?.surface = reader!!.surface
        }
    }

    fun stop() {
        runCatching { projection.unregisterCallback(callback) }
        handler.post {
            release()
            runCatching { projection.stop() }
            thread.quitSafely()
        }
    }

    private fun size(): Pair<Int, Int> {
        // Even dimensions keep JPEG encoders and scalers happy.
        val w = ((screenWidth * quality.scale).toInt() / 2) * 2
        val h = ((screenHeight * quality.scale).toInt() / 2) * 2
        return w to h
    }

    private fun createDisplay() {
        val (w, h) = size()
        reader = newReader(w, h)
        display = projection.createVirtualDisplay(
            "krelzox-remote", w, h, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler,
        )
    }

    private fun newReader(w: Int, h: Int): ImageReader =
        ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3).apply {
            setOnImageAvailableListener({ r -> onImage(r) }, handler)
        }

    private fun onImage(r: ImageReader) {
        held?.close()
        held = runCatching { r.acquireLatestImage() }.getOrNull()
        if (active) schedule()
    }

    /** Encodes the held image now, or as soon as the frame interval allows. */
    private fun schedule() {
        handler.removeCallbacks(encodeHeld)
        if (held == null) return
        val wait = lastFrameAt + 1000L / quality.fps - SystemClock.uptimeMillis()
        if (wait <= 0) encode() else handler.postDelayed(encodeHeld, wait)
    }

    private fun encode() {
        val image = held ?: return
        held = null
        if (!active) {
            held = image
            return
        }
        lastFrameAt = SystemClock.uptimeMillis()
        image.use {
            val plane = it.planes[0]
            val pixelStride = plane.pixelStride
            val rowPadding = plane.rowStride - pixelStride * it.width
            val paddedWidth = it.width + rowPadding / pixelStride
            val bmp = bitmap?.takeIf { b -> b.width == paddedWidth && b.height == it.height }
                ?: Bitmap.createBitmap(paddedWidth, it.height, Bitmap.Config.ARGB_8888).also { b -> bitmap = b }
            bmp.copyPixelsFromBuffer(plane.buffer)
            val visible = if (rowPadding == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, it.width, it.height)
            jpeg.reset()
            visible.compress(Bitmap.CompressFormat.JPEG, quality.jpeg, jpeg)
            if (visible !== bmp) visible.recycle()
            hub.publish(Frame(++seq, jpeg.toByteArray(), it.width, it.height))
        }
    }

    private fun release() {
        handler.removeCallbacks(encodeHeld)
        held?.close()
        held = null
        display?.release()
        display = null
        reader?.close()
        reader = null
        bitmap?.recycle()
        bitmap = null
    }
}
