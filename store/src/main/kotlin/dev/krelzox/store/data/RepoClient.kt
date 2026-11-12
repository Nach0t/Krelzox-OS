package dev.krelzox.store.data

import android.util.Log
import dev.krelzox.store.index.IndexParser
import dev.krelzox.store.index.IndexVerificationException
import dev.krelzox.store.index.IndexVerifier
import dev.krelzox.store.model.RepoConfig
import dev.krelzox.store.model.RepoIndex
import dev.krelzox.store.net.Http
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads, verifies and parses repository indexes. The signed JAR is cached on disk and
 * re-verified every time it is parsed; a download only replaces the cache after it has
 * passed verification and is not older than the index already accepted (anti-rollback).
 */
class RepoClient(
    private val cacheDir: File,
    private val preferences: StorePreferences,
    private val locales: List<String>,
) {

    sealed interface Result {
        data class Ok(val index: RepoIndex, val fromNetwork: Boolean) : Result
        data class Failed(val repo: RepoConfig, val reason: String, val cached: RepoIndex?) : Result
    }

    private fun jarFor(repo: RepoConfig) = File(cacheDir, "repos/${repo.id}/index-v1.jar")

    /** Parses the cached index without touching the network; null when there is none or it fails. */
    suspend fun loadCached(repo: RepoConfig): RepoIndex? = withContext(Dispatchers.IO) {
        val jar = jarFor(repo)
        if (!jar.exists()) return@withContext null
        runCatching { parse(repo, jar) }
            .onFailure { Log.w(TAG, "Cached index for ${repo.id} rejected", it) }
            .getOrNull()
    }

    /** Refreshes [repo] from the network (conditional request) and returns its index. */
    suspend fun refresh(repo: RepoConfig): Result = withContext(Dispatchers.IO) {
        val state = preferences.syncState(repo.id)
        val jar = jarFor(repo)
        val download = File(jar.path + ".download")
        try {
            val response = Http.get(
                repo.indexUrl,
                etag = state.etag.takeIf { jar.exists() },
                ifModifiedSince = state.lastModified.takeIf { jar.exists() },
            )
            try {
                when (response.code) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> {
                        preferences.saveSyncState(repo.id, state.copy(checkedAt = System.currentTimeMillis()))
                        val cached = parse(repo, jar)
                        return@withContext Result.Ok(cached, fromNetwork = false)
                    }
                    HttpURLConnection.HTTP_OK -> {
                        download.parentFile?.mkdirs()
                        response.connection.inputStream.use { input ->
                            download.outputStream().use { input.copyTo(it, 64 * 1024) }
                        }
                    }
                    else -> throw IOException("HTTP ${response.code}")
                }
                val fresh = parse(repo, download)
                if (fresh.repo.timestamp < state.indexTimestamp) {
                    throw IndexVerificationException("Index is older than the one already accepted")
                }
                if (!download.renameTo(jar)) {
                    jar.delete()
                    if (!download.renameTo(jar)) throw IOException("Could not store the index")
                }
                preferences.saveSyncState(
                    repo.id,
                    RepoSyncState(
                        etag = response.connection.getHeaderField("ETag"),
                        lastModified = response.connection.lastModified.takeIf { it > 0 },
                        indexTimestamp = fresh.repo.timestamp,
                        checkedAt = System.currentTimeMillis(),
                    ),
                )
                Result.Ok(fresh, fromNetwork = true)
            } finally {
                response.connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Refreshing ${repo.id} failed", e)
            download.delete()
            Result.Failed(repo, reasonFor(e), loadCached(repo))
        }
    }

    private fun parse(repo: RepoConfig, jar: File): RepoIndex =
        IndexVerifier.readVerified(jar, repo.fingerprint) { stream ->
            IndexParser(repo.id, repo.address, locales).parse(stream.bufferedReader(Charsets.UTF_8))
        }

    private fun reasonFor(e: Exception): String = when (e) {
        is SecurityException -> "verification"
        is IOException -> "network"
        else -> "format"
    }

    private companion object {
        const val TAG = "RepoClient"
    }
}
