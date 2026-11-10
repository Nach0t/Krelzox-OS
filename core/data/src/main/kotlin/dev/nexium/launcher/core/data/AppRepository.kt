package dev.nexium.launcher.core.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

interface AppRepository {
    /** Emits the current list of launchable apps, and again whenever packages change. */
    fun observeApps(): Flow<List<AppEntry>>
}

/**
 * Reads launchable activities through public PackageManager APIs only.
 * Visibility comes from the <queries> block in the app manifest (no QUERY_ALL_PACKAGES).
 */
class PackageManagerAppRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppRepository {

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)

    override fun observeApps(): Flow<List<AppEntry>> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageAdded(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageChanged(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
        }
        trySend(Unit) // initial load
        launcherApps?.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps?.unregisterCallback(callback) }
    }
        .conflate()
        .map { loadApps() }
        .flowOn(ioDispatcher)

    private fun loadApps(): List<AppEntry> = LaunchableMerger.merge(
        tvNative = query(Intent.CATEGORY_LEANBACK_LAUNCHER, isTvNative = true),
        launcher = query(Intent.CATEGORY_LAUNCHER, isTvNative = false),
        ownPackage = appContext.packageName,
    )

    private fun query(category: String, isTvNative: Boolean): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        val results: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        return results.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (!activity.exported || !activity.enabled) return@mapNotNull null
            AppEntry(
                packageName = activity.packageName,
                activityName = activity.name,
                label = info.loadLabel(packageManager).toString().trim().ifEmpty { activity.packageName },
                isTvNative = isTvNative,
            )
        }
    }
}
