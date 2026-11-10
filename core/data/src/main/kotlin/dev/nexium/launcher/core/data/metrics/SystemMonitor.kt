package dev.nexium.launcher.core.data.metrics

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Lightweight system sampler. One reading every [INTERVAL_MS] while collected: a few
 * tiny kernel text files plus Android APIs, all on the IO dispatcher. It does nothing
 * when nobody collects (share it with WhileSubscribed), so Nexium in the background
 * costs zero reads.
 *
 * Kernel sources are resolved once and probed at runtime: on builds where SELinux
 * blocks them, the metric is reported as unavailable instead of being estimated.
 */
class SystemMonitor(context: Context) {

    private val activityManager = context.applicationContext.getSystemService(ActivityManager::class.java)
    private val memoryInfo = ActivityManager.MemoryInfo()

    private val procStat = File("/proc/stat")
    private val procLoadAvg = File("/proc/loadavg")
    // Sources are resolved lazily, on the IO dispatcher during the first sample (never on
    // the main thread when the container is created).
    private val thermalFile: File? by lazy { resolveThermalZone() }
    private val fanFile: File? by lazy { resolveHwmon("pwmfan", "fan1_input") }
    private val cpuDirs: List<File> by lazy {
        File("/sys/devices/system/cpu").listFiles()
            ?.filter { it.name.matches(Regex("cpu\\d+")) }
            ?.sortedBy { it.name.removePrefix("cpu").toInt() }
            .orEmpty()
    }
    private val maxFreqMHz: Int? by lazy {
        cpuDirs.firstNotNullOfOrNull { read(File(it, "cpufreq/cpuinfo_max_freq"))?.let(KernelParsers::kiloHertzToMHz) }
    }

    val metrics: Flow<SystemMetrics> = flow {
        // Fresh state per collection: deltas and history never span a pause.
        val state = SamplerState()
        var reported = false
        while (true) {
            val metrics = sample(state)
            if (!reported) {
                Log.i(TAG, "Sources available: ${MetricId.entries.filter { metrics.snapshot.isAvailable(it) }}; " +
                    "unavailable: ${metrics.snapshot.unavailable}")
                reported = true
            }
            emit(metrics)
            delay(INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)

    private class SamplerState {
        var previousCpu: KernelParsers.CpuTimes? = null
        var previousRx: Long = -1
        var previousTx: Long = -1
        var previousAt: Long = 0
        var storage: Pair<Long, Long>? = null
        var samples: Long = 0
        val cpu = ArrayDeque<Float>()
        val ram = ArrayDeque<Float>()
        val temperature = ArrayDeque<Float>()
        val netRx = ArrayDeque<Float>()
        val netTx = ArrayDeque<Float>()
    }

    private fun sample(state: SamplerState): SystemMetrics {
        val now = SystemClock.elapsedRealtime()
        val unavailable = mutableSetOf<MetricId>()

        // CPU usage: needs two readings of /proc/stat.
        val cpuTimes = read(procStat)?.let(KernelParsers::cpuTimes)
        val cpuPercent = if (cpuTimes == null) {
            unavailable += MetricId.Cpu
            null
        } else {
            state.previousCpu?.let { KernelParsers.cpuUsagePercent(it, cpuTimes) }.also { state.previousCpu = cpuTimes }
        }

        val load = read(procLoadAvg)?.let(KernelParsers::loadAverage).also { if (it == null) unavailable += MetricId.Load }

        val freqs = cpuDirs.mapNotNull { read(File(it, "cpufreq/scaling_cur_freq"))?.let(KernelParsers::kiloHertzToMHz) }
        val freq = if (freqs.isEmpty()) null.also { unavailable += MetricId.Frequency } else freqs.average().toInt()

        val temperature = thermalFile?.let(::read)?.let(KernelParsers::milliCelsius)
            .also { if (it == null) unavailable += MetricId.Temperature }
        val fan = fanFile?.let(::read)?.let(KernelParsers::nonNegativeInt)
            .also { if (it == null) unavailable += MetricId.Fan }

        // Memory: official API, always available.
        activityManager?.getMemoryInfo(memoryInfo)

        // Storage changes slowly: refresh every ~30 s.
        if (state.storage == null || state.samples % STORAGE_EVERY == 0L) {
            state.storage = runCatching {
                StatFs(Environment.getDataDirectory().path).let { it.totalBytes to it.availableBytes }
            }.getOrNull() ?: state.storage
        }

        // Real traffic measured from system-wide counters (not an estimate).
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        var rxRate: Long? = null
        var txRate: Long? = null
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            unavailable += MetricId.Traffic
        } else {
            if (state.previousRx >= 0) {
                rxRate = KernelParsers.ratePerSecond(state.previousRx, rx, now - state.previousAt)
                txRate = KernelParsers.ratePerSecond(state.previousTx, tx, now - state.previousAt)
            }
            state.previousRx = rx
            state.previousTx = tx
        }
        state.previousAt = now
        state.samples++

        val snapshot = SystemSnapshot(
            cpuPercent = cpuPercent,
            load = load,
            cpuCores = cpuDirs.size.coerceAtLeast(Runtime.getRuntime().availableProcessors()),
            cpuFreqMHz = freq,
            cpuMaxFreqMHz = maxFreqMHz,
            temperatureC = temperature,
            fanRpm = fan,
            ramTotalBytes = memoryInfo.totalMem,
            ramAvailableBytes = memoryInfo.availMem,
            lowMemory = memoryInfo.lowMemory,
            storageTotalBytes = state.storage?.first ?: 0L,
            storageFreeBytes = state.storage?.second ?: 0L,
            uptimeMs = now,
            netRxBytesPerSec = rxRate,
            netTxBytesPerSec = txRate,
            unavailable = unavailable,
        )

        cpuPercent?.let { state.cpu.push(it) }
        state.ram.push(snapshot.ramPercent)
        temperature?.let { state.temperature.push(it) }
        rxRate?.let { state.netRx.push(it.toFloat()) }
        txRate?.let { state.netTx.push(it.toFloat()) }

        return SystemMetrics(
            snapshot = snapshot,
            cpuHistory = state.cpu.toList(),
            ramHistory = state.ram.toList(),
            temperatureHistory = state.temperature.toList(),
            netRxHistory = state.netRx.toList(),
            netTxHistory = state.netTx.toList(),
            sampleIntervalMs = INTERVAL_MS,
            historyCapacity = HISTORY,
        )
    }

    private fun ArrayDeque<Float>.push(value: Float) {
        addLast(value)
        while (size > HISTORY) removeFirst()
    }

    private fun read(file: File): String? = try {
        file.readText()
    } catch (e: Exception) {
        null // missing file or blocked by SELinux: reported as unavailable
    }

    /** Prefers the CPU thermal zone ("cpu-thermal" on the Pi 5). */
    private fun resolveThermalZone(): File? {
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }.orEmpty()
        val cpu = zones.firstOrNull { read(File(it, "type"))?.contains("cpu", ignoreCase = true) == true }
        return File(cpu ?: zones.minByOrNull { it.name } ?: return null, "temp")
    }

    private fun resolveHwmon(name: String, input: String): File? =
        File("/sys/class/hwmon").listFiles().orEmpty()
            .firstOrNull { read(File(it, "name"))?.trim() == name }
            ?.let { File(it, input) }

    companion object {
        private const val TAG = "NexiumMetrics"
        const val INTERVAL_MS = 2_000L

        /** 120 samples × 2 s = the last 4 minutes. */
        const val HISTORY = 120
        private const val STORAGE_EVERY = 15L
    }
}
