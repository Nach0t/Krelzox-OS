package dev.krelzox.store.data

import dev.krelzox.store.model.RepoConfig
import dev.krelzox.store.net.Http
import dev.krelzox.store.net.IntegrityException
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class DataTest {

    private val payload = ByteArray(200_000) { (it % 251).toByte() }
    private val payloadSha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }

    @Test
    fun verifiedCopyAcceptsMatchingHashAndReportsProgress() = runBlocking {
        val out = ByteArrayOutputStream()
        var lastProgress = 0L
        val read = Http.copyVerified(payload.inputStream(), out, payload.size.toLong(), payloadSha.uppercase()) { lastProgress = it }
        assertEquals(payload.size.toLong(), read)
        assertEquals(payload.size.toLong(), lastProgress)
        assertArrayEquals(payload, out.toByteArray())
    }

    @Test
    fun verifiedCopyRejectsWrongHash() = runBlocking {
        expectIntegrityFailure { Http.copyVerified(payload.inputStream(), ByteArrayOutputStream(), 0, "0".repeat(64)) }
    }

    @Test
    fun verifiedCopyStopsAFileLargerThanPublished() = runBlocking {
        expectIntegrityFailure { Http.copyVerified(payload.inputStream(), ByteArrayOutputStream(), 1_000, payloadSha) }
    }

    @Test
    fun onlyHttpsUrlsAreAllowed() {
        try {
            Http.requireHttps("http://f-droid.org/repo/index-v1.jar")
            fail("cleartext URL accepted")
        } catch (expected: java.io.IOException) {
        }
        Http.requireHttps("https://f-droid.org/repo/index-v1.jar")
    }

    @Test
    fun historyRoundTripsAndSkipsDamagedLines() {
        val entry = HistoryEntry(1_700_000_000_000, "org.example", "Example \"App\"", Operation.UPDATE, "2.0", Outcome.FAILED, "Signature mismatch")
        assertEquals(entry, Codecs.decodeHistoryLine(Codecs.encodeHistoryLine(entry)))
        val noVersion = entry.copy(versionName = null, message = null, outcome = Outcome.SUCCESS)
        assertEquals(noVersion, Codecs.decodeHistoryLine(Codecs.encodeHistoryLine(noVersion)))
        assertNull(Codecs.decodeHistoryLine("{\"t\":1,\"p\":\"x\"")) // truncated
        assertNull(Codecs.decodeHistoryLine("{\"t\":1,\"p\":\"x\",\"o\":\"FORMAT_DISK\",\"r\":\"SUCCESS\"}"))
    }

    @Test
    fun reposRoundTripAndInvalidEntriesAreDropped() {
        val repos = listOf(RepoConfig.FDROID, RepoConfig("mine", "Mine", "https://repo.example/repo", "f".repeat(64), enabled = false))
        assertEquals(repos, Codecs.decodeRepos(Codecs.encodeRepos(repos)))
        val tampered = """[{"id":"x","name":"X","address":"http://cleartext/repo","fingerprint":"${"a".repeat(64)}"},
            {"id":"y","name":"Y","address":"https://ok/repo","fingerprint":"short"}]"""
        assertEquals(emptyList<RepoConfig>(), Codecs.decodeRepos(tampered))
        assertEquals(emptyList<RepoConfig>(), Codecs.decodeRepos("not json"))
    }

    @Test
    fun repoInputIsNormalizedAndValidated() {
        assertEquals(RepoConfig.FDROID.fingerprint, RepoConfig.normalizeFingerprint("43:23:8D:51:2C:1E:5E:B2:D6:56:9F:4A:3A:FB:F5:52:34:18:B8:2E:0A:3E:D1:55:27:70:AB:B9:A9:C9:CC:AB"))
        assertNull(RepoConfig.normalizeFingerprint("1234"))
        assertEquals("https://repo.example/fdroid/repo", RepoConfig.normalizeAddress(" https://repo.example/fdroid/repo/ "))
        assertNull(RepoConfig.normalizeAddress("http://repo.example/repo"))
        assertNull(RepoConfig.normalizeAddress("https://user@repo.example/repo"))
    }

    private suspend fun expectIntegrityFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("integrity failure expected")
        } catch (expected: IntegrityException) {
        }
    }
}
