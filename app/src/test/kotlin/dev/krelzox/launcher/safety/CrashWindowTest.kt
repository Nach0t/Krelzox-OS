package dev.krelzox.launcher.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashWindowTest {

    private val now = 10_000_000L
    private val window = CrashWindow.WINDOW_MS

    @Test
    fun `three crashes inside the window is a loop`() {
        assertTrue(CrashWindow.isLoop(listOf(now - 60_000, now - 30_000, now - 1_000), now))
    }

    @Test
    fun `two crashes is not a loop`() {
        assertFalse(CrashWindow.isLoop(listOf(now - 30_000, now - 1_000), now))
    }

    @Test
    fun `old crashes expire`() {
        assertFalse(CrashWindow.isLoop(listOf(now - window - 1, now - 30_000, now - 1_000), now))
    }

    @Test
    fun `future timestamps (clock changes) are ignored`() {
        assertFalse(CrashWindow.isLoop(listOf(now + 50_000, now - 30_000, now - 1_000), now))
    }

    @Test
    fun `add keeps only recent and bounded history`() {
        val result = CrashWindow.add(listOf(now - window - 5, now - 3, now - 2, now - 1), now)
        assertEquals(listOf(now - 2, now - 1, now), result)
    }

    @Test
    fun `codec round trip and garbage tolerance`() {
        assertEquals(listOf(1L, 2L, 3L), CrashWindow.decode(CrashWindow.encode(listOf(1L, 2L, 3L))))
        assertEquals(listOf(5L), CrashWindow.decode("x, 5 ,,"))
        assertEquals(emptyList<Long>(), CrashWindow.decode(null))
    }
}
