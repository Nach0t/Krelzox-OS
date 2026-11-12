package dev.krelzox.launcher.core.data.metrics

/** Metrics that come from optional kernel sources and may be unavailable on some builds. */
enum class MetricId { Cpu, Load, Frequency, Temperature, Fan, Traffic }

/**
 * One reading of the system. Values from Android APIs (RAM, storage, uptime) are always
 * present; kernel-sourced values are null when their source cannot be read, and the
 * metric is then listed in [unavailable] so the UI can say "No disponible" instead of
 * guessing. A null value whose id is NOT in [unavailable] is still being measured
 * (for example CPU usage needs two readings).
 */
data class SystemSnapshot(
    val cpuPercent: Float?,
    /** 1, 5 and 15 minute load averages. */
    val load: List<Float>?,
    val cpuCores: Int,
    /** Average current frequency of the online cores. */
    val cpuFreqMHz: Int?,
    val cpuMaxFreqMHz: Int?,
    val temperatureC: Float?,
    val fanRpm: Int?,
    val ramTotalBytes: Long,
    val ramAvailableBytes: Long,
    val lowMemory: Boolean,
    val storageTotalBytes: Long,
    val storageFreeBytes: Long,
    val uptimeMs: Long,
    val netRxBytesPerSec: Long?,
    val netTxBytesPerSec: Long?,
    val unavailable: Set<MetricId>,
) {
    val ramUsedBytes: Long get() = (ramTotalBytes - ramAvailableBytes).coerceAtLeast(0)
    val ramPercent: Float get() = if (ramTotalBytes > 0) ramUsedBytes * 100f / ramTotalBytes else 0f
    val storageUsedBytes: Long get() = (storageTotalBytes - storageFreeBytes).coerceAtLeast(0)
    val storagePercent: Float get() = if (storageTotalBytes > 0) storageUsedBytes * 100f / storageTotalBytes else 0f

    fun isAvailable(id: MetricId): Boolean = id !in unavailable
}

/** Latest snapshot plus recent history (oldest first), one point per sample. */
data class SystemMetrics(
    val snapshot: SystemSnapshot,
    val cpuHistory: List<Float>,
    val ramHistory: List<Float>,
    val temperatureHistory: List<Float>,
    val netRxHistory: List<Float>,
    val netTxHistory: List<Float>,
    val sampleIntervalMs: Long,
    val historyCapacity: Int,
)

/** Pure parsers for kernel text files. Unit tested; never throw. */
object KernelParsers {

    private val whitespace = Regex("\\s+")

    /** Aggregate CPU times from the first ("cpu ...") line of /proc/stat. */
    data class CpuTimes(val idle: Long, val total: Long)

    fun cpuTimes(procStat: String): CpuTimes? {
        val line = procStat.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
        // user nice system idle iowait irq softirq steal (guest time is already in user)
        val values = line.trim().split(whitespace).drop(1).take(8).map { it.toLongOrNull() ?: return null }
        if (values.size < 4) return null
        val idle = values[3] + (values.getOrNull(4) ?: 0L)
        return CpuTimes(idle = idle, total = values.sum())
    }

    /** Busy share between two readings, 0–100, or null if no time elapsed. */
    fun cpuUsagePercent(previous: CpuTimes, current: CpuTimes): Float? {
        val total = current.total - previous.total
        if (total <= 0) return null
        val idle = (current.idle - previous.idle).coerceIn(0, total)
        return ((1.0 - idle.toDouble() / total) * 100.0).toFloat().coerceIn(0f, 100f)
    }

    fun loadAverage(text: String): List<Float>? {
        val values = text.trim().split(whitespace).take(3).map { it.toFloatOrNull() ?: return null }
        return values.takeIf { it.size == 3 }
    }

    /** Thermal zones report millidegrees Celsius. Implausible values are rejected. */
    fun milliCelsius(text: String): Float? =
        text.trim().toLongOrNull()?.let { it / 1000f }?.takeIf { it in -40f..150f }

    /** cpufreq reports kHz. */
    fun kiloHertzToMHz(text: String): Int? =
        text.trim().toLongOrNull()?.takeIf { it > 0 }?.let { (it / 1000).toInt() }

    fun nonNegativeInt(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 0 }

    /** Bytes per second between two counter readings; null on counter reset or no elapsed time. */
    fun ratePerSecond(previousBytes: Long, currentBytes: Long, elapsedMs: Long): Long? {
        if (elapsedMs <= 0 || currentBytes < previousBytes) return null
        return (currentBytes - previousBytes) * 1000 / elapsedMs
    }
}
