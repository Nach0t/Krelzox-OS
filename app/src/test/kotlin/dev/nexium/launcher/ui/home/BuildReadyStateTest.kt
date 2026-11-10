package dev.nexium.launcher.ui.home

import dev.nexium.launcher.core.data.AppCategory
import dev.nexium.launcher.core.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildReadyStateTest {

    private fun app(pkg: String, label: String, category: AppCategory = AppCategory.Other) =
        AppEntry(packageName = pkg, activityName = "$pkg.Main", label = label, isTvNative = true, category = category)

    private val a = app("com.a", "A")
    private val b = app("com.b", "B", AppCategory.Video)
    private val c = app("com.c", "C", AppCategory.Audio)

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
    fun `media section contains video, audio and image apps`() {
        assertEquals(listOf(b, c), buildReadyState(listOf(a, b, c), emptyList()).media)
    }

    @Test
    fun `featured prefers the most recent installed app`() {
        val state = buildReadyState(listOf(a, b, c), listOf(a.key), listOf("com.gone/x", c.key, b.key))
        assertEquals(Featured(c, FeaturedReason.Recent), state.featured)
    }

    @Test
    fun `featured falls back to first favorite, then first app`() {
        assertEquals(Featured(b, FeaturedReason.Favorite), buildReadyState(listOf(a, b), listOf(b.key)).featured)
        assertEquals(Featured(a, FeaturedReason.Suggested), buildReadyState(listOf(a, b), emptyList()).featured)
    }

    @Test
    fun `no apps means no featured`() {
        assertNull(buildReadyState(emptyList(), listOf(a.key), listOf(a.key)).featured)
    }
}
