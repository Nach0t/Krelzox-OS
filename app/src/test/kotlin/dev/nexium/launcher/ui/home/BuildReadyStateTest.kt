package dev.nexium.launcher.ui.home

import dev.nexium.launcher.core.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildReadyStateTest {

    private fun app(pkg: String, label: String) =
        AppEntry(packageName = pkg, activityName = "$pkg.Main", label = label, isTvNative = true)

    private val a = app("com.a", "A")
    private val b = app("com.b", "B")
    private val c = app("com.c", "C")

    @Test
    fun `favorites keep the user's order`() {
        val state = buildReadyState(listOf(a, b, c), listOf(c.key, a.key))
        assertEquals(listOf(c, a), state.favorites)
        assertEquals(setOf(c.key, a.key), state.favoriteKeys)
    }

    @Test
    fun `favorites of uninstalled apps are hidden`() {
        val state = buildReadyState(listOf(a, b), listOf("com.gone/com.gone.Main", b.key))
        assertEquals(listOf(b), state.favorites)
        assertEquals(setOf(b.key), state.favoriteKeys)
    }

    @Test
    fun `all apps are always listed, favorites included`() {
        val state = buildReadyState(listOf(a, b, c), listOf(b.key))
        assertEquals(listOf(a, b, c), state.apps)
    }

    @Test
    fun `no favorites gives empty section`() {
        val state = buildReadyState(listOf(a), emptyList())
        assertEquals(emptyList<AppEntry>(), state.favorites)
    }
}
