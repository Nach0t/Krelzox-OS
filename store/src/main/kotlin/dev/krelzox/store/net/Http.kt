package dev.krelzox.store.net

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Minimal HTTPS client for indexes, images and APKs. Cleartext HTTP is refused. */
object Http {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val USER_AGENT = "KrelzoxStore/0.1"

    class Response(val code: Int, val connection: HttpURLConnection)

    fun requireHttps(url: String) {
        if (!url.startsWith("https://")) throw IOException("Only HTTPS URLs are allowed")
    }

    /**
     * Opens a GET request. [ifModifiedSince]/[etag] make index refreshes cheap: the server
     * answers 304 when nothing changed. Redirects are followed only to HTTPS URLs.
     */
    fun get(url: String, etag: String? = null, ifModifiedSince: Long? = null): Response {
        var current = url
        repeat(5) {
            requireHttps(current)
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                etag?.let { setRequestProperty("If-None-Match", it) }
                ifModifiedSince?.let { this.ifModifiedSince = it }
            }
            val code = connection.responseCode
            if (code in 300..399 && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
                val location = connection.getHeaderField("Location") ?: throw IOException("Redirect without location")
                connection.disconnect()
                current = URL(URL(current), location).toString()
                return@repeat
            }
            return Response(code, connection)
        }
        throw IOException("Too many redirects")
    }

    /**
     * Downloads [url] into [destination] and verifies it: the file must not exceed
     * [expectedSize] (when known) and must match [expectedSha256]. On any failure the
     * partial file is deleted. Cancelling the coroutine stops the download.
     */
    suspend fun downloadVerified(
        url: String,
        destination: File,
        expectedSize: Long,
        expectedSha256: String,
        onProgress: (read: Long, total: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val response = get(url)
        try {
            if (response.code != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${response.code}")
            val total = expectedSize.takeIf { it > 0 } ?: response.connection.contentLengthLong
            destination.parentFile?.mkdirs()
            val partial = File(destination.path + ".part")
            try {
                response.connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        copyVerified(input, output, total, expectedSha256) { read ->
                            currentCoroutineContext().ensureActive()
                            onProgress(read, total)
                        }
                    }
                }
                if (!partial.renameTo(destination)) throw IOException("Could not store the download")
            } finally {
                partial.delete()
            }
        } finally {
            response.connection.disconnect()
        }
    }

    /**
     * Copies [input] to [output] while hashing it. Throws [IntegrityException] if more than
     * [maxSize] bytes arrive (when [maxSize] > 0) or if the SHA-256 differs from [expectedSha256].
     */
    suspend fun copyVerified(
        input: InputStream,
        output: OutputStream,
        maxSize: Long,
        expectedSha256: String,
        onChunk: suspend (read: Long) -> Unit = {},
    ): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var read = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            read += count
            if (maxSize > 0 && read > maxSize) throw IntegrityException("File is larger than published")
            digest.update(buffer, 0, count)
            output.write(buffer, 0, count)
            onChunk(read)
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            throw IntegrityException("SHA-256 mismatch")
        }
        return read
    }
}

class IntegrityException(message: String) : SecurityException(message)
