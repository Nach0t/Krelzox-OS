package dev.krelzox.store.index

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val ADDRESS = "https://repo.example/fdroid/repo"

/** SHA-256 of the throwaway test certificate that signed index-signed.jar. */
private const val TEST_FINGERPRINT = "7a8769fd7bcb4e82831a107b9724465f11a9e9bb6e5c2478dc429e03e62d6bc6"

private fun resource(name: String): File =
    File(requireNotNull(IndexTest::class.java.classLoader!!.getResource(name)) { "missing $name" }.toURI())

private fun parse(locales: List<String> = listOf("es-ES", "es", "en-US", "en")) =
    IndexParser("test", ADDRESS, locales).parse(resource("index-v1.json").reader())

class IndexTest {

    @Test
    fun parsesRepoAndKeepsOnlyVerifiableApps() {
        val index = parse()
        assertEquals("Krelzox Test Repo", index.repo.name)
        assertEquals(1794404913394, index.repo.timestamp)
        // The third app only has an MD5-hashed package: it must not be offered at all.
        assertEquals(listOf("org.example.player", "org.example.puzzle"), index.apps.map { it.packageName })
    }

    @Test
    fun picksLocalizedFieldsByPreferenceWithEnglishFallback() {
        val player = parse().apps.first()
        assertEquals("Example Player", player.name) // no Spanish name → en-US
        assertEquals("Reproduce cualquier formato", player.summary) // Spanish summary wins
        assertTrue(player.description.contains("Plays everything."))
        assertTrue(player.description.contains("• Video"))
        assertFalse(player.description.contains("<"))
    }

    @Test
    fun resolvesAssetsAgainstConfiguredAddressNotTheIndexOne() {
        val player = parse().apps.first()
        assertEquals("$ADDRESS/org.example.player/en-US/icon_abc.png", player.iconUrl)
        // TV screenshots are preferred over phone ones.
        assertEquals(listOf("$ADDRESS/org.example.player/en-US/tvScreenshots/tv1.png"), player.screenshotUrls)
        assertTrue(player.hasTvAssets)
        assertTrue(player.screenshotUrls.none { it.contains("evil.example") })
    }

    @Test
    fun readsVersionsNewestFirstWithNormalizedHashesAndPermissionNames() {
        val versions = parse().apps.first().versions
        assertEquals(listOf(30L, 29L, 20L), versions.map { it.versionCode })
        assertEquals("a".repeat(64), versions[0].sha256) // uppercase in the index
        assertEquals(listOf("android.permission.INTERNET", "android.permission.READ_LOGS"), versions[0].permissions)
        assertEquals(listOf("x86_64"), versions[0].nativeCode)
        assertTrue(versions[2].nativeCode.isEmpty())
    }

    @Test
    fun gamesAreDetectedFromCategories() {
        val (player, puzzle) = parse().apps
        assertFalse(player.isGame)
        assertTrue(puzzle.isGame)
        assertNull(puzzle.iconUrl)
        assertFalse(puzzle.hasTvAssets)
    }

    @Test
    fun preferredLocalesEndWithEnglish() {
        val locales = IndexParser.preferredLocales(listOf(java.util.Locale.forLanguageTag("es-CL")))
        assertEquals(listOf("es-CL", "es", "en-US", "en"), locales)
    }

    @Test
    fun signedIndexWithPinnedKeyIsAccepted() {
        val index = IndexVerifier.readVerified(resource("index-signed.jar"), TEST_FINGERPRINT) { stream ->
            IndexParser("test", ADDRESS, listOf("en-US")).parse(stream.bufferedReader())
        }
        assertEquals(2, index.apps.size)
    }

    @Test
    fun signedIndexWithAnotherKeyIsRejected() {
        expectRejected("index-signed.jar", "0".repeat(64))
    }

    @Test
    fun tamperedIndexIsRejected() {
        expectRejected("index-tampered.jar", TEST_FINGERPRINT)
    }

    @Test
    fun unsignedIndexIsRejected() {
        expectRejected("index-unsigned.jar", TEST_FINGERPRINT)
    }

    private fun expectRejected(jar: String, fingerprint: String) {
        try {
            IndexVerifier.readVerified(resource(jar), fingerprint) { stream ->
                IndexParser("test", ADDRESS, listOf("en-US")).parse(stream.bufferedReader())
            }
            fail("$jar should have been rejected")
        } catch (expected: SecurityException) {
            // IndexVerificationException or the JAR verifier's digest error.
        }
    }
}
