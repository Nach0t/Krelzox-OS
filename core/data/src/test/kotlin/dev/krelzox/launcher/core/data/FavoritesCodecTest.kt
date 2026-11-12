package dev.krelzox.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesCodecTest {

    @Test
    fun `null or blank decodes to empty`() {
        assertEquals(emptyList<String>(), FavoritesCodec.decode(null))
        assertEquals(emptyList<String>(), FavoritesCodec.decode(""))
        assertEquals(emptyList<String>(), FavoritesCodec.decode("\n \n"))
    }

    @Test
    fun `round trip keeps order`() {
        val keys = listOf("com.b/com.b.Main", "com.a/com.a.Main", "com.c/com.c.Tv")
        assertEquals(keys, FavoritesCodec.decode(FavoritesCodec.encode(keys)))
    }

    @Test
    fun `decode drops duplicates and whitespace`() {
        assertEquals(listOf("a/A", "b/B"), FavoritesCodec.decode(" a/A \nb/B\na/A\n"))
    }

    @Test
    fun `toggle appends missing key at the end`() {
        assertEquals(listOf("a/A", "b/B"), FavoritesCodec.toggle(listOf("a/A"), "b/B"))
    }

    @Test
    fun `toggle removes present key and keeps the rest in order`() {
        assertEquals(listOf("a/A", "c/C"), FavoritesCodec.toggle(listOf("a/A", "b/B", "c/C"), "b/B"))
    }
}
