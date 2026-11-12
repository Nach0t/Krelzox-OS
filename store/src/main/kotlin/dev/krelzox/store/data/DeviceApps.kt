package dev.krelzox.store.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dev.krelzox.store.domain.DeviceProfile
import dev.krelzox.store.domain.InstalledPackage
import dev.krelzox.store.index.IndexVerifier
import java.io.File

/** Reads the device's capabilities and installed packages from PackageManager. */
class DeviceApps(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    fun profile(): DeviceProfile = DeviceProfile(
        sdk = Build.VERSION.SDK_INT,
        abis = Build.SUPPORTED_ABIS.toList(),
        features = pm.systemAvailableFeatures.mapNotNull { it.name }.toSet(),
    )

    /** Every installed package with its version and signing certificates. */
    fun installed(): Map<String, InstalledPackage> {
        val packages = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(SIGNING.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(SIGNING)
        }
        return packages.associate { it.packageName to it.toInstalled() }
    }

    fun installed(packageName: String): InstalledPackage? = runCatching {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(SIGNING.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, SIGNING)
        }
        info.toInstalled()
    }.getOrNull()

    /** Package name, version code and signers of a downloaded APK, read before installing it. */
    fun inspectArchive(apk: File): InstalledPackage? {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(SIGNING.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.path, SIGNING)
        }
        return info?.toInstalled()
    }

    fun launchIntent(packageName: String) =
        pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)

    private fun PackageInfo.toInstalled(): InstalledPackage {
        val signing = signingInfo
        val certificates = when {
            signing == null -> emptyArray()
            signing.hasMultipleSigners() -> signing.apkContentsSigners
            else -> signing.signingCertificateHistory ?: emptyArray()
        }
        return InstalledPackage(
            packageName = packageName,
            versionCode = longVersionCode,
            versionName = versionName.orEmpty(),
            signers = certificates.map { IndexVerifier.sha256Hex(it.toByteArray()) }.toSet(),
        )
    }

    private companion object {
        const val SIGNING = PackageManager.GET_SIGNING_CERTIFICATES
    }
}
