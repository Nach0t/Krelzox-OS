package dev.krelzox.store.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.krelzox.store.model.RepoConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.storeDataStore by preferencesDataStore(name = "krelzox_store")

/** Small persisted state: repositories, favorites, last section and per-repo sync state. */
class StorePreferences(private val context: Context) {

    private val store get() = context.storeDataStore

    val favorites: Flow<Set<String>> = store.data.map { it[FAVORITES].orEmpty() }

    val repos: Flow<List<RepoConfig>> = store.data.map { prefs ->
        prefs[REPOS]?.let(Codecs::decodeRepos)?.takeIf { it.isNotEmpty() } ?: listOf(RepoConfig.FDROID)
    }

    suspend fun lastSection(): String? = store.data.first()[LAST_SECTION]

    suspend fun setLastSection(section: String) {
        store.edit { it[LAST_SECTION] = section }
    }

    suspend fun toggleFavorite(packageName: String) {
        store.edit { prefs ->
            val current = prefs[FAVORITES].orEmpty()
            prefs[FAVORITES] = if (packageName in current) current - packageName else current + packageName
        }
    }

    suspend fun saveRepos(repos: List<RepoConfig>) {
        store.edit { it[REPOS] = Codecs.encodeRepos(repos) }
    }

    suspend fun syncState(repoId: String): RepoSyncState {
        val prefs = store.data.first()
        return RepoSyncState(
            etag = prefs[stringPreferencesKey("etag_$repoId")],
            lastModified = prefs[longPreferencesKey("modified_$repoId")],
            indexTimestamp = prefs[longPreferencesKey("timestamp_$repoId")] ?: 0L,
            checkedAt = prefs[longPreferencesKey("checked_$repoId")] ?: 0L,
        )
    }

    suspend fun saveSyncState(repoId: String, state: RepoSyncState) {
        store.edit { prefs ->
            val etagKey = stringPreferencesKey("etag_$repoId")
            val modifiedKey = longPreferencesKey("modified_$repoId")
            if (state.etag != null) prefs[etagKey] = state.etag else prefs.remove(etagKey)
            if (state.lastModified != null) prefs[modifiedKey] = state.lastModified else prefs.remove(modifiedKey)
            prefs[longPreferencesKey("timestamp_$repoId")] = state.indexTimestamp
            prefs[longPreferencesKey("checked_$repoId")] = state.checkedAt
        }
    }

    private companion object {
        val FAVORITES = stringSetPreferencesKey("favorites")
        val REPOS = stringPreferencesKey("repos")
        val LAST_SECTION = stringPreferencesKey("last_section")
    }
}

/** HTTP cache validators and the newest index timestamp accepted for a repository. */
data class RepoSyncState(
    val etag: String?,
    val lastModified: Long?,
    val indexTimestamp: Long,
    val checkedAt: Long,
)

/** Append-only operations log (JSON lines), trimmed to the newest [MAX_ENTRIES]. */
class HistoryStore(context: Context) {

    private val file = File(context.filesDir, "history.jsonl")
    private val lock = Mutex()

    suspend fun read(): List<HistoryEntry> = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!file.exists()) return@withLock emptyList()
            file.readLines().mapNotNull(Codecs::decodeHistoryLine).sortedByDescending { it.timestamp }
        }
    }

    suspend fun append(entry: HistoryEntry) = withContext(Dispatchers.IO) {
        lock.withLock {
            val lines = if (file.exists()) file.readLines() else emptyList()
            val kept = (lines + Codecs.encodeHistoryLine(entry)).takeLast(MAX_ENTRIES)
            val tmp = File(file.path + ".tmp")
            tmp.writeText(kept.joinToString("\n", postfix = "\n"))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    private companion object {
        const val MAX_ENTRIES = 500
    }
}
