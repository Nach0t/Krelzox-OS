package dev.krelzox.store.domain

import dev.krelzox.store.model.ApkVersion
import dev.krelzox.store.model.CatalogApp

/** What the device offers to an APK. Built from Build/PackageManager on the device. */
data class DeviceProfile(
    val sdk: Int,
    /** Supported ABIs, preferred first (Build.SUPPORTED_ABIS). */
    val abis: List<String>,
    val features: Set<String>,
)

/** Why a version cannot be installed on this device. */
sealed interface Incompatibility {
    data class SdkTooLow(val required: Int) : Incompatibility
    data class SdkTooHigh(val max: Int) : Incompatibility
    data class Abi(val available: List<String>) : Incompatibility
    data class Features(val missing: List<String>) : Incompatibility
}

object Compatibility {

    /** Every reason [version] cannot run on [device]; empty when it is compatible. */
    fun check(version: ApkVersion, device: DeviceProfile): List<Incompatibility> {
        val problems = ArrayList<Incompatibility>()
        if (device.sdk < version.minSdk) problems += Incompatibility.SdkTooLow(version.minSdk)
        version.maxSdk?.let { if (device.sdk > it) problems += Incompatibility.SdkTooHigh(it) }
        if (version.nativeCode.isNotEmpty() && version.nativeCode.none { it in device.abis }) {
            problems += Incompatibility.Abi(version.nativeCode)
        }
        val missing = version.features.filter { it !in device.features }
        if (missing.isNotEmpty()) problems += Incompatibility.Features(missing)
        return problems
    }

    fun isCompatible(version: ApkVersion, device: DeviceProfile): Boolean = check(version, device).isEmpty()
}

/** State of an app installed on the device, as reported by PackageManager. */
data class InstalledPackage(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    /** Lowercase hex SHA-256 of the current signing certificate(s). */
    val signers: Set<String>,
)

object VersionSelector {

    /**
     * The version to install or update to: the newest one compatible with [device]. When
     * the app is already installed, only versions signed by the installed key qualify
     * (Android refuses any other), and only newer ones.
     */
    fun best(app: CatalogApp, device: DeviceProfile, installed: InstalledPackage?): ApkVersion? =
        app.versions
            .asSequence()
            .filter { Compatibility.isCompatible(it, device) }
            .filter { installed == null || it.signer in installed.signers }
            .filter { installed == null || it.versionCode > installed.versionCode }
            .maxByOrNull { it.versionCode }

    /** Newest compatible version regardless of what is installed (for the listing). */
    fun newestCompatible(app: CatalogApp, device: DeviceProfile): ApkVersion? =
        app.versions.filter { Compatibility.isCompatible(it, device) }.maxByOrNull { it.versionCode }
}

/** What the store can do for an app right now. */
sealed interface AppStatus {
    /** Not installed and a compatible version exists. */
    data class Installable(val version: ApkVersion) : AppStatus

    /** Not installed and nothing in the repository runs on this device. */
    data class Incompatible(val reasons: List<Incompatibility>) : AppStatus

    /** Installed and up to date with the repository. */
    data class UpToDate(val installed: InstalledPackage) : AppStatus

    /** Installed and a newer, same-signature, compatible version exists. */
    data class Updatable(val installed: InstalledPackage, val version: ApkVersion) : AppStatus

    /**
     * Installed, but signed with a key the repository never used for a newer version:
     * the store cannot update it (it was installed from another source).
     */
    data class SignatureMismatch(val installed: InstalledPackage) : AppStatus
}

object AppStatusResolver {

    fun resolve(app: CatalogApp, device: DeviceProfile, installed: InstalledPackage?): AppStatus {
        if (installed == null) {
            val version = VersionSelector.best(app, device, null)
            if (version != null) return AppStatus.Installable(version)
            val newest = app.versions.maxByOrNull { it.versionCode }
            return AppStatus.Incompatible(newest?.let { Compatibility.check(it, device) }.orEmpty())
        }
        VersionSelector.best(app, device, installed)?.let { return AppStatus.Updatable(installed, it) }
        val newerOtherKey = app.versions.any {
            it.versionCode > installed.versionCode && it.signer !in installed.signers &&
                Compatibility.isCompatible(it, device)
        }
        val sameKeyKnown = app.versions.any { it.signer in installed.signers }
        return if (newerOtherKey && !sameKeyKnown) {
            AppStatus.SignatureMismatch(installed)
        } else {
            AppStatus.UpToDate(installed)
        }
    }

    /** Every catalog app with a safe update available, ordered by name. */
    fun updates(
        apps: Collection<CatalogApp>,
        device: DeviceProfile,
        installed: Map<String, InstalledPackage>,
    ): List<Pair<CatalogApp, AppStatus.Updatable>> =
        apps.mapNotNull { app ->
            val current = installed[app.packageName] ?: return@mapNotNull null
            (resolve(app, device, current) as? AppStatus.Updatable)?.let { app to it }
        }.sortedBy { it.first.name.lowercase() }
}
