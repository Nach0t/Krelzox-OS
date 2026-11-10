package dev.nexium.launcher.core.data

/**
 * Ordered favorites stored as a single newline-separated string
 * (Preferences DataStore has no ordered list type). App keys are
 * "package/activity" and can never contain a newline.
 */
internal object FavoritesCodec {

    private const val SEPARATOR = "\n"

    fun decode(raw: String?): List<String> =
        raw.orEmpty()
            .split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun encode(keys: List<String>): String = keys.distinct().joinToString(SEPARATOR)

    /** Removes the key if present, otherwise appends it at the end. */
    fun toggle(current: List<String>, key: String): List<String> =
        if (key in current) current - key else current + key
}
