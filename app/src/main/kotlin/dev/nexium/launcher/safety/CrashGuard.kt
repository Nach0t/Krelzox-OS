package dev.nexium.launcher.safety

import android.content.Context
import androidx.core.content.edit

/**
 * Detects crash loops so the launcher can fall back to a minimal safe mode
 * instead of leaving the TV without a usable home screen.
 *
 * Uses SharedPreferences with commit(): the write happens inside the uncaught
 * exception handler, right before the process dies, so it must be synchronous.
 */
class CrashGuard(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Records crashes, then delegates to the previous handler (system crash dialog / kill). */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { recordCrash() }
            previous?.uncaughtException(thread, error)
        }
    }

    fun recordCrash() {
        val updated = CrashWindow.add(read(), clock())
        prefs.edit(commit = true) { putString(KEY_CRASHES, CrashWindow.encode(updated)) }
    }

    fun isInCrashLoop(): Boolean = CrashWindow.isLoop(read(), clock())

    fun reset() {
        prefs.edit(commit = true) { remove(KEY_CRASHES) }
    }

    private fun read(): List<Long> = CrashWindow.decode(prefs.getString(KEY_CRASHES, null))

    private companion object {
        const val PREFS_NAME = "nexium_crash_guard"
        const val KEY_CRASHES = "crash_timestamps"
    }
}

/** Pure crash-window logic, unit tested. */
internal object CrashWindow {
    const val WINDOW_MS = 2 * 60 * 1000L
    const val THRESHOLD = 3

    fun recent(timestamps: List<Long>, now: Long): List<Long> =
        timestamps.filter { it in (now - WINDOW_MS)..now }

    fun add(timestamps: List<Long>, now: Long): List<Long> =
        (recent(timestamps, now) + now).takeLast(THRESHOLD)

    fun isLoop(timestamps: List<Long>, now: Long): Boolean =
        recent(timestamps, now).size >= THRESHOLD

    fun encode(timestamps: List<Long>): String = timestamps.joinToString(",")

    fun decode(raw: String?): List<Long> =
        raw.orEmpty().split(",").mapNotNull { it.trim().toLongOrNull() }
}
