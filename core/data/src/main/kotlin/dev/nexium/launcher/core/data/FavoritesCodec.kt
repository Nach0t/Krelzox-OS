package dev.nexium.launcher.core.data

/**
 * Ordered key lists stored as a single newline-separated string
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

    /**
     * Moves [key] by [offset] positions counted among [visible] keys only.
     * Hidden keys (apps temporarily not installed) keep their place in storage,
     * so moving never "swaps" with something the user cannot see.
     */
    fun moveAmongVisible(current: List<String>, key: String, offset: Int, visible: Set<String>): List<String> {
        val visibleOrder = current.filter { it in visible }
        val from = visibleOrder.indexOf(key)
        if (from < 0 || offset == 0) return current
        val to = (from + offset).coerceIn(0, visibleOrder.lastIndex)
        if (to == from) return current
        val anchor = visibleOrder[to]
        val without = current.filter { it != key }
        val anchorIndex = without.indexOf(anchor)
        val insertAt = if (offset < 0) anchorIndex else anchorIndex + 1
        return without.toMutableList().apply { add(insertAt, key) }
    }

    /** Most-recent-first history without duplicates, bounded to [max] entries. */
    fun pushRecent(current: List<String>, key: String, max: Int): List<String> =
        (listOf(key) + current.filter { it != key }).take(max)
}
