package dev.krelzox.store.index

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.jar.JarFile

/**
 * Verifies a signed repository index (JAR) against a pinned certificate fingerprint.
 *
 * The index entry is read once: [read] consumes it while the JAR verifier checks each
 * byte against the signed digests. Only when the entry has been read to the end are its
 * signers known, so the result of [read] is returned only after the signer matches the
 * pinned fingerprint. A tampered entry makes the read throw [SecurityException].
 */
object IndexVerifier {

    const val ENTRY = "index-v1.json"

    fun <T> readVerified(jarFile: File, fingerprint: String, read: (InputStream) -> T): T {
        JarFile(jarFile, true).use { jar ->
            val entry = jar.getJarEntry(ENTRY) ?: throw IndexVerificationException("Index entry missing")
            val result = jar.getInputStream(entry).use { raw ->
                val stream = NonClosingStream(raw)
                val value = read(stream)
                // Signers are only available once the entry has been read completely.
                val buffer = ByteArray(64 * 1024)
                @Suppress("ControlFlowWithEmptyBody")
                while (raw.read(buffer) != -1) {
                    // Drain: the bytes were already consumed by the verifier.
                }
                value
            }
            val signers = entry.codeSigners
            if (signers.isNullOrEmpty()) throw IndexVerificationException("Index is not signed")
            val fingerprints = signers.mapNotNull { signer ->
                (signer.signerCertPath.certificates.firstOrNull() as? X509Certificate)?.let(::certificateFingerprint)
            }
            if (fingerprint.lowercase() !in fingerprints) {
                throw IndexVerificationException("Index signed by an unexpected key")
            }
            return result
        }
    }

    /** Lowercase hex SHA-256 of a certificate's DER encoding (F-Droid's fingerprint format). */
    fun certificateFingerprint(certificate: X509Certificate): String = sha256Hex(certificate.encoded)

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Lets the parser close its reader without closing the JAR entry before it is drained. */
    private class NonClosingStream(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }
}

class IndexVerificationException(message: String) : SecurityException(message)
