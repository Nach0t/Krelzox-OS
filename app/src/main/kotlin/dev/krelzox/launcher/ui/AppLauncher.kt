package dev.krelzox.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import dev.krelzox.launcher.core.data.AppEntry

/** Starts other apps defensively: a failed launch must never crash the launcher. */
object AppLauncher {

    private const val TAG = "KrelzoxLauncher"

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

    /** System "default home app" chooser: lets the user switch back to another launcher. */
    fun openHomeSettings(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** Opens a system settings screen by action (e.g. Settings.ACTION_WIFI_SETTINGS), falling back to Settings. */
    fun openSystemScreen(context: Context, action: String): Boolean =
        start(context, Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) || openSettings(context)

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
