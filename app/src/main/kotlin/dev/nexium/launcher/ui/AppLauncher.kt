package dev.nexium.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import dev.nexium.launcher.core.data.AppEntry

/** Starts other apps defensively: a failed launch must never crash the launcher. */
object AppLauncher {

    private const val TAG = "NexiumLauncher"

    fun launch(context: Context, entry: AppEntry): Boolean {
        val category = if (entry.isTvNative) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(category)
            .setComponent(entry.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return start(context, intent)
    }

    fun openSettings(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** System app-details screen (force stop, storage, uninstall). No extra permission needed. */
    fun openAppInfo(context: Context, packageName: String): Boolean =
        start(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "Activity not found: ${intent.component}", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Not allowed to start: ${intent.component}", e)
        false
    }
}
