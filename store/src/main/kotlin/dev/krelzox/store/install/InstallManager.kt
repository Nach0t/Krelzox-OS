package dev.krelzox.store.install

import android.util.Log
import dev.krelzox.store.data.DeviceApps
import dev.krelzox.store.data.HistoryEntry
import dev.krelzox.store.data.HistoryStore
import dev.krelzox.store.data.Operation
import dev.krelzox.store.data.Outcome
import dev.krelzox.store.domain.Compatibility
import dev.krelzox.store.model.ApkVersion
import dev.krelzox.store.model.CatalogApp
import dev.krelzox.store.net.Http
import dev.krelzox.store.net.IntegrityException
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Progress of one store operation, shown on the app's page and in lists. */
sealed interface Task {
    data object Queued : Task
    data class Downloading(val read: Long, val total: Long) : Task {
        val fraction: Float get() = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data object Verifying : Task
    data object WaitingForSystem : Task
    data class Failed(val reason: FailureReason, val detail: String? = null) : Task
}

enum class FailureReason {
    /** The user has not allowed Krelzox Store as an install source yet. */
    PERMISSION,
    INCOMPATIBLE,
    STORAGE,
    NETWORK,
    /** Hash, size, package name, version or signature did not match the index. */
    VERIFICATION,
    /** The system installer refused the package. */
    SYSTEM,
}

/**
 * Runs installs, updates and uninstalls one at a time:
 * download → verify (size, SHA-256, package, version, signer) → system installer.
 * Nothing reaches the installer unless every check passed, and a failed update leaves the
 * installed version untouched. Every finished operation is written to the history.
 */
class InstallManager(
    private val apkDir: File,
    private val installer: SystemInstaller,
    private val deviceApps: DeviceApps,
    private val history: HistoryStore,
    private val scope: CoroutineScope,
    private val onPackagesChanged: () -> Unit,
) {

    private val _tasks = MutableStateFlow<Map<String, Task>>(emptyMap())
    val tasks: StateFlow<Map<String, Task>> = _tasks.asStateFlow()

    private val jobs = HashMap<String, Job>()
    private val serial = Mutex()

    fun install(app: CatalogApp, version: ApkVersion, repoAddress: String) {
        val isUpdate = deviceApps.installed(app.packageName) != null
        start(app.packageName) {
            runInstall(app, version, repoAddress, isUpdate)
        }
    }

    fun uninstall(app: CatalogApp) {
        start(app.packageName) {
            set(app.packageName, Task.WaitingForSystem)
            val version = deviceApps.installed(app.packageName)?.versionName
            val result = installer.uninstall(app.packageName)
            finish(app, Operation.UNINSTALL, version, result)
        }
    }

    /** Cancels a queued or downloading operation. The system dialog is cancelled on screen. */
    fun cancel(packageName: String) {
        val task = _tasks.value[packageName]
        if (task is Task.WaitingForSystem) return
        jobs.remove(packageName)?.cancel()
        clear(packageName)
    }

    fun dismissFailure(packageName: String) {
        if (_tasks.value[packageName] is Task.Failed) clear(packageName)
    }

    private fun start(packageName: String, block: suspend () -> Unit) {
        val current = _tasks.value[packageName]
        if (current != null && current !is Task.Failed) return
        set(packageName, Task.Queued)
        jobs[packageName] = scope.launch {
            try {
                serial.withLock { block() }
            } catch (e: CancellationException) {
                clear(packageName)
                throw e
            } finally {
                jobs.remove(packageName)
            }
        }
    }

    private suspend fun runInstall(app: CatalogApp, version: ApkVersion, repoAddress: String, isUpdate: Boolean) {
        val pkg = app.packageName
        val operation = if (isUpdate) Operation.UPDATE else Operation.INSTALL

        if (!installer.canInstall()) return fail(app, operation, version, FailureReason.PERMISSION)
        if (!Compatibility.isCompatible(version, deviceApps.profile())) {
            return fail(app, operation, version, FailureReason.INCOMPATIBLE)
        }
        apkDir.mkdirs()
        if (apkDir.usableSpace < version.size * 3) return fail(app, operation, version, FailureReason.STORAGE)

        val apk = File(apkDir, "$pkg-${version.versionCode}.apk")
        try {
            set(pkg, Task.Downloading(0, version.size))
            var lastPercent = -1L
            try {
                Http.downloadVerified("$repoAddress/${version.apkName}", apk, version.size, version.sha256) { read, total ->
                    val percent = if (total > 0) read * 100 / total else 0
                    if (percent != lastPercent) {
                        lastPercent = percent
                        set(pkg, Task.Downloading(read, total))
                    }
                }
            } catch (e: IntegrityException) {
                return fail(app, operation, version, FailureReason.VERIFICATION, e.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Download of $pkg failed", e)
                return fail(app, operation, version, FailureReason.NETWORK, e.message)
            }

            set(pkg, Task.Verifying)
            val archive = withContext(Dispatchers.IO) { deviceApps.inspectArchive(apk) }
            val problem = when {
                archive == null -> "Unreadable package"
                archive.packageName != pkg -> "Package name differs from the index"
                archive.versionCode != version.versionCode -> "Version differs from the index"
                version.signer !in archive.signers -> "Signing certificate differs from the index"
                else -> deviceApps.installed(pkg)?.let { current ->
                    if (current.signers.intersect(archive.signers).isEmpty()) "Signed with a different key than the installed app" else null
                }
            }
            if (problem != null) return fail(app, operation, version, FailureReason.VERIFICATION, problem)

            set(pkg, Task.WaitingForSystem)
            val result = installer.install(apk, pkg, isUpdate)
            finish(app, operation, version.versionName, result)
        } finally {
            withContext(Dispatchers.IO) { apk.delete() }
        }
    }

    private suspend fun finish(app: CatalogApp, operation: Operation, versionName: String?, result: SystemInstaller.Result) {
        val (outcome, message) = when (result) {
            SystemInstaller.Result.Success -> Outcome.SUCCESS to null
            SystemInstaller.Result.Cancelled -> Outcome.CANCELLED to null
            is SystemInstaller.Result.Failure -> Outcome.FAILED to (result.message ?: "Status ${result.status}")
        }
        history.append(HistoryEntry(System.currentTimeMillis(), app.packageName, app.name, operation, versionName, outcome, message))
        onPackagesChanged()
        if (result is SystemInstaller.Result.Failure) {
            set(app.packageName, Task.Failed(FailureReason.SYSTEM, message))
        } else {
            clear(app.packageName)
        }
    }

    private suspend fun fail(app: CatalogApp, operation: Operation, version: ApkVersion, reason: FailureReason, detail: String? = null) {
        Log.w(TAG, "${operation.name} ${app.packageName} failed: $reason ${detail.orEmpty()}")
        history.append(
            HistoryEntry(System.currentTimeMillis(), app.packageName, app.name, operation, version.versionName, Outcome.FAILED, detail ?: reason.name),
        )
        set(app.packageName, Task.Failed(reason, detail))
    }

    private fun set(packageName: String, task: Task) = _tasks.update { it + (packageName to task) }

    private fun clear(packageName: String) = _tasks.update { it - packageName }

    private companion object {
        const val TAG = "InstallManager"
    }
}
