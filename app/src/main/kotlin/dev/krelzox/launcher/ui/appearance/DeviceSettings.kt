package dev.krelzox.launcher.ui.appearance

import android.content.Context
import android.content.pm.PackageManager

/**
 * Device-specific settings that Android TV only exposes inside its own Settings app.
 * Neither KonstaKANG's "Raspberry Pi settings" nor the screensaver picker is exported
 * to other apps, so Krelzox can only open Settings and say where to go (ADR-005).
 */
object DeviceSettings {

    /** KonstaKANG's Raspberry Pi settings (declared in the manifest's <queries>). */
    private const val KONSTAKANG_PACKAGE = "com.konstakang.settings.device"

    fun hasRaspberrySettings(context: Context): Boolean = isInstalled(context, KONSTAKANG_PACKAGE)

    /** Krelzox Remote, a separate Krelzox OS app (declared in the manifest's <queries>). */
    const val KRELZOX_REMOTE_PACKAGE = "dev.krelzox.remote"
    const val KRELZOX_REMOTE_PANEL = "dev.krelzox.remote.ui.RemoteActivity"

    fun hasKrelzoxRemote(context: Context): Boolean = isInstalled(context, KRELZOX_REMOTE_PACKAGE)

    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
