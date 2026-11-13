package dev.krelzox.remote.control

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Remote keys the TV can act on. */
enum class RemoteKey {
    UP, DOWN, LEFT, RIGHT, CENTER, BACK, HOME, RECENTS, NOTIFICATIONS, POWER_MENU,
    PLAY_PAUSE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN, MUTE,
}

/**
 * An action sent by the browser. Coordinates are normalized to 0..1 of the screen, so
 * they do not depend on the size of the image the browser shows.
 */
sealed interface Command {
    data class Tap(val x: Float, val y: Float) : Command
    data class DoubleTap(val x: Float, val y: Float) : Command
    data class LongPress(val x: Float, val y: Float) : Command
    data class Swipe(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val durationMs: Long) : Command
    /** Scroll by a fraction of the screen around a point (positive dy scrolls content up). */
    data class Scroll(val x: Float, val y: Float, val dx: Float, val dy: Float) : Command
    data class Key(val key: RemoteKey) : Command
    data class Text(val text: String) : Command
    data object Backspace : Command
    data object Enter : Command
    data class Launch(val packageName: String) : Command
    /** Shows the remote cursor on the TV at a point (trackpad mode). */
    data class Cursor(val x: Float, val y: Float) : Command
}

object CommandParser {

    private const val MAX_TEXT = 500
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    /** Parses a `{"type":"cmd",...}` message; null for anything malformed or out of range. */
    fun parse(json: String): Command? = runCatching {
        val o = JsonParser.parseString(json).asJsonObject
        if (o.str("type") != "cmd") return null
        when (o.str("action")) {
            "tap" -> Command.Tap(o.coord("x"), o.coord("y"))
            "doubletap" -> Command.DoubleTap(o.coord("x"), o.coord("y"))
            "longpress" -> Command.LongPress(o.coord("x"), o.coord("y"))
            "swipe" -> Command.Swipe(
                o.coord("x1"), o.coord("y1"), o.coord("x2"), o.coord("y2"),
                (o.get("ms")?.asLong ?: 250L).coerceIn(50L, 2_000L),
            )
            "scroll" -> Command.Scroll(
                o.coord("x"), o.coord("y"),
                o.num("dx").coerceIn(-1f, 1f), o.num("dy").coerceIn(-1f, 1f),
            )
            "key" -> Command.Key(RemoteKey.valueOf(o.str("key")!!.uppercase()))
            "text" -> o.str("text")?.takeIf { it.isNotEmpty() && it.length <= MAX_TEXT }?.let(Command::Text)
            "backspace" -> Command.Backspace
            "enter" -> Command.Enter
            "launch" -> o.str("package")?.takeIf { PACKAGE.matches(it) && it.length <= 200 }?.let(Command::Launch)
            "cursor" -> Command.Cursor(o.coord("x"), o.coord("y"))
            else -> null
        }
    }.getOrNull()

    private fun JsonObject.str(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.num(name: String): Float {
        val v = get(name)!!.asFloat
        require(v.isFinite())
        return v
    }

    /** Normalized coordinate: must be within the screen. */
    private fun JsonObject.coord(name: String): Float = num(name).also { require(it in 0f..1f) }
}
