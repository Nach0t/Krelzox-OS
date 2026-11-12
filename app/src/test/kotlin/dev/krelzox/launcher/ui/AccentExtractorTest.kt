package dev.krelzox.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentExtractorTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `transparent artwork falls back to brand blue`() {
        assertEquals(AccentExtractor.FALLBACK, AccentExtractor.accentOf(IntArray(64) { 0 }))
    }

    @Test
    fun `white or grey artwork falls back to brand blue`() {
        val white = IntArray(64) { argb(255, 245, 245, 245) }
        assertEquals(AccentExtractor.FALLBACK, AccentExtractor.accentOf(white))
    }

    @Test
    fun `saturated color dominates a white background`() {
        val pixels = IntArray(64) { i -> if (i < 16) argb(255, 230, 40, 40) else argb(255, 250, 250, 250) }
        val accent = AccentExtractor.accentOf(pixels)
        val r = (accent shr 16) and 0xFF
        val g = (accent shr 8) and 0xFF
        val b = accent and 0xFF
        assertTrue("expected a red accent, got r=$r g=$g b=$b", r > g && r > b)
    }

    @Test
    fun `accent is always opaque`() {
        val pixels = IntArray(64) { argb(255, 30, 160, 90) }
        assertEquals(0xFF, (AccentExtractor.accentOf(pixels) ushr 24) and 0xFF)
    }
}
