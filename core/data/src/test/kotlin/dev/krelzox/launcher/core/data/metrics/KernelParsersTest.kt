package dev.krelzox.launcher.core.data.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KernelParsersTest {

    // Real first lines captured from the Raspberry Pi 5.
    private val stat1 = "cpu  26661 2602 16307 5146418 4101 3030 1004 0 0 0\ncpu0 6156 716 4216 1286071 990 1064 309 0 0 0\n"
    private val stat2 = "cpu  26674 2602 16346 5147044 4101 3031 1005 0 0 0\n"

    @Test
    fun `parses aggregate cpu times`() {
        val t = KernelParsers.cpuTimes(stat1)!!
        assertEquals(5146418L + 4101L, t.idle)
        assertEquals(26661L + 2602 + 16307 + 5146418 + 4101 + 3030 + 1004 + 0, t.total)
    }

    @Test
    fun `cpu usage between two real readings`() {
        val usage = KernelParsers.cpuUsagePercent(KernelParsers.cpuTimes(stat1)!!, KernelParsers.cpuTimes(stat2)!!)!!
        // busy = 13 + 39 + 1 + 1 = 54 of 680 jiffies
        assertEquals(54f * 100f / 680f, usage, 0.01f)
    }

    @Test
    fun `cpu usage is null without elapsed time and clamped otherwise`() {
        val t = KernelParsers.CpuTimes(idle = 10, total = 100)
        assertNull(KernelParsers.cpuUsagePercent(t, t))
        assertEquals(100f, KernelParsers.cpuUsagePercent(t, KernelParsers.CpuTimes(idle = 5, total = 200))!!, 0.001f)
    }

    @Test
    fun `garbage proc stat gives null`() {
        assertNull(KernelParsers.cpuTimes(""))
        assertNull(KernelParsers.cpuTimes("cpu0 1 2 3 4"))
        assertNull(KernelParsers.cpuTimes("cpu  a b c d"))
    }

    @Test
    fun `load average`() {
        assertEquals(listOf(0.44f, 0.37f, 0.29f), KernelParsers.loadAverage("0.44 0.37 0.29 2/1795 11750\n"))
        assertNull(KernelParsers.loadAverage("0.44"))
    }

    @Test
    fun `temperature in millidegrees with plausibility check`() {
        assertEquals(51.25f, KernelParsers.milliCelsius("51250\n")!!, 0.001f)
        assertNull(KernelParsers.milliCelsius("999999"))
        assertNull(KernelParsers.milliCelsius("n/a"))
    }

    @Test
    fun `frequency, integers and rates`() {
        assertEquals(2400, KernelParsers.kiloHertzToMHz("2400000"))
        assertNull(KernelParsers.kiloHertzToMHz("0"))
        assertEquals(1350, KernelParsers.nonNegativeInt(" 1350\n"))
        assertNull(KernelParsers.nonNegativeInt("-1"))
        assertEquals(500L, KernelParsers.ratePerSecond(1000, 2000, 2000))
        assertNull(KernelParsers.ratePerSecond(2000, 1000, 2000))
        assertNull(KernelParsers.ratePerSecond(1000, 2000, 0))
    }

    @Test
    fun `snapshot derived values`() {
        val s = SystemSnapshot(
            cpuPercent = null, load = null, cpuCores = 4, cpuFreqMHz = null, cpuMaxFreqMHz = null,
            temperatureC = null, fanRpm = null, ramTotalBytes = 1000, ramAvailableBytes = 250, lowMemory = false,
            storageTotalBytes = 200, storageFreeBytes = 50, uptimeMs = 0, netRxBytesPerSec = null,
            netTxBytesPerSec = null, unavailable = setOf(MetricId.Fan),
        )
        assertEquals(750L, s.ramUsedBytes)
        assertEquals(75f, s.ramPercent, 0.001f)
        assertEquals(75f, s.storagePercent, 0.001f)
        assertEquals(false, s.isAvailable(MetricId.Fan))
        assertEquals(true, s.isAvailable(MetricId.Cpu))
    }
}

class NetworkDetailsTest {
    @Test
    fun `picks first ipv4 and a global ipv6`() {
        val addresses = listOf(
            java.net.InetAddress.getByName("fe80::1"),
            java.net.InetAddress.getByName("2001:db8::1"),
            java.net.InetAddress.getByName("192.168.1.20"),
        )
        val (v4, v6) = dev.krelzox.launcher.core.data.NetworkDetails.pickAddresses(addresses)
        assertEquals("192.168.1.20", v4)
        assertEquals("2001:db8:0:0:0:0:0:1", v6)
    }

    @Test
    fun `no addresses`() {
        assertEquals(null to null, dev.krelzox.launcher.core.data.NetworkDetails.pickAddresses(emptyList()))
    }
}
