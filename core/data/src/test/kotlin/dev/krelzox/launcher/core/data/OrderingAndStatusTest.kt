package dev.krelzox.launcher.core.data

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderingAndStatusTest {

    private val list = listOf("a", "b", "c", "d")
    private val all = list.toSet()

    @Test
    fun `move right and left`() {
        assertEquals(listOf("a", "c", "b", "d"), FavoritesCodec.moveAmongVisible(list, "b", +1, all))
        assertEquals(listOf("c", "a", "b", "d"), FavoritesCodec.moveAmongVisible(list, "c", -2, all))
    }

    @Test
    fun `move is clamped at the edges`() {
        assertEquals(list, FavoritesCodec.moveAmongVisible(list, "a", -1, all))
        assertEquals(list, FavoritesCodec.moveAmongVisible(list, "d", +3, all))
        assertEquals(listOf("b", "c", "d", "a"), FavoritesCodec.moveAmongVisible(list, "a", +10, all))
    }

    @Test
    fun `move of unknown key or zero offset is a no-op`() {
        assertEquals(list, FavoritesCodec.moveAmongVisible(list, "x", 1, all))
        assertEquals(list, FavoritesCodec.moveAmongVisible(list, "b", 0, all))
    }

    @Test
    fun `move among visible skips hidden keys`() {
        val stored = listOf("nav", "gone", "cfg")
        val visible = setOf("nav", "cfg")
        assertEquals(listOf("cfg", "nav", "gone"), FavoritesCodec.moveAmongVisible(stored, "cfg", -1, visible))
        assertEquals(listOf("gone", "cfg", "nav"), FavoritesCodec.moveAmongVisible(stored, "nav", +1, visible))
    }

    @Test
    fun `move among visible keeps hidden keys and clamps`() {
        val stored = listOf("a", "x", "b", "y", "c")
        val visible = setOf("a", "b", "c")
        assertEquals(stored, FavoritesCodec.moveAmongVisible(stored, "a", -1, visible))
        assertEquals(stored, FavoritesCodec.moveAmongVisible(stored, "x", 1, visible))
        assertEquals(listOf("x", "b", "y", "c", "a"), FavoritesCodec.moveAmongVisible(stored, "a", +5, visible))
    }

    @Test
    fun `recents are most-recent-first, unique and bounded`() {
        assertEquals(listOf("c", "a", "b"), FavoritesCodec.pushRecent(listOf("a", "b", "c"), "c", 8))
        assertEquals(listOf("x", "a"), FavoritesCodec.pushRecent(listOf("a", "b", "c"), "x", 2))
        assertEquals(listOf("a"), FavoritesCodec.pushRecent(emptyList(), "a", 8))
    }

    @Test
    fun `category mapping`() {
        assertEquals(AppCategory.Video, AppCategory.fromPlatform(ApplicationInfo.CATEGORY_VIDEO))
        assertEquals(AppCategory.Audio, AppCategory.fromPlatform(ApplicationInfo.CATEGORY_AUDIO))
        assertEquals(AppCategory.Game, AppCategory.fromPlatform(ApplicationInfo.CATEGORY_UNDEFINED, isGameFlag = true))
        assertEquals(AppCategory.Other, AppCategory.fromPlatform(ApplicationInfo.CATEGORY_UNDEFINED))
        assertTrue(AppCategory.Image.isMedia)
        assertFalse(AppCategory.Game.isMedia)
    }

    @Test
    fun `network status mapping`() {
        assertEquals(NetworkStatus.Offline, NetworkStatus.from(hasInternet = false, wifi = true, ethernet = false))
        assertEquals(NetworkStatus.Wifi, NetworkStatus.from(hasInternet = true, wifi = true, ethernet = false))
        assertEquals(NetworkStatus.Ethernet, NetworkStatus.from(hasInternet = true, wifi = true, ethernet = true))
        assertEquals(NetworkStatus.Other, NetworkStatus.from(hasInternet = true, wifi = false, ethernet = false))
    }
}
