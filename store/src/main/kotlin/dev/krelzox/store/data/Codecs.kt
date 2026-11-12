package dev.krelzox.store.data

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import dev.krelzox.store.model.RepoConfig
import java.io.StringReader
import java.io.StringWriter

/** What the store did to an app. */
enum class Operation { INSTALL, UPDATE, UNINSTALL }

/** Outcome of an operation, as reported by the system installer. */
enum class Outcome { SUCCESS, FAILED, CANCELLED }

/** One line of the operations history (Library › History). */
data class HistoryEntry(
    val timestamp: Long,
    val packageName: String,
    val appName: String,
    val operation: Operation,
    val versionName: String?,
    val outcome: Outcome,
    val message: String?,
)

/**
 * Text encodings for the store's persisted state. JSON written with Gson's streaming
 * writer (no reflection); unknown or malformed entries are skipped on read so a damaged
 * file never prevents the store from starting.
 */
object Codecs {

    fun encodeHistoryLine(entry: HistoryEntry): String = json { w ->
        w.beginObject()
        w.name("t").value(entry.timestamp)
        w.name("p").value(entry.packageName)
        w.name("n").value(entry.appName)
        w.name("o").value(entry.operation.name)
        entry.versionName?.let { w.name("v").value(it) }
        w.name("r").value(entry.outcome.name)
        entry.message?.let { w.name("m").value(it) }
        w.endObject()
    }

    fun decodeHistoryLine(line: String): HistoryEntry? = runCatching {
        var timestamp = 0L
        var packageName: String? = null
        var appName = ""
        var operation: Operation? = null
        var version: String? = null
        var outcome: Outcome? = null
        var message: String? = null
        JsonReader(StringReader(line)).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "t" -> timestamp = r.nextLong()
                    "p" -> packageName = r.nextString()
                    "n" -> appName = r.nextString()
                    "o" -> operation = Operation.valueOf(r.nextString())
                    "v" -> version = r.nextString()
                    "r" -> outcome = Outcome.valueOf(r.nextString())
                    "m" -> message = r.nextString()
                    else -> r.skipValue()
                }
            }
            r.endObject()
        }
        HistoryEntry(timestamp, packageName!!, appName, operation!!, version, outcome!!, message)
    }.getOrNull()

    fun encodeRepos(repos: List<RepoConfig>): String = json { w ->
        w.beginArray()
        for (repo in repos) {
            w.beginObject()
            w.name("id").value(repo.id)
            w.name("name").value(repo.name)
            w.name("address").value(repo.address)
            w.name("fingerprint").value(repo.fingerprint)
            w.name("enabled").value(repo.enabled)
            w.endObject()
        }
        w.endArray()
    }

    /** Decodes repositories, dropping any entry whose address or fingerprint is invalid. */
    fun decodeRepos(text: String): List<RepoConfig> = runCatching {
        val result = ArrayList<RepoConfig>()
        JsonReader(StringReader(text)).use { r ->
            r.beginArray()
            while (r.hasNext()) {
                var id: String? = null
                var name = ""
                var address: String? = null
                var fingerprint: String? = null
                var enabled = true
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "id" -> id = r.nextString()
                        "name" -> name = r.nextString()
                        "address" -> address = r.nextString()
                        "fingerprint" -> fingerprint = r.nextString()
                        "enabled" -> enabled = if (r.peek() == JsonToken.BOOLEAN) r.nextBoolean() else true.also { r.skipValue() }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                val validAddress = address?.let(RepoConfig::normalizeAddress)
                val validFingerprint = fingerprint?.let(RepoConfig::normalizeFingerprint)
                if (id != null && validAddress != null && validFingerprint != null) {
                    result += RepoConfig(id, name.ifBlank { validAddress }, validAddress, validFingerprint, enabled)
                }
            }
            r.endArray()
        }
        result
    }.getOrDefault(emptyList())

    private fun json(block: (JsonWriter) -> Unit): String {
        val out = StringWriter()
        JsonWriter(out).use(block)
        return out.toString()
    }
}
