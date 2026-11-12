package dev.krelzox.launcher.ui.home

import dev.krelzox.launcher.core.data.AppCategory
import dev.krelzox.launcher.core.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildReadyStateTest {

    private fun app(pkg: String, label: String, category: AppCategory = AppCategory.Other) =
        AppEntry(packageName = pkg, activityName = "$pkg.Main", label = label, isTvNative = true, category = category)

    private val a = app("com.a", "A")
    private val b = app("com.b", "B", AppCategory.Video)
    private val c = app("com.c", "C", AppCategory.Audio)
    private val settings = app("com.android.tv.settings", "Settings")

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
        assertEquals(listOf(a, b, c), buildReadyState(listOf(a, b, c), listOf(b.key)).apps)
    }

    @Test
    fun `media section contains video, audio and image apps`() {
        assertEquals(listOf(b, c), buildReadyState(listOf(a, b, c), emptyList()).media)
    }

    @Test
    fun `hero carousel is recents first, then favorites, without duplicates`() {
        val state = buildReadyState(listOf(a, b, c), favoriteKeys = listOf(a.key, c.key), recentKeys = listOf(c.key, b.key))
        assertEquals(
            listOf(
                Featured(c, FeaturedReason.Continue),
                Featured(b, FeaturedReason.Recent),
                Featured(a, FeaturedReason.Favorite),
            ),
            state.heroCandidates,
        )
        assertEquals(Featured(c, FeaturedReason.Continue), state.featured)
    }

    @Test
    fun `settings app is excluded from the hero even if it was used last`() {
        val state = buildReadyState(
            apps = listOf(a, b, settings),
            favoriteKeys = listOf(settings.key, a.key),
            recentKeys = listOf(settings.key, b.key),
            heroExcludedPackages = setOf(settings.packageName),
        )
        assertEquals(listOf(b, a), state.heroCandidates.map { it.entry })
        assertEquals(FeaturedReason.Continue, state.heroCandidates.first().reason)
    }

    @Test
    fun `a short carousel is topped up with suggested apps`() {
        val d = app("com.d", "D")
        val state = buildReadyState(listOf(a, b, c, d, settings), favoriteKeys = listOf(b.key), heroExcludedPackages = setOf(settings.packageName))
        assertEquals(
            listOf(Featured(b, FeaturedReason.Favorite), Featured(a, FeaturedReason.Suggested), Featured(c, FeaturedReason.Suggested)),
            state.heroCandidates,
        )
    }

    @Test
    fun `without recents or favorites the hero suggests apps`() {
        val state = buildReadyState(listOf(a, b, c, settings), emptyList(), heroExcludedPackages = setOf(settings.packageName))
        assertEquals(listOf(a, b, c), state.heroCandidates.map { it.entry })
        assertTrue(state.heroCandidates.all { it.reason == FeaturedReason.Suggested })
    }

    @Test
    fun `excluded apps are still used when nothing else is installed`() {
        val state = buildReadyState(listOf(settings), emptyList(), heroExcludedPackages = setOf(settings.packageName))
        assertEquals(listOf(settings), state.heroCandidates.map { it.entry })
    }

    @Test
    fun `hero carousel is bounded`() {
        val many = (1..9).map { app("com.p$it", "P$it") }
        val state = buildReadyState(many, many.map { it.key })
        assertEquals(MAX_HERO_CANDIDATES, state.heroCandidates.size)
    }

    @Test
    fun `no apps means no hero`() {
        assertNull(buildReadyState(emptyList(), listOf(a.key), listOf(a.key)).featured)
    }
}
