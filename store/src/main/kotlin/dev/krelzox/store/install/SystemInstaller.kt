package dev.krelzox.store.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Talks to the system PackageInstaller. A regular app cannot install silently: Android
 * shows its own confirmation for every install and uninstall, and requires the user to
 * allow Krelzox Store as an install source once.
 */
class SystemInstaller(private val context: Context) {

    sealed interface Result {
        data object Success : Result
        data object Cancelled : Result
        data class Failure(val status: Int, val message: String?) : Result
    }

    private val installer: PackageInstaller get() = context.packageManager.packageInstaller

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Writes [apk] into an install session and commits it; suspends until the system answers. */
    suspend fun install(apk: File, packageName: String, isUpdate: Boolean): Result {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(apk.length())
            setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
            // Updates of apps this store installed may skip the prompt when Android allows it.
            if (isUpdate) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = withContext(Dispatchers.IO) {
            val id = installer.createSession(params)
            try {
                installer.openSession(id).use { session ->
                    apk.inputStream().use { input ->
                        session.openWrite("base.apk", 0, apk.length()).use { output ->
                            input.copyTo(output, 64 * 1024)
                            session.fsync(output)
                        }
                    }
                }
            } catch (e: Exception) {
                installer.abandonSession(id)
                throw e
            }
            id
        }
        val pending = InstallResultReceiver.await(sessionId)
        installer.openSession(sessionId).use { session ->
            session.commit(InstallResultReceiver.intentSender(context, sessionId))
        }
        return pending.await()
    }

    /** Asks the system to uninstall [packageName]; the user confirms on screen. */
    suspend fun uninstall(packageName: String): Result {
        // Negative keys never collide with install session ids, which are positive.
        val key = -1 - (packageName.hashCode() and Int.MAX_VALUE) % 1_000_000
        val pending = InstallResultReceiver.await(key)
        installer.uninstall(packageName, InstallResultReceiver.intentSender(context, key))
        return pending.await()
    }
}

/**
 * Receives PackageInstaller status broadcasts. When the system needs the user to confirm,
 * it launches the confirmation screen; final results complete the waiting operation.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getIntExtra(EXTRA_KEY, Int.MIN_VALUE)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirm != null) {
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                complete(key, SystemInstaller.Result.Failure(status, "Missing confirmation"))
            }
            return
        }
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val result = when (status) {
            PackageInstaller.STATUS_SUCCESS -> SystemInstaller.Result.Success
            PackageInstaller.STATUS_FAILURE_ABORTED -> SystemInstaller.Result.Cancelled
            else -> SystemInstaller.Result.Failure(status, message)
        }
        complete(key, result)
    }

    companion object {
        private const val ACTION = "dev.krelzox.store.INSTALL_RESULT"
        private const val EXTRA_KEY = "key"
        private val waiting = HashMap<Int, CompletableDeferred<SystemInstaller.Result>>()

        @Synchronized
        fun await(key: Int): CompletableDeferred<SystemInstaller.Result> =
            CompletableDeferred<SystemInstaller.Result>().also { waiting[key] = it }

        @Synchronized
        private fun complete(key: Int, result: SystemInstaller.Result) {
            waiting.remove(key)?.complete(result)
        }

        /** Explicit, mutable PendingIntent: the installer fills in the status extras. */
        fun intentSender(context: Context, key: Int) = PendingIntent.getBroadcast(
            context,
            key,
            Intent(context, InstallResultReceiver::class.java).setAction(ACTION).putExtra(EXTRA_KEY, key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        ).intentSender
    }
}
