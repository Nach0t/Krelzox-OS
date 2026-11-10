package dev.nexium.launcher.ui.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PerformanceFormatTest {

    private val es = Locale.forLanguageTag("es-CL")
    private val en = Locale.US

    // Some JDKs use a non-breaking space before % or units; compare normalized.
    private fun norm(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    @Test
    fun `percent follows the locale`() {
        assertEquals("12%", norm(PerformanceFormat.percent(12.4f, en)))
        assertTrue(norm(PerformanceFormat.percent(12.4f, es)).startsWith("12"))
    }

    @Test
    fun `temperature uses locale decimals`() {
        assertEquals("51,3 °C", norm(PerformanceFormat.temperature(51.3f, es)))
        assertEquals("51.3 °C", PerformanceFormat.temperature(51.3f, en))
        assertEquals("51 °C", PerformanceFormat.temperatureShort(51.3f, en))
    }

    @Test
    fun `megabytes are decimal with grouping`() {
        assertEquals("2,241 MB", PerformanceFormat.megabytes(2_241_000_000L, en))
        assertEquals("2.241 MB", norm(PerformanceFormat.megabytes(2_241_000_000L, es)))
    }

    @Test
    fun `sizes and rates pick the right unit`() {
        assertEquals("16.8 GB", PerformanceFormat.size(16_754_000_000L, en))
        assertEquals("512 MB", PerformanceFormat.size(512_000_000L, en))
        assertEquals("1.5 MB/s", PerformanceFormat.rate(1_500_000L, en))
        assertEquals("35 KB/s", PerformanceFormat.rate(35_000L, en))
        assertEquals("400 B/s", PerformanceFormat.rate(400L, en))
    }

    @Test
    fun `frequency and load`() {
        assertEquals("2.4 GHz", PerformanceFormat.frequency(2400, en))
        assertEquals("800 MHz", PerformanceFormat.frequency(800, en))
        assertEquals("0,44 · 0,37 · 0,29", PerformanceFormat.load(listOf(0.44f, 0.37f, 0.29f), es))
    }

    @Test
    fun `uptime`() {
        assertEquals("12 min", PerformanceFormat.duration(12 * 60_000L))
        assertEquals("3 h 37 min", PerformanceFormat.duration((3 * 60 + 37) * 60_000L))
        assertEquals("2 d 4 h", PerformanceFormat.duration(((2 * 24 + 4) * 60 + 10) * 60_000L))
    }
}
