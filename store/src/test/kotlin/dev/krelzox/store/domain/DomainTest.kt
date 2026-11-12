package dev.krelzox.store.domain

import dev.krelzox.store.model.ApkVersion
import dev.krelzox.store.model.CatalogApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val KEY_A = "a".repeat(64)
private val KEY_B = "b".repeat(64)

/** The reference device: Raspberry Pi 5, Android TV 17, no touchscreen. */
private val PI = DeviceProfile(sdk = 37, abis = listOf("arm64-v8a"), features = setOf("android.software.leanback"))

private fun version(
    code: Long,
    abi: List<String> = emptyList(),
    signer: String = KEY_A,
    minSdk: Int = 21,
    maxSdk: Int? = null,
    features: List<String> = emptyList(),
) = ApkVersion(
    versionCode = code, versionName = "v$code", apkName = "app_$code.apk", sha256 = "0".repeat(64), size = 1,
    minSdk = minSdk, targetSdk = 35, maxSdk = maxSdk, nativeCode = abi, features = features,
    permissions = emptyList(), signer = signer, added = 0, antiFeatures = emptyList(),
)

private fun app(
    vararg versions: ApkVersion,
    name: String = "Player",
    summary: String = "",
    categories: List<String> = emptyList(),
    pkg: String = "org.example.$name",
) = CatalogApp(
    repoId = "r", packageName = pkg, name = name, summary = summary, description = "", whatsNew = "",
    categories = categories, license = "MIT", author = null, webSite = null, sourceCode = null, issueTracker = null,
    changelog = null, added = 0, lastUpdated = 0, antiFeatures = emptyList(), iconUrl = null, tvBannerUrl = null,
    featureGraphicUrl = null, screenshotUrls = emptyList(), hasTvAssets = false, versions = versions.sortedByDescending { it.versionCode },
)

private fun installed(code: Long, signer: String = KEY_A) = InstalledPackage("org.example.Player", code, "v$code", setOf(signer))

class DomainTest {

    @Test
    fun picksNewestBuildForTheDeviceAbiNotTheNewestOverall() {
        // Like VLC in F-Droid: the newest version code is the x86_64 build.
        val player = app(version(30, listOf("x86_64")), version(29, listOf("arm64-v8a")), version(28, listOf("armeabi-v7a")))
        assertEquals(29L, VersionSelector.best(player, PI, null)?.versionCode)
    }

    @Test
    fun reportsEveryIncompatibility() {
        val v = version(1, abi = listOf("x86"), minSdk = 40, features = listOf("android.hardware.touchscreen", "android.software.leanback"))
        val problems = Compatibility.check(v, PI)
        assertEquals(
            listOf(
                Incompatibility.SdkTooLow(40),
                Incompatibility.Abi(listOf("x86")),
                Incompatibility.Features(listOf("android.hardware.touchscreen")),
            ),
            problems,
        )
        assertTrue(Compatibility.check(version(2, maxSdk = 36), PI).contains(Incompatibility.SdkTooHigh(36)))
    }

    @Test
    fun statusFollowsTheRealInstallation() {
        val player = app(version(10), version(12))
        assertEquals(12L, (AppStatusResolver.resolve(player, PI, null) as AppStatus.Installable).version.versionCode)
        assertEquals(12L, (AppStatusResolver.resolve(player, PI, installed(10)) as AppStatus.Updatable).version.versionCode)
        assertTrue(AppStatusResolver.resolve(player, PI, installed(12)) is AppStatus.UpToDate)
        // Installed build newer than the repository: nothing to update.
        assertTrue(AppStatusResolver.resolve(player, PI, installed(15)) is AppStatus.UpToDate)
    }

    @Test
    fun neverOffersAnUpdateSignedWithAnotherKey() {
        val player = app(version(10, signer = KEY_B), version(12, signer = KEY_B))
        val status = AppStatusResolver.resolve(player, PI, installed(9, signer = KEY_A))
        assertTrue(status is AppStatus.SignatureMismatch)
        assertNull(VersionSelector.best(player, PI, installed(9, signer = KEY_A)))
    }

    @Test
    fun incompatibleWhenNothingRunsOnTheDevice() {
        val status = AppStatusResolver.resolve(app(version(3, listOf("x86_64"))), PI, null)
        assertEquals(AppStatus.Incompatible(listOf(Incompatibility.Abi(listOf("x86_64")))), status)
    }

    @Test
    fun updatesListOnlyContainsSafeUpdates() {
        val a = app(version(2), name = "Alpha")
        val b = app(version(5, signer = KEY_B), name = "Beta")
        val c = app(version(1), name = "Gamma")
        val installedNow = mapOf(
            a.packageName to InstalledPackage(a.packageName, 1, "1", setOf(KEY_A)),
            b.packageName to InstalledPackage(b.packageName, 1, "1", setOf(KEY_A)),
            c.packageName to InstalledPackage(c.packageName, 1, "1", setOf(KEY_A)),
        )
        val updates = AppStatusResolver.updates(listOf(c, b, a), PI, installedNow)
        assertEquals(listOf("Alpha"), updates.map { it.first.name })
    }

    @Test
    fun searchIgnoresAccentsAndRanksNameMatchesFirst() {
        val music = app(version(1), name = "Música Libre", summary = "Reproductor")
        val notes = app(version(1), name = "Notes", summary = "Write música sheets")
        val other = app(version(1), name = "Calculator", summary = "Numbers")
        assertEquals(listOf("Música Libre", "Notes"), Search.search(listOf(other, notes, music), "musica").map { it.name })
        assertEquals(listOf("Música Libre"), Search.search(listOf(music, notes), "musica libre").map { it.name })
        assertTrue(Search.search(listOf(music), "   ").isEmpty())
    }

    @Test
    fun searchMatchesCategories() {
        val game = app(version(1), name = "Blocks", categories = listOf("Puzzle Game"))
        assertEquals(listOf("Blocks"), Search.search(listOf(game), "puzzle").map { it.name })
    }
}
